package com.pokermc.xoso.game;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;

/**
 * Stores lottery draw results per day. Uses overworld's persistent state
 * so all blocks on the server share the same results and data survives restarts.
 */
public class LotteryDrawStorage {

    public record DrawResult(String special, String first, String second, String third) {}

    public static void store(MinecraftServer server, long day, DrawResult result) {
        var state = LotteryDrawPersistentState.get(server);
        if (state != null) state.put(day, result);
    }

    /** Convenience: store from ServerWorld (uses its server). */
    public static void store(ServerWorld world, long day, DrawResult result) {
        var server = world.getServer();
        if (server != null) store(server, day, result);
    }

    public static DrawResult get(MinecraftServer server, long day) {
        var state = LotteryDrawPersistentState.get(server);
        return state != null ? state.get(day) : null;
    }

    /** Convenience: get from ServerWorld (uses its server). */
    public static DrawResult get(ServerWorld world, long day) {
        var server = world.getServer();
        return server != null ? get(server, day) : null;
    }
}
