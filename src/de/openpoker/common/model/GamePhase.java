package de.openpoker.common.model;

public enum GamePhase {
    WAITING_FOR_PLAYERS,
    PREFLOP,
    FLOP,
    TURN,
    RIVER,
    SHOWDOWN;

    public boolean isBettingPhase() {
        return this == PREFLOP || this == FLOP || this == TURN || this == RIVER;
    }
}
