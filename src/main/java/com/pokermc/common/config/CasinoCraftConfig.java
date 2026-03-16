package com.pokermc.common.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
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

    /** Version config - khi khác với mod version thì xóa config cũ và tạo mới. */
    public String configVersion = "";

    private static String getModVersion() {
        return FabricLoader.getInstance().getModContainer("casinocraft")
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("1.0.0");
    }
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

    // ── XOSO (Xổ số) ───────────────────────────────────────────────────────────
    public int xosoTicketPrice = 5;
    public int xosoPrizeDacBiet = 1000;  // Giải đặc biệt
    public int xosoPrizeNhat = 500;       // Giải nhất
    public int xosoPrizeNhi = 200;       // Giải nhì
    public int xosoPrizeBa = 50;         // Giải ba

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
            String modVersion = getModVersion();

            if (Files.exists(CONFIG_PATH)) {
                try (Reader r = Files.newBufferedReader(CONFIG_PATH)) {
                    CasinoCraftConfig cfg = GSON.fromJson(r, CasinoCraftConfig.class);
                    if (cfg != null) {
                        String fileVersion = cfg.configVersion != null ? cfg.configVersion : "";
                        if (!fileVersion.equals(modVersion)) {
                            // Mod đã cập nhật → xóa config cũ, tạo mới để tránh xung đột
                            Files.deleteIfExists(CONFIG_PATH);
                            System.out.println("[CasinoCraft] Config cũ (v" + fileVersion + ") đã xóa, tạo config mới (v" + modVersion + ").");
                            CasinoCraftConfig def = new CasinoCraftConfig();
                            def.configVersion = modVersion;
                            def.save();
                            return def;
                        }
                        ensureMaps(cfg);
                        ensureDefaultTrades(cfg);
                        ensureSellGives(cfg);
                        cfg.configVersion = modVersion; // Đảm bảo version luôn cập nhật khi save
                        return cfg;
                    }
                }
            }
            CasinoCraftConfig fromLegacy = tryLoadLegacy();
            if (fromLegacy != null) {
                fromLegacy.configVersion = modVersion;
                fromLegacy.save();
                return fromLegacy;
            }
        } catch (IOException e) {
            System.err.println("[CasinoCraft] Failed to load config: " + e.getMessage());
        }
        CasinoCraftConfig def = new CasinoCraftConfig();
        def.configVersion = getModVersion();
        def.save();
        return def;
    }

    private static void ensureMaps(CasinoCraftConfig cfg) {
        if (cfg.buyRates == null) cfg.buyRates = new LinkedHashMap<>();
        if (cfg.sellRates == null) cfg.sellRates = new LinkedHashMap<>();
        if (cfg.sellGives == null) cfg.sellGives = new LinkedHashMap<>();
    }

    /** Ensure default trade items (iron, gold, emerald, diamond) when config has none. */
    private static void ensureDefaultTrades(CasinoCraftConfig cfg) {
        if (!cfg.buyRates.isEmpty()) return;
        cfg.buyRates.put("minecraft:iron_ingot", 2);
        cfg.buyRates.put("minecraft:gold_ingot", 3);
        cfg.buyRates.put("minecraft:emerald", 7);
        cfg.buyRates.put("minecraft:diamond", 13);
        cfg.sellRates.put("minecraft:iron_ingot", 2);
        cfg.sellRates.put("minecraft:gold_ingot", 3);
        cfg.sellRates.put("minecraft:emerald", 7);
        cfg.sellRates.put("minecraft:diamond", 13);
        cfg.sellGives.put("minecraft:iron_ingot", 1);
        cfg.sellGives.put("minecraft:gold_ingot", 1);
        cfg.sellGives.put("minecraft:emerald", 1);
        cfg.sellGives.put("minecraft:diamond", 1);
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
        ensureMaps(cfg);
        ensureDefaultTrades(cfg);
        ensureSellGives(cfg);
        cfg.save();
        return cfg;
    }

    private static void mergeConfig(CasinoCraftConfig target, CasinoCraftConfig source) {
        if (source.configVersion != null) target.configVersion = source.configVersion;
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
        target.xosoTicketPrice = source.xosoTicketPrice;
        target.xosoPrizeDacBiet = source.xosoPrizeDacBiet;
        target.xosoPrizeNhat = source.xosoPrizeNhat;
        target.xosoPrizeNhi = source.xosoPrizeNhi;
        target.xosoPrizeBa = source.xosoPrizeBa;
        if (source.buyRates != null) target.buyRates = source.buyRates;
        if (source.sellRates != null) target.sellRates = source.sellRates;
        if (source.sellGives != null) target.sellGives = source.sellGives;
    }

    public void save() {
        try {
            configVersion = getModVersion(); // Luôn ghi version hiện tại khi save
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer w = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(this, w);
            }
        } catch (IOException e) {
            System.err.println("[CasinoCraft] Failed to save config: " + e.getMessage());
        }
    }
}
