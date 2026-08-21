package de.openpoker.client.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JPanel;
import de.openpoker.common.model.Card;
import de.openpoker.common.model.Rank;
import de.openpoker.common.model.Suit;

public final class CardPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private static final int WIDTH = 68;
    private static final int HEIGHT = 96;

    private Card card;
    private boolean highlighted;

    public CardPanel() {
        setPreferredSize(new Dimension(WIDTH + 6, HEIGHT + 6));
        setOpaque(false);
    }

    public void setCard(Card card) {
        this.card = card;
        repaint();
    }

    public void setHighlighted(boolean highlighted) {
        this.highlighted = highlighted;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int x = 3;
        int y = 3;
        int w = WIDTH;
        int h = HEIGHT;
        int arc = 12;

        if (card == null) {
            // Leerer Kartenslot (Dezente gestrichelte Umrandung auf dem Tisch)
            g2.setColor(new Color(0, 0, 0, 40));
            g2.fillRoundRect(x, y, w, h, arc, arc);
            g2.setColor(new Color(255, 255, 255, 40));
            float[] dash = {6f, 4f};
            g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f, dash, 0f));
            g2.drawRoundRect(x, y, w, h, arc, arc);
            return;
        }

        // 1. Weicher Kartenschatten
        g2.setColor(new Color(0, 0, 0, 35));
        g2.fillRoundRect(x + 2, y + 4, w, h, arc, arc);
        g2.setColor(new Color(0, 0, 0, 70));
        g2.fillRoundRect(x + 1, y + 2, w, h, arc, arc);

        // 2. Kartenhintergrund (Edles Elfenbein-Weiß mit leichtem Verlauf)
        GradientPaint bgGradient = new GradientPaint(
            x, y, new Color(255, 255, 255),
            x, y + h, new Color(245, 247, 250)
        );
        g2.setPaint(bgGradient);
        g2.fillRoundRect(x, y, w, h, arc, arc);

        // 3. Kartenrand (Gold bei Highlight, sonst dezent Silbergrau)
        if (highlighted) {
            g2.setColor(new Color(255, 215, 0));
            g2.setStroke(new BasicStroke(2.5f));
            g2.drawRoundRect(x, y, w, h, arc, arc);
        } else {
            g2.setColor(new Color(210, 215, 225));
            g2.setStroke(new BasicStroke(1.0f));
            g2.drawRoundRect(x, y, w, h, arc, arc);
        }

        // 4. Farben & Symbole
        boolean isRed = card.suit() == Suit.HEARTS || card.suit() == Suit.DIAMONDS;
        Color primaryColor = isRed ? new Color(215, 35, 35) : new Color(28, 30, 38);
        Color secondaryColor = isRed ? new Color(255, 230, 230) : new Color(230, 235, 245);

        String suitSymbol = getSuitSymbol(card.suit());
        String rankStr = getRankString(card.rank());

        // 5. Dekorativer Hintergrund-Wasserzeichen-Suit in der Kartenmitte
        g2.setFont(new Font("SansSerif", Font.BOLD, 38));
        int centerSymW = g2.getFontMetrics().stringWidth(suitSymbol);
        g2.setColor(primaryColor);
        g2.drawString(suitSymbol, x + (w - centerSymW) / 2, y + h / 2 + 13);

        // 6. Ecke Oben Links (Wert + Symbol)
        g2.setColor(primaryColor);
        g2.setFont(new Font("SansSerif", Font.BOLD, 13));
        g2.drawString(rankStr, x + 7, y + 16);

        g2.setFont(new Font("SansSerif", Font.PLAIN, 12));
        g2.drawString(suitSymbol, x + 7, y + 28);

        // 7. Ecke Unten Rechts (Kopfstehend / Invertiert für echten Karten-Look)
        g2.setFont(new Font("SansSerif", Font.PLAIN, 12));
        int symW = g2.getFontMetrics().stringWidth(suitSymbol);
        g2.drawString(suitSymbol, x + w - symW - 7, y + h - 18);

        g2.setFont(new Font("SansSerif", Font.BOLD, 13));
        int rankW = g2.getFontMetrics().stringWidth(rankStr);
        g2.drawString(rankStr, x + w - rankW - 7, y + h - 6);
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

    private static String getRankString(Rank rank) {
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
