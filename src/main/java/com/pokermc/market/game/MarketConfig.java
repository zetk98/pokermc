package com.pokermc.market.game;

import com.pokermc.common.config.CasinoCraftConfig;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Market item IDs and base prices. Includes copper. */
public final class MarketConfig {

    private static final List<String> DEFAULT_IDS = List.of(
            "minecraft:iron_ingot",
            "minecraft:copper_ingot",
            "minecraft:gold_ingot",
            "minecraft:emerald",
            "minecraft:diamond"
    );

    private static final Map<String, Integer> DEFAULT_BASES = new LinkedHashMap<>();
    static {
        DEFAULT_BASES.put("minecraft:iron_ingot", 2);
        DEFAULT_BASES.put("minecraft:copper_ingot", 2);
        DEFAULT_BASES.put("minecraft:gold_ingot", 3);
        DEFAULT_BASES.put("minecraft:emerald", 7);
        DEFAULT_BASES.put("minecraft:diamond", 13);
    }

    public static List<String> getMarketItemIds(CasinoCraftConfig cfg) {
        if (cfg.marketBasePrices != null && !cfg.marketBasePrices.isEmpty()) {
            return List.copyOf(cfg.marketBasePrices.keySet());
        }
        return DEFAULT_IDS;
    }

    public static int getBasePrice(CasinoCraftConfig cfg, String itemId) {
        if (cfg.marketBasePrices != null && cfg.marketBasePrices.containsKey(itemId)) {
            return Math.max(1, cfg.marketBasePrices.get(itemId));
        }
        return Math.max(1, DEFAULT_BASES.getOrDefault(itemId, 1));
    }
}
