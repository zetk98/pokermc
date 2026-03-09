package com.pokermc.poker.game;

import com.pokermc.common.game.Card;

import java.util.*;

/**
 * Texas Hold'em hand evaluator.
 * Picks the best 5-card hand from up to 7 cards.
 */
public class HandEvaluator {

    public record HandResult(HandRank rank, int[] tiebreakers) implements Comparable<HandResult> {
        @Override
        public int compareTo(HandResult other) {
            int cmp = this.rank.compareTo(other.rank);
            if (cmp != 0) return cmp;
            for (int i = 0; i < Math.min(tiebreakers.length, other.tiebreakers.length); i++) {
                cmp = Integer.compare(tiebreakers[i], other.tiebreakers[i]);
                if (cmp != 0) return cmp;
            }
            return 0;
        }

        public String getDisplayName() {
            return rank.displayName;
        }
    }

    public static HandResult evaluate(List<Card> cards) {
        if (cards.size() < 5) throw new IllegalArgumentException("Need at least 5 cards, got " + cards.size());

        HandResult best = null;
        for (List<Card> combo : combinations(cards, 5)) {
            HandResult result = evaluateFive(combo);
            if (best == null || result.compareTo(best) > 0) {
                best = result;
            }
        }
        return best;
    }

    private static List<List<Card>> combinations(List<Card> cards, int k) {
        List<List<Card>> result = new ArrayList<>();
        combHelper(cards, k, 0, new ArrayList<>(), result);
        return result;
    }

    private static void combHelper(List<Card> cards, int k, int start, List<Card> cur, List<List<Card>> out) {
        if (cur.size() == k) {
            out.add(new ArrayList<>(cur));
            return;
        }
        for (int i = start; i < cards.size(); i++) {
            cur.add(cards.get(i));
            combHelper(cards, k, i + 1, cur, out);
            cur.remove(cur.size() - 1);
        }
    }

    public static HandResult evaluateFive(List<Card> cards) {
        // Build rank values sorted descending
        int[] rv = cards.stream().mapToInt(c -> c.rank().value).toArray();
        Arrays.sort(rv);
        reverseInPlace(rv);

        boolean isFlush = cards.stream().map(Card::suit).distinct().count() == 1;
        boolean isStraight = checkStraight(rv);
        boolean isWheel = rv[0] == 14 && rv[1] == 5 && rv[2] == 4 && rv[3] == 3 && rv[4] == 2;

        if (isFlush && (isStraight || isWheel)) {
            if (rv[0] == 14 && rv[1] == 13) return new HandResult(HandRank.ROYAL_FLUSH, rv);
            if (isWheel) return new HandResult(HandRank.STRAIGHT_FLUSH, new int[]{5, 4, 3, 2, 1});
            return new HandResult(HandRank.STRAIGHT_FLUSH, rv);
        }

        Map<Integer, Integer> freq = freqMap(rv);
        int[] quads = ranksByFreq(freq, 4);
        int[] trips = ranksByFreq(freq, 3);
        int[] pairs = ranksByFreq(freq, 2);
        int[] singles = ranksByFreq(freq, 1);

        if (quads.length > 0)
            return new HandResult(HandRank.FOUR_OF_A_KIND, concat(quads, singles));

        if (trips.length > 0 && pairs.length > 0)
            return new HandResult(HandRank.FULL_HOUSE, concat(trips, pairs));

        if (isFlush)
            return new HandResult(HandRank.FLUSH, rv);

        if (isStraight)
            return new HandResult(HandRank.STRAIGHT, rv);

        if (isWheel)
            return new HandResult(HandRank.STRAIGHT, new int[]{5, 4, 3, 2, 1});

        if (trips.length > 0)
            return new HandResult(HandRank.THREE_OF_A_KIND, concat(trips, singles));

        if (pairs.length >= 2) {
            // Top two pairs + kicker
            int[] topPairs = Arrays.copyOf(pairs, 2); // already desc from ranksByFreq
            int[] kickers = Arrays.copyOf(singles, 1);
            return new HandResult(HandRank.TWO_PAIR, concat(topPairs, kickers));
        }

        if (pairs.length == 1)
            return new HandResult(HandRank.ONE_PAIR, concat(pairs, singles));

        return new HandResult(HandRank.HIGH_CARD, rv);
    }

    private static boolean checkStraight(int[] sortedDesc) {
        if (sortedDesc[0] - sortedDesc[4] == 4) {
            // All 5 values different?
            Set<Integer> set = new HashSet<>();
            for (int v : sortedDesc) set.add(v);
            return set.size() == 5;
        }
        return false;
    }

    private static Map<Integer, Integer> freqMap(int[] ranks) {
        Map<Integer, Integer> map = new HashMap<>();
        for (int r : ranks) map.merge(r, 1, Integer::sum);
        return map;
    }

    /** Returns ranks with exactly targetFreq occurrences, sorted descending. */
    private static int[] ranksByFreq(Map<Integer, Integer> freq, int targetFreq) {
        List<Integer> result = new ArrayList<>();
        for (Map.Entry<Integer, Integer> e : freq.entrySet()) {
            if (e.getValue() == targetFreq) result.add(e.getKey());
        }
        result.sort(Collections.reverseOrder());
        return result.stream().mapToInt(Integer::intValue).toArray();
    }

    private static int[] concat(int[] a, int[] b) {
        int[] r = new int[a.length + b.length];
        System.arraycopy(a, 0, r, 0, a.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }

    private static void reverseInPlace(int[] arr) {
        for (int i = 0, j = arr.length - 1; i < j; i++, j--) {
            int t = arr[i]; arr[i] = arr[j]; arr[j] = t;
        }
    }
}
