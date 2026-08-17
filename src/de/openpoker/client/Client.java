package de.openpoker.client;

import java.io.IOException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.SwingUtilities;
import de.openpoker.client.ui.PokerWindow;
import de.openpoker.common.network.GameStateDTO;
import de.openpoker.common.network.PlayerAction;

public final class Client implements AutoCloseable {
    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 8888;
    private static final int CONNECT_TIMEOUT_MS = 5_000;

    private final String host;
    private final int port;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final ExecutorService writer = new ThreadPoolExecutor(
        1,
        1,
        0L,
        TimeUnit.MILLISECONDS,
        new ArrayBlockingQueue<>(100),
        task -> {
            Thread thread = new Thread(task, "poker-writer");
            thread.setDaemon(true);
            return thread;
        },
        new ThreadPoolExecutor.AbortPolicy());

    private volatile Socket socket;
    private volatile ObjectOutputStream out;
    private volatile PokerWindow window;

    public Client(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void start() {
        SwingUtilities.invokeLater(() -> {
            PokerWindow pokerWindow = new PokerWindow();
            window = pokerWindow;
            pokerWindow.setActionListener(this::sendActionToServer);
            pokerWindow.setCloseHandler(this::close);
            pokerWindow.setConnectionState(false, "Verbinde mit " + host + ":" + port + "...");
            pokerWindow.setVisible(true);

            Thread reader = new Thread(() -> receiveGameStates(pokerWindow), "poker-reader");
            reader.setDaemon(true);
            reader.start();
        });
    }

    private void receiveGameStates(PokerWindow pokerWindow) {
        String disconnectMessage = "Verbindung zum Server beendet";
        try (Socket connection = new Socket()) {
            socket = connection;
            connection.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            if (closed.get()) {
                return;
            }

            try (ObjectOutputStream output = new ObjectOutputStream(connection.getOutputStream())) {
                output.flush();
                try (ObjectInputStream input = new ObjectInputStream(connection.getInputStream())) {
                    input.setObjectInputFilter(Client::filterServerMessage);
                    out = output;
                    onUiThread(() -> pokerWindow.setConnectionState(true, "Mit Server verbunden"));

                    while (!closed.get()) {
                        Object message = input.readObject();
                        if (message instanceof GameStateDTO state) {
                            onUiThread(() -> pokerWindow.updateGameState(state));
                        }
                    }
                }
            }
        } catch (Exception exception) {
            disconnectMessage = "Verbindungsfehler: " + errorMessage(exception);
            if (!closed.get()) {
                System.err.println(disconnectMessage);
            }
        } finally {
            out = null;
            socket = null;
            if (!closed.get()) {
                String message = disconnectMessage;
                onUiThread(() -> pokerWindow.setConnectionState(false, message));
            }
        }
    }

    private boolean sendActionToServer(PlayerAction action) {
        if (out == null || closed.get()) {
            return false;
        }
        try {
            writer.execute(() -> writeAction(action));
            return true;
        } catch (RejectedExecutionException ignored) {
            System.err.println("Aktion verworfen: Sendewarteschlange ist voll oder der Client wird beendet.");
            return false;
        }
    }

    private void writeAction(PlayerAction action) {
        ObjectOutputStream output = out;
        if (output == null || closed.get()) {
            return;
        }

        try {
            output.writeObject(action);
            output.reset();
            output.flush();
        } catch (IOException exception) {
            out = null;
            closeSocket();
            String message = "Senden fehlgeschlagen: " + errorMessage(exception);
            System.err.println(message);
            PokerWindow pokerWindow = window;
            if (pokerWindow != null) {
                onUiThread(() -> pokerWindow.setConnectionState(false, message));
            }
        }
    }

    private void onUiThread(Runnable action) {
        SwingUtilities.invokeLater(() -> {
            if (!closed.get()) {
                action.run();
            }
        });
    }

    private static String errorMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    private static ObjectInputFilter.Status filterServerMessage(ObjectInputFilter.FilterInfo info) {
        if (info.depth() > 25 || info.arrayLength() > 10_000) {
            return ObjectInputFilter.Status.REJECTED;
        }

        Class<?> type = info.serialClass();
        if (type == null) {
            return ObjectInputFilter.Status.UNDECIDED;
        }
        String className = type.getName();
        return className.startsWith("de.openpoker.common.")
                || className.startsWith("java.lang.")
                || className.startsWith("java.util.")
                || type.isArray()
            ? ObjectInputFilter.Status.ALLOWED
            : ObjectInputFilter.Status.REJECTED;
    }

    private void closeSocket() {
        Socket connection = socket;
        socket = null;
        if (connection != null) {
            try {
                connection.close();
            } catch (IOException ignored) {
                // Die Verbindung ist bereits beendet.
            }
        }
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            out = null;
            closeSocket();
            writer.shutdownNow();
        }
    }

    public static void main(String[] args) {
        String host = args.length > 0 && !args[0].isBlank() ? args[0] : DEFAULT_HOST;
        int port = DEFAULT_PORT;
        if (args.length > 1) {
            try {
                port = Integer.parseInt(args[1]);
                if (port < 1 || port > 65_535) {
                    throw new NumberFormatException();
                }
            } catch (NumberFormatException exception) {
                System.err.println("Ungültiger Port: " + args[1]);
                return;
            }
        }
        new Client(host, port).start();
    }
}
