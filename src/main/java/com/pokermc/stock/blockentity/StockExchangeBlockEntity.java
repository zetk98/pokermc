package com.pokermc.stock.blockentity;

import com.pokermc.PokerMod;
import com.pokermc.stock.game.StockMarketGame;
import com.pokermc.stock.network.StockNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Block entity for the Stock Exchange.
 * Handles price updates and player interactions.
 */
public class StockExchangeBlockEntity extends BlockEntity {

    private final StockMarketGame game = new StockMarketGame();
    private final Set<ServerPlayerEntity> viewers = new java.util.LinkedHashSet<>();

    public StockExchangeBlockEntity(BlockPos pos, BlockState state) {
        super(PokerMod.STOCK_EXCHANGE_BLOCK_ENTITY, pos, state);
    }

    public void addViewer(ServerPlayerEntity player) { viewers.add(player); }
    public void removeViewer(ServerPlayerEntity player) { viewers.remove(player); }

    public java.util.List<ServerPlayerEntity> getViewers() {
        viewers.removeIf(p -> !p.isAlive() || p.isDisconnected());
        return new java.util.ArrayList<>(viewers);
    }

    public void openFor(ServerPlayerEntity player) {
        try {
            viewers.add(player);
            String json = StockNetworking.serializeState(game, player,
                    getWorld() != null ? getWorld().getTime() : 0);
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(
                    player, new StockNetworking.OpenStockExchangePayload(pos, json));
        } catch (Exception e) {
            System.err.println("[CasinoCraft] Stock Exchange openFor failed: " + e.getMessage());
            player.sendMessage(Text.literal("§c[Stock Exchange] Error opening: " + e.getMessage()), false);
        }
    }

    public static void tick(World world, BlockPos pos, BlockState state, StockExchangeBlockEntity be) {
        if (world.isClient()) return;
        try {
            be.viewers.removeIf(p -> !p.isAlive() || p.isDisconnected());

            if (!(world instanceof ServerWorld sw)) return;

            net.minecraft.server.MinecraftServer server = sw.getServer();
            if (be.game.tick(sw.getTime(), server, sw)) {
                be.markDirty();
                StockNetworking.broadcastState(be);
            }
        } catch (Exception e) {
            System.err.println("[CasinoCraft] Stock Exchange tick error: " + e.getMessage());
        }
    }

    public StockMarketGame getGame() { return game; }

    // No custom persistence - game state saved in StockMarketPersistentState

    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
        return createNbt(registries);
    }

    @Nullable
    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }
}
