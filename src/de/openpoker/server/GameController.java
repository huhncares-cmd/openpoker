package de.openpoker.server;

import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import de.openpoker.common.network.GameStateDTO;
import de.openpoker.common.network.PlayerAction;

public class GameController {
    private final GameTable gameTable = new GameTable();
    private GamePhase currentPhase = GamePhase.WAITING_FOR_PLAYERS;
    private final List<String> chatHistory = Collections.synchronizedList(new ArrayList<>());
    private final List<Player> connectedPlayers = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, String> playerLastActions = new ConcurrentHashMap<>();
    private int activePlayerIndex = 0;
    private int actionsInCurrentPhase = 0;

    public GameController() {
        chatHistory.add("System: Willkommen beim OpenPoker Server!");
    }

    public synchronized Player addPlayer(String name, ObjectOutputStream out) {
        String id = "P" + (connectedPlayers.size() + 1);
        Player player = new Player(id, name, 1000, out);

        dealCardsToPlayer(player);
        connectedPlayers.add(player);
        chatHistory.add("System: " + name + " (" + id + ") ist dem Tisch beigetreten.");

        if (connectedPlayers.size() >= 2 && currentPhase == GamePhase.WAITING_FOR_PLAYERS) {
            startNewRound();
        } else {
            broadcastGameState("Warte auf zweiten Spieler...");
        }

        return player;
    }

    public synchronized void removePlayer(Player player) {
        connectedPlayers.remove(player);
        playerLastActions.remove(player.getName());
        chatHistory.add("System: " + player.getName() + " hat den Tisch verlassen.");
        if (connectedPlayers.size() < 2) {
            currentPhase = GamePhase.WAITING_FOR_PLAYERS;
        }
        broadcastGameState(player.getName() + " ist gegangen.");
    }

    private void dealCardsToPlayer(Player player) {
        player.getCards().clear();
        player.setFolded(false);
        if (gameTable.getDeck().remainingCards() >= 2) {
            player.getCards().add(gameTable.getDeck().drawCard());
            player.getCards().add(gameTable.getDeck().drawCard());
        }
    }

    public synchronized void startNewRound() {
        gameTable.getDeck().reset();
        gameTable.getCommunityCards().clear();
        gameTable.setPot(0);
        playerLastActions.clear();

        for (Player p : connectedPlayers) {
            dealCardsToPlayer(p);
        }

        currentPhase = GamePhase.PREFLOP;
        activePlayerIndex = 0;
        actionsInCurrentPhase = 0;
        chatHistory.add("System: Neue Runde gestartet! Phase: PREFLOP");
        broadcastGameState("Neue Runde gestartet!");
    }

    public synchronized void handleAction(Player player, PlayerAction action) {
        String sender = player.getName();

        if (action instanceof PlayerAction.Chat c) {
            if (c.message() != null && !c.message().isBlank()) {
                chatHistory.add(sender + ": " + c.message());
            }
            broadcastGameState("Chat-Nachricht empfangen");
            return;
        }

        if (action instanceof PlayerAction.NextRound) {
            if (currentPhase == GamePhase.SHOWDOWN) {
                startNewRound();
            }
            return;
        }

        if (connectedPlayers.size() < 2) {
            chatHistory.add("System: Mindestens 2 Spieler erforderlich!");
            broadcastGameState("Zu wenige Spieler");
            return;
        }

        Player currentActive = connectedPlayers.get(activePlayerIndex % connectedPlayers.size());
        if (!currentActive.getId().equals(player.getId())) {
            System.out.println("Ignoriere Aktion von " + player.getName() + " - " + currentActive.getName() + " ist am Zug!");
            return;
        }

        switch (action) {
            case PlayerAction.Raise r -> {
                int amount = r.amount() > 0 ? r.amount() : 50;
                player.setChips(player.getChips() - amount);
                gameTable.addPot(amount);
                playerLastActions.put(sender, "RAISE +" + amount);
                chatHistory.add("System: " + sender + " erhöht um " + amount + " Chips.");
            }
            case PlayerAction.Call c -> {
                playerLastActions.put(sender, "CALL");
                chatHistory.add("System: " + sender + " geht mit (CALL).");
            }
            case PlayerAction.Check c -> {
                playerLastActions.put(sender, "CHECK");
                chatHistory.add("System: " + sender + " schiebt (CHECK).");
            }
            case PlayerAction.Fold f -> {
                player.setFolded(true);
                playerLastActions.put(sender, "FOLD");
                chatHistory.add("System: " + sender + " passt (FOLD).");

                long activeUnfolded = connectedPlayers.stream().filter(p -> !p.isFolded()).count();

                if (activeUnfolded <= 1) {
                    currentPhase = GamePhase.SHOWDOWN;
                    Player winner = connectedPlayers.stream().filter(p -> !p.isFolded()).findFirst().orElse(null);
                    if (winner != null) {
                        winner.setChips(winner.getChips() + gameTable.getPot());
                        chatHistory.add("🏆 SYSTEM: " + winner.getName() + " gewinnt " + gameTable.getPot() + " Chips (alle anderen haben gepasst)!");
                    }
                    broadcastGameState("Rundenende! Klicke 'Nächste Runde'.");
                    return;
                }
            }
            default -> {}
        }

        actionsInCurrentPhase++;

        advanceToNextActivePlayer();

        List<Player> activeUnfoldedPlayers = connectedPlayers.stream().filter(p -> !p.isFolded()).toList();
        if (actionsInCurrentPhase >= activeUnfoldedPlayers.size()) {
            actionsInCurrentPhase = 0;
            advancePhase();
        } else {
            Player nextActive = connectedPlayers.get(activePlayerIndex % connectedPlayers.size());
            broadcastGameState("Zug wechselt zu " + nextActive.getName());
        }
    }

    private void advanceToNextActivePlayer() {
        if (connectedPlayers.isEmpty()) return;
        do {
            activePlayerIndex = (activePlayerIndex + 1) % connectedPlayers.size();
        } while (connectedPlayers.get(activePlayerIndex % connectedPlayers.size()).isFolded()
                 && connectedPlayers.stream().anyMatch(p -> !p.isFolded()));
    }

    private void advancePhase() {
        switch (currentPhase) {
            case PREFLOP -> {
                currentPhase = GamePhase.FLOP;
                gameTable.dealCommunityCard();
                gameTable.dealCommunityCard();
                gameTable.dealCommunityCard();
                chatHistory.add("System: 3 Tischkarten aufgedeckt. Phase: FLOP");
            }
            case FLOP -> {
                currentPhase = GamePhase.TURN;
                gameTable.dealCommunityCard();
                chatHistory.add("System: 4. Tischkarte aufgedeckt. Phase: TURN");
            }
            case TURN -> {
                currentPhase = GamePhase.RIVER;
                gameTable.dealCommunityCard();
                chatHistory.add("System: 5. Tischkarte aufgedeckt. Phase: RIVER");
            }
            case RIVER -> {
                currentPhase = GamePhase.SHOWDOWN;

                Player winner = null;
                HandEvaluator.HandResult bestResult = null;

                for (Player p : connectedPlayers) {
                    if (p.isFolded()) continue;

                    HandEvaluator.HandResult result = HandEvaluator.evaluateHand(p.getCards(), gameTable.getCommunityCards());
                    chatHistory.add("System: " + p.getName() + " zeigt: " + result.description());

                    if (bestResult == null || result.compareTo(bestResult) > 0) {
                        bestResult = result;
                        winner = p;
                    }
                }

                if (winner != null && bestResult != null) {
                    winner.setChips(winner.getChips() + gameTable.getPot());
                    chatHistory.add("🏆 SYSTEM: " + winner.getName() + " gewinnt " + gameTable.getPot() + " Chips mit " + bestResult.description() + "!");
                }
            }
            case SHOWDOWN, WAITING_FOR_PLAYERS -> {}
        }
        broadcastGameState("Neue Phase: " + currentPhase.name());
    }

    public synchronized void broadcastGameState(String statusMessage) {
        String activePlayerName = connectedPlayers.isEmpty() ? "-" : connectedPlayers.get(activePlayerIndex % connectedPlayers.size()).getName();
        List<String> playerNames = connectedPlayers.stream().map(p -> p.getName() + (p.isFolded() ? " [FOLDED]" : "") + " (" + p.getChips() + " Chips)").toList();

        for (Player p : connectedPlayers) {
            try {
                GameStateDTO dto = new GameStateDTO(
                    gameTable.getPot(),
                    new ArrayList<>(gameTable.getCommunityCards()),
                    new ArrayList<>(p.getCards()),
                    p.getName(),
                    activePlayerName,
                    playerNames,
                    new HashMap<>(playerLastActions),
                    statusMessage,
                    new ArrayList<>(chatHistory)
                );
                p.getOut().writeObject(dto);
                p.getOut().flush();
            } catch (Exception e) {
                System.err.println("Fehler beim Senden an " + p.getName() + ": " + e.getMessage());
            }
        }
    }
}
