package de.openpoker.server;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import de.openpoker.common.model.Card;
import de.openpoker.common.network.GameStateDTO;

public final class Player {
    private final String id;
    private final String name;
    private int chips;
    private boolean folded;
    private boolean inHand;
    private boolean allIn;
    private int currentBet;
    private int handContribution;
    private final List<Card> cards = new ArrayList<>();
    private final ObjectOutputStream out;
    private final ExecutorService sender;
    private final AtomicBoolean sendFailureReported = new AtomicBoolean();
    private volatile boolean senderClosed;

    public Player(String id, String name, int chips, ObjectOutputStream out) {
        this.id = id;
        this.name = name;
        this.chips = chips;
        this.out = out;
        this.sender = Executors.newSingleThreadExecutor(
            Thread.ofVirtual().name("poker-send-" + id + "-", 0).factory());
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getChips() {
        return chips;
    }

    public void setChips(int chips) {
        this.chips = chips;
    }

    public void addChips(int amount) {
        chips = Math.addExact(chips, amount);
    }

    public boolean isFolded() {
        return folded;
    }

    public void setFolded(boolean folded) {
        this.folded = folded;
    }

    public boolean isInHand() {
        return inHand;
    }

    public void setInHand(boolean inHand) {
        this.inHand = inHand;
    }

    public boolean isAllIn() {
        return allIn;
    }

    public int getCurrentBet() {
        return currentBet;
    }

    public int getHandContribution() {
        return handContribution;
    }

    public void prepareForHand(boolean participating) {
        cards.clear();
        folded = false;
        inHand = participating;
        allIn = false;
        currentBet = 0;
        handContribution = 0;
    }

    public void resetBettingRound() {
        currentBet = 0;
    }

    public int commitChips(int requestedAmount) {
        if (requestedAmount < 0) {
            throw new IllegalArgumentException("Der Einsatz darf nicht negativ sein.");
        }

        int paid = Math.min(requestedAmount, chips);
        chips -= paid;
        currentBet += paid;
        handContribution += paid;
        allIn = inHand && chips == 0;
        return paid;
    }

    public List<Card> getCards() {
        return cards;
    }

    public void send(GameStateDTO state, Runnable onFailure) {
        if (senderClosed) {
            return;
        }

        try {
            sender.execute(() -> {
                try {
                    out.writeObject(state);
                    out.reset();
                    out.flush();
                } catch (IOException | RuntimeException exception) {
                    if (sendFailureReported.compareAndSet(false, true)) {
                        try {
                            out.close();
                        } catch (IOException ignored) {
                            // Das Schließen dient nur dazu, den Reader-Thread aufzuwecken.
                        }
                        onFailure.run();
                    }
                }
            });
        } catch (RejectedExecutionException ignored) {
            // Der Spieler wurde zeitgleich entfernt.
        }
    }

    public void closeSender() {
        senderClosed = true;
        sender.shutdownNow();
    }
}
