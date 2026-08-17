package de.openpoker.server;

import java.util.ArrayList;
import java.util.List;
import de.openpoker.common.model.Card;

public class GameTable {
    private final List<Player> players = new ArrayList<>();
    private final List<Card> communityCards = new ArrayList<>();
    private final Deck deck = new Deck();
    private int pot = 0;

    public void addPlayer(Player player) {
        players.add(player);
    }

    public void removePlayer(Player player) {
        players.remove(player);
    }

    public List<Player> getPlayers() {
        return players;
    }

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
        this.pot = pot;
    }

    public void addPot(int amount) {
        this.pot += amount;
    }

    public void dealCommunityCard() {
        if (deck.remainingCards() > 0) {
            communityCards.add(deck.drawCard());
        }
    }
}
