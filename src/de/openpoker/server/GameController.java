package de.openpoker.server;

import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.openpoker.common.model.Card;
import de.openpoker.common.model.GamePhase;
import de.openpoker.common.network.ActionType;
import de.openpoker.common.network.GameStateDTO;
import de.openpoker.common.network.PlayerAction;
import de.openpoker.common.network.PlayerStateDTO;

public final class GameController {
    static final int STARTING_CHIPS = 1_000;
    static final int MAX_PLAYERS = 10;
    static final int DEFAULT_SMALL_BLIND = 10;
    static final int DEFAULT_BIG_BLIND = 20;
    static final int MIN_RAISE = 50;
    private static final int CHAT_HISTORY_LIMIT = 100;
    private static final int CHAT_MESSAGE_LIMIT = 500;

    private final int smallBlind;
    private final int bigBlind;
    private final GameTable gameTable = new GameTable();
    private final List<Player> connectedPlayers = new ArrayList<>();
    private final List<Player> handPlayers = new ArrayList<>();
    private final List<String> chatHistory = new ArrayList<>();
    private final Map<String, String> playerLastActions = new HashMap<>();
    private final Set<String> pendingPlayerIds = new HashSet<>();

    private GamePhase currentPhase = GamePhase.WAITING_FOR_PLAYERS;
    private int activePlayerIndex = -1;
    private int currentBet;
    private long turnId;
    private long nextPlayerId = 1;
    private String previousStarterId;
    private int dealerIndex = -1;
    private String currentDealerId;

    public GameController() {
        this(DEFAULT_SMALL_BLIND, DEFAULT_BIG_BLIND);
    }

    public GameController(int smallBlind, int bigBlind) {
        this.smallBlind = Math.max(0, smallBlind);
        this.bigBlind = Math.max(0, bigBlind);
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
        List<Player> readyPlayers = findReadyPlayers();
        resetRound(readyPlayers);

        if (readyPlayers.size() < 2) {
            waitForPlayers();
            return;
        }

        handPlayers.addAll(readyPlayers);
        dealHoleCards();

        dealerIndex = (dealerIndex + 1) % readyPlayers.size();
        Player dealerPlayer = readyPlayers.get(dealerIndex);
        currentDealerId = dealerPlayer.getId();
        currentPhase = GamePhase.PREFLOP;

        if (smallBlind > 0 && bigBlind > 0) {
            postBlindsAndSelectFirstPlayer(readyPlayers, dealerPlayer);
        } else {
            selectFirstPlayerWithoutBlinds();
        }

        if (activePlayerIndex >= 0 && activePlayerIndex < connectedPlayers.size()) {
            previousStarterId = connectedPlayers.get(activePlayerIndex).getId();
        }
        turnId++;

        addChat("System: Neue Runde gestartet! Phase: PREFLOP");
        broadcastGameState("Neue Runde gestartet.");
    }

    private List<Player> findReadyPlayers() {
        List<Player> readyPlayers = new ArrayList<>();
        for (Player player : connectedPlayers) {
            if (player.getChips() > 0) {
                readyPlayers.add(player);
            }
        }
        return readyPlayers;
    }

    private void resetRound(List<Player> readyPlayers) {
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
    }

    private void waitForPlayers() {
        currentPhase = GamePhase.WAITING_FOR_PLAYERS;
        activePlayerIndex = -1;
        currentDealerId = null;
        addChat("System: Mindestens zwei Spieler mit Chips sind erforderlich.");
        broadcastGameState("Mindestens zwei Spieler mit Chips sind erforderlich.");
    }

    private void dealHoleCards() {
        for (int cardNumber = 0; cardNumber < 2; cardNumber++) {
            for (Player player : handPlayers) {
                player.getCards().add(gameTable.getDeck().drawCard());
            }
        }
    }

    private void postBlindsAndSelectFirstPlayer(List<Player> readyPlayers, Player dealerPlayer) {
        Player sbPlayer;
        Player bbPlayer;
        int firstActorIndex;

        if (readyPlayers.size() == 2) {
            // Heads-Up: Dealer ist Small Blind und beginnt vor dem Flop.
            sbPlayer = dealerPlayer;
            bbPlayer = readyPlayers.get((dealerIndex + 1) % 2);
            firstActorIndex = connectedPlayers.indexOf(sbPlayer);
        } else {
            sbPlayer = readyPlayers.get((dealerIndex + 1) % readyPlayers.size());
            bbPlayer = readyPlayers.get((dealerIndex + 2) % readyPlayers.size());
            Player utgPlayer = readyPlayers.get((dealerIndex + 3) % readyPlayers.size());
            firstActorIndex = connectedPlayers.indexOf(utgPlayer);
        }

        int sbPaid = sbPlayer.commitChips(Math.min(smallBlind, sbPlayer.getChips()));
        gameTable.addPot(sbPaid);
        playerLastActions.put(sbPlayer.getId(), "SB " + sbPaid);

        int bbPaid = bbPlayer.commitChips(Math.min(bigBlind, bbPlayer.getChips()));
        gameTable.addPot(bbPaid);
        playerLastActions.put(bbPlayer.getId(), "BB " + bbPaid);

        currentBet = Math.max(sbPlayer.getCurrentBet(), bbPlayer.getCurrentBet());
        addChat("System: Dealer ist " + dealerPlayer.getName()
            + ". SB: " + sbPlayer.getName() + " (" + sbPaid + ")"
            + ", BB: " + bbPlayer.getName() + " (" + bbPaid + ").");

        preparePendingPlayers();
        activePlayerIndex = findNextActionableIndex(firstActorIndex - 1);
        if (activePlayerIndex < 0
                || !pendingPlayerIds.contains(connectedPlayers.get(activePlayerIndex).getId())) {
            activePlayerIndex = findNextPendingIndex(activePlayerIndex);
        }
    }

    private void selectFirstPlayerWithoutBlinds() {
        int previousIndex = indexOfConnectedPlayer(previousStarterId);
        activePlayerIndex = findNextActionableIndex(previousIndex);
        preparePendingPlayers();
    }

    public synchronized void handleAction(Player player, PlayerAction action) {
        if (!connectedPlayers.contains(player)) {
            return;
        }

        ActionType actionType = action.getType();
        if (actionType == ActionType.CHAT) {
            handleChat(player, action.getMessage());
            return;
        }

        if (actionType == ActionType.NEXT_ROUND) {
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

        if (action.getTurnId() != turnId) {
            sendGameState(player, "Diese Aktion gehört zu einem bereits beendeten Zug.");
            return;
        }

        switch (actionType) {
            case FOLD:
                handleFold(player);
                break;
            case CHECK:
                handleCheck(player);
                break;
            case CALL:
                handleCall(player);
                break;
            case RAISE:
                handleRaise(player, action.getAmount());
                break;
            default:
                break;
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
        int totalCost = toCall + raiseAmount;
        int maxRaisePossible = player.getChips() - toCall;

        if (maxRaisePossible <= 0) {
            sendGameState(player, "Du kannst nicht erhöhen.");
            return;
        }

        boolean isAllIn = (raiseAmount == maxRaisePossible);
        int minAllowed = Math.min(MIN_RAISE, maxRaisePossible);

        if (raiseAmount < minAllowed && !isAllIn) {
            sendGameState(player, "Der Mindest-Raise beträgt " + minAllowed + " Chips.");
            return;
        }
        if (totalCost > player.getChips()) {
            sendGameState(player, "Für diesen Raise fehlen Chips.");
            return;
        }

        turnId++;
        int paid = player.commitChips(totalCost);
        gameTable.addPot(paid);
        currentBet += raiseAmount;
        String raiseLabel = isAllIn || player.isAllIn() ? "ALL-IN +" + raiseAmount : "RAISE +" + raiseAmount;
        playerLastActions.put(player.getId(), raiseLabel);
        addChat("System: " + player.getName() + " erhöht um " + raiseAmount + " Chips"
            + (player.isAllIn() ? " (ALL-IN)." : "."));

        pendingPlayerIds.clear();
        for (Player other : connectedPlayers) {
            if (other != player && canAct(other)) {
                pendingPlayerIds.add(other.getId());
            }
        }
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
                case PREFLOP:
                    currentPhase = GamePhase.FLOP;
                    dealCommunityCards(3);
                    addChat("System: FLOP – drei Tischkarten wurden aufgedeckt.");
                    break;
                case FLOP:
                    currentPhase = GamePhase.TURN;
                    dealCommunityCards(1);
                    addChat("System: TURN – die vierte Tischkarte wurde aufgedeckt.");
                    break;
                case TURN:
                    currentPhase = GamePhase.RIVER;
                    dealCommunityCards(1);
                    addChat("System: RIVER – die fünfte Tischkarte wurde aufgedeckt.");
                    break;
                case RIVER:
                    finishShowdown();
                    return;
                default:
                    throw new IllegalStateException("Ungültige Spielphase.");
            }

            currentBet = 0;
            for (Player player : handPlayers) {
                player.resetBettingRound();
            }
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
        enterShowdown();

        List<Player> remainingPlayers = contenders();
        Player winner = remainingPlayers.isEmpty() ? null : remainingPlayers.get(0);
        int pot = gameTable.takePot();
        String winMessage = status;
        if (winner != null) {
            winner.addChips(pot);
            playerLastActions.put(winner.getId(), "🏆 GEWINNT " + pot);
            addChat("🏆 SYSTEM: " + winner.getName() + " gewinnt " + pot
                + " Chips (alle anderen haben gepasst).");
            winMessage = winner.getName() + " gewinnt " + pot + " Chips!";
        }
        broadcastGameState(winMessage);
    }

    private void enterShowdown() {
        currentPhase = GamePhase.SHOWDOWN;
        currentBet = 0;
        activePlayerIndex = -1;
        pendingPlayerIds.clear();
        for (Player player : handPlayers) {
            player.resetBettingRound();
        }
    }

    private void finishShowdown() {
        enterShowdown();

        List<Player> eligiblePlayers = contenders();
        Map<Player, HandEvaluator.HandResult> results = evaluateHands(eligiblePlayers);
        int pot = gameTable.getPot();
        Map<Player, Integer> payouts = calculatePayouts(eligiblePlayers, results, pot);
        gameTable.takePot();
        applyPayouts(payouts);
        broadcastGameState(createWinSummary(eligiblePlayers, results, payouts));
    }

    private Map<Player, HandEvaluator.HandResult> evaluateHands(List<Player> players) {
        Map<Player, HandEvaluator.HandResult> results = new HashMap<>();
        for (Player player : players) {
            HandEvaluator.HandResult result = HandEvaluator.evaluateHand(
                player.getCards(), gameTable.getCommunityCards());
            results.put(player, result);
            addChat("System: " + player.getName() + " zeigt " + result.description() + ".");
        }
        return results;
    }

    private void applyPayouts(Map<Player, Integer> payouts) {
        for (Map.Entry<Player, Integer> payout : payouts.entrySet()) {
            payout.getKey().addChips(payout.getValue());
        }
    }

    private String createWinSummary(
            List<Player> eligiblePlayers,
            Map<Player, HandEvaluator.HandResult> results,
            Map<Player, Integer> payouts) {
        StringBuilder winSummary = new StringBuilder();
        if (payouts.isEmpty()) {
            List<Player> winners = bestPlayers(eligiblePlayers, results);
            String names = playerNames(winners);
            addChat("🏆 SYSTEM: " + names + " gewinnt den Showdown mit "
                + results.get(winners.get(0)).description() + " (Pot: 0 Chips).");
            winSummary.append(names).append(" gewinnt mit ").append(results.get(winners.get(0)).description());
        } else {
            for (Map.Entry<Player, Integer> payout : payouts.entrySet()) {
                Player player = payout.getKey();
                int amount = payout.getValue();
                HandEvaluator.HandResult result = results.get(player);
                String reason = result == null ? " zurück" : " mit " + result.description();
                playerLastActions.put(player.getId(), "🏆 +" + amount + (result != null ? " (" + result.description() + ")" : ""));
                addChat("🏆 SYSTEM: " + player.getName() + " erhält " + amount + " Chips" + reason + ".");
                if (winSummary.length() > 0) {
                    winSummary.append(" | ");
                }
                winSummary.append(player.getName()).append(" gewinnt ").append(amount).append(" Chips (")
                    .append(result != null ? result.description() : "Split").append(")");
            }
        }
        return winSummary.toString();
    }

    private String playerNames(List<Player> players) {
        StringBuilder names = new StringBuilder();
        for (Player player : players) {
            if (names.length() > 0) {
                names.append(", ");
            }
            names.append(player.getName());
        }
        return names.length() == 0 ? "-" : names.toString();
    }

    private Map<Player, Integer> calculatePayouts(
            List<Player> eligiblePlayers,
            Map<Player, HandEvaluator.HandResult> results,
            int pot) {
        Map<Player, Integer> payouts = new HashMap<>();
        if (pot == 0 || eligiblePlayers.isEmpty()) {
            return payouts;
        }

        List<Integer> contributionLevels = new ArrayList<>();
        for (Player player : handPlayers) {
            int contribution = player.getHandContribution();
            if (contribution > 0 && !contributionLevels.contains(contribution)) {
                contributionLevels.add(contribution);
            }
        }
        Collections.sort(contributionLevels);

        int previousLevel = 0;
        int distributed = 0;
        for (int level : contributionLevels) {
            List<Player> contributors = new ArrayList<>();
            for (Player player : handPlayers) {
                if (player.getHandContribution() >= level) {
                    contributors.add(player);
                }
            }
            int sidePot = (level - previousLevel) * contributors.size();
            List<Player> candidates = new ArrayList<>();
            for (Player player : eligiblePlayers) {
                if (player.getHandContribution() >= level) {
                    candidates.add(player);
                }
            }

            if (candidates.isEmpty()) {
                int refund = level - previousLevel;
                for (Player player : contributors) {
                    addPayout(payouts, player, refund);
                }
            } else {
                distribute(sidePot, bestPlayers(candidates, results), payouts);
            }
            distributed += sidePot;
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
        HandEvaluator.HandResult best = results.get(candidates.get(0));
        for (Player player : candidates) {
            HandEvaluator.HandResult current = results.get(player);
            if (current.compareTo(best) > 0) {
                best = current;
            }
        }

        List<Player> winners = new ArrayList<>();
        for (Player player : candidates) {
            if (results.get(player).compareTo(best) == 0) {
                winners.add(player);
            }
        }
        return winners;
    }

    private void distribute(int amount, List<Player> winners, Map<Player, Integer> payouts) {
        int share = amount / winners.size();
        int remainder = amount % winners.size();
        for (int index = 0; index < winners.size(); index++) {
            int winnerAmount = share + (index < remainder ? 1 : 0);
            addPayout(payouts, winners.get(index), winnerAmount);
        }
    }

    private void addPayout(Map<Player, Integer> payouts, Player player, int amount) {
        Integer currentAmount = payouts.get(player);
        if (currentAmount == null) {
            payouts.put(player, amount);
        } else {
            payouts.put(player, currentAmount + amount);
        }
    }

    private void preparePendingPlayers() {
        pendingPlayerIds.clear();
        for (Player player : connectedPlayers) {
            if (canAct(player)) {
                pendingPlayerIds.add(player.getId());
            }
        }
        if (pendingPlayerIds.size() < 2) {
            pendingPlayerIds.clear();
        }
    }

    private boolean canAct(Player player) {
        return player.isInHand() && !player.isFolded() && !player.isAllIn();
    }

    private List<Player> contenders() {
        List<Player> result = new ArrayList<>();
        for (Player player : handPlayers) {
            if (!player.isFolded()) {
                result.add(player);
            }
        }
        return result;
    }

    private int playersReadyForHand() {
        int count = 0;
        for (Player player : connectedPlayers) {
            if (player.getChips() > 0) {
                count++;
            }
        }
        return count;
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
            int index = (fromExclusive + offset) % connectedPlayers.size();
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
            int index = (fromExclusive + offset) % connectedPlayers.size();
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
            chatHistory.remove(0);
        }
        chatHistory.add(message);
    }

    private void broadcastGameState(String statusMessage) {
        List<Player> recipients = new ArrayList<>(connectedPlayers);
        for (Player player : recipients) {
            sendGameState(player, statusMessage);
        }
    }

    private void sendGameState(Player recipient, String statusMessage) {
        GameStateDTO state = createGameState(recipient, statusMessage);
        if (!recipient.send(state)) {
            removePlayer(recipient);
        }
    }

    synchronized GameStateDTO snapshot(Player recipient) {
        if (!connectedPlayers.contains(recipient)) {
            throw new IllegalArgumentException("Der Spieler ist nicht verbunden.");
        }
        return createGameState(recipient, "");
    }

    private GameStateDTO createGameState(Player recipient, String statusMessage) {
        Player activePlayer = activePlayer();
        List<PlayerStateDTO> players = new ArrayList<>();
        for (Player player : connectedPlayers) {
            boolean revealCards = currentPhase == GamePhase.SHOWDOWN
                && player.isInHand() && !player.isFolded();
            List<Card> cards = revealCards ? new ArrayList<>(player.getCards()) : null;
            boolean isDealer = player.getId().equals(currentDealerId);
            PlayerStateDTO playerState = new PlayerStateDTO(
                player.getId(),
                player.getName(),
                player.getChips(),
                player.getCurrentBet(),
                player.isInHand(),
                player.isInHand() && player.isFolded(),
                player.isAllIn(),
                currentPhase.isBettingPhase() && player == activePlayer,
                playerLastActions.get(player.getId()),
                cards,
                isDealer);
            players.add(playerState);
        }
        return new GameStateDTO(
            gameTable.getPot(),
            currentBet,
            new ArrayList<>(gameTable.getCommunityCards()),
            new ArrayList<>(recipient.getCards()),
            recipient.getId(),
            currentPhase,
            turnId,
            players,
            statusMessage,
            new ArrayList<>(chatHistory));
    }
}
