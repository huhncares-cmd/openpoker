package de.openpoker.server;

import java.util.ArrayList;
import java.util.List;
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
        @Override
        public int compareTo(HandResult other) {
            int comparison = Integer.compare(rank.getValue(), other.rank.getValue());
            if (comparison != 0) {
                return comparison;
            }

            for (int i = 0; i < tieBreakers.size(); i++) {
                comparison = Integer.compare(tieBreakers.get(i), other.tieBreakers.get(i));
                if (comparison != 0) {
                    return comparison;
                }
            }
            return 0;
        }
    }

    public static HandResult evaluateHand(List<Card> holeCards, List<Card> communityCards) {
        List<Card> cards = new ArrayList<>();
        cards.addAll(holeCards);
        cards.addAll(communityCards);

        if (cards.size() < 5 || cards.size() > 7) {
            throw new IllegalArgumentException("Für die Auswertung werden 5 bis 7 Karten benötigt.");
        }

        if (cards.size() == 5) {
            return evaluateFive(cards);
        }

        HandResult best = null;
        if (cards.size() == 6) {
            for (int skip = 0; skip < cards.size(); skip++) {
                HandResult result = evaluateWithout(cards, skip, -1);
                if (best == null || result.compareTo(best) > 0) {
                    best = result;
                }
            }
        } else {
            // Bei 7 Karten werden jeweils 2 weggelassen: 7 über 2 = 21 Möglichkeiten.
            for (int skipOne = 0; skipOne < cards.size(); skipOne++) {
                for (int skipTwo = skipOne + 1; skipTwo < cards.size(); skipTwo++) {
                    HandResult result = evaluateWithout(cards, skipOne, skipTwo);
                    if (best == null || result.compareTo(best) > 0) {
                        best = result;
                    }
                }
            }
        }
        return best;
    }

    private static HandResult evaluateWithout(List<Card> cards, int skipOne, int skipTwo) {
        List<Card> fiveCards = new ArrayList<>();
        for (int index = 0; index < cards.size(); index++) {
            if (index != skipOne && index != skipTwo) {
                fiveCards.add(cards.get(index));
            }
        }
        return evaluateFive(fiveCards);
    }

    private static HandResult evaluateFive(List<Card> cards) {
        int[] rankCounts = new int[15];
        List<Integer> ranksDescending = new ArrayList<>();
        for (Card card : cards) {
            int value = card.rank().getValue();
            rankCounts[value]++;
        }

        List<Integer> fourOfAKind = new ArrayList<>();
        List<Integer> threeOfAKind = new ArrayList<>();
        List<Integer> pairs = new ArrayList<>();
        List<Integer> singleCards = new ArrayList<>();
        for (int value = 14; value >= 2; value--) {
            for (int count = 0; count < rankCounts[value]; count++) {
                ranksDescending.add(value);
            }
            if (rankCounts[value] == 4) {
                fourOfAKind.add(value);
            } else if (rankCounts[value] == 3) {
                threeOfAKind.add(value);
            } else if (rankCounts[value] == 2) {
                pairs.add(value);
            } else if (rankCounts[value] == 1) {
                singleCards.add(value);
            }
        }

        // Flush prüfen: Haben alle 5 Karten dieselbe Farbe?
        boolean flush = true;
        Suit firstSuit = cards.get(0).suit();
        for (Card card : cards) {
            if (card.suit() != firstSuit) {
                flush = false;
                break;
            }
        }

        List<Integer> ranksAscending = new ArrayList<>();
        for (int value = 2; value <= 14; value++) {
            if (rankCounts[value] > 0) {
                ranksAscending.add(value);
            }
        }
        int straightHigh = straightHigh(ranksAscending);

        if (flush && straightHigh == 14) {
            return result(HandRank.ROYAL_FLUSH, List.of(14));
        }
        if (flush && straightHigh > 0) {
            return result(HandRank.STRAIGHT_FLUSH, List.of(straightHigh));
        }
        if (!fourOfAKind.isEmpty()) {
            return result(HandRank.FOUR_OF_A_KIND,
                    List.of(fourOfAKind.get(0), singleCards.get(0)));
        }
        if (!threeOfAKind.isEmpty() && !pairs.isEmpty()) {
            return result(HandRank.FULL_HOUSE,
                    List.of(threeOfAKind.get(0), pairs.get(0)));
        }
        if (flush) {
            return result(HandRank.FLUSH, ranksDescending);
        }
        if (straightHigh > 0) {
            return result(HandRank.STRAIGHT, List.of(straightHigh));
        }
        if (!threeOfAKind.isEmpty()) {
            List<Integer> tieBreakers = new ArrayList<>();
            tieBreakers.add(threeOfAKind.get(0));
            tieBreakers.addAll(singleCards);
            return result(HandRank.THREE_OF_A_KIND, tieBreakers);
        }
        if (pairs.size() >= 2) {
            return result(HandRank.TWO_PAIR,
                    List.of(pairs.get(0), pairs.get(1), singleCards.get(0)));
        }
        if (pairs.size() == 1) {
            List<Integer> tieBreakers = new ArrayList<>();
            tieBreakers.add(pairs.get(0));
            tieBreakers.addAll(singleCards);
            return result(HandRank.ONE_PAIR, tieBreakers);
        }
        return result(HandRank.HIGH_CARD, ranksDescending);
    }

    private static int straightHigh(List<Integer> ranks) {
        if (ranks.size() != 5) {
            return 0;
        }

        // Sonderfall: Wheel-Straße (Ass als 1: A-2-3-4-5)
        if (ranks.equals(List.of(2, 3, 4, 5, 14))) {
            return 5;
        }
        for (int i = 1; i < ranks.size(); i++) {
            if (ranks.get(i) != ranks.get(0) + i) {
                return 0;
            }
        }
        return ranks.get(ranks.size() - 1);
    }

    private static HandResult result(HandRank rank, List<Integer> tieBreakers) {
        return new HandResult(rank, tieBreakers, rank.getName());
    }
}
