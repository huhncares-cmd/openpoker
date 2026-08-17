package de.openpoker.server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import de.openpoker.common.model.Card;
import de.openpoker.common.model.Rank;
import de.openpoker.common.model.Suit;

public class HandEvaluator {

    public enum HandRank {
        HIGH_CARD(1, "Höchste Karte"),
        ONE_PAIR(2, "Ein Paar"),
        TWO_PAIR(3, "Zwei Paare"),
        THREE_OF_A_KIND(4, "Drilling"),
        STRAIGHT(5, "Straße"),
        FLUSH(6, "Flush"),
        FULL_HOUSE(7, "Full House"),
        FOUR_OF_A_KIND(8, "Vierling"),
        STRAIGHT_FLUSH(9, "Straight Flush"),
        ROYAL_FLUSH(10, "Royal Flush");

        private final int value;
        private final String name;

        HandRank(int value, String name) {
            this.value = value;
            this.name = name;
        }

        public int getValue() {
            return value;
        }

        public String getName() {
            return name;
        }
    }

    public record HandResult(HandRank rank, int score, String description) implements Comparable<HandResult> {
        @Override
        public int compareTo(HandResult o) {
            return Integer.compare(this.score, o.score);
        }
    }

    public static HandResult evaluateHand(List<Card> holeCards, List<Card> communityCards) {
        List<Card> allCards = new ArrayList<>();
        if (holeCards != null) allCards.addAll(holeCards);
        if (communityCards != null) allCards.addAll(communityCards);

        if (allCards.size() < 5) {
            return new HandResult(HandRank.HIGH_CARD, 100, "Unvollständig");
        }

        allCards.sort((c1, c2) -> Integer.compare(c2.rank().getValue(), c1.rank().getValue()));

        Map<Rank, Integer> rankCounts = new HashMap<>();
        Map<Suit, Integer> suitCounts = new HashMap<>();

        for (Card c : allCards) {
            rankCounts.put(c.rank(), rankCounts.getOrDefault(c.rank(), 0) + 1);
            suitCounts.put(c.suit(), suitCounts.getOrDefault(c.suit(), 0) + 1);
        }

        boolean isFlush = suitCounts.values().stream().anyMatch(count -> count >= 5);
        boolean isStraight = checkStraight(allCards);

        if (isFlush && isStraight) {
            if (allCards.get(0).rank() == Rank.ACE) {
                return new HandResult(HandRank.ROYAL_FLUSH, 1000, "Royal Flush");
            }
            return new HandResult(HandRank.STRAIGHT_FLUSH, 900 + allCards.get(0).rank().getValue(), "Straight Flush");
        }

        for (Map.Entry<Rank, Integer> entry : rankCounts.entrySet()) {
            if (entry.getValue() == 4) {
                return new HandResult(HandRank.FOUR_OF_A_KIND, 800 + entry.getKey().getValue(), "Vierling (" + entry.getKey() + ")");
            }
        }

        boolean hasThree = false;
        Rank threeRank = null;
        boolean hasPair = false;
        Rank pairRank = null;

        for (Map.Entry<Rank, Integer> entry : rankCounts.entrySet()) {
            if (entry.getValue() >= 3 && !hasThree) {
                hasThree = true;
                threeRank = entry.getKey();
            } else if (entry.getValue() >= 2) {
                hasPair = true;
                pairRank = entry.getKey();
            }
        }

        if (hasThree && hasPair) {
            return new HandResult(HandRank.FULL_HOUSE, 700 + threeRank.getValue(), "Full House (" + threeRank + " über " + pairRank + ")");
        }

        if (isFlush) {
            return new HandResult(HandRank.FLUSH, 600 + allCards.get(0).rank().getValue(), "Flush");
        }

        if (isStraight) {
            return new HandResult(HandRank.STRAIGHT, 500 + allCards.get(0).rank().getValue(), "Straße (" + allCards.get(0).rank() + " hoch)");
        }

        if (hasThree) {
            return new HandResult(HandRank.THREE_OF_A_KIND, 400 + threeRank.getValue(), "Drilling (" + threeRank + ")");
        }

        List<Rank> pairs = new ArrayList<>();
        for (Map.Entry<Rank, Integer> entry : rankCounts.entrySet()) {
            if (entry.getValue() == 2) {
                pairs.add(entry.getKey());
            }
        }

        if (pairs.size() >= 2) {
            pairs.sort((r1, r2) -> Integer.compare(r2.getValue(), r1.getValue()));
            return new HandResult(HandRank.TWO_PAIR, 300 + pairs.get(0).getValue() * 10 + pairs.get(1).getValue(), "Zwei Paare (" + pairs.get(0) + " & " + pairs.get(1) + ")");
        }

        if (pairs.size() == 1) {
            return new HandResult(HandRank.ONE_PAIR, 200 + pairs.get(0).getValue(), "Ein Paar (" + pairs.get(0) + ")");
        }

        Rank highCardRank = allCards.get(0).rank();
        return new HandResult(HandRank.HIGH_CARD, 100 + highCardRank.getValue(), "Höchste Karte (" + highCardRank + ")");
    }

    private static boolean checkStraight(List<Card> sortedCards) {
        List<Integer> values = new ArrayList<>();
        for (Card c : sortedCards) {
            if (!values.contains(c.rank().getValue())) {
                values.add(c.rank().getValue());
            }
        }
        Collections.sort(values);

        int consecutive = 1;
        for (int i = 0; i < values.size() - 1; i++) {
            if (values.get(i + 1) == values.get(i) + 1) {
                consecutive++;
                if (consecutive >= 5) return true;
            } else {
                consecutive = 1;
            }
        }
        return false;
    }
}
