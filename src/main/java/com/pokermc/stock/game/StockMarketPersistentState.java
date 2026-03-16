package com.pokermc.stock.game;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.pokermc.PokerMod;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Persistent storage for stock market data.
 * Saves prices and last update tick to disk.
 */
public class StockMarketPersistentState extends PersistentState {

    /** Current price for each stock (ticker -> price) */
    private final Map<String, Integer> prices = new HashMap<>();

    /** Price change percentage for each stock (ticker -> change%) */
    private final Map<String, Integer> changes = new HashMap<>();

    /** Last update tick */
    private long lastUpdateTick = 0;

    public StockMarketPersistentState() {
        super();
        initializeDefaultPrices();
    }

    private StockMarketPersistentState(Map<String, Integer> prices, Map<String, Integer> changes, long lastUpdateTick) {
        this();
        if (prices != null) this.prices.putAll(prices);
        if (changes != null) this.changes.putAll(changes);
        this.lastUpdateTick = lastUpdateTick;
    }

    private void initializeDefaultPrices() {
        for (StockType type : StockType.values()) {
            prices.put(type.getTicker(), type.getBasePrice());
            changes.put(type.getTicker(), 0);
        }
    }

    public int getPrice(String ticker) {
        return prices.getOrDefault(ticker, StockType.byTicker(ticker) != null ? StockType.byTicker(ticker).getBasePrice() : 100);
    }

    public int getChange(String ticker) {
        return changes.getOrDefault(ticker, 0);
    }

    public void setPrice(String ticker, int price, int change) {
        prices.put(ticker, price);
        changes.put(ticker, change);
        markDirty();
    }

    public long getLastUpdateTick() {
        return lastUpdateTick;
    }

    public void setLastUpdateTick(long tick) {
        this.lastUpdateTick = tick;
        markDirty();
    }

    public Map<String, Integer> getAllPrices() {
        return new HashMap<>(prices);
    }

    public Map<String, Integer> getAllChanges() {
        return new HashMap<>(changes);
    }

    // ===== Codec-based Serialization =====

    private static final Codec<StockMarketPersistentState> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("prices", new HashMap<>()).forGetter(s -> s.prices),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("changes", new HashMap<>()).forGetter(s -> s.changes),
            Codec.LONG.optionalFieldOf("lastUpdateTick", 0L).forGetter(s -> s.lastUpdateTick)
    ).apply(inst, StockMarketPersistentState::new));

    private static final PersistentStateType<StockMarketPersistentState> TYPE =
            new PersistentStateType<>(
                    PokerMod.MOD_ID + "_stock_market",
                    StockMarketPersistentState::new,
                    CODEC,
                    DataFixTypes.LEVEL
            );

    public static StockMarketPersistentState get(MinecraftServer server) {
        if (server == null) return null;
        ServerWorld overworld = server.getWorld(World.OVERWORLD);
        if (overworld == null) return null;
        return overworld.getPersistentStateManager().getOrCreate(TYPE);
    }
}
