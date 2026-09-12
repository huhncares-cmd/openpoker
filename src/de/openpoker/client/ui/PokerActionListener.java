package de.openpoker.client.ui;

import de.openpoker.common.network.PlayerAction;

public interface PokerActionListener {
    boolean sendAction(PlayerAction action);
}
