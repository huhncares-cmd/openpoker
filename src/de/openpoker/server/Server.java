package de.openpoker.server;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import de.openpoker.common.network.PlayerAction;

public final class Server {
    private static final int DEFAULT_PORT = 8888;
    private final int port;
    private final GameController gameController = new GameController();
    private int playerCounter = 1;

    public Server(int port) {
        this.port = port;
    }

    public void start() throws IOException {
        System.out.println("Poker Server startet auf Port " + port + "...");
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Neuer Client verbunden: " + clientSocket.getInetAddress());
                new Thread(() -> handleClient(clientSocket), "poker-client-handler").start();
            }
        }
    }

    private void handleClient(Socket socket) {
        String defaultName;
        synchronized (this) {
            defaultName = "Spieler " + playerCounter++;
        }
        String playerName = defaultName;
        Player player = null;

        try (Socket clientSocket = socket;
             ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(clientSocket.getInputStream())) {
            out.flush();

            Object initial = in.readObject();
            if (initial instanceof String) {
                String customName = (String) initial;
                if (!customName.isBlank()) {
                    playerName = customName.trim();
                    if (playerName.length() > 20) {
                        playerName = playerName.substring(0, 20);
                    }
                }
            }
            player = gameController.addPlayer(playerName, out);

            while (!clientSocket.isClosed()) {
                Object message = in.readObject();
                if (message instanceof PlayerAction) {
                    PlayerAction action = (PlayerAction) message;
                    gameController.handleAction(player, action);
                }
            }
        } catch (EOFException exception) {
            System.out.println(playerName + " getrennt (Verbindung beendet).");
        } catch (Exception exception) {
            String reason = exception.getMessage();
            if (reason == null) {
                reason = exception.getClass().getSimpleName();
            }
            System.out.println(playerName + " getrennt (" + reason + ").");
        } finally {
            if (player != null) {
                gameController.removePlayer(player);
            }
        }
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
