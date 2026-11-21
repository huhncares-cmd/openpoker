package de.openpoker.server;

import java.util.ArrayList;
import java.util.List;
import de.openpoker.common.model.Card;
import de.openpoker.common.model.Suit;

public final class HandEvaluator {

    private HandEvaluator() {
    }

    public static HandResult evaluateHand(List<Card> holeCards, List<Card> communityCards) {
        List<Card> cards = new ArrayList<>();
        cards.addAll(holeCards);
        cards.addAll(communityCards);

        return findBestFive(cards, 0, new ArrayList<>());
    }

    private static HandResult findBestFive(List<Card> cards, int startIndex, List<Card> chosen) {
        if (chosen.size() == 5) {
            return evaluateFive(chosen);
        }

        HandResult best = null;
        int needed = 5 - chosen.size();
        for (int index = startIndex; index <= cards.size() - needed; index++) {
            chosen.add(cards.get(index));
            HandResult candidate = findBestFive(cards, index + 1, chosen);
            if (best == null || candidate.compareTo(best) > 0) {
                best = candidate;
            }
            chosen.remove(chosen.size() - 1);
        }
        return best;
    }

    private static HandResult evaluateFive(List<Card> cards) {
        int[] rankCounts = countRanks(cards);
        boolean flush = isFlush(cards);
        int straightHigh = findStraightHigh(rankCounts);

        List<Integer> fourOfAKind = ranksWithCount(rankCounts, 4);
        List<Integer> threeOfAKind = ranksWithCount(rankCounts, 3);
        List<Integer> pairs = ranksWithCount(rankCounts, 2);
        List<Integer> singleCards = ranksWithCount(rankCounts, 1);

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
            return result(HandRank.FLUSH, ranksDescending(rankCounts));
        }
        if (straightHigh > 0) {
            return result(HandRank.STRAIGHT, List.of(straightHigh));
        }
        if (!threeOfAKind.isEmpty()) {
            return result(HandRank.THREE_OF_A_KIND,
                    buildTieBreakers(threeOfAKind.get(0), singleCards));
        }
        if (pairs.size() >= 2) {
            return result(HandRank.TWO_PAIR,
                    List.of(pairs.get(0), pairs.get(1), singleCards.get(0)));
        }
        if (pairs.size() == 1) {
            return result(HandRank.ONE_PAIR,
                    buildTieBreakers(pairs.get(0), singleCards));
        }
        return result(HandRank.HIGH_CARD, ranksDescending(rankCounts));
    }

    private static int[] countRanks(List<Card> cards) {
        int[] rankCounts = new int[15];
        for (Card card : cards) {
            rankCounts[card.rank().getValue()]++;
        }
        return rankCounts;
    }

    private static List<Integer> ranksWithCount(int[] rankCounts, int targetCount) {
        List<Integer> ranks = new ArrayList<>();
        for (int value = 14; value >= 2; value--) {
            if (rankCounts[value] == targetCount) {
                ranks.add(value);
            }
        }
        return ranks;
    }

    private static List<Integer> ranksDescending(int[] rankCounts) {
        List<Integer> ranks = new ArrayList<>();
        for (int value = 14; value >= 2; value--) {
            for (int count = 0; count < rankCounts[value]; count++) {
                ranks.add(value);
            }
        }
        return ranks;
    }

    private static boolean isFlush(List<Card> cards) {
        // alle karten müssen dieselbe farbe haben
        Suit firstSuit = cards.get(0).suit();
        for (Card card : cards) {
            if (card.suit() != firstSuit) {
                return false;
            }
        }
        return true;
    }

    private static int findStraightHigh(int[] rankCounts) {
        List<Integer> ranksAscending = new ArrayList<>();
        for (int value = 2; value <= 14; value++) {
            if (rankCounts[value] > 0) {
                ranksAscending.add(value);
            }
        }
        return straightHigh(ranksAscending);
    }

    private static List<Integer> buildTieBreakers(int primaryRank, List<Integer> kickers) {
        List<Integer> tieBreakers = new ArrayList<>();
        tieBreakers.add(primaryRank);
        tieBreakers.addAll(kickers);
        return tieBreakers;
    }

    private static int straightHigh(List<Integer> ranks) {
        if (ranks.size() != 5) {
            return 0;
        }

        // beim wheel zählt das ass als 1
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
