package de.openpoker.common.network;

import java.io.Serializable;
import java.util.List;
import de.openpoker.common.model.Card;

public record PlayerStateDTO(
        String id,
        String name,
        int chips,
        int currentBet,
        boolean inHand,
        boolean folded,
        boolean allIn,
        boolean active,
        String lastAction,
        List<Card> cards,
        boolean isDealer) implements Serializable {
}
