package de.openpoker.common.network;

import java.io.Serializable;

public final class PlayerAction implements Serializable {
    private static final long serialVersionUID = 1L;

    private final ActionType type;
    private final long turnId;
    private final int amount;
    private final String message;

    public PlayerAction(ActionType type) {
        this(type, 0, 0, null);
    }

    public PlayerAction(ActionType type, long turnId) {
        this(type, turnId, 0, null);
    }

    public PlayerAction(ActionType type, long turnId, int amount) {
        this(type, turnId, amount, null);
    }

    public PlayerAction(ActionType type, String message) {
        this(type, 0, 0, message);
    }

    private PlayerAction(ActionType type, long turnId, int amount, String message) {
        this.type = type;
        this.turnId = turnId;
        this.amount = amount;
        this.message = message;
    }

    public ActionType getType() {
        return type;
    }

    public long getTurnId() {
        return turnId;
    }

    public int getAmount() {
        return amount;
    }

    public String getMessage() {
        return message;
    }
}
