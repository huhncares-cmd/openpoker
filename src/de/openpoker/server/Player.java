package de.openpoker.server;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
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
    private boolean senderClosed;

    public Player(String id, String name, int chips, ObjectOutputStream out) {
        this.id = id;
        this.name = name;
        this.chips = chips;
        this.out = out;
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
        if (chips < 0) {
            throw new IllegalArgumentException("Chips dürfen nicht negativ sein.");
        }
        this.chips = chips;
    }

    public void addChips(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Betrag darf nicht negativ sein.");
        }
        this.chips += amount;
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

    public synchronized boolean send(GameStateDTO state) {
        if (senderClosed || out == null) {
            return false;
        }

        try {
            out.writeObject(state);
            out.reset();
            out.flush();
            return true;
        } catch (IOException exception) {
            senderClosed = true;
            return false;
        }
    }

    public synchronized void closeSender() {
        senderClosed = true;
    }
}
