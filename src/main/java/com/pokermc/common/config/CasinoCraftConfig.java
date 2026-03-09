package com.pokermc.common.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Toàn bộ config mod - 1 file duy nhất: config/casinocraft.json
 */
public class CasinoCraftConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = Path.of("config", "casinocraft.json");
    private static final Path LEGACY_TRADES = Path.of("config", "casinocraft_trades.json");
    private static CasinoCraftConfig instance;

    // ── Config chung ───────────────────────────────────────────────────────────
    public String betItemId = "minecraft:diamond";
    public int startingChips = 100;
    public int chipsPerItem = 10;
    public int smallBlindAmount = 1;
    public int bigBlindAmount = 2;
    public int minRaiseAmount = 2;
    public int maxPlayers = 8;
    public int minPlayers = 2;
    public int notificationDurationSeconds = 5;
    public int turnTimeSeconds = 30;
    public int zcoinBagMaxCapacity = 99999;
    /** Minimum ZC required to create or join a poker/blackjack table. */
    public int minZcToJoin = 10;
    /** Blackjack max bet per hand (from config, no room selection). */
    public int blackjackMaxBet = 100;

    // ── Trades (item ↔ ZC) ─────────────────────────────────────────────────────
    public Map<String, Integer> buyRates = new LinkedHashMap<>();
    public Map<String, Integer> sellRates = new LinkedHashMap<>();
    public Map<String, Integer> sellGives = new LinkedHashMap<>();

    public CasinoCraftConfig() {
        buyRates.put("minecraft:iron_ingot", 2);
        buyRates.put("minecraft:gold_ingot", 3);
        buyRates.put("minecraft:emerald", 7);
        buyRates.put("minecraft:diamond", 13);
        sellRates.put("minecraft:iron_ingot", 2);
        sellRates.put("minecraft:gold_ingot", 3);
        sellRates.put("minecraft:emerald", 7);
        sellRates.put("minecraft:diamond", 13);
        sellGives.put("minecraft:iron_ingot", 1);
        sellGives.put("minecraft:gold_ingot", 1);
        sellGives.put("minecraft:emerald", 1);
        sellGives.put("minecraft:diamond", 1);
    }

    public static CasinoCraftConfig get() {
        if (instance == null) instance = load();
        return instance;
    }

    public static void reload() {
        instance = load();
    }

    public Item getBetItem() {
        Identifier id = Identifier.tryParse(betItemId);
        if (id != null && Registries.ITEM.containsId(id)) {
            return Registries.ITEM.get(id);
        }
        return Items.DIAMOND;
    }

    public String getBetItemName() {
        return Registries.ITEM.getId(getBetItem()).getPath();
    }

    private static void ensureSellGives(CasinoCraftConfig cfg) {
        if (cfg.sellGives == null) cfg.sellGives = new LinkedHashMap<>();
        for (String k : cfg.sellRates.keySet())
            cfg.sellGives.putIfAbsent(k, cfg.sellRates.get(k));
    }

    private static CasinoCraftConfig load() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            if (Files.exists(CONFIG_PATH)) {
                try (Reader r = Files.newBufferedReader(CONFIG_PATH)) {
                    CasinoCraftConfig cfg = GSON.fromJson(r, CasinoCraftConfig.class);
                    if (cfg != null) {
                        ensureSellGives(cfg);
                        ensureMaps(cfg);
                        return cfg;
                    }
                }
            }
            CasinoCraftConfig fromLegacy = tryLoadLegacy();
            if (fromLegacy != null) return fromLegacy;
        } catch (IOException e) {
            System.err.println("[CasinoCraft] Failed to load config: " + e.getMessage());
        }
        CasinoCraftConfig def = new CasinoCraftConfig();
        def.save();
        return def;
    }

    private static void ensureMaps(CasinoCraftConfig cfg) {
        if (cfg.buyRates == null) cfg.buyRates = new LinkedHashMap<>();
        if (cfg.sellRates == null) cfg.sellRates = new LinkedHashMap<>();
        if (cfg.sellGives == null) cfg.sellGives = new LinkedHashMap<>();
    }

    private static final Path LEGACY_CONFIG = Path.of("config", "casinocraft", "config.json");
    private static final Path LEGACY_TRADES_FILE = Path.of("config", "casinocraft", "trades.json");

    private static CasinoCraftConfig tryLoadLegacy() throws IOException {
        CasinoCraftConfig cfg = new CasinoCraftConfig();
        if (Files.exists(LEGACY_CONFIG)) {
            try (Reader r = Files.newBufferedReader(LEGACY_CONFIG)) {
                var legacy = GSON.fromJson(r, CasinoCraftConfig.class);
                if (legacy != null) mergeConfig(cfg, legacy);
            }
            Files.deleteIfExists(LEGACY_CONFIG);
        }
        if (Files.exists(LEGACY_TRADES)) {
            try (Reader r = Files.newBufferedReader(LEGACY_TRADES)) {
                var legacy = GSON.fromJson(r, CasinoCraftConfig.class);
                if (legacy != null && legacy.buyRates != null) {
                    cfg.buyRates = legacy.buyRates;
                    if (legacy.sellRates != null) cfg.sellRates = legacy.sellRates;
                    if (legacy.sellGives != null) cfg.sellGives = legacy.sellGives;
                }
            }
            Files.deleteIfExists(LEGACY_TRADES);
        }
        if (Files.exists(LEGACY_TRADES_FILE)) {
            try (Reader r = Files.newBufferedReader(LEGACY_TRADES_FILE)) {
                var legacy = GSON.fromJson(r, CasinoCraftConfig.class);
                if (legacy != null && legacy.buyRates != null) {
                    cfg.buyRates = legacy.buyRates;
                    if (legacy.sellRates != null) cfg.sellRates = legacy.sellRates;
                    if (legacy.sellGives != null) cfg.sellGives = legacy.sellGives;
                }
            }
            Files.deleteIfExists(LEGACY_TRADES_FILE);
        }
        ensureSellGives(cfg);
        cfg.save();
        return cfg;
    }

    private static void mergeConfig(CasinoCraftConfig target, CasinoCraftConfig source) {
        if (source.betItemId != null) target.betItemId = source.betItemId;
        target.startingChips = source.startingChips;
        target.chipsPerItem = source.chipsPerItem;
        target.smallBlindAmount = source.smallBlindAmount;
        target.bigBlindAmount = source.bigBlindAmount;
        target.minRaiseAmount = source.minRaiseAmount;
        target.maxPlayers = source.maxPlayers;
        target.minPlayers = source.minPlayers;
        target.notificationDurationSeconds = source.notificationDurationSeconds;
        target.turnTimeSeconds = source.turnTimeSeconds;
        target.zcoinBagMaxCapacity = source.zcoinBagMaxCapacity;
        target.minZcToJoin = source.minZcToJoin;
        target.blackjackMaxBet = source.blackjackMaxBet;
        if (source.buyRates != null) target.buyRates = source.buyRates;
        if (source.sellRates != null) target.sellRates = source.sellRates;
        if (source.sellGives != null) target.sellGives = source.sellGives;
    }

    public void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer w = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(this, w);
            }
        } catch (IOException e) {
            System.err.println("[CasinoCraft] Failed to save config: " + e.getMessage());
        }
    }
}
