package com.pokermc.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.io.*;
import java.nio.file.*;

public class PokerConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = Path.of("config", "pokermc.json");
    private static PokerConfig instance;

    // Config fields
    public String betItemId              = "minecraft:diamond";
    public int    startingChips          = 100;
    public int    chipsPerItem           = 10;
    public int    smallBlindAmount       = 1;
    public int    bigBlindAmount         = 2;
    public int    minRaiseAmount         = 2;
    public int    maxPlayers             = 8;
    public int    minPlayers             = 2;
    /** How long (seconds) join/leave notifications stay on screen. */
    public int    notificationDurationSeconds = 5;

    public static PokerConfig get() {
        if (instance == null) instance = load();
        return instance;
    }

    public Item getBetItem() {
        Identifier id = Identifier.tryParse(betItemId);
        if (id != null && Registries.ITEM.containsId(id)) {
            return Registries.ITEM.get(id);
        }
        return Items.DIAMOND;
    }

    public String getBetItemName() {
        Item item = getBetItem();
        return Registries.ITEM.getId(item).getPath();
    }

    private static PokerConfig load() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            if (Files.exists(CONFIG_PATH)) {
                try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
                    PokerConfig cfg = GSON.fromJson(reader, PokerConfig.class);
                    if (cfg != null) return cfg;
                }
            }
        } catch (IOException e) {
            System.err.println("[PokerMC] Failed to load config: " + e.getMessage());
        }
        PokerConfig def = new PokerConfig();
        def.save();
        return def;
    }

    public void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            System.err.println("[PokerMC] Failed to save config: " + e.getMessage());
        }
    }
}
