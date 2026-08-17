package de.openpoker.server;

import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import de.openpoker.common.model.Card;

public class Player {
    private final String id;
    private final String name;
    private int chips;
    private final List<Card> cards = new ArrayList<>();
    private final transient ObjectOutputStream out;

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
        this.chips = chips;
    }

    public List<Card> getCards() {
        return cards;
    }

    public ObjectOutputStream getOut() {
        return out;
    }
}
