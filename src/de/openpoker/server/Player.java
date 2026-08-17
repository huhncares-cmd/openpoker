package de.openpoker.server;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import de.openpoker.common.model.Card;
import de.openpoker.common.network.GameStateDTO;

public final class Player {
    private final String id;
    private final String name;
    private int chips;
    private boolean folded;
    private boolean inHand;
    private boolean allIn;
    private int currentBet;
    private int handContribution;
    private final List<Card> cards = new ArrayList<>();
    private final ObjectOutputStream out;
    private final BlockingQueue<Delivery> pendingDelivery = new ArrayBlockingQueue<>(1);
    private final Thread sender;
    private final AtomicBoolean sendFailureReported = new AtomicBoolean();
    private volatile boolean senderClosed;

    public Player(String id, String name, int chips, ObjectOutputStream out) {
        this.id = id;
        this.name = name;
        this.chips = chips;
        this.out = out;
        sender = Thread.ofVirtual().name("poker-send-" + id).start(this::sendLoop);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getChips() {
        return chips;
    }

    public void setChips(int chips) {
        if (chips < 0) {
            throw new IllegalArgumentException("Chips dürfen nicht negativ sein.");
        }
        this.chips = chips;
    }

    public void addChips(int amount) {
        chips = Math.addExact(chips, amount);
    }

    public boolean isFolded() {
        return folded;
    }

    public void setFolded(boolean folded) {
        this.folded = folded;
    }

    public boolean isInHand() {
        return inHand;
    }

    public void setInHand(boolean inHand) {
        this.inHand = inHand;
    }

    public boolean isAllIn() {
        return allIn;
    }

    public int getCurrentBet() {
        return currentBet;
    }

    public int getHandContribution() {
        return handContribution;
    }

    public void prepareForHand(boolean participating) {
        cards.clear();
        folded = false;
        inHand = participating;
        allIn = false;
        currentBet = 0;
        handContribution = 0;
    }

    public void resetBettingRound() {
        currentBet = 0;
    }

    public int commitChips(int requestedAmount) {
        if (requestedAmount < 0) {
            throw new IllegalArgumentException("Der Einsatz darf nicht negativ sein.");
        }

        int paid = Math.min(requestedAmount, chips);
        chips -= paid;
        currentBet += paid;
        handContribution += paid;
        allIn = inHand && chips == 0;
        return paid;
    }

    public List<Card> getCards() {
        return cards;
    }

    public void send(GameStateDTO state, Runnable onFailure) {
        if (senderClosed) {
            return;
        }

        Delivery delivery = new Delivery(state, onFailure);
        while (!pendingDelivery.offer(delivery)) {
            pendingDelivery.poll();
        }
    }

    public void closeSender() {
        senderClosed = true;
        sender.interrupt();
    }

    private void sendLoop() {
        Delivery delivery = null;
        try {
            while (!senderClosed) {
                delivery = pendingDelivery.take();
                out.writeObject(delivery.state());
                out.reset();
                out.flush();
                delivery = null;
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } catch (IOException | RuntimeException exception) {
            if (sendFailureReported.compareAndSet(false, true)) {
                try {
                    out.close();
                } catch (IOException ignored) {
                    // Das Schließen dient nur dazu, den Reader-Thread aufzuwecken.
                }
                if (delivery != null) {
                    delivery.onFailure().run();
                }
            }
        }
    }

    private record Delivery(GameStateDTO state, Runnable onFailure) {
    }
}
