package de.openpoker.common.network;

import java.io.Serializable;

public sealed interface PlayerAction extends Serializable {
    record Join(String playerName) implements PlayerAction {}
    record Fold(String playerName) implements PlayerAction {}
    record Check(String playerName) implements PlayerAction {}
    record Call(String playerName) implements PlayerAction {}
    record Raise(String playerName, int amount) implements PlayerAction {}
    record Chat(String playerName, String message) implements PlayerAction {}
    record NextRound(String playerName) implements PlayerAction {}
}
