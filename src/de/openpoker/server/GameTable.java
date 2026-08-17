package de.openpoker.server;

import java.util.ArrayList;
import java.util.List;
import de.openpoker.common.model.Card;

public final class GameTable {
    private final List<Card> communityCards = new ArrayList<>();
    private final Deck deck = new Deck();
    private int pot;

    public List<Card> getCommunityCards() {
        return communityCards;
    }

    public Deck getDeck() {
        return deck;
    }

    public int getPot() {
        return pot;
    }

    public void setPot(int pot) {
        if (pot < 0) {
            throw new IllegalArgumentException("Der Pot darf nicht negativ sein.");
        }
        this.pot = pot;
    }

    public void addPot(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Der Einsatz darf nicht negativ sein.");
        }
        pot = Math.addExact(pot, amount);
    }

    public int takePot() {
        int result = pot;
        pot = 0;
        return result;
    }

    public void dealCommunityCard() {
        if (deck.remainingCards() > 0) {
            communityCards.add(deck.drawCard());
        }
    }
}
