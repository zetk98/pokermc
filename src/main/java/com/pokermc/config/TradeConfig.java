package com.pokermc.config;

import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Configures item ↔ ZC exchange rates.
 * Edit  config/casinocraft_trades.json  to add/remove items or change rates.
 *
 * buyRates:  itemId → ZC you receive when depositing 1 of that item
 * sellRates: itemId → ZC cost per withdraw unit
 * sellGives: itemId → items per unit (1 ZC→2 iron, 2 ZC→4 gold, etc.)
 */
public class TradeConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = Path.of("config", "casinocraft_trades.json");
    private static TradeConfig instance;

    /** item-id → ZC received per item deposited */
    public Map<String, Integer> buyRates  = new LinkedHashMap<>();
    /** item-id → ZC cost per withdraw unit */
    public Map<String, Integer> sellRates = new LinkedHashMap<>();
    /** item-id → items received per withdraw unit */
    public Map<String, Integer> sellGives = new LinkedHashMap<>();

    public TradeConfig() {
        // BUY: deposit items → receive ZC
        buyRates.put("minecraft:iron_ingot",  1);   // 1 iron = 1 zc
        buyRates.put("minecraft:gold_ingot",  2);   // 1 gold = 2 zc
        buyRates.put("minecraft:emerald",     5);   // 1 emerald = 5 zc
        buyRates.put("minecraft:diamond",     10);  // 1 diamond = 10 zc
        buyRates.put("casinocraft:zcoin",         1);   // 1 ZCoin item = 1 ZC

        // SELL: spend ZC → receive items (2zc=1 iron, 3zc=1 gold, 7zc=1 emerald, 13zc=1 diamond)
        sellRates.put("minecraft:iron_ingot", 2);   // 2 zc = 1 iron
        sellRates.put("minecraft:gold_ingot", 3);   // 3 zc = 1 gold
        sellRates.put("minecraft:emerald",    7);   // 7 zc = 1 emerald
        sellRates.put("minecraft:diamond",    13);  // 13 zc = 1 diamond
        sellRates.put("casinocraft:zcoin",        1);   // 1 zc = 1 ZCoin item

        sellGives.put("minecraft:iron_ingot", 1);
        sellGives.put("minecraft:gold_ingot", 1);
        sellGives.put("minecraft:emerald",    1);
        sellGives.put("minecraft:diamond",    1);
        sellGives.put("casinocraft:zcoin",        1);
    }

    public static TradeConfig get() {
        if (instance == null) instance = load();
        return instance;
    }

    public static void reload() { instance = load(); }

    private static void ensureSellGives(TradeConfig cfg) {
        if (cfg.sellGives == null) cfg.sellGives = new LinkedHashMap<>();
        for (String k : cfg.sellRates.keySet())
            cfg.sellGives.putIfAbsent(k, cfg.sellRates.get(k));
    }

    private static TradeConfig load() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            if (Files.exists(CONFIG_PATH)) {
                try (Reader r = Files.newBufferedReader(CONFIG_PATH)) {
                    TradeConfig cfg = GSON.fromJson(r, TradeConfig.class);
                    if (cfg != null) {
                        ensureSellGives(cfg);
                        return cfg;
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("[CasinoCraft] Failed to load trades config: " + e.getMessage());
        }
        TradeConfig def = new TradeConfig();
        def.save();
        return def;
    }

    public void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer w = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(this, w);
            }
        } catch (IOException e) {
            System.err.println("[CasinoCraft] Failed to save trades config: " + e.getMessage());
        }
    }
}
