package com.pokermc.config;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Persistent per-player ZC bank balance stored in config/pokermc_wallets.json.
 * Survives server restarts.  Each player's balance is identified by name.
 */
public class WalletStorage {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path WALLET_PATH = Path.of("config", "pokermc_wallets.json");
    private static WalletStorage instance;

    private Map<String, Integer> balances = new LinkedHashMap<>();

    // ── Singleton ─────────────────────────────────────────────────────────────

    public static WalletStorage get() {
        if (instance == null) instance = load();
        return instance;
    }

    // ── API ───────────────────────────────────────────────────────────────────

    public int getBalance(String player) {
        return balances.getOrDefault(player, 0);
    }

    public void addBalance(String player, int amount) {
        if (amount <= 0) return;
        balances.merge(player, amount, Integer::sum);
        save();
    }

    /**
     * Deduct {@code amount} from the player's balance.
     * @return true if successful, false if insufficient funds.
     */
    public boolean deductBalance(String player, int amount) {
        int cur = getBalance(player);
        if (cur < amount) return false;
        balances.put(player, cur - amount);
        save();
        return true;
    }

    public void setBalance(String player, int amount) {
        balances.put(player, Math.max(0, amount));
        save();
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private static WalletStorage load() {
        try {
            Files.createDirectories(WALLET_PATH.getParent());
            if (Files.exists(WALLET_PATH)) {
                try (Reader r = Files.newBufferedReader(WALLET_PATH)) {
                    Map<String, Integer> map = GSON.fromJson(r,
                            new TypeToken<LinkedHashMap<String, Integer>>() {}.getType());
                    WalletStorage ws = new WalletStorage();
                    if (map != null) ws.balances = map;
                    return ws;
                }
            }
        } catch (IOException e) {
            System.err.println("[PokerMC] Failed to load wallets: " + e.getMessage());
        }
        WalletStorage ws = new WalletStorage();
        ws.save();
        return ws;
    }

    public void save() {
        try {
            Files.createDirectories(WALLET_PATH.getParent());
            try (Writer w = Files.newBufferedWriter(WALLET_PATH)) {
                GSON.toJson(balances, w);
            }
        } catch (IOException e) {
            System.err.println("[PokerMC] Failed to save wallets: " + e.getMessage());
        }
    }
}
