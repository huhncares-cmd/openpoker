package de.openpoker.client.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.List;
import java.util.Map;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import de.openpoker.common.model.Card;

public class PokerTablePanel extends JPanel {
    private final JLabel potLabel = new JLabel("POT: 0 CHIPS", JLabel.CENTER);
    private final JPanel cardsContainer = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 10));

    private List<String> playerNames;
    private Map<String, String> playerLastActions;
    private String activePlayer;

    public PokerTablePanel() {
        setLayout(new BorderLayout());
        setOpaque(false);
        setBorder(new EmptyBorder(30, 30, 30, 30));

        potLabel.setFont(new Font("SansSerif", Font.BOLD, 20));
        potLabel.setForeground(new Color(255, 215, 0));
        add(potLabel, BorderLayout.NORTH);

        cardsContainer.setOpaque(false);
        add(cardsContainer, BorderLayout.CENTER);
    }

    public void updateTable(int pot, List<Card> communityCards, List<String> playerNames, Map<String, String> playerLastActions, String activePlayer) {
        potLabel.setText("💰 POT: " + pot + " CHIPS");
        this.playerNames = playerNames;
        this.playerLastActions = playerLastActions;
        this.activePlayer = activePlayer;

        cardsContainer.removeAll();
        if (communityCards != null) {
            for (Card card : communityCards) {
                CardPanel cp = new CardPanel();
                cp.setCard(card);
                cardsContainer.add(cp);
            }
        }

        revalidate();
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int margin = 35;
        int w = getWidth() - (margin * 2);
        int h = getHeight() - (margin * 2);

        // Edler Holzrand (Mahagoni)
        g2.setColor(new Color(55, 30, 15));
        g2.fillRoundRect(margin, margin, w, h, 80, 80);

        // Grüner Casino-Filz
        g2.setColor(new Color(24, 110, 50));
        g2.fillRoundRect(margin + 14, margin + 14, w - 28, h - 28, 65, 65);

        // Goldene Zierlinie
        g2.setColor(new Color(212, 175, 55, 180));
        g2.drawRoundRect(margin + 18, margin + 18, w - 36, h - 36, 60, 60);

        // Spieler nur in der unteren Hälfte platzieren (Links -> Unten -> Rechts)
        if (playerNames != null && !playerNames.isEmpty()) {
            int numPlayers = playerNames.size();
            double centerX = getWidth() / 2.0;
            double centerY = getHeight() / 2.0 + 10;
            double radiusX = (w / 2.0) - 35;
            double radiusY = (h / 2.0) - 30;

            for (int i = 0; i < numPlayers; i++) {
                String playerName = playerNames.get(i);
                boolean isActive = playerName.equals(activePlayer);
                String lastAction = (playerLastActions != null) ? playerLastActions.get(playerName) : null;

                // Verteilung von Links (180°) über Unten (90°) nach Rechts (0°)
                double angle;
                if (numPlayers == 1) {
                    angle = Math.PI / 2; // Genau unten in der Mitte
                } else {
                    double startAngle = Math.PI * 0.85; // Links unten
                    double endAngle = Math.PI * 0.15;   // Rechts unten
                    angle = startAngle - (i * (startAngle - endAngle) / (numPlayers - 1));
                }

                int px = (int) (centerX + radiusX * Math.cos(angle));
                int py = (int) (centerY + radiusY * Math.sin(angle));

                drawPlayerAvatar(g2, px, py, playerName, isActive, lastAction);
            }
        }
    }

    private void drawPlayerAvatar(Graphics2D g2, int x, int y, String name, boolean isActive, String lastAction) {
        int avatarRadius = 24;

        if (isActive) {
            g2.setColor(new Color(50, 255, 100, 200));
            g2.fillOval(x - avatarRadius - 4, y - avatarRadius - 4, (avatarRadius + 4) * 2, (avatarRadius + 4) * 2);
        }

        g2.setColor(new Color(30, 32, 40));
        g2.fillOval(x - avatarRadius, y - avatarRadius, avatarRadius * 2, avatarRadius * 2);
        g2.setColor(isActive ? new Color(50, 255, 100) : new Color(212, 175, 55));
        g2.drawOval(x - avatarRadius, y - avatarRadius, avatarRadius * 2, avatarRadius * 2);

        String initial = name.length() > 0 ? name.substring(0, Math.min(3, name.length())) : "P";
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("SansSerif", Font.BOLD, 12));
        int strW = g2.getFontMetrics().stringWidth(initial);
        g2.drawString(initial, x - strW / 2, y + 4);

        g2.setFont(new Font("SansSerif", Font.BOLD, 11));
        int nameW = g2.getFontMetrics().stringWidth(name) + 10;
        g2.setColor(new Color(20, 20, 25, 220));
        g2.fillRoundRect(x - nameW / 2, y + avatarRadius + 2, nameW, 18, 8, 8);
        g2.setColor(Color.WHITE);
        g2.drawString(name, x - (nameW - 10) / 2, y + avatarRadius + 15);

        if (lastAction != null && !lastAction.isEmpty()) {
            g2.setFont(new Font("SansSerif", Font.BOLD, 10));
            int actW = g2.getFontMetrics().stringWidth(lastAction) + 10;

            Color actBg = lastAction.contains("RAISE") ? new Color(200, 130, 20) :
                          lastAction.contains("FOLD") ? new Color(180, 40, 40) :
                          new Color(40, 140, 60);

            g2.setColor(actBg);
            g2.fillRoundRect(x - actW / 2, y - avatarRadius - 16, actW, 16, 6, 6);
            g2.setColor(Color.WHITE);
            g2.drawString(lastAction, x - (actW - 10) / 2, y - avatarRadius - 4);
        }
    }
}
