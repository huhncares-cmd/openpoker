package de.openpoker.common.network;

import java.io.Serializable;
import java.util.List;
import de.openpoker.common.model.Card;
import de.openpoker.common.model.GamePhase;

public record GameStateDTO(
    int pot,
    int currentBet,
    List<Card> communityCards,
    List<Card> myCards,
    String myPlayerId,
    GamePhase phase,
    List<PlayerStateDTO> players,
    String statusMessage,
    List<String> chatHistory
) implements Serializable {
}
