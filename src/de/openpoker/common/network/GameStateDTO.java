package de.openpoker.common.network;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import de.openpoker.common.model.Card;

public record GameStateDTO(
    int pot,
    List<Card> communityCards,
    List<Card> myCards,
    String myPlayerName,
    String activePlayerName,
    List<String> playerNames,
    Map<String, String> playerLastActions,
    String statusMessage,
    List<String> chatHistory
) implements Serializable {
}
