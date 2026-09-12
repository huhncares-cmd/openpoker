package de.openpoker.server;

public enum HandRank {
    HIGH_CARD(1, "Höchste Karte"),
    ONE_PAIR(2, "Ein Paar"),
    TWO_PAIR(3, "Zwei Paare"),
    THREE_OF_A_KIND(4, "Drilling"),
    STRAIGHT(5, "Straße"),
    FLUSH(6, "Flush"),
    FULL_HOUSE(7, "Full House"),
    FOUR_OF_A_KIND(8, "Vierling"),
    STRAIGHT_FLUSH(9, "Straight Flush"),
    ROYAL_FLUSH(10, "Royal Flush");

    private final int value;
    private final String name;

    HandRank(int value, String name) {
        this.value = value;
        this.name = name;
    }

    public int getValue() {
        return value;
    }

    public String getName() {
        return name;
    }
}
