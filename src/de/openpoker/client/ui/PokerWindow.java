package de.openpoker.client.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.function.Consumer;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import de.openpoker.common.model.Card;
import de.openpoker.common.network.GameStateDTO;
import de.openpoker.common.network.PlayerAction;

public class PokerWindow extends JFrame {
    private final PokerTablePanel tablePanel = new PokerTablePanel();
    private final JPanel myCardsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
    private final JTextArea chatArea = new JTextArea();
    private final JTextField chatInput = new JTextField(12);
    private final JLabel turnStatusLabel = new JLabel("Warte auf Server...", JLabel.CENTER);

    private final JButton foldBtn = createStyledButton("FOLD", new Color(180, 40, 40));
    private final JButton checkBtn = createStyledButton("CHECK", new Color(60, 90, 150));
    private final JButton callBtn = createStyledButton("CALL", new Color(40, 140, 60));
    private final JButton raiseBtn = createStyledButton("RAISE +50", new Color(200, 130, 20));
    private final JButton nextRoundBtn = createStyledButton("NÄCHSTE RUNDE ➔", new Color(120, 50, 180));

    private Consumer<PlayerAction> actionListener;
    private String myPlayerName;

    public PokerWindow() {
        setTitle("OpenPoker Client");
        setSize(980, 680);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // Statusleiste ganz oben
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBackground(new Color(20, 22, 28));
        topPanel.setBorder(new EmptyBorder(8, 10, 8, 10));

        turnStatusLabel.setFont(new Font("SansSerif", Font.BOLD, 17));
        turnStatusLabel.setForeground(Color.CYAN);
        topPanel.add(turnStatusLabel, BorderLayout.CENTER);
        add(topPanel, BorderLayout.NORTH);

        // Mitte: Pokertisch
        add(tablePanel, BorderLayout.CENTER);

        // Rechts: Chatbereich
        JPanel chatPanel = new JPanel(new BorderLayout());
        chatPanel.setPreferredSize(new Dimension(260, 0));
        chatPanel.setBackground(new Color(28, 30, 36));
        chatPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        JLabel chatTitle = new JLabel("💬 CHAT & LOGS", JLabel.CENTER);
        chatTitle.setFont(new Font("SansSerif", Font.BOLD, 14));
        chatTitle.setForeground(Color.LIGHT_GRAY);
        chatTitle.setBorder(new EmptyBorder(0, 0, 8, 0));
        chatPanel.add(chatTitle, BorderLayout.NORTH);

        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        chatArea.setBackground(new Color(18, 20, 24));
        chatArea.setForeground(new Color(220, 220, 220));
        chatArea.setFont(new Font("SansSerif", Font.PLAIN, 12));

        JScrollPane scrollPane = new JScrollPane(chatArea);
        scrollPane.setBorder(null);
        chatPanel.add(scrollPane, BorderLayout.CENTER);

        JPanel chatInputPanel = new JPanel(new BorderLayout(5, 0));
        chatInputPanel.setOpaque(false);
        chatInputPanel.setBorder(new EmptyBorder(8, 0, 0, 0));

        JButton sendBtn = new JButton("Senden");
        sendBtn.setFont(new Font("SansSerif", Font.BOLD, 12));

        Runnable sendChat = () -> {
            String text = chatInput.getText().trim();
            if (!text.isEmpty()) {
                sendAction(new PlayerAction.Chat(myPlayerName != null ? myPlayerName : "Spieler", text));
                chatInput.setText("");
            }
        };

        sendBtn.addActionListener(e -> sendChat.run());
        chatInput.addActionListener(e -> sendChat.run());

        chatInputPanel.add(chatInput, BorderLayout.CENTER);
        chatInputPanel.add(sendBtn, BorderLayout.EAST);
        chatPanel.add(chatInputPanel, BorderLayout.SOUTH);

        add(chatPanel, BorderLayout.EAST);

        // Unten: Handkarten & Aktions-Buttons
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBackground(new Color(18, 20, 24));
        bottomPanel.setBorder(new EmptyBorder(10, 10, 15, 10));

        myCardsPanel.setOpaque(false);
        bottomPanel.add(myCardsPanel, BorderLayout.NORTH);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 8));
        buttonPanel.setOpaque(false);

        foldBtn.addActionListener(e -> sendAction(new PlayerAction.Fold(myPlayerName)));
        checkBtn.addActionListener(e -> sendAction(new PlayerAction.Check(myPlayerName)));
        callBtn.addActionListener(e -> sendAction(new PlayerAction.Call(myPlayerName)));
        raiseBtn.addActionListener(e -> sendAction(new PlayerAction.Raise(myPlayerName, 50)));
        nextRoundBtn.addActionListener(e -> sendAction(new PlayerAction.NextRound(myPlayerName)));

        setButtonsEnabled(false);
        nextRoundBtn.setVisible(false);

        buttonPanel.add(foldBtn);
        buttonPanel.add(checkBtn);
        buttonPanel.add(callBtn);
        buttonPanel.add(raiseBtn);
        buttonPanel.add(nextRoundBtn);

        bottomPanel.add(buttonPanel, BorderLayout.SOUTH);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    private JButton createStyledButton(String text, Color bg) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("SansSerif", Font.BOLD, 14));
        btn.setBackground(bg);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setPreferredSize(new Dimension(150, 38));
        return btn;
    }

    public void setActionListener(Consumer<PlayerAction> listener) {
        this.actionListener = listener;
    }

    private void sendAction(PlayerAction action) {
        if (actionListener != null) {
            actionListener.accept(action);
        }
    }

    private void setButtonsEnabled(boolean enabled) {
        foldBtn.setEnabled(enabled);
        checkBtn.setEnabled(enabled);
        callBtn.setEnabled(enabled);
        raiseBtn.setEnabled(enabled);
    }

    public void updateGameState(GameStateDTO state) {
        if (state.myPlayerName() != null) {
            this.myPlayerName = state.myPlayerName();
            setTitle("OpenPoker - " + myPlayerName);
        }

        tablePanel.updateTable(state.pot(), state.communityCards(), state.playerNames(), state.playerLastActions(), state.activePlayerName());

        myCardsPanel.removeAll();
        if (state.myCards() != null) {
            for (Card card : state.myCards()) {
                CardPanel cp = new CardPanel();
                cp.setCard(card);
                myCardsPanel.add(cp);
            }
        }

        if (state.chatHistory() != null) {
            chatArea.setText(String.join("\n", state.chatHistory()));
            chatArea.setCaretPosition(chatArea.getDocument().getLength());
        }

        boolean isEnoughPlayers = state.playerNames() != null && state.playerNames().size() >= 2;
        boolean isShowdown = state.statusMessage() != null && (state.statusMessage().contains("SHOWDOWN") || state.statusMessage().contains("FOLD"));
        boolean isMyTurn = isEnoughPlayers && !isShowdown && myPlayerName != null && myPlayerName.equals(state.activePlayerName());

        setButtonsEnabled(isMyTurn);
        nextRoundBtn.setVisible(isShowdown && isEnoughPlayers);

        if (!isEnoughPlayers) {
            turnStatusLabel.setText("⏳ Warte auf 2. Spieler... (" + (state.playerNames() != null ? state.playerNames().size() : 0) + "/2)");
            turnStatusLabel.setForeground(new Color(255, 175, 0));
        } else if (isShowdown) {
            turnStatusLabel.setText("🏆 RUNDENENDE / SHOWDOWN - Klicke 'Nächste Runde'");
            turnStatusLabel.setForeground(new Color(200, 130, 255));
        } else if (isMyTurn) {
            turnStatusLabel.setText("🎯 DU BIST AM ZUG (" + myPlayerName + ")!");
            turnStatusLabel.setForeground(new Color(50, 225, 100));
        } else {
            turnStatusLabel.setText("⏳ Warte auf Zug von: " + state.activePlayerName());
            turnStatusLabel.setForeground(new Color(100, 180, 255));
        }

        revalidate();
        repaint();
    }
}
