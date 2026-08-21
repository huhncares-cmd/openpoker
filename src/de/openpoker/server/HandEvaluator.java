package de.openpoker.server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import de.openpoker.common.model.Card;
import de.openpoker.common.model.Suit;

public final class HandEvaluator {

    private HandEvaluator() {
    }

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

    public record HandResult(HandRank rank, List<Integer> tieBreakers, String description)
            implements Comparable<HandResult> {

        public HandResult {
            Objects.requireNonNull(rank, "rank");
            tieBreakers = List.copyOf(tieBreakers);
            Objects.requireNonNull(description, "description");
        }

        @Override
        public int compareTo(HandResult other) {
            Objects.requireNonNull(other, "other");

            int comparison = Integer.compare(rank.getValue(), other.rank.getValue());
            if (comparison != 0) {
                return comparison;
            }

            int sharedLength = Math.min(tieBreakers.size(), other.tieBreakers.size());
            for (int i = 0; i < sharedLength; i++) {
                comparison = Integer.compare(tieBreakers.get(i), other.tieBreakers.get(i));
                if (comparison != 0) {
                    return comparison;
                }
            }
            return Integer.compare(tieBreakers.size(), other.tieBreakers.size());
        }
    }

    public static HandResult evaluateHand(List<Card> holeCards, List<Card> communityCards) {
        List<Card> cards = new ArrayList<>();
        if (holeCards != null) {
            cards.addAll(holeCards);
        }
        if (communityCards != null) {
            cards.addAll(communityCards);
        }

        if (cards.size() < 5) {
            List<Integer> highCards = new ArrayList<>();
            for (Card card : cards) {
                highCards.add(card.rank().getValue());
            }
            highCards.sort(Collections.reverseOrder());
            return new HandResult(HandRank.HIGH_CARD, highCards, "Unvollständig");
        }

        HandResult best = null;
        int cardCount = cards.size();
        for (int first = 0; first < cardCount - 4; first++) {
            for (int second = first + 1; second < cardCount - 3; second++) {
                for (int third = second + 1; third < cardCount - 2; third++) {
                    for (int fourth = third + 1; fourth < cardCount - 1; fourth++) {
                        for (int fifth = fourth + 1; fifth < cardCount; fifth++) {
                            HandResult result = evaluateFive(List.of(
                                    cards.get(first), cards.get(second), cards.get(third),
                                    cards.get(fourth), cards.get(fifth)));
                            if (best == null || result.compareTo(best) > 0) {
                                best = result;
                            }
                        }
                    }
                }
            }
        }
        return best;
    }

    private static HandResult evaluateFive(List<Card> cards) {
        Map<Integer, Integer> rankCounts = new HashMap<>();
        List<Integer> ranksDescending = new ArrayList<>();
        for (Card card : cards) {
            int val = card.rank().getValue();
            ranksDescending.add(val);
            rankCounts.put(val, rankCounts.getOrDefault(val, 0) + 1);
        }
        ranksDescending.sort(Collections.reverseOrder());

        // Sortiere Gruppen: erst nach Häufigkeit (z.B. Drilling vor Paar), dann nach Kartenwert absteigend
        List<Map.Entry<Integer, Integer>> groups = new ArrayList<>(rankCounts.entrySet());
        groups.sort((a, b) -> {
            int countCompare = Integer.compare(b.getValue(), a.getValue());
            if (countCompare != 0) {
                return countCompare;
            }
            return Integer.compare(b.getKey(), a.getKey());
        });

        // Flush prüfen: Haben alle 5 Karten dieselbe Farbe?
        boolean flush = true;
        Suit firstSuit = cards.get(0).suit();
        for (Card card : cards) {
            if (card.suit() != firstSuit) {
                flush = false;
                break;
            }
        }

        int straightHigh = straightHigh(new ArrayList<>(rankCounts.keySet()));

        if (flush && straightHigh == 14) {
            return result(HandRank.ROYAL_FLUSH, List.of(14));
        }
        if (flush && straightHigh > 0) {
            return result(HandRank.STRAIGHT_FLUSH, List.of(straightHigh));
        }
        if (groups.get(0).getValue() == 4) {
            return result(HandRank.FOUR_OF_A_KIND,
                    List.of(groups.get(0).getKey(), groups.get(1).getKey()));
        }
        if (groups.get(0).getValue() == 3 && groups.get(1).getValue() == 2) {
            return result(HandRank.FULL_HOUSE,
                    List.of(groups.get(0).getKey(), groups.get(1).getKey()));
        }
        if (flush) {
            return result(HandRank.FLUSH, ranksDescending);
        }
        if (straightHigh > 0) {
            return result(HandRank.STRAIGHT, List.of(straightHigh));
        }
        if (groups.get(0).getValue() == 3) {
            return result(HandRank.THREE_OF_A_KIND, groupRanks(groups));
        }
        if (groups.get(0).getValue() == 2 && groups.get(1).getValue() == 2) {
            return result(HandRank.TWO_PAIR, groupRanks(groups));
        }
        if (groups.get(0).getValue() == 2) {
            return result(HandRank.ONE_PAIR, groupRanks(groups));
        }
        return result(HandRank.HIGH_CARD, ranksDescending);
    }

    private static int straightHigh(List<Integer> ranks) {
        if (ranks.size() != 5) {
            return 0;
        }

        List<Integer> sorted = new ArrayList<>(ranks);
        Collections.sort(sorted);

        // Sonderfall: Wheel-Straße (Ass als 1: A-2-3-4-5)
        if (sorted.equals(List.of(2, 3, 4, 5, 14))) {
            return 5;
        }
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i) != sorted.get(0) + i) {
                return 0;
            }
        }
        return sorted.get(sorted.size() - 1);
    }

    private static List<Integer> groupRanks(List<Map.Entry<Integer, Integer>> groups) {
        List<Integer> result = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : groups) {
            result.add(entry.getKey());
        }
        return result;
    }

    private static HandResult result(HandRank rank, List<Integer> tieBreakers) {
        return new HandResult(rank, tieBreakers, rank.getName());
    }
}
