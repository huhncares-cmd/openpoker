package de.openpoker.client.ui;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import de.openpoker.common.model.Card;
import de.openpoker.common.model.Rank;
import de.openpoker.common.model.Suit;
import de.openpoker.common.network.PlayerStateDTO;

public final class PokerTablePanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private final JLabel potLabel = new JLabel("💰 POT: 0 CHIPS", JLabel.CENTER);
    private final JPanel cardsContainer = new JPanel(new FlowLayout(FlowLayout.CENTER, 14, 8));

    private transient List<PlayerStateDTO> players = List.of();

    public PokerTablePanel() {
        setLayout(new BorderLayout());
        setOpaque(false);
        setBorder(new EmptyBorder(25, 25, 25, 25));

        potLabel.setFont(new Font("SansSerif", Font.BOLD, 20));
        potLabel.setForeground(new Color(255, 220, 80));

        JPanel potWrapper = new JPanel(new FlowLayout(FlowLayout.CENTER));
        potWrapper.setOpaque(false);
        potWrapper.add(potLabel);
        add(potWrapper, BorderLayout.NORTH);

        cardsContainer.setOpaque(false);
        add(cardsContainer, BorderLayout.CENTER);
    }

    public void updateTable(int pot, List<Card> communityCards, List<PlayerStateDTO> players) {
        potLabel.setText("💰 POT: " + String.format("%,d", pot) + " CHIPS");
        this.players = players == null ? List.of() : List.copyOf(players);

        cardsContainer.removeAll();
        if (communityCards != null && !communityCards.isEmpty()) {
            for (Card card : communityCards) {
                CardPanel cp = new CardPanel();
                cp.setCard(card);
                cardsContainer.add(cp);
            }
        } else {
            // Leere Platzhalter anzeigen, solange keine Tischkarten da sind (5 Slots)
            for (int i = 0; i < 5; i++) {
                CardPanel cp = new CardPanel();
                cp.setCard(null);
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
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int margin = 28;
        int w = getWidth() - (margin * 2);
        int h = getHeight() - (margin * 2);

        // 1. Äußerer Tischrand (Edler Mahagoni-/Leder-Look mit Farbverlauf)
        GradientPaint railGradient = new GradientPaint(
            margin, margin, new Color(42, 22, 14),
            margin + w, margin + h, new Color(24, 12, 8)
        );
        g2.setPaint(railGradient);
        g2.fillRoundRect(margin, margin, w, h, 85, 85);

        // Weicher Rand-Schatten
        g2.setColor(new Color(0, 0, 0, 90));
        g2.drawRoundRect(margin, margin, w, h, 85, 85);

        // 2. Äußere Gold-Zierlinie
        g2.setColor(new Color(212, 175, 55, 160));
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawRoundRect(margin + 6, margin + 6, w - 12, h - 12, 75, 75);

        // 3. Grüner Casino-Filz mit Radial-Verlauf (Zentrum heller smaragdgrün, Rand samtig dunkelgrün)
        float centerX = getWidth() / 2f;
        float centerY = getHeight() / 2f;
        float radius = Math.max(w, h) / 1.6f;

        RadialGradientPaint feltGradient = new RadialGradientPaint(
            new Point2D.Float(centerX, centerY - 10),
            radius,
            new float[]{0.0f, 0.65f, 1.0f},
            new Color[]{
                new Color(32, 128, 62),   // Helles Casino-Smaragdgrün
                new Color(20, 92, 44),    // Klassisches Grün
                new Color(10, 52, 24)     // Tiefes Samtdunkelgrün
            }
        );
        g2.setPaint(feltGradient);
        g2.fillRoundRect(margin + 14, margin + 14, w - 28, h - 28, 68, 68);

        // 4. Innere goldene Tischlinie
        g2.setColor(new Color(212, 175, 55, 140));
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawRoundRect(margin + 20, margin + 20, w - 40, h - 40, 60, 60);

        // 5. Dezenter Tisch-Wasserzeichen-Bogen in der Mitte
        g2.setColor(new Color(255, 255, 255, 12));
        g2.setStroke(new BasicStroke(2.0f));
        g2.drawOval((int) (centerX - 140), (int) (centerY - 55), 280, 110);

        // 6. Spieler zeichnen
        if (!players.isEmpty()) {
            int numPlayers = players.size();
            double radiusX = (w / 2.0) - 48;
            double radiusY = (h / 2.0) - 42;

            for (int i = 0; i < numPlayers; i++) {
                PlayerStateDTO player = players.get(i);

                double angle;
                if (numPlayers == 1) {
                    angle = Math.PI / 2;
                } else {
                    double startAngle = Math.PI * 0.88;
                    double endAngle = Math.PI * 0.12;
                    angle = startAngle - (i * (startAngle - endAngle) / (numPlayers - 1));
                }

                int px = (int) (centerX + radiusX * Math.cos(angle));
                int py = (int) (centerY + 10 + radiusY * Math.sin(angle));

                drawPlayerAvatar(g2, px, py, player);
            }
        }
    }

    private void drawPlayerAvatar(Graphics2D g2, int x, int y, PlayerStateDTO player) {
        int avatarRadius = 24;
        boolean isActive = player.active();
        boolean isWinner = player.lastAction() != null
                && (player.lastAction().contains("🏆") || player.lastAction().contains("GEWINNT"));

        // 1. Handkarten (Aufgedeckt beim Showdown oder verdeckte Karten-Rückseiten)
        if (player.inHand()) {
            List<Card> cards = player.cards();
            if (cards != null && !cards.isEmpty()) {
                // Aufgedeckte Showdown-Karten
                int cardW = 24;
                int cardH = 34;
                int gap = 4;
                int totalW = cards.size() * cardW + (cards.size() - 1) * gap;
                int startX = x - totalW / 2;
                int cardY = y - avatarRadius - cardH - 6;

                for (int i = 0; i < cards.size(); i++) {
                    drawMiniCard(g2, startX + i * (cardW + gap), cardY, cardW, cardH, cards.get(i), isWinner);
                }
            } else if (!player.folded()) {
                // Verdeckte Handkarten (Kartenrücken)
                int cardW = 18;
                int cardH = 26;
                int startX = x - 15;
                int cardY = y - avatarRadius - cardH - 4;
                drawMiniCardBack(g2, startX, cardY, cardW, cardH);
                drawMiniCardBack(g2, startX + 12, cardY, cardW, cardH);
            }
        }

        // 2. Kronen-Symbol für Gewinner
        if (isWinner) {
            g2.setFont(new Font("SansSerif", Font.PLAIN, 18));
            int crownY = (player.cards() != null && !player.cards().isEmpty())
                    ? y - avatarRadius - 44
                    : y - avatarRadius - 10;
            g2.drawString("👑", x - 9, crownY);
        }

        // 3. Leuchtrand (Gold für Gewinner, Grün für aktiven Spieler)
        if (isWinner) {
            g2.setColor(new Color(255, 215, 0, 100));
            g2.fillOval(x - avatarRadius - 8, y - avatarRadius - 8, (avatarRadius + 8) * 2, (avatarRadius + 8) * 2);
            g2.setColor(new Color(255, 215, 0, 220));
            g2.fillOval(x - avatarRadius - 4, y - avatarRadius - 4, (avatarRadius + 4) * 2, (avatarRadius + 4) * 2);
        } else if (isActive) {
            g2.setColor(new Color(50, 255, 100, 220));
            g2.fillOval(x - avatarRadius - 4, y - avatarRadius - 4, (avatarRadius + 4) * 2, (avatarRadius + 4) * 2);
        }

        // 4. Avatar-Kreis mit Farbverlauf
        GradientPaint avatarGrad = new GradientPaint(
            x - avatarRadius, y - avatarRadius,
            player.folded() || !player.inHand() ? new Color(60, 62, 70) : new Color(38, 42, 54),
            x + avatarRadius, y + avatarRadius,
            player.folded() || !player.inHand() ? new Color(35, 36, 42) : new Color(20, 22, 28)
        );
        g2.setPaint(avatarGrad);
        g2.fillOval(x - avatarRadius, y - avatarRadius, avatarRadius * 2, avatarRadius * 2);

        g2.setColor(isWinner ? new Color(255, 215, 0) : isActive ? new Color(50, 255, 100) : new Color(212, 175, 55));
        g2.setStroke(new BasicStroke(isWinner ? 2.5f : 1.5f));
        g2.drawOval(x - avatarRadius, y - avatarRadius, avatarRadius * 2, avatarRadius * 2);

        // 5. Initialen
        String name = player.name() == null ? "Spieler" : player.name();
        String initial = name.length() > 0 ? name.substring(0, Math.min(3, name.length())) : "P";
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("SansSerif", Font.BOLD, 12));
        int strW = g2.getFontMetrics().stringWidth(initial);
        g2.drawString(initial, x - strW / 2, y + 4);

        // 5b. Dealer-Button
        if (player.isDealer()) {
            int dbX = x + avatarRadius - 4;
            int dbY = y - avatarRadius + 4;
            int dbR = 9;
            g2.setColor(new Color(0, 0, 0, 80));
            g2.fillOval(dbX - dbR + 1, dbY - dbR + 1, dbR * 2, dbR * 2);
            g2.setColor(new Color(250, 250, 250));
            g2.fillOval(dbX - dbR, dbY - dbR, dbR * 2, dbR * 2);
            g2.setColor(new Color(212, 175, 55));
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawOval(dbX - dbR, dbY - dbR, dbR * 2, dbR * 2);
            g2.setColor(new Color(30, 30, 30));
            g2.setFont(new Font("SansSerif", Font.BOLD, 10));
            g2.drawString("D", dbX - 4, dbY + 4);
        }

        // 6. Spieler-Name & Chips Badge
        g2.setFont(new Font("SansSerif", Font.BOLD, 11));
        String playerLabel = name + " · 🪙 " + String.format("%,d", player.chips());
        int nameW = g2.getFontMetrics().stringWidth(playerLabel) + 12;
        g2.setColor(new Color(16, 18, 24, 230));
        g2.fillRoundRect(x - nameW / 2, y + avatarRadius + 3, nameW, 18, 8, 8);
        g2.setColor(new Color(255, 255, 255, 200));
        g2.setStroke(new BasicStroke(0.8f));
        g2.drawRoundRect(x - nameW / 2, y + avatarRadius + 3, nameW, 18, 8, 8);
        g2.setColor(Color.WHITE);
        g2.drawString(playerLabel, x - (nameW - 12) / 2, y + avatarRadius + 16);

        // 7. Letzte Aktion / Gewinner Badge
        String lastAction = !player.inHand() ? "WARTET"
            : player.folded() ? "FOLD"
            : player.allIn() ? "ALL-IN"
            : player.lastAction();

        if (lastAction != null && !lastAction.isEmpty()) {
            if (player.currentBet() > 0 && !player.folded() && !isWinner) {
                lastAction += " · " + player.currentBet();
            }
            g2.setFont(new Font("SansSerif", Font.BOLD, 10));
            int actW = g2.getFontMetrics().stringWidth(lastAction) + 12;

            Color actBg = isWinner ? new Color(255, 215, 0) :
                          lastAction.contains("RAISE") ? new Color(210, 130, 20) :
                          lastAction.contains("FOLD") ? new Color(190, 40, 40) :
                          new Color(40, 140, 60);
            Color textColor = isWinner ? new Color(25, 25, 25) : Color.WHITE;

            g2.setColor(actBg);
            g2.fillRoundRect(x - actW / 2, y + avatarRadius + 24, actW, 17, 6, 6);
            g2.setColor(textColor);
            g2.drawString(lastAction, x - (actW - 12) / 2, y + avatarRadius + 36);
        }
    }

    private void drawMiniCard(Graphics2D g2, int cx, int cy, int w, int h, Card card, boolean isWinner) {
        // Schatten
        g2.setColor(new Color(0, 0, 0, 90));
        g2.fillRoundRect(cx + 1, cy + 1, w, h, 6, 6);

        // Hintergrund
        g2.setColor(Color.WHITE);
        g2.fillRoundRect(cx, cy, w, h, 6, 6);

        // Rand (Gold bei Gewinner, sonst dezent Grau)
        g2.setColor(isWinner ? new Color(255, 215, 0) : new Color(180, 180, 180));
        g2.setStroke(new BasicStroke(isWinner ? 2.0f : 1.0f));
        g2.drawRoundRect(cx, cy, w, h, 6, 6);

        if (card != null) {
            boolean isRed = card.suit() == Suit.HEARTS || card.suit() == Suit.DIAMONDS;
            g2.setColor(isRed ? new Color(215, 35, 35) : new Color(28, 30, 38));

            String rankStr = getRankShort(card.rank());
            String suitSym = getSuitSymbol(card.suit());

            g2.setFont(new Font("SansSerif", Font.BOLD, 9));
            g2.drawString(rankStr, cx + 3, cy + 10);

            g2.setFont(new Font("SansSerif", Font.PLAIN, 12));
            int fontW = g2.getFontMetrics().stringWidth(suitSym);
            g2.drawString(suitSym, cx + (w - fontW) / 2, cy + h - 6);
        }
    }

    private void drawMiniCardBack(Graphics2D g2, int cx, int cy, int w, int h) {
        // Schatten
        g2.setColor(new Color(0, 0, 0, 80));
        g2.fillRoundRect(cx + 1, cy + 1, w, h, 5, 5);

        // Blauer Kartenrücken mit Farbverlauf
        GradientPaint backGrad = new GradientPaint(
            cx, cy, new Color(30, 55, 130),
            cx + w, cy + h, new Color(18, 32, 85)
        );
        g2.setPaint(backGrad);
        g2.fillRoundRect(cx, cy, w, h, 5, 5);

        // Goldene Zierlinie
        g2.setColor(new Color(212, 175, 55));
        g2.setStroke(new BasicStroke(0.9f));
        g2.drawRoundRect(cx + 2, cy + 2, w - 4, h - 4, 3, 3);

        // Weißer Rand
        g2.setColor(new Color(240, 240, 240));
        g2.drawRoundRect(cx, cy, w, h, 5, 5);
    }

    private static String getSuitSymbol(Suit suit) {
        if (suit == null) return "?";
        return switch (suit) {
            case CLUBS -> "♣";
            case DIAMONDS -> "♦";
            case HEARTS -> "♥";
            case SPADES -> "♠";
        };
    }

    private static String getRankShort(Rank rank) {
        if (rank == null) return "?";
        return switch (rank) {
            case TWO -> "2";
            case THREE -> "3";
            case FOUR -> "4";
            case FIVE -> "5";
            case SIX -> "6";
            case SEVEN -> "7";
            case EIGHT -> "8";
            case NINE -> "9";
            case TEN -> "10";
            case JACK -> "J";
            case QUEEN -> "Q";
            case KING -> "K";
            case ACE -> "A";
        };
    }
}
