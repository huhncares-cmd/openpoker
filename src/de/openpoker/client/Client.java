package de.openpoker.client;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import javax.swing.SwingUtilities;
import de.openpoker.client.ui.PokerWindow;
import de.openpoker.common.network.GameStateDTO;
import de.openpoker.common.network.PlayerAction;

public class Client {
    private static final String HOST = "localhost";
    private static final int PORT = 8888;
    private ObjectOutputStream out;

    public void start() {
        SwingUtilities.invokeLater(() -> {
            PokerWindow window = new PokerWindow();
            window.setVisible(true);

            window.setActionListener(this::sendActionToServer);

            new Thread(() -> {
                try {
                    Socket socket = new Socket(HOST, PORT);
                    out = new ObjectOutputStream(socket.getOutputStream());
                    ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

                    System.out.println("Erfolgreich mit dem Server verbunden!");

                    while (true) {
                        GameStateDTO state = (GameStateDTO) in.readObject();
                        SwingUtilities.invokeLater(() -> window.updateGameState(state));
                    }
                } catch (Exception e) {
                    System.err.println("Verbindung beendet: " + e.getMessage());
                }
            }).start();
        });
    }

    private void sendActionToServer(PlayerAction action) {
        if (out != null) {
            try {
                out.writeObject(action);
                out.flush();
                System.out.println("Aktion an Server gesendet: " + action.getClass().getSimpleName());
            } catch (Exception e) {
                System.err.println("Fehler beim Senden der Aktion: " + e.getMessage());
            }
        }
    }

    public static void main(String[] args) {
        new Client().start();
    }
}
