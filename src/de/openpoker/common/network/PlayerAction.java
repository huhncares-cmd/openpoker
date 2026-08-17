package de.openpoker.common.network;

import java.io.Serializable;

public sealed interface PlayerAction extends Serializable {
    record Fold() implements PlayerAction {}
    record Check() implements PlayerAction {}
    record Call() implements PlayerAction {}
    record Raise(int amount) implements PlayerAction {}
    record Chat(String message) implements PlayerAction {}
    record NextRound() implements PlayerAction {}
}
