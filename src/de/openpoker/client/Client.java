package de.openpoker.client;

import java.awt.GridLayout;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import de.openpoker.client.ui.PokerWindow;
import de.openpoker.common.network.GameStateDTO;
import de.openpoker.common.network.PlayerAction;

public final class Client {
    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 8888;
    private static final int CONNECT_TIMEOUT_MS = 5_000;

    private final String playerName;
    private final String host;
    private final int port;

    private volatile boolean closed;
    private volatile Socket socket;
    private volatile ObjectOutputStream out;
    private volatile PokerWindow window;

    public Client(String host, int port) {
        this("Spieler", host, port);
    }

    public Client(String playerName, String host, int port) {
        this.playerName = playerName.trim();
        this.host = host;
        this.port = port;
    }

    public void start() {
        SwingUtilities.invokeLater(() -> {
            PokerWindow pokerWindow = new PokerWindow();
            window = pokerWindow;
            pokerWindow.setTitle("OpenPoker Client - " + playerName);
            pokerWindow.setActionListener(action -> sendActionToServer(action));
            pokerWindow.setCloseHandler(() -> close());
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
            if (closed) {
                return;
            }

            try (ObjectOutputStream output = new ObjectOutputStream(connection.getOutputStream())) {
                output.flush();
                output.writeObject(playerName);
                output.flush();

                try (ObjectInputStream input = new ObjectInputStream(connection.getInputStream())) {
                    out = output;
                    onUiThread(() -> pokerWindow.setConnectionState(true, "Mit Server verbunden (" + playerName + ")"));

                    while (!closed) {
                        Object message = input.readObject();
                        if (message instanceof GameStateDTO) {
                            GameStateDTO state = (GameStateDTO) message;
                            onUiThread(() -> pokerWindow.updateGameState(state));
                        }
                    }
                }
            }
        } catch (Exception exception) {
            disconnectMessage = "Verbindungsfehler: " + exception.getMessage();
            if (!closed) {
                System.err.println(disconnectMessage);
            }
        } finally {
            out = null;
            socket = null;
            if (!closed) {
                String message = disconnectMessage;
                onUiThread(() -> pokerWindow.setConnectionState(false, message));
            }
        }
    }

    private synchronized boolean sendActionToServer(PlayerAction action) {
        ObjectOutputStream output = out;
        if (output == null || closed) {
            return false;
        }

        try {
            output.writeObject(action);
            output.reset();
            output.flush();
            return true;
        } catch (IOException exception) {
            out = null;
            close();
            String message = "Senden fehlgeschlagen: " + exception.getMessage();
            System.err.println(message);
            onUiThread(() -> window.setConnectionState(false, message));
            return false;
        }
    }

    private void onUiThread(Runnable action) {
        SwingUtilities.invokeLater(() -> {
            if (!closed) {
                action.run();
            }
        });
    }

    public synchronized void close() {
        if (!closed) {
            closed = true;
            out = null;
            Socket connection = socket;
            socket = null;
            if (connection != null) {
                try {
                    connection.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private record ConnectionConfig(String name, String host, int port) {}

    private static ConnectionConfig promptConnectionSettings() {
        JTextField nameField = new JTextField(System.getProperty("user.name", "Spieler"), 12);
        JTextField hostField = new JTextField(DEFAULT_HOST, 12);
        JTextField portField = new JTextField(String.valueOf(DEFAULT_PORT), 6);

        JPanel panel = new JPanel(new GridLayout(3, 2, 8, 8));
        panel.add(new JLabel("Spielername:"));
        panel.add(nameField);
        panel.add(new JLabel("Server-Adresse:"));
        panel.add(hostField);
        panel.add(new JLabel("Port:"));
        panel.add(portField);

        int result = JOptionPane.showConfirmDialog(
            null,
            panel,
            "OpenPoker – Tisch beitreten",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE
        );

        if (result != JOptionPane.OK_OPTION) {
            return null;
        }

        String name = nameField.getText().trim();
        String host = hostField.getText().trim();
        int port = Integer.parseInt(portField.getText().trim());

        return new ConnectionConfig(name, host, port);
    }

    public static void main(String[] args) {
        String name = "Spieler";
        String host = DEFAULT_HOST;
        int port = DEFAULT_PORT;

        if (args.length >= 3) {
            name = args[0];
            host = args[1];
            port = Integer.parseInt(args[2]);
        } else if (args.length == 2) {
            host = args[0];
            port = Integer.parseInt(args[1]);
        } else if (args.length == 1) {
            host = args[0];
        } else {
            ConnectionConfig config = promptConnectionSettings();
            if (config == null) {
                return;
            }
            name = config.name();
            host = config.host();
            port = config.port();
        }

        new Client(name, host, port).start();
    }
}
