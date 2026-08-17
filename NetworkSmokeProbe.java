import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

import de.openpoker.common.model.GamePhase;
import de.openpoker.common.network.GameStateDTO;
import de.openpoker.common.network.PlayerAction;

public final class NetworkSmokeProbe {
    private record Peer(Socket socket, ObjectOutputStream out, ObjectInputStream in) implements AutoCloseable {
        private static Peer connect(int port) throws Exception {
            Socket socket = new Socket("127.0.0.1", port);
            socket.setSoTimeout(3_000);
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            return new Peer(socket, out, new ObjectInputStream(socket.getInputStream()));
        }

        private GameStateDTO read() throws Exception {
            return (GameStateDTO) in.readObject();
        }

        private void send(PlayerAction action) throws Exception {
            out.writeObject(action);
            out.reset();
            out.flush();
        }

        @Override
        public void close() throws Exception {
            socket.close();
        }
    }

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(args[0]);
        try (Peer first = Peer.connect(port)) {
            GameStateDTO waiting = first.read();
            if (waiting.phase() != GamePhase.WAITING_FOR_PLAYERS) {
                throw new AssertionError("Erster Client wartet nicht.");
            }

            try (Peer second = Peer.connect(port)) {
                GameStateDTO firstStart = first.read();
                GameStateDTO secondStart = second.read();
                boolean firstActive = firstStart.players().stream()
                    .anyMatch(player -> player.id().equals(firstStart.myPlayerId()) && player.active());
                Peer active = firstActive ? first : second;
                Peer caller = firstActive ? second : first;

                active.send(new PlayerAction.Raise(50));
                GameStateDTO firstRaised = first.read();
                GameStateDTO secondRaised = second.read();
                if (firstRaised.pot() != 50 || secondRaised.currentBet() != 50) {
                    throw new AssertionError("Raise wurde nicht korrekt übertragen.");
                }

                caller.send(new PlayerAction.Call());
                GameStateDTO firstFlop = first.read();
                GameStateDTO secondFlop = second.read();
                if (firstFlop.phase() != GamePhase.FLOP || secondFlop.pot() != 100) {
                    throw new AssertionError("Call/Phasenwechsel ist inkorrekt.");
                }
            }
        }
        System.out.println("network_smoke_ok");
    }
}
