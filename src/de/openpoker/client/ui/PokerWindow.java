package de.openpoker.client.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JFrame;
import de.openpoker.common.network.ActionType;
import de.openpoker.common.network.GameStateDTO;
import de.openpoker.common.network.PlayerAction;
import de.openpoker.common.network.PlayerStateDTO;

public final class PokerWindow extends JFrame {
    private static final long serialVersionUID = 1L; //eclipse warning

    private final StatusBarPanel statusBar = new StatusBarPanel();
    private final PokerTablePanel tablePanel = new PokerTablePanel();
    private final ChatPanel chatPanel = new ChatPanel(text -> sendAction(new PlayerAction(ActionType.CHAT, text)));
    private final ActionBarPanel actionBar = new ActionBarPanel(
        this::sendTurnAction,
        () -> sendAction(new PlayerAction(ActionType.NEXT_ROUND))
    );

    private PokerActionListener actionListener;
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

        add(statusBar, BorderLayout.NORTH);
        add(tablePanel, BorderLayout.CENTER);
        add(chatPanel, BorderLayout.EAST);
        add(actionBar, BorderLayout.SOUTH);
        refreshControls();
    }

    public void setActionListener(PokerActionListener listener) {
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

    public void setConnectionState(boolean connected, String message) {
        this.connected = connected;
        if (!connected) {
            turnActionPending = false;
        }
        chatPanel.setChatEnabled(connected);
        refreshControls();

        if (!connected) {
            statusBar.showDisconnected(message);
        } else if (gameState == null) {
            statusBar.showWaitingForState(message);
        } else {
            statusBar.updateStatus(gameState, findMe(gameState));
        }
    }

    public void updateGameState(GameStateDTO state) {
        gameState = state;
        turnActionPending = false;
        PlayerStateDTO me = findMe(state);

        tablePanel.updateTable(state.pot(), state.communityCards(), state.players());
        actionBar.updateCards(state.myCards());
        chatPanel.updateMessages(state.chatHistory());

        if (me != null) {
            setTitle("OpenPoker - " + me.name());
        }
        refreshControls();
        if (connected) {
            statusBar.updateStatus(state, me);
        }
        revalidate();
        repaint();
    }

    private void sendTurnAction(ActionType type, int amount) {
        if (!connected || gameState == null || turnActionPending) {
            return;
        }
        PlayerAction action = new PlayerAction(type, gameState.turnId(), amount);
        if (sendAction(action)) {
            turnActionPending = true;
            refreshControls();
        }
    }

    private boolean sendAction(PlayerAction action) {
        return connected && actionListener != null && actionListener.sendAction(action);
    }

    private void refreshControls() {
        PlayerStateDTO me = gameState == null ? null : findMe(gameState);
        actionBar.updateControls(gameState, me, connected, turnActionPending);
    }

    private static PlayerStateDTO findMe(GameStateDTO state) {
        for (PlayerStateDTO player : state.players()) {
            if (player.id().equals(state.myPlayerId())) {
                return player;
            }
        }
        return null;
    }
}
