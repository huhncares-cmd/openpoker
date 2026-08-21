package de.openpoker.client.ui;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.LongFunction;
import java.util.function.Predicate;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import de.openpoker.common.model.Card;
import de.openpoker.common.model.GamePhase;
import de.openpoker.common.network.GameStateDTO;
import de.openpoker.common.network.PlayerAction;
import de.openpoker.common.network.PlayerStateDTO;

public final class PokerWindow extends JFrame {
    private static final long serialVersionUID = 1L;

    private final PokerTablePanel tablePanel = new PokerTablePanel();
    private final JPanel myCardsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 4));
    private final JTextArea chatArea = new JTextArea();
    private final JTextField chatInput = new JTextField(12);
    private final JLabel turnStatusLabel = new JLabel("Warte auf Server...", JLabel.CENTER);

    private final JButton foldBtn = new ModernButton("FOLD", new Color(195, 45, 45), new Color(150, 25, 25));
    private final JButton checkBtn = new ModernButton("CHECK", new Color(45, 105, 200), new Color(25, 75, 160));
    private final JButton callBtn = new ModernButton("CALL", new Color(38, 155, 70), new Color(22, 115, 48));
    private final JButton raise50Btn = new ModernButton("RAISE +50", new Color(225, 130, 20), new Color(180, 95, 10));
    private final JButton raise100Btn = new ModernButton("RAISE +100", new Color(225, 110, 15), new Color(180, 80, 10));
    private final JButton allInBtn = new ModernButton("ALL-IN", new Color(215, 40, 20), new Color(160, 20, 10));
    private final JButton nextRoundBtn = new ModernButton("NÄCHSTE RUNDE ➔", new Color(135, 55, 195), new Color(95, 30, 150));
    private final JButton sendBtn = new ModernButton("Senden", new Color(55, 65, 85), new Color(40, 48, 65));

    private transient Predicate<PlayerAction> actionListener;
    private GameStateDTO gameState;
    private boolean connected;
    private boolean turnActionPending;

    public PokerWindow() {
        setTitle("OpenPoker");
        setSize(1040, 720);
        setMinimumSize(new Dimension(900, 620));
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);
        getContentPane().setBackground(new Color(14, 16, 22));
        setLayout(new BorderLayout());

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBackground(new Color(18, 20, 28));
        topPanel.setBorder(new EmptyBorder(10, 15, 10, 15));
        turnStatusLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
        turnStatusLabel.setForeground(new Color(100, 200, 255));
        topPanel.add(turnStatusLabel, BorderLayout.CENTER);
        add(topPanel, BorderLayout.NORTH);

        add(tablePanel, BorderLayout.CENTER);
        add(createChatPanel(), BorderLayout.EAST);
        add(createActionPanel(), BorderLayout.SOUTH);
        refreshControls();
    }

    private JPanel createChatPanel() {
        JPanel chatPanel = new JPanel(new BorderLayout());
        chatPanel.setPreferredSize(new Dimension(280, 0));
        chatPanel.setBackground(new Color(20, 23, 31));
        chatPanel.setBorder(new EmptyBorder(12, 10, 12, 12));

        JLabel chatTitle = new JLabel("💬 TISCH-CHAT & LOGS", JLabel.CENTER);
        chatTitle.setFont(new Font("SansSerif", Font.BOLD, 13));
        chatTitle.setForeground(new Color(190, 195, 210));
        chatTitle.setBorder(new EmptyBorder(0, 0, 10, 0));
        chatPanel.add(chatTitle, BorderLayout.NORTH);

        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        chatArea.setBackground(new Color(13, 15, 20));
        chatArea.setForeground(new Color(225, 230, 240));
        chatArea.setFont(new Font("SansSerif", Font.PLAIN, 12));
        chatArea.setBorder(new EmptyBorder(6, 8, 6, 8));

        JScrollPane scrollPane = new JScrollPane(chatArea);
        scrollPane.setBorder(null);
        chatPanel.add(scrollPane, BorderLayout.CENTER);

        JPanel inputPanel = new JPanel(new BorderLayout(6, 0));
        inputPanel.setOpaque(false);
        inputPanel.setBorder(new EmptyBorder(10, 0, 0, 0));

        chatInput.setBackground(new Color(28, 32, 44));
        chatInput.setForeground(Color.WHITE);
        chatInput.setCaretColor(Color.WHITE);
        chatInput.setFont(new Font("SansSerif", Font.PLAIN, 12));
        chatInput.setBorder(new EmptyBorder(6, 8, 6, 8));
        chatInput.addActionListener(e -> sendChat());

        sendBtn.setPreferredSize(new Dimension(80, 32));
        sendBtn.addActionListener(e -> sendChat());

        inputPanel.add(chatInput, BorderLayout.CENTER);
        inputPanel.add(sendBtn, BorderLayout.EAST);
        chatPanel.add(inputPanel, BorderLayout.SOUTH);
        return chatPanel;
    }

    private JPanel createActionPanel() {
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBackground(new Color(16, 18, 25));
        bottomPanel.setBorder(new EmptyBorder(8, 12, 14, 12));

        JPanel handContainer = new JPanel(new BorderLayout());
        handContainer.setOpaque(false);

        JLabel handLabel = new JLabel("DEINE HAND", JLabel.CENTER);
        handLabel.setFont(new Font("SansSerif", Font.BOLD, 11));
        handLabel.setForeground(new Color(160, 170, 190));
        handLabel.setBorder(new EmptyBorder(0, 0, 2, 0));
        handContainer.add(handLabel, BorderLayout.NORTH);

        myCardsPanel.setOpaque(false);
        handContainer.add(myCardsPanel, BorderLayout.CENTER);
        bottomPanel.add(handContainer, BorderLayout.NORTH);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 8));
        buttonPanel.setOpaque(false);

        foldBtn.addActionListener(e -> sendTurnAction(PlayerAction.Fold::new));
        checkBtn.addActionListener(e -> sendTurnAction(PlayerAction.Check::new));
        callBtn.addActionListener(e -> sendTurnAction(PlayerAction.Call::new));
        raise50Btn.addActionListener(e -> sendTurnAction(turnId -> new PlayerAction.Raise(turnId, 50)));
        raise100Btn.addActionListener(e -> sendTurnAction(turnId -> new PlayerAction.Raise(turnId, 100)));
        allInBtn.addActionListener(e -> {
            PlayerStateDTO me = gameState == null ? null : findMe(gameState).orElse(null);
            if (me != null) {
                int toCall = Math.max(0, gameState.currentBet() - me.currentBet());
                int allInRaise = me.chips() - toCall;
                if (allInRaise > 0) {
                    sendTurnAction(turnId -> new PlayerAction.Raise(turnId, allInRaise));
                } else {
                    sendTurnAction(PlayerAction.Call::new);
                }
            }
        });
        nextRoundBtn.addActionListener(e -> sendAction(new PlayerAction.NextRound()));

        buttonPanel.add(foldBtn);
        buttonPanel.add(checkBtn);
        buttonPanel.add(callBtn);
        buttonPanel.add(raise50Btn);
        buttonPanel.add(raise100Btn);
        buttonPanel.add(allInBtn);
        buttonPanel.add(nextRoundBtn);
        bottomPanel.add(buttonPanel, BorderLayout.SOUTH);
        return bottomPanel;
    }

    private void sendChat() {
        String text = chatInput.getText().trim();
        if (!text.isEmpty() && sendAction(new PlayerAction.Chat(text))) {
            chatInput.setText("");
        }
    }

    public void setActionListener(Predicate<PlayerAction> listener) {
        actionListener = listener;
    }

    public void setCloseHandler(Runnable handler) {
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                handler.run();
            }
        });
    }

    private boolean sendAction(PlayerAction action) {
        return connected && actionListener != null && actionListener.test(action);
    }

    private void sendTurnAction(LongFunction<PlayerAction> actionFactory) {
        if (!connected || actionListener == null || gameState == null || turnActionPending) {
            return;
        }
        if (sendAction(actionFactory.apply(gameState.turnId()))) {
            turnActionPending = true;
            refreshControls();
        }
    }

    public void setConnectionState(boolean connected, String message) {
        this.connected = connected;
        if (!connected) {
            turnActionPending = false;
        }
        chatInput.setEnabled(connected);
        sendBtn.setEnabled(connected);
        refreshControls();

        if (!connected) {
            turnStatusLabel.setText(message == null || message.isBlank() ? "Verbindung getrennt" : message);
            turnStatusLabel.setForeground(new Color(240, 90, 90));
        } else if (gameState == null) {
            turnStatusLabel.setText(message == null || message.isBlank() ? "Verbunden, warte auf Spielstand..." : message);
            turnStatusLabel.setForeground(new Color(100, 180, 255));
        } else {
            updateStatus(gameState);
        }
    }

    public void updateGameState(GameStateDTO state) {
        gameState = Objects.requireNonNull(state);
        turnActionPending = false;
        List<PlayerStateDTO> players = state.players() == null ? List.of() : state.players();
        tablePanel.updateTable(state.pot(), state.communityCards(), players);

        myCardsPanel.removeAll();
        if (state.myCards() != null && !state.myCards().isEmpty()) {
            for (Card card : state.myCards()) {
                CardPanel cardPanel = new CardPanel();
                cardPanel.setCard(card);
                myCardsPanel.add(cardPanel);
            }
        } else {
            // Leere Hand-Platzhalter
            for (int i = 0; i < 2; i++) {
                CardPanel cardPanel = new CardPanel();
                cardPanel.setCard(null);
                myCardsPanel.add(cardPanel);
            }
        }

        if (state.chatHistory() != null) {
            chatArea.setText(String.join("\n", state.chatHistory()));
            chatArea.setCaretPosition(chatArea.getDocument().getLength());
        }

        findMe(state).ifPresent(me -> setTitle("OpenPoker - " + me.name()));
        refreshControls();
        if (connected) {
            updateStatus(state);
        }
        revalidate();
        repaint();
    }

    private void refreshControls() {
        PlayerStateDTO me = gameState == null ? null : findMe(gameState).orElse(null);
        GamePhase phase = gameState == null ? null : gameState.phase();
        boolean myTurn = connected && !turnActionPending && phase != null && phase.isBettingPhase()
            && me != null && me.active() && !me.folded();
        int toCall = myTurn ? Math.max(0, gameState.currentBet() - me.currentBet()) : 0;
        int maxRaise = me != null ? me.chips() - toCall : 0;

        foldBtn.setEnabled(myTurn);
        checkBtn.setEnabled(myTurn && toCall == 0);
        callBtn.setEnabled(myTurn && toCall > 0 && me.chips() > 0);
        callBtn.setText(toCall > 0 && me != null ? "CALL " + Math.min(toCall, me.chips()) : "CALL");

        raise50Btn.setEnabled(myTurn && maxRaise >= 50);
        raise100Btn.setEnabled(myTurn && maxRaise >= 100);
        allInBtn.setEnabled(myTurn && maxRaise > 0);
        if (me != null && myTurn && maxRaise > 0) {
            allInBtn.setText("ALL-IN (" + me.chips() + ")");
        } else {
            allInBtn.setText("ALL-IN");
        }

        boolean showdown = phase == GamePhase.SHOWDOWN;
        nextRoundBtn.setVisible(showdown);
        nextRoundBtn.setEnabled(connected && showdown);
    }

    private void updateStatus(GameStateDTO state) {
        List<PlayerStateDTO> players = state.players() == null ? List.of() : state.players();
        PlayerStateDTO me = findMe(state).orElse(null);
        PlayerStateDTO active = players.stream().filter(PlayerStateDTO::active).findFirst().orElse(null);
        String message = state.statusMessage();

        if (state.phase() == GamePhase.WAITING_FOR_PLAYERS) {
            turnStatusLabel.setText(message == null || message.isBlank() ? "Warte auf spielbereite Spieler..." : message);
            turnStatusLabel.setForeground(new Color(255, 175, 0));
        } else if (state.phase() == GamePhase.SHOWDOWN) {
            turnStatusLabel.setText("🏆 " + (message == null || message.isBlank() ? "Rundenende – Showdown" : message));
            turnStatusLabel.setForeground(new Color(255, 215, 0));
        } else if (me != null && me.active()) {
            turnStatusLabel.setText("🎯 DU BIST AM ZUG (" + me.name() + ")");
            turnStatusLabel.setForeground(new Color(50, 225, 100));
        } else if (active != null) {
            turnStatusLabel.setText("⏳ " + active.name() + " ist am Zug");
            turnStatusLabel.setForeground(new Color(100, 180, 255));
        } else if (players.size() < 2) {
            turnStatusLabel.setText("⏳ Warte auf 2. Spieler... (" + players.size() + "/2)");
            turnStatusLabel.setForeground(new Color(255, 175, 0));
        } else {
            turnStatusLabel.setText(message == null || message.isBlank() ? "Spiel läuft..." : message);
            turnStatusLabel.setForeground(new Color(100, 180, 255));
        }
    }

    private Optional<PlayerStateDTO> findMe(GameStateDTO state) {
        if (state.players() == null) {
            return Optional.empty();
        }
        return state.players().stream()
            .filter(player -> Objects.equals(player.id(), state.myPlayerId()))
            .findFirst();
    }

    private static final class ModernButton extends JButton {
        private static final long serialVersionUID = 1L;
        private final Color topColor;
        private final Color bottomColor;
        private boolean hover;

        ModernButton(String text, Color topColor, Color bottomColor) {
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
}
