package de.openpoker.client.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.List;
import java.util.function.BiConsumer;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import de.openpoker.common.model.Card;
import de.openpoker.common.model.GamePhase;
import de.openpoker.common.network.ActionType;
import de.openpoker.common.network.GameStateDTO;
import de.openpoker.common.network.PlayerStateDTO;

public final class ActionBarPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    private final JPanel myCardsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 4));
    private final JButton foldBtn = new ModernButton("FOLD", new Color(195, 45, 45), new Color(150, 25, 25));
    private final JButton checkBtn = new ModernButton("CHECK", new Color(45, 105, 200), new Color(25, 75, 160));
    private final JButton callBtn = new ModernButton("CALL", new Color(38, 155, 70), new Color(22, 115, 48));
    private final JButton raise50Btn = new ModernButton("RAISE +50", new Color(225, 130, 20), new Color(180, 95, 10));
    private final JButton raise100Btn = new ModernButton("RAISE +100", new Color(225, 110, 15), new Color(180, 80, 10));
    private final JButton allInBtn = new ModernButton("ALL-IN", new Color(215, 40, 20), new Color(160, 20, 10));
    private final JButton nextRoundBtn = new ModernButton("NÄCHSTE RUNDE", new Color(135, 55, 195), new Color(95, 30, 150));

    private final BiConsumer<ActionType, Integer> turnActionHandler;
    private int currentAllInRaise;

    public ActionBarPanel(BiConsumer<ActionType, Integer> turnActionHandler, Runnable nextRoundHandler) {
        super(new BorderLayout());
        this.turnActionHandler = turnActionHandler;

        setBackground(new Color(16, 18, 25));
        setBorder(new EmptyBorder(8, 12, 14, 12));

        add(createHandContainer(), BorderLayout.NORTH);
        add(createButtonPanel(nextRoundHandler), BorderLayout.SOUTH);
    }

    private JPanel createHandContainer() {
        JPanel handContainer = new JPanel(new BorderLayout());
        handContainer.setOpaque(false);

        JLabel handLabel = new JLabel("DEINE HAND", JLabel.CENTER);
        handLabel.setFont(new Font("SansSerif", Font.BOLD, 11));
        handLabel.setForeground(new Color(160, 170, 190));
        handLabel.setBorder(new EmptyBorder(0, 0, 2, 0));
        handContainer.add(handLabel, BorderLayout.NORTH);

        myCardsPanel.setOpaque(false);
        handContainer.add(myCardsPanel, BorderLayout.CENTER);
        return handContainer;
    }

    private JPanel createButtonPanel(Runnable nextRoundHandler) {
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 8));
        buttonPanel.setOpaque(false);

        foldBtn.addActionListener(e -> turnActionHandler.accept(ActionType.FOLD, 0));
        checkBtn.addActionListener(e -> turnActionHandler.accept(ActionType.CHECK, 0));
        callBtn.addActionListener(e -> turnActionHandler.accept(ActionType.CALL, 0));
        raise50Btn.addActionListener(e -> turnActionHandler.accept(ActionType.RAISE, 50));
        raise100Btn.addActionListener(e -> turnActionHandler.accept(ActionType.RAISE, 100));
        allInBtn.addActionListener(e -> handleAllIn());
        nextRoundBtn.addActionListener(e -> nextRoundHandler.run());

        buttonPanel.add(foldBtn);
        buttonPanel.add(checkBtn);
        buttonPanel.add(callBtn);
        buttonPanel.add(raise50Btn);
        buttonPanel.add(raise100Btn);
        buttonPanel.add(allInBtn);
        buttonPanel.add(nextRoundBtn);
        return buttonPanel;
    }

    private void handleAllIn() {
        if (currentAllInRaise > 0) {
            turnActionHandler.accept(ActionType.RAISE, currentAllInRaise);
        } else {
            turnActionHandler.accept(ActionType.CALL, 0);
        }
    }

    public void updateCards(List<Card> cards) {
        myCardsPanel.removeAll();
        if (!cards.isEmpty()) {
            for (Card card : cards) {
                CardPanel cardPanel = new CardPanel();
                cardPanel.setCard(card);
                myCardsPanel.add(cardPanel);
            }
        } else {
            // zwei leere karten anzeigen
            for (int i = 0; i < 2; i++) {
                CardPanel cardPanel = new CardPanel();
                cardPanel.setCard(null);
                myCardsPanel.add(cardPanel);
            }
        }
    }

    public void updateControls(
            GameStateDTO gameState,
            PlayerStateDTO me,
            boolean connected,
            boolean turnActionPending) {
        GamePhase phase = gameState == null ? null : gameState.phase();
        boolean myTurn = connected && !turnActionPending && phase != null && phase.isBettingPhase()
            && me != null && me.active() && !me.folded();
        int toCall = myTurn ? Math.max(0, gameState.currentBet() - me.currentBet()) : 0;
        int maxRaise = me != null ? me.chips() - toCall : 0;
        currentAllInRaise = maxRaise;

        foldBtn.setEnabled(myTurn);
        checkBtn.setEnabled(myTurn && toCall == 0);
        callBtn.setEnabled(myTurn && toCall > 0 && me.chips() > 0);
        callBtn.setText(toCall > 0 ? "CALL " + Math.min(toCall, me.chips()) : "CALL");

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
}
