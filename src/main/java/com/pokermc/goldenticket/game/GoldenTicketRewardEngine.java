package com.pokermc.goldenticket.game;

import net.minecraft.util.math.random.Random;

/**
 * Weighted reward distribution for Golden Ticket.
 * Lower rewards have higher probability; higher rewards are much rarer.
 * Uses weight(v) = (max - v + 1)^6 — very strong house edge, high rewards extremely rare.
 */
public final class GoldenTicketRewardEngine {

    private static final int WEIGHT_EXPONENT = 6;

    /**
     * Roll a reward in [rewardMin, rewardMax] with decreasing probability as value increases.
     */
    public static int rollReward(GoldenTicketTierConfig cfg, Random rng) {
        int min = cfg.rewardMin;
        int max = cfg.rewardMax;
        if (min >= max) return min;

        // Precompute weights: weight(v) = (max - v + 1)^exp — high exponent = rare big wins
        long totalWeight = 0;
        int range = max - min + 1;
        long[] weights = new long[range];
        for (int i = 0; i < range; i++) {
            int v = min + i;
            long base = max - v + 1;
            long w = 1;
            for (int e = 0; e < WEIGHT_EXPONENT; e++) w *= base;
            weights[i] = w;
            totalWeight += w;
        }

        if (totalWeight <= 0) return min;

        long roll = (long) (rng.nextDouble() * totalWeight);
        for (int i = 0; i < range; i++) {
            roll -= weights[i];
            if (roll < 0) return min + i;
        }
        return max;
    }
}
