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
import de.openpoker.common.model.Suit;

public final class CardPanel extends JPanel {
    private static final long serialVersionUID = 1L; //eclipse warning
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

        if (card == null) {
            drawEmptySlot(g2);
            return;
        }

        // ki-hilfe bei farben, schatten und der aufteilung der zeichenmethoden
        drawCardBackground(g2);
        drawCardBorder(g2);
        drawCardSymbols(g2);
    }

    private void drawEmptySlot(Graphics2D g2) {
        g2.setColor(new Color(0, 0, 0, 40));
        g2.fillRoundRect(3, 3, WIDTH, HEIGHT, 12, 12);
        g2.setColor(new Color(255, 255, 255, 40));
        float[] dash = {6f, 4f};
        g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f, dash, 0f));
        g2.drawRoundRect(3, 3, WIDTH, HEIGHT, 12, 12);
    }

    private void drawCardBackground(Graphics2D g2) {
        // kartenschatten
        g2.setColor(new Color(0, 0, 0, 35));
        g2.fillRoundRect(5, 7, WIDTH, HEIGHT, 12, 12);
        g2.setColor(new Color(0, 0, 0, 70));
        g2.fillRoundRect(4, 5, WIDTH, HEIGHT, 12, 12);

        // kartenhintergrund
        GradientPaint bgGradient = new GradientPaint(
            3, 3, new Color(255, 255, 255),
            3, 3 + HEIGHT, new Color(245, 247, 250)
        );
        g2.setPaint(bgGradient);
        g2.fillRoundRect(3, 3, WIDTH, HEIGHT, 12, 12);
    }

    private void drawCardBorder(Graphics2D g2) {
        // goldener rand bei markierung
        if (highlighted) {
            g2.setColor(new Color(255, 215, 0));
            g2.setStroke(new BasicStroke(2.5f));
        } else {
            g2.setColor(new Color(210, 215, 225));
            g2.setStroke(new BasicStroke(1.0f));
        }
        g2.drawRoundRect(3, 3, WIDTH, HEIGHT, 12, 12);
    }

    private void drawCardSymbols(Graphics2D g2) {
        boolean isRed = card.suit() == Suit.HEARTS || card.suit() == Suit.DIAMONDS;
        Color primaryColor = isRed ? new Color(215, 35, 35) : new Color(28, 30, 38);
        String suitSymbol = card.suit().getSymbol();
        String rankStr = card.rank().getSymbol();

        drawCenterSymbol(g2, primaryColor, suitSymbol);
        drawCardCorners(g2, primaryColor, suitSymbol, rankStr);
    }

    private void drawCenterSymbol(Graphics2D g2, Color color, String suitSymbol) {
        g2.setFont(new Font("SansSerif", Font.BOLD, 38));
        int centerSymW = g2.getFontMetrics().stringWidth(suitSymbol);
        g2.setColor(color);
        g2.drawString(suitSymbol, 3 + (WIDTH - centerSymW) / 2, 3 + HEIGHT / 2 + 13);
    }

    private void drawCardCorners(Graphics2D g2, Color color, String suitSymbol, String rankStr) {
        g2.setColor(color);
        g2.setFont(new Font("SansSerif", Font.BOLD, 13));
        g2.drawString(rankStr, 10, 19);

        g2.setFont(new Font("SansSerif", Font.PLAIN, 12));
        g2.drawString(suitSymbol, 10, 31);

        int symW = g2.getFontMetrics().stringWidth(suitSymbol);
        g2.drawString(suitSymbol, 3 + WIDTH - symW - 7, 3 + HEIGHT - 18);

        g2.setFont(new Font("SansSerif", Font.BOLD, 13));
        int rankW = g2.getFontMetrics().stringWidth(rankStr);
        g2.drawString(rankStr, 3 + WIDTH - rankW - 7, 3 + HEIGHT - 6);
    }

}
