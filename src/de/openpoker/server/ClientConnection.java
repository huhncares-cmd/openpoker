package de.openpoker.server;

import de.openpoker.common.network.GameStateDTO;

/** Verbindung zu einem Client; die Spiellogik kennt dadurch weder Sockets noch Serialisierung. */
@FunctionalInterface
public interface ClientConnection {
    /** @return false, wenn die Verbindung unterbrochen ist */
    boolean send(GameStateDTO state);
}
