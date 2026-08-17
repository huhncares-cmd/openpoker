package de.openpoker.common.network;

import java.io.Serializable;

public record PlayerStateDTO(
    String id,
    String name,
    int chips,
    int currentBet,
    boolean inHand,
    boolean folded,
    boolean allIn,
    boolean active,
    String lastAction
) implements Serializable {
}
