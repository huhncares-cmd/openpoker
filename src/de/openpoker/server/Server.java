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
        String playerName = "Spieler";
        Player player = null;

        try (Socket clientSocket = socket;
             ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(clientSocket.getInputStream())) {
            out.flush();

            playerName = ((String) in.readObject()).trim();
            player = gameController.addPlayer(playerName, out);

            while (!clientSocket.isClosed()) {
                PlayerAction action = (PlayerAction) in.readObject();
                gameController.handleAction(player, action);
            }
        } catch (EOFException exception) {
            System.out.println(playerName + " getrennt (Verbindung beendet).");
        } catch (Exception exception) {
            System.out.println(playerName + " getrennt (" + exception.getMessage() + ").");
        } finally {
            if (player != null) {
                gameController.removePlayer(player);
            }
        }
    }

    public static void main(String[] args) throws IOException {
        new Server(DEFAULT_PORT).start();
    }
}
