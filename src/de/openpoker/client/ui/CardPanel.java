package de.openpoker.client.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JPanel;
import de.openpoker.common.model.Card;
import de.openpoker.common.model.Suit;

public final class CardPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private Card card;

    public CardPanel() {
        setPreferredSize(new Dimension(65, 95));
        setOpaque(false);
    }

    public void setCard(Card card) {
        this.card = card;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int w = getWidth() - 4;
        int h = getHeight() - 4;

        g2.setColor(new Color(0, 0, 0, 40));
        g2.fillRoundRect(4, 4, w - 2, h - 2, 12, 12);

        g2.setColor(Color.WHITE);
        g2.fillRoundRect(2, 2, w - 2, h - 2, 12, 12);

        g2.setColor(new Color(200, 200, 200));
        g2.drawRoundRect(2, 2, w - 2, h - 2, 12, 12);

        if (card != null) {
            boolean isRed = card.suit() == Suit.HEARTS || card.suit() == Suit.DIAMONDS;
            Color cardColor = isRed ? new Color(210, 30, 30) : new Color(30, 30, 35);
            g2.setColor(cardColor);

            String suitSymbol = switch (card.suit()) {
                case CLUBS -> "♣";
                case DIAMONDS -> "♦";
                case HEARTS -> "♥";
                case SPADES -> "♠";
            };

            String rankStr = switch (card.rank()) {
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

            g2.setFont(new Font("SansSerif", Font.BOLD, 13));
            g2.drawString(rankStr, 8, 18);
            g2.drawString(suitSymbol, 8, 30);

            g2.setFont(new Font("SansSerif", Font.BOLD, 32));
            int fontW = g2.getFontMetrics().stringWidth(suitSymbol);
            g2.drawString(suitSymbol, (w - fontW) / 2 + 1, h / 2 + 12);
        }
    }
}
