package de.openpoker.server;

import java.io.ObjectOutputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import de.openpoker.common.model.Card;
import de.openpoker.common.model.GamePhase;
import de.openpoker.common.network.GameStateDTO;
import de.openpoker.common.network.PlayerAction;
import de.openpoker.common.network.PlayerStateDTO;

public final class GameController {
    static final int STARTING_CHIPS = 1_000;
    static final int MAX_PLAYERS = 10;
    static final int MIN_RAISE = 50;
    private static final int CHAT_HISTORY_LIMIT = 100;
    private static final int CHAT_MESSAGE_LIMIT = 500;

    private final GameTable gameTable = new GameTable();
    private final List<Player> connectedPlayers = new ArrayList<>();
    private final List<Player> handPlayers = new ArrayList<>();
    private final ArrayDeque<String> chatHistory = new ArrayDeque<>();
    private final Map<String, String> playerLastActions = new HashMap<>();
    private final Set<String> pendingPlayerIds = new HashSet<>();

    private GamePhase currentPhase = GamePhase.WAITING_FOR_PLAYERS;
    private int activePlayerIndex = -1;
    private int currentBet;
    private long turnId;
    private long nextPlayerId = 1;
    private String previousStarterId;

    public GameController() {
        addChat("System: Willkommen beim OpenPoker Server!");
    }

    public synchronized Player addPlayer(String name, ObjectOutputStream out) {
        if (connectedPlayers.size() >= MAX_PLAYERS) {
            throw new IllegalStateException("Der Tisch ist voll (maximal " + MAX_PLAYERS + " Spieler).");
        }

        String id = "P" + nextPlayerId++;
        Player player = new Player(id, name, STARTING_CHIPS, out);
        player.prepareForHand(false);
        connectedPlayers.add(player);
        addChat("System: " + name + " (" + id + ") ist dem Tisch beigetreten.");

        if (currentPhase == GamePhase.WAITING_FOR_PLAYERS && playersReadyForHand() >= 2) {
            startNewRoundInternal();
        } else {
            String status = currentPhase != GamePhase.WAITING_FOR_PLAYERS
                ? "Du steigst in der nächsten Runde ein."
                : "Warte auf genügend Spieler für die nächste Runde.";
            broadcastGameState(status);
        }
        return player;
    }

    public synchronized void removePlayer(Player player) {
        int removedIndex = connectedPlayers.indexOf(player);
        if (removedIndex < 0) {
            return;
        }

        boolean wasActive = removedIndex == activePlayerIndex;
        if (player.isInHand() && !player.isFolded() && !player.isAllIn() && currentPhase.isBettingPhase()) {
            player.setFolded(true);
            playerLastActions.put(player.getId(), "FOLD (getrennt)");
        }
        if (currentPhase.isBettingPhase()) {
            turnId++;
        }
        pendingPlayerIds.remove(player.getId());
        connectedPlayers.remove(removedIndex);
        player.closeSender();
        addChat("System: " + player.getName() + " hat den Tisch verlassen.");

        if (connectedPlayers.isEmpty()) {
            activePlayerIndex = -1;
        } else if (removedIndex < activePlayerIndex) {
            activePlayerIndex--;
        } else if (wasActive) {
            activePlayerIndex = removedIndex - 1;
        }

        if (currentPhase.isBettingPhase()) {
            if (contenders().size() <= 1) {
                finishHandAfterFolds("Runde durch Verbindungsabbruch beendet.");
                if (connectedPlayers.size() < 2) {
                    currentPhase = GamePhase.WAITING_FOR_PLAYERS;
                }
            } else if (pendingPlayerIds.isEmpty()) {
                advancePhaseUntilActionIsNeeded();
                return;
            } else if (wasActive || activePlayerIndex < 0
                    || !pendingPlayerIds.contains(connectedPlayers.get(activePlayerIndex).getId())) {
                activePlayerIndex = findNextPendingIndex(activePlayerIndex);
            }
        } else if (connectedPlayers.size() < 2) {
            currentPhase = GamePhase.WAITING_FOR_PLAYERS;
            activePlayerIndex = -1;
        }

        broadcastGameState(connectedPlayers.size() < 2
            ? "Warte auf einen weiteren Spieler."
            : player.getName() + " ist gegangen.");
    }

    private void startNewRoundInternal() {
        List<Player> readyPlayers = connectedPlayers.stream()
            .filter(player -> player.getChips() > 0)
            .toList();

        gameTable.getDeck().reset();
        gameTable.getCommunityCards().clear();
        gameTable.setPot(0);
        currentBet = 0;
        pendingPlayerIds.clear();
        playerLastActions.clear();
        handPlayers.clear();

        for (Player player : connectedPlayers) {
            player.prepareForHand(readyPlayers.contains(player));
        }

        if (readyPlayers.size() < 2) {
            currentPhase = GamePhase.WAITING_FOR_PLAYERS;
            activePlayerIndex = -1;
            addChat("System: Mindestens zwei Spieler mit Chips sind erforderlich.");
            broadcastGameState("Mindestens zwei Spieler mit Chips sind erforderlich.");
            return;
        }

        handPlayers.addAll(readyPlayers);
        for (int cardNumber = 0; cardNumber < 2; cardNumber++) {
            for (Player player : handPlayers) {
                player.getCards().add(gameTable.getDeck().drawCard());
            }
        }

        currentPhase = GamePhase.PREFLOP;
        int previousIndex = indexOfConnectedPlayer(previousStarterId);
        activePlayerIndex = findNextActionableIndex(previousIndex);
        previousStarterId = connectedPlayers.get(activePlayerIndex).getId();
        preparePendingPlayers();
        turnId++;

        addChat("System: Neue Runde gestartet! Phase: PREFLOP");
        broadcastGameState("Neue Runde gestartet.");
    }

    public synchronized void handleAction(Player player, PlayerAction action) {
        if (player == null || action == null || !connectedPlayers.contains(player)) {
            return;
        }

        if (action instanceof PlayerAction.Chat chat) {
            handleChat(player, chat.message());
            return;
        }

        if (action instanceof PlayerAction.NextRound) {
            if (currentPhase == GamePhase.SHOWDOWN) {
                startNewRoundInternal();
            } else {
                sendGameState(player, "Die aktuelle Runde ist noch nicht beendet.");
            }
            return;
        }

        if (!currentPhase.isBettingPhase()) {
            sendGameState(player, "In dieser Phase sind keine Spielaktionen möglich.");
            return;
        }

        Player activePlayer = activePlayer();
        if (activePlayer != player || !pendingPlayerIds.contains(player.getId())) {
            sendGameState(player, "Du bist gerade nicht am Zug.");
            return;
        }

        long submittedTurnId = switch (action) {
            case PlayerAction.Fold fold -> fold.turnId();
            case PlayerAction.Check check -> check.turnId();
            case PlayerAction.Call call -> call.turnId();
            case PlayerAction.Raise raise -> raise.turnId();
            case PlayerAction.Chat ignored -> throw new IllegalStateException("Chat wurde bereits behandelt.");
            case PlayerAction.NextRound ignored -> throw new IllegalStateException("Rundenwechsel wurde bereits behandelt.");
        };
        if (submittedTurnId != turnId) {
            sendGameState(player, "Diese Aktion gehört zu einem bereits beendeten Zug.");
            return;
        }

        switch (action) {
            case PlayerAction.Fold ignored -> handleFold(player);
            case PlayerAction.Check ignored -> handleCheck(player);
            case PlayerAction.Call ignored -> handleCall(player);
            case PlayerAction.Raise raise -> handleRaise(player, raise.amount());
            case PlayerAction.Chat ignored -> throw new IllegalStateException("Chat wurde bereits behandelt.");
            case PlayerAction.NextRound ignored -> throw new IllegalStateException("Rundenwechsel wurde bereits behandelt.");
        }
    }

    private void handleChat(Player player, String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) {
            return;
        }

        String message = rawMessage.strip();
        if (message.length() > CHAT_MESSAGE_LIMIT) {
            message = message.substring(0, CHAT_MESSAGE_LIMIT);
        }
        addChat(player.getName() + ": " + message);
        broadcastGameState("Neue Chat-Nachricht.");
    }

    private void handleFold(Player player) {
        turnId++;
        player.setFolded(true);
        pendingPlayerIds.remove(player.getId());
        playerLastActions.put(player.getId(), "FOLD");
        addChat("System: " + player.getName() + " passt.");

        if (contenders().size() <= 1) {
            finishHandAfterFolds("Rundenende – alle anderen Spieler haben gepasst.");
            return;
        }
        finishAction(player.getName() + " passt.");
    }

    private void handleCheck(Player player) {
        int toCall = currentBet - player.getCurrentBet();
        if (toCall != 0) {
            sendGameState(player, "CHECK ist nicht möglich; es fehlen " + toCall + " Chips.");
            return;
        }

        turnId++;
        pendingPlayerIds.remove(player.getId());
        playerLastActions.put(player.getId(), "CHECK");
        addChat("System: " + player.getName() + " checkt.");
        finishAction(player.getName() + " checkt.");
    }

    private void handleCall(Player player) {
        int toCall = currentBet - player.getCurrentBet();
        if (toCall <= 0) {
            sendGameState(player, "Es gibt keinen Einsatz zu callen; nutze CHECK.");
            return;
        }

        turnId++;
        int paid = player.commitChips(toCall);
        gameTable.addPot(paid);
        pendingPlayerIds.remove(player.getId());
        String label = paid < toCall || player.isAllIn() ? "ALL-IN " + paid : "CALL " + paid;
        playerLastActions.put(player.getId(), label);
        addChat("System: " + player.getName() + " zahlt " + paid + " Chips"
            + (player.isAllIn() ? " und ist all-in." : "."));
        finishAction(player.getName() + " callt " + paid + ".");
    }

    private void handleRaise(Player player, int raiseAmount) {
        int toCall = currentBet - player.getCurrentBet();
        long totalCost = (long) toCall + raiseAmount;
        if (raiseAmount != MIN_RAISE) {
            sendGameState(player, "In dieser Variante beträgt ein Raise genau " + MIN_RAISE + " Chips.");
            return;
        }
        if (totalCost > player.getChips()) {
            sendGameState(player, "Für diesen Raise fehlen Chips.");
            return;
        }
        if ((long) currentBet + raiseAmount > Integer.MAX_VALUE) {
            sendGameState(player, "Der Einsatz ist zu hoch.");
            return;
        }

        turnId++;
        int paid = player.commitChips((int) totalCost);
        gameTable.addPot(paid);
        currentBet += raiseAmount;
        playerLastActions.put(player.getId(), "RAISE +" + raiseAmount);
        addChat("System: " + player.getName() + " erhöht um " + raiseAmount + " Chips.");

        pendingPlayerIds.clear();
        connectedPlayers.stream()
            .filter(other -> other != player && canAct(other))
            .map(Player::getId)
            .forEach(pendingPlayerIds::add);
        finishAction(player.getName() + " erhöht um " + raiseAmount + ".");
    }

    private void finishAction(String status) {
        if (pendingPlayerIds.isEmpty()) {
            advancePhaseUntilActionIsNeeded();
            return;
        }

        activePlayerIndex = findNextPendingIndex(activePlayerIndex);
        broadcastGameState(status);
    }

    private void advancePhaseUntilActionIsNeeded() {
        while (currentPhase.isBettingPhase()) {
            switch (currentPhase) {
                case PREFLOP -> {
                    currentPhase = GamePhase.FLOP;
                    dealCommunityCards(3);
                    addChat("System: FLOP – drei Tischkarten wurden aufgedeckt.");
                }
                case FLOP -> {
                    currentPhase = GamePhase.TURN;
                    dealCommunityCards(1);
                    addChat("System: TURN – die vierte Tischkarte wurde aufgedeckt.");
                }
                case TURN -> {
                    currentPhase = GamePhase.RIVER;
                    dealCommunityCards(1);
                    addChat("System: RIVER – die fünfte Tischkarte wurde aufgedeckt.");
                }
                case RIVER -> {
                    finishShowdown();
                    return;
                }
                case WAITING_FOR_PLAYERS, SHOWDOWN -> throw new IllegalStateException("Ungültige Spielphase.");
            }

            currentBet = 0;
            handPlayers.forEach(Player::resetBettingRound);
            playerLastActions.clear();
            preparePendingPlayers();
            if (!pendingPlayerIds.isEmpty()) {
                activePlayerIndex = findNextPendingIndex(activePlayerIndex);
                broadcastGameState("Neue Phase: " + currentPhase.name());
                return;
            }
        }
    }

    private void finishHandAfterFolds(String status) {
        currentPhase = GamePhase.SHOWDOWN;
        currentBet = 0;
        activePlayerIndex = -1;
        pendingPlayerIds.clear();
        handPlayers.forEach(Player::resetBettingRound);

        Player winner = contenders().stream().findFirst().orElse(null);
        int pot = gameTable.takePot();
        String winMessage = status;
        if (winner != null) {
            winner.addChips(pot);
            playerLastActions.put(winner.getId(), "🏆 GEWINNT " + pot);
            addChat("🏆 SYSTEM: " + winner.getName() + " gewinnt " + pot + " Chips (alle anderen haben gepasst).");
            winMessage = winner.getName() + " gewinnt " + pot + " Chips!";
        }
        broadcastGameState(winMessage);
    }

    private void finishShowdown() {
        currentPhase = GamePhase.SHOWDOWN;
        currentBet = 0;
        activePlayerIndex = -1;
        pendingPlayerIds.clear();
        handPlayers.forEach(Player::resetBettingRound);

        List<Player> eligiblePlayers = contenders();
        Map<Player, HandEvaluator.HandResult> results = new HashMap<>();
        for (Player player : eligiblePlayers) {
            HandEvaluator.HandResult result = HandEvaluator.evaluateHand(
                player.getCards(), gameTable.getCommunityCards());
            results.put(player, result);
            addChat("System: " + player.getName() + " zeigt " + result.description() + ".");
        }

        int pot = gameTable.getPot();
        Map<Player, Integer> payouts = calculatePayouts(eligiblePlayers, results, pot);
        gameTable.takePot();
        payouts.forEach(Player::addChips);

        StringBuilder winSummary = new StringBuilder();
        if (payouts.isEmpty()) {
            List<Player> winners = bestPlayers(eligiblePlayers, results);
            String names = winners.stream().map(Player::getName).reduce((left, right) -> left + ", " + right).orElse("-");
            addChat("🏆 SYSTEM: " + names + " gewinnt den Showdown mit "
                + results.get(winners.getFirst()).description() + " (Pot: 0 Chips).");
            winSummary.append(names).append(" gewinnt mit ").append(results.get(winners.getFirst()).description());
        } else {
            payouts.forEach((player, amount) -> {
                HandEvaluator.HandResult result = results.get(player);
                String reason = result == null ? " zurück" : " mit " + result.description();
                playerLastActions.put(player.getId(), "🏆 +" + amount + (result != null ? " (" + result.description() + ")" : ""));
                addChat("🏆 SYSTEM: " + player.getName() + " erhält " + amount + " Chips" + reason + ".");
                if (winSummary.length() > 0) {
                    winSummary.append(" | ");
                }
                winSummary.append(player.getName()).append(" gewinnt ").append(amount).append(" Chips (")
                    .append(result != null ? result.description() : "Split").append(")");
            });
        }
        broadcastGameState(winSummary.toString());
    }

    private Map<Player, Integer> calculatePayouts(
            List<Player> eligiblePlayers,
            Map<Player, HandEvaluator.HandResult> results,
            int pot) {
        Map<Player, Integer> payouts = new LinkedHashMap<>();
        if (pot == 0 || eligiblePlayers.isEmpty()) {
            return payouts;
        }

        TreeSet<Integer> contributionLevels = new TreeSet<>();
        handPlayers.stream()
            .map(Player::getHandContribution)
            .filter(contribution -> contribution > 0)
            .forEach(contributionLevels::add);

        int previousLevel = 0;
        int distributed = 0;
        for (int level : contributionLevels) {
            List<Player> contributors = handPlayers.stream()
                .filter(player -> player.getHandContribution() >= level)
                .toList();
            int sidePot = Math.multiplyExact(level - previousLevel, contributors.size());
            List<Player> candidates = eligiblePlayers.stream()
                .filter(player -> player.getHandContribution() >= level)
                .toList();

            if (candidates.isEmpty()) {
                int refund = level - previousLevel;
                contributors.forEach(player -> payouts.merge(player, refund, Integer::sum));
            } else {
                distribute(sidePot, bestPlayers(candidates, results), payouts);
            }
            distributed = Math.addExact(distributed, sidePot);
            previousLevel = level;
        }

        if (distributed < pot) {
            distribute(pot - distributed, bestPlayers(eligiblePlayers, results), payouts);
        } else if (distributed > pot) {
            throw new IllegalStateException("Spielereinsätze übersteigen den Pot.");
        }
        return payouts;
    }

    private List<Player> bestPlayers(
            List<Player> candidates,
            Map<Player, HandEvaluator.HandResult> results) {
        HandEvaluator.HandResult best = candidates.stream()
            .map(results::get)
            .max(HandEvaluator.HandResult::compareTo)
            .orElseThrow();
        return candidates.stream()
            .filter(player -> results.get(player).compareTo(best) == 0)
            .toList();
    }

    private void distribute(int amount, List<Player> winners, Map<Player, Integer> payouts) {
        int share = amount / winners.size();
        int remainder = amount % winners.size();
        for (int index = 0; index < winners.size(); index++) {
            payouts.merge(winners.get(index), share + (index < remainder ? 1 : 0), Integer::sum);
        }
    }

    private void preparePendingPlayers() {
        pendingPlayerIds.clear();
        List<Player> actionablePlayers = connectedPlayers.stream().filter(this::canAct).toList();
        if (actionablePlayers.size() >= 2) {
            actionablePlayers.stream().map(Player::getId).forEach(pendingPlayerIds::add);
        }
    }

    private boolean canAct(Player player) {
        return player.isInHand() && !player.isFolded() && !player.isAllIn();
    }

    private List<Player> contenders() {
        return handPlayers.stream().filter(player -> !player.isFolded()).toList();
    }

    private int playersReadyForHand() {
        return (int) connectedPlayers.stream().filter(player -> player.getChips() > 0).count();
    }

    private Player activePlayer() {
        if (activePlayerIndex < 0 || activePlayerIndex >= connectedPlayers.size()) {
            return null;
        }
        return connectedPlayers.get(activePlayerIndex);
    }

    private int indexOfConnectedPlayer(String playerId) {
        if (playerId == null) {
            return -1;
        }
        for (int index = 0; index < connectedPlayers.size(); index++) {
            if (connectedPlayers.get(index).getId().equals(playerId)) {
                return index;
            }
        }
        return -1;
    }

    private int findNextActionableIndex(int fromExclusive) {
        if (connectedPlayers.isEmpty()) {
            return -1;
        }
        for (int offset = 1; offset <= connectedPlayers.size(); offset++) {
            int index = Math.floorMod(fromExclusive + offset, connectedPlayers.size());
            if (canAct(connectedPlayers.get(index))) {
                return index;
            }
        }
        return -1;
    }

    private int findNextPendingIndex(int fromExclusive) {
        if (connectedPlayers.isEmpty()) {
            return -1;
        }
        for (int offset = 1; offset <= connectedPlayers.size(); offset++) {
            int index = Math.floorMod(fromExclusive + offset, connectedPlayers.size());
            if (pendingPlayerIds.contains(connectedPlayers.get(index).getId())) {
                return index;
            }
        }
        return -1;
    }

    private void dealCommunityCards(int count) {
        for (int index = 0; index < count; index++) {
            gameTable.dealCommunityCard();
        }
    }

    private void addChat(String message) {
        if (chatHistory.size() == CHAT_HISTORY_LIMIT) {
            chatHistory.removeFirst();
        }
        chatHistory.addLast(message);
    }

    private void broadcastGameState(String statusMessage) {
        for (Player player : List.copyOf(connectedPlayers)) {
            sendGameState(player, statusMessage);
        }
    }

    private void sendGameState(Player recipient, String statusMessage) {
        recipient.send(createGameState(recipient, statusMessage), () -> removePlayer(recipient));
    }

    synchronized GameStateDTO snapshot(Player recipient) {
        if (!connectedPlayers.contains(recipient)) {
            throw new IllegalArgumentException("Der Spieler ist nicht verbunden.");
        }
        return createGameState(recipient, "");
    }

    private GameStateDTO createGameState(Player recipient, String statusMessage) {
        Player activePlayer = activePlayer();
        List<PlayerStateDTO> players = connectedPlayers.stream()
            .map(player -> {
                boolean revealCards = (currentPhase == GamePhase.SHOWDOWN && player.isInHand() && !player.isFolded())
                    || player == recipient;
                List<Card> cards = revealCards ? List.copyOf(player.getCards()) : null;
                return new PlayerStateDTO(
                    player.getId(),
                    player.getName(),
                    player.getChips(),
                    player.getCurrentBet(),
                    player.isInHand(),
                    player.isInHand() && player.isFolded(),
                    player.isAllIn(),
                    currentPhase.isBettingPhase() && player == activePlayer,
                    playerLastActions.get(player.getId()),
                    cards);
            })
            .toList();
        return new GameStateDTO(
            gameTable.getPot(),
            currentBet,
            List.copyOf(gameTable.getCommunityCards()),
            List.copyOf(recipient.getCards()),
            recipient.getId(),
            currentPhase,
            turnId,
            players,
            statusMessage,
            List.copyOf(chatHistory));
    }
}
