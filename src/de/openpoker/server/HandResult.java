package de.openpoker.server;

import java.util.List;

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
