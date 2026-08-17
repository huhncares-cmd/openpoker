package de.openpoker.server;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicInteger;
import de.openpoker.common.network.PlayerAction;

public class Server {
    private static final int PORT = 8888;
    private final GameController gameController = new GameController();
    private final AtomicInteger playerCounter = new AtomicInteger(1);

    public void start() {
        System.out.println("Poker Server startet auf Port " + PORT + "...");
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Neuer Client verbunden: " + clientSocket.getInetAddress());
                new Thread(() -> handleClient(clientSocket)).start();
            }
        } catch (IOException e) {
            System.err.println("Server-Fehler: " + e.getMessage());
        }
    }

    private void handleClient(Socket socket) {
        String playerName = "Spieler " + playerCounter.getAndIncrement();
        Player player = null;

        try {
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

            player = gameController.addPlayer(playerName, out);

            while (true) {
                PlayerAction action = (PlayerAction) in.readObject();
                gameController.handleAction(player, action);
            }

        } catch (Exception e) {
            System.out.println(playerName + " getrennt: " + e.getMessage());
        } finally {
            if (player != null) {
                gameController.removePlayer(player);
            }
        }
    }

    public static void main(String[] args) {
        new Server().start();
    }
}
