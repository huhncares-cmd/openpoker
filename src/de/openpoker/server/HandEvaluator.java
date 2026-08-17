package de.openpoker.server;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import de.openpoker.common.model.Card;

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

    /**
     * The tie breakers are ordered from most to least significant. For example,
     * two pair stores [higher pair, lower pair, kicker].
     */
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
            List<Integer> highCards = cards.stream()
                    .map(card -> card.rank().getValue())
                    .sorted(Comparator.reverseOrder())
                    .toList();
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
        for (Card card : cards) {
            rankCounts.merge(card.rank().getValue(), 1, Integer::sum);
        }

        List<Integer> ranksDescending = cards.stream()
                .map(card -> card.rank().getValue())
                .sorted(Comparator.reverseOrder())
                .toList();
        List<Map.Entry<Integer, Integer>> groups = rankCounts.entrySet().stream()
                .sorted(Map.Entry.<Integer, Integer>comparingByValue().reversed()
                        .thenComparing(Map.Entry.<Integer, Integer>comparingByKey().reversed()))
                .toList();

        boolean flush = cards.stream().allMatch(card -> card.suit() == cards.get(0).suit());
        int straightHigh = straightHigh(rankCounts.keySet().stream().toList());

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

        List<Integer> sorted = ranks.stream().sorted().toList();
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
        return groups.stream().map(Map.Entry::getKey).toList();
    }

    private static HandResult result(HandRank rank, List<Integer> tieBreakers) {
        return new HandResult(rank, tieBreakers, rank.getName());
    }
}
