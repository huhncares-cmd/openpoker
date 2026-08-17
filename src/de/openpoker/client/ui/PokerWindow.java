package de.openpoker.client.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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
import de.openpoker.common.model.GamePhase;
import de.openpoker.common.network.GameStateDTO;
import de.openpoker.common.network.PlayerAction;
import de.openpoker.common.network.PlayerStateDTO;

public final class PokerWindow extends JFrame {
    private static final long serialVersionUID = 1L;
    private static final int RAISE_AMOUNT = 50;

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
    private final JButton sendBtn = new JButton("Senden");

    private transient Consumer<PlayerAction> actionListener;
    private GameStateDTO gameState;
    private boolean connected;

    public PokerWindow() {
        setTitle("OpenPoker Client");
        setSize(980, 680);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBackground(new Color(20, 22, 28));
        topPanel.setBorder(new EmptyBorder(8, 10, 8, 10));
        turnStatusLabel.setFont(new Font("SansSerif", Font.BOLD, 17));
        turnStatusLabel.setForeground(Color.CYAN);
        topPanel.add(turnStatusLabel, BorderLayout.CENTER);
        add(topPanel, BorderLayout.NORTH);

        add(tablePanel, BorderLayout.CENTER);
        add(createChatPanel(), BorderLayout.EAST);
        add(createActionPanel(), BorderLayout.SOUTH);
        refreshControls();
    }

    private JPanel createChatPanel() {
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

        JPanel inputPanel = new JPanel(new BorderLayout(5, 0));
        inputPanel.setOpaque(false);
        inputPanel.setBorder(new EmptyBorder(8, 0, 0, 0));
        sendBtn.setFont(new Font("SansSerif", Font.BOLD, 12));
        sendBtn.addActionListener(e -> sendChat());
        chatInput.addActionListener(e -> sendChat());
        inputPanel.add(chatInput, BorderLayout.CENTER);
        inputPanel.add(sendBtn, BorderLayout.EAST);
        chatPanel.add(inputPanel, BorderLayout.SOUTH);
        return chatPanel;
    }

    private JPanel createActionPanel() {
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBackground(new Color(18, 20, 24));
        bottomPanel.setBorder(new EmptyBorder(10, 10, 15, 10));
        myCardsPanel.setOpaque(false);
        bottomPanel.add(myCardsPanel, BorderLayout.NORTH);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 8));
        buttonPanel.setOpaque(false);
        foldBtn.addActionListener(e -> sendAction(new PlayerAction.Fold()));
        checkBtn.addActionListener(e -> sendAction(new PlayerAction.Check()));
        callBtn.addActionListener(e -> sendAction(new PlayerAction.Call()));
        raiseBtn.addActionListener(e -> sendAction(new PlayerAction.Raise(RAISE_AMOUNT)));
        nextRoundBtn.addActionListener(e -> sendAction(new PlayerAction.NextRound()));
        buttonPanel.add(foldBtn);
        buttonPanel.add(checkBtn);
        buttonPanel.add(callBtn);
        buttonPanel.add(raiseBtn);
        buttonPanel.add(nextRoundBtn);
        bottomPanel.add(buttonPanel, BorderLayout.SOUTH);
        return bottomPanel;
    }

    private JButton createStyledButton(String text, Color background) {
        JButton button = new JButton(text);
        button.setFont(new Font("SansSerif", Font.BOLD, 14));
        button.setBackground(background);
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setPreferredSize(new Dimension(150, 38));
        return button;
    }

    private void sendChat() {
        String text = chatInput.getText().trim();
        if (connected && !text.isEmpty()) {
            sendAction(new PlayerAction.Chat(text));
            chatInput.setText("");
        }
    }

    public void setActionListener(Consumer<PlayerAction> listener) {
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

    private void sendAction(PlayerAction action) {
        if (connected && actionListener != null) {
            actionListener.accept(action);
        }
    }

    public void setConnectionState(boolean connected, String message) {
        this.connected = connected;
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
        List<PlayerStateDTO> players = state.players() == null ? List.of() : state.players();
        tablePanel.updateTable(state.pot(), state.communityCards(), players);

        myCardsPanel.removeAll();
        if (state.myCards() != null) {
            for (Card card : state.myCards()) {
                CardPanel cardPanel = new CardPanel();
                cardPanel.setCard(card);
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
        boolean myTurn = connected && phase != null && phase.isBettingPhase()
            && me != null && me.active() && !me.folded();
        int toCall = myTurn ? Math.max(0, gameState.currentBet() - me.currentBet()) : 0;

        foldBtn.setEnabled(myTurn);
        checkBtn.setEnabled(myTurn && toCall == 0);
        callBtn.setEnabled(myTurn && toCall > 0 && me.chips() > 0);
        callBtn.setText(toCall > 0 && me != null ? "CALL " + Math.min(toCall, me.chips()) : "CALL");
        raiseBtn.setEnabled(myTurn && me.chips() >= toCall + RAISE_AMOUNT);

        boolean showdown = phase == GamePhase.SHOWDOWN;
        nextRoundBtn.setVisible(showdown);
        nextRoundBtn.setEnabled(connected && showdown);
    }

    private void updateStatus(GameStateDTO state) {
        List<PlayerStateDTO> players = state.players() == null ? List.of() : state.players();
        PlayerStateDTO me = findMe(state).orElse(null);
        PlayerStateDTO active = players.stream().filter(PlayerStateDTO::active).findFirst().orElse(null);
        String message = state.statusMessage();

        if (players.size() < 2) {
            turnStatusLabel.setText("⏳ Warte auf 2. Spieler... (" + players.size() + "/2)");
            turnStatusLabel.setForeground(new Color(255, 175, 0));
        } else if (state.phase() == GamePhase.WAITING_FOR_PLAYERS) {
            turnStatusLabel.setText(message == null || message.isBlank() ? "Warte auf spielbereite Spieler..." : message);
            turnStatusLabel.setForeground(new Color(255, 175, 0));
        } else if (state.phase() == GamePhase.SHOWDOWN) {
            turnStatusLabel.setText("🏆 " + (message == null || message.isBlank() ? "Rundenende" : message));
            turnStatusLabel.setForeground(new Color(200, 130, 255));
        } else if (me != null && me.active()) {
            turnStatusLabel.setText("🎯 DU BIST AM ZUG (" + me.name() + ")");
            turnStatusLabel.setForeground(new Color(50, 225, 100));
        } else if (active != null) {
            turnStatusLabel.setText("⏳ " + active.name() + " ist am Zug");
            turnStatusLabel.setForeground(new Color(100, 180, 255));
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
}
