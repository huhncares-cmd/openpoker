package de.openpoker.client.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JButton;

public class ModernButton extends JButton {
    private static final long serialVersionUID = 1L;
    private final Color topColor;
    private final Color bottomColor;
    private boolean hover;

    public ModernButton(String text, Color topColor, Color bottomColor) {
        super(text);
        this.topColor = topColor;
        this.bottomColor = bottomColor;
        setFont(new Font("SansSerif", Font.BOLD, 12));
        setForeground(Color.WHITE);
        setFocusPainted(false);
        setBorderPainted(false);
        setContentAreaFilled(false);
        setOpaque(false);
        setCursor(new Cursor(Cursor.HAND_CURSOR));
        setPreferredSize(new Dimension(130, 36));

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                hover = true;
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hover = false;
                repaint();
            }
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        // ki-hilfe bei farbverlauf und hover-effekt
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();
        int arc = 10;

        if (!isEnabled()) {
            g2.setColor(new Color(45, 48, 58));
            g2.fillRoundRect(0, 0, w, h, arc, arc);
            g2.setColor(new Color(100, 105, 120));
            g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);
            g2.setColor(new Color(120, 125, 140));
            drawCenteredString(g2, getText(), w, h);
            return;
        }

        Color c1 = hover ? topColor.brighter() : topColor;
        Color c2 = hover ? bottomColor.brighter() : bottomColor;

        GradientPaint gradient = new GradientPaint(0, 0, c1, 0, h, c2);
        g2.setPaint(gradient);
        g2.fillRoundRect(0, 0, w, h, arc, arc);

        g2.setColor(new Color(255, 255, 255, hover ? 140 : 80));
        g2.setStroke(new BasicStroke(1.2f));
        g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);

        g2.setColor(Color.WHITE);
        drawCenteredString(g2, getText(), w, h);
    }

    private void drawCenteredString(Graphics2D g2, String text, int w, int h) {
        int strW = g2.getFontMetrics().stringWidth(text);
        int strH = g2.getFontMetrics().getAscent();
        g2.drawString(text, (w - strW) / 2, (h + strH) / 2 - 2);
    }
}
