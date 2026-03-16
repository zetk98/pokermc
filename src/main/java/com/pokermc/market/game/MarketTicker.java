package com.pokermc.market.game;

import com.pokermc.common.config.CasinoCraftConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;

/**
 * Ticks market prices every 5 min. Runs on server tick.
 */
public class MarketTicker {

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(MarketTicker::tick);
    }

    private static void tick(MinecraftServer server) {
        ServerWorld overworld = server.getWorld(World.OVERWORLD);
        if (overworld == null) return;
        MarketPersistentState state = MarketPersistentState.get(server);
        if (state == null) return;
        state.initFromConfig(CasinoCraftConfig.get());
        state.tick(overworld.getTime(), server);
    }
}
