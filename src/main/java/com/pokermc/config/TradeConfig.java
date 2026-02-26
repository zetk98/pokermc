package com.pokermc.config;

import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Configures item ↔ ZC exchange rates.
 * Edit  config/pokermc_trades.json  to add/remove items or change rates.
 *
 * buyRates:  itemId → ZC you receive when depositing 1 of that item
 * sellRates: itemId → ZC cost to receive 1 of that item
 */
public class TradeConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = Path.of("config", "pokermc_trades.json");
    private static TradeConfig instance;

    /** item-id → ZC received per item deposited */
    public Map<String, Integer> buyRates  = new LinkedHashMap<>();
    /** item-id → ZC cost to buy 1 item */
    public Map<String, Integer> sellRates = new LinkedHashMap<>();

    public TradeConfig() {
        buyRates.put("minecraft:iron_ingot",  5);
        buyRates.put("minecraft:gold_ingot",  20);
        buyRates.put("minecraft:emerald",     50);
        buyRates.put("minecraft:diamond",     100);

        sellRates.put("minecraft:iron_ingot", 5);
        sellRates.put("minecraft:gold_ingot", 20);
        sellRates.put("minecraft:emerald",    50);
        sellRates.put("minecraft:diamond",    100);
    }

    public static TradeConfig get() {
        if (instance == null) instance = load();
        return instance;
    }

    public static void reload() { instance = load(); }

    private static TradeConfig load() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            if (Files.exists(CONFIG_PATH)) {
                try (Reader r = Files.newBufferedReader(CONFIG_PATH)) {
                    TradeConfig cfg = GSON.fromJson(r, TradeConfig.class);
                    if (cfg != null) return cfg;
                }
            }
        } catch (IOException e) {
            System.err.println("[PokerMC] Failed to load trades config: " + e.getMessage());
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
            System.err.println("[PokerMC] Failed to save trades config: " + e.getMessage());
        }
    }
}
