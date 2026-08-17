package de.openpoker.server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import de.openpoker.common.model.Card;
import de.openpoker.common.model.Rank;
import de.openpoker.common.model.Suit;

public final class Deck {
    private final List<Card> cards = new ArrayList<>();

    public Deck() {
        reset();
    }

    public void reset() {
        cards.clear();
        for (Suit suit : Suit.values()) {
            for (Rank rank : Rank.values()) {
                cards.add(new Card(suit, rank));
            }
        }
        Collections.shuffle(cards);
    }

    public Card drawCard() {
        return cards.remove(cards.size() - 1);
    }

    public int remainingCards() {
        return cards.size();
    }
}
