package de.openpoker.client.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import de.openpoker.common.model.GamePhase;
import de.openpoker.common.network.GameStateDTO;
import de.openpoker.common.network.PlayerStateDTO;

public final class StatusBarPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    private static final Color INFO_COLOR = new Color(100, 180, 255);
    private static final Color WARNING_COLOR = new Color(255, 175, 0);
    private static final Color SHOWDOWN_COLOR = new Color(255, 215, 0);
    private static final Color ACTIVE_TURN_COLOR = new Color(50, 225, 100);
    private static final Color ERROR_COLOR = new Color(240, 90, 90);

    private final JLabel statusLabel = new JLabel("Warte auf Server...", JLabel.CENTER);

    public StatusBarPanel() {
        super(new BorderLayout());
        setBackground(new Color(18, 20, 28));
        setBorder(new EmptyBorder(10, 15, 10, 15));
        statusLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
        statusLabel.setForeground(new Color(100, 200, 255));
        add(statusLabel, BorderLayout.CENTER);
    }

    public void showDisconnected(String message) {
        setStatus(textOrFallback(message, "Verbindung getrennt"), ERROR_COLOR);
    }

    public void showWaitingForState(String message) {
        setStatus(textOrFallback(message, "Verbunden, warte auf Spielstand..."), INFO_COLOR);
    }

    public void updateStatus(GameStateDTO state, PlayerStateDTO me) {
        List<PlayerStateDTO> players = state.players();
        PlayerStateDTO active = findActivePlayer(players);
        String message = state.statusMessage();

        if (state.phase() == GamePhase.WAITING_FOR_PLAYERS) {
            setStatus(textOrFallback(message, "Warte auf spielbereite Spieler..."), WARNING_COLOR);
        } else if (state.phase() == GamePhase.SHOWDOWN) {
            setStatus(textOrFallback(message, "Rundenende – Showdown"), SHOWDOWN_COLOR);
        } else if (me != null && me.active()) {
            setStatus("DU BIST AM ZUG (" + me.name() + ")", ACTIVE_TURN_COLOR);
        } else if (active != null) {
            setStatus(active.name() + " ist am Zug", INFO_COLOR);
        } else if (players.size() < 2) {
            setStatus("Warte auf 2. Spieler... (" + players.size() + "/2)", WARNING_COLOR);
        } else {
            setStatus(textOrFallback(message, "Spiel läuft..."), INFO_COLOR);
        }
    }

    private void setStatus(String text, Color color) {
        statusLabel.setText(text);
        statusLabel.setForeground(color);
    }

    private static String textOrFallback(String message, String fallback) {
        return (message == null || message.isBlank()) ? fallback : message;
    }

    private static PlayerStateDTO findActivePlayer(List<PlayerStateDTO> players) {
        for (PlayerStateDTO player : players) {
            if (player.active()) {
                return player;
            }
        }
        return null;
    }
}
