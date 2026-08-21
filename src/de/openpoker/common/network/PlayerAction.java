package de.openpoker.common.network;

import java.io.Serializable;

public sealed interface PlayerAction extends Serializable {
    record Fold(long turnId) implements PlayerAction {
    }

    record Check(long turnId) implements PlayerAction {
    }

    record Call(long turnId) implements PlayerAction {
    }

    record Raise(long turnId, int amount) implements PlayerAction {
    }

    record Chat(String message) implements PlayerAction {
    }

    record NextRound() implements PlayerAction {
    }
}
