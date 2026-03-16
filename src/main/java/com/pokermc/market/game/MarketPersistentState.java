package com.pokermc.market.game;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.pokermc.PokerMod;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;
import net.minecraft.world.World;

import java.util.*;

/**
 * Persistent market state: current prices + 7-level history (35 min).
 * Prices refresh every 5 min (6000 ticks).
 */
public class MarketPersistentState extends PersistentState {

    private static final int TICKS_PER_UPDATE = 6000;  // 5 min
    private static final int HISTORY_SIZE = 7;
    private static final double MIN_MULT = 0.5;
    private static final double MAX_MULT = 2.0;
    private static final double SENSITIVITY = 0.02;
    private static final double DECAY = 0.1;
    private static final double NOISE = 0.05;

    /** itemId -> current sell price (ZC per item when player sells to market) */
    private final Map<String, Integer> sellPrices = new LinkedHashMap<>();
    /** itemId -> current buy price (ZC per item when player buys from market) */
    private final Map<String, Integer> buyPrices = new LinkedHashMap<>();
    /** itemId -> price history [0]=oldest (35min ago), [6]=newest (current) */
    private final Map<String, List<Integer>> priceHistory = new LinkedHashMap<>();
    /** itemId -> net sells - buys (positive = more selling) */
    private final Map<String, Integer> tradeImbalance = new LinkedHashMap<>();
    private long lastUpdateTick = 0;

    public MarketPersistentState() {}

    private MarketPersistentState(Map<String, Integer> sp, Map<String, Integer> bp,
                                  Map<String, List<Integer>> ph, Map<String, Integer> ti, long lt) {
        if (sp != null) sellPrices.putAll(sp);
        if (bp != null) buyPrices.putAll(bp);
        if (ph != null) priceHistory.putAll(ph);
        if (ti != null) tradeImbalance.putAll(ti);
        lastUpdateTick = lt;
    }

    private static MarketPersistentState fromCodec(Map<String, Integer> sp, Map<String, Integer> bp,
                                                   Map<String, List<Integer>> ph, Map<String, Integer> ti, long lt) {
        return new MarketPersistentState(sp != null ? sp : Map.of(), bp != null ? bp : Map.of(),
                ph != null ? ph : Map.of(), ti != null ? ti : Map.of(), lt);
    }

    public int getSellPrice(String itemId) {
        return Math.max(1, sellPrices.getOrDefault(itemId, 1));
    }

    public int getBuyPrice(String itemId) {
        return Math.max(1, buyPrices.getOrDefault(itemId, 1));
    }

    public List<Integer> getPriceHistory(String itemId) {
        return priceHistory.getOrDefault(itemId, Collections.emptyList());
    }

    public long getLastUpdateTick() {
        return lastUpdateTick;
    }

    public void recordSell(String itemId, int amount) {
        tradeImbalance.merge(itemId, amount, Integer::sum);
    }

    public void recordBuy(String itemId, int amount) {
        tradeImbalance.merge(itemId, -amount, Integer::sum);
    }

    /** Tick market: update prices every 5 min. */
    public void tick(long worldTime, MinecraftServer server) {
        if (worldTime - lastUpdateTick < TICKS_PER_UPDATE) return;
        lastUpdateTick = worldTime;

        var cfg = com.pokermc.common.config.CasinoCraftConfig.get();
        List<String> itemIds = MarketConfig.getMarketItemIds(cfg);

        for (String id : itemIds) {
            int base = MarketConfig.getBasePrice(cfg, id);
            if (base <= 0) continue;

            int current = sellPrices.getOrDefault(id, base);
            int imbalance = tradeImbalance.getOrDefault(id, 0);

            // Supply/demand: more sells -> price drops; more buys -> price rises
            double mult = 1.0 - SENSITIVITY * imbalance;
            mult = Math.max(0.7, Math.min(1.3, mult));

            // Mean reversion toward base
            double towardBase = (base - current) * DECAY * 0.01;

            // Random noise
            double noise = (server.getOverworld().getRandom().nextDouble() - 0.5) * 2 * NOISE;

            double newPrice = current * mult + towardBase + noise * base;
            newPrice = Math.max(base * MIN_MULT, Math.min(base * MAX_MULT, newPrice));
            int rounded = Math.max(1, (int) Math.round(newPrice));

            sellPrices.put(id, rounded);
            buyPrices.put(id, Math.max(rounded, rounded + 1));  // buy costs 1 more or same
            tradeImbalance.put(id, (int) (tradeImbalance.getOrDefault(id, 0) * 0.5));  // decay

            // Update history (ensure mutable list - codec may return immutable)
            List<Integer> hist = new ArrayList<>(priceHistory.getOrDefault(id, List.of()));
            hist.add(rounded);
            while (hist.size() > HISTORY_SIZE) hist.remove(0);
            priceHistory.put(id, hist);
        }
        markDirty();
    }

    public void initFromConfig(com.pokermc.common.config.CasinoCraftConfig cfg) {
        for (String id : MarketConfig.getMarketItemIds(cfg)) {
            int base = MarketConfig.getBasePrice(cfg, id);
            if (base <= 0) continue;
            if (!sellPrices.containsKey(id)) {
                sellPrices.put(id, base);
                buyPrices.put(id, base + 1);
                List<Integer> hist = new ArrayList<>();
                for (int i = 0; i < HISTORY_SIZE; i++) hist.add(base);
                priceHistory.put(id, hist);
            }
        }
        if (!sellPrices.isEmpty()) markDirty();
    }

    private static final Codec<MarketPersistentState> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.unboundedMap(Codec.STRING, Codec.INT).fieldOf("sellPrices").forGetter(s -> s.sellPrices),
            Codec.unboundedMap(Codec.STRING, Codec.INT).fieldOf("buyPrices").forGetter(s -> s.buyPrices),
            Codec.unboundedMap(Codec.STRING, Codec.INT.listOf()).fieldOf("priceHistory").forGetter(s -> s.priceHistory),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("tradeImbalance", Map.of()).forGetter(s -> s.tradeImbalance),
            Codec.LONG.fieldOf("lastUpdateTick").forGetter(s -> s.lastUpdateTick)
    ).apply(inst, MarketPersistentState::fromCodec));

    private static final PersistentStateType<MarketPersistentState> TYPE =
            new PersistentStateType<>(PokerMod.MOD_ID + "_market", MarketPersistentState::new, CODEC, DataFixTypes.LEVEL);

    public static MarketPersistentState get(MinecraftServer server) {
        if (server == null) return null;
        ServerWorld ow = server.getWorld(World.OVERWORLD);
        if (ow == null) return null;
        return ow.getPersistentStateManager().getOrCreate(TYPE);
    }
}
