package com.pokermc.market.blockentity;

import com.pokermc.PokerMod;
import com.pokermc.common.config.CasinoCraftConfig;
import com.pokermc.market.game.MarketPersistentState;
import com.pokermc.market.network.MarketNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class MarketBlockEntity extends BlockEntity {

    public MarketBlockEntity(BlockPos pos, BlockState state) {
        super(PokerMod.MARKET_BLOCK_ENTITY, pos, state);
    }

    public void openFor(ServerPlayerEntity player) {
        try {
            var server = player.getEntityWorld() instanceof net.minecraft.server.world.ServerWorld sw ? sw.getServer() : null;
            if (server == null) return;
            MarketPersistentState state = MarketPersistentState.get(server);
            if (state == null) return;
            state.initFromConfig(CasinoCraftConfig.get());
            var overworld = server.getWorld(World.OVERWORLD);
            long worldTime = overworld != null ? overworld.getTime() : 0;
            String json = MarketNetworking.serializeState(player, state, worldTime);
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(
                    player, new MarketNetworking.OpenMarketPayload(pos, json));
        } catch (Exception e) {
            System.err.println("[Market] openFor failed: " + e.getMessage());
            player.sendMessage(net.minecraft.text.Text.literal("[Market] Error: " + e.getMessage()), false);
        }
    }

    public static void tick(World world, BlockPos pos, BlockState state, MarketBlockEntity be) {
        // Market state is ticked via ServerTickEvents in MarketTicker
    }
}
