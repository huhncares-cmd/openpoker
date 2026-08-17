package de.openpoker.common.network;

import java.io.Serializable;

public record PlayerStateDTO(
    String id,
    String name,
    int chips,
    int currentBet,
    boolean folded,
    boolean active,
    String lastAction
) implements Serializable {
}
