package de.openpoker.server;

import java.io.IOException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicInteger;
import de.openpoker.common.network.PlayerAction;

public final class Server {
    private static final int DEFAULT_PORT = 8888;
    private final int port;
    private final GameController gameController = new GameController();
    private final AtomicInteger playerCounter = new AtomicInteger(1);

    public Server(int port) {
        this.port = port;
    }

    public void start() throws IOException {
        System.out.println("Poker Server startet auf Port " + port + "...");
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Neuer Client verbunden: " + clientSocket.getInetAddress());
                Thread.startVirtualThread(() -> handleClient(clientSocket));
            }
        }
    }

    private void handleClient(Socket socket) {
        String defaultName = "Spieler " + playerCounter.getAndIncrement();
        String playerName = defaultName;
        Player player = null;

        try (socket;
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {
            out.flush();
            in.setObjectInputFilter(Server::filterClientMessage);

            Object initial = in.readObject();
            if (initial instanceof String customName && !customName.isBlank()) {
                playerName = customName.trim();
                if (playerName.length() > 20) {
                    playerName = playerName.substring(0, 20);
                }
                player = gameController.addPlayer(playerName, out);
            } else if (initial instanceof PlayerAction action) {
                player = gameController.addPlayer(playerName, out);
                gameController.handleAction(player, action);
            } else {
                player = gameController.addPlayer(playerName, out);
            }

            while (!socket.isClosed()) {
                Object message = in.readObject();
                if (message instanceof PlayerAction action) {
                    gameController.handleAction(player, action);
                }
            }
        } catch (Exception e) {
            System.out.println(playerName + " getrennt: " + e.getMessage());
        } finally {
            if (player != null) {
                gameController.removePlayer(player);
            }
        }
    }

    private static ObjectInputFilter.Status filterClientMessage(ObjectInputFilter.FilterInfo info) {
        if (info.depth() > 10 || info.arrayLength() > 1_024) {
            return ObjectInputFilter.Status.REJECTED;
        }

        Class<?> type = info.serialClass();
        if (type == null) {
            return ObjectInputFilter.Status.UNDECIDED;
        }
        return type == String.class || PlayerAction.class.isAssignableFrom(type)
            ? ObjectInputFilter.Status.ALLOWED
            : ObjectInputFilter.Status.REJECTED;
    }

    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
                if (port < 1 || port > 65_535) {
                    throw new NumberFormatException();
                }
            } catch (NumberFormatException exception) {
                System.err.println("Ungültiger Port: " + args[0]);
                System.exit(2);
            }
        }

        try {
            new Server(port).start();
        } catch (IOException exception) {
            System.err.println("Server-Fehler: " + exception.getMessage());
            System.exit(1);
        }
    }
}
