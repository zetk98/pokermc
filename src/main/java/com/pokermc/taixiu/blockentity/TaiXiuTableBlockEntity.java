package com.pokermc.taixiu.blockentity;

import com.pokermc.PokerMod;
import com.pokermc.taixiu.game.TaiXiuGameManager;
import com.pokermc.taixiu.network.TaiXiuNetworking;
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
 * Block entity for Tai Xiu (Sic Bo) table.
 * Uses the global TaiXiuGameManager - all blocks share the same game state.
 * Game runs continuously via ServerTickEvents, independent of viewers.
 * Sends updates to viewers every tick for smooth realtime display.
 */
public class TaiXiuTableBlockEntity extends BlockEntity {

    private final Set<ServerPlayerEntity> viewers = new java.util.LinkedHashSet<>();

    public TaiXiuTableBlockEntity(BlockPos pos, BlockState state) {
        super(PokerMod.TAIXIU_TABLE_BLOCK_ENTITY, pos, state);
    }

    public void addViewer(ServerPlayerEntity player) { viewers.add(player); }
    public void removeViewer(ServerPlayerEntity player) { viewers.remove(player); }

    public java.util.List<ServerPlayerEntity> getViewers() {
        viewers.removeIf(p -> !p.isAlive() || p.isDisconnected());
        return new java.util.ArrayList<>(viewers);
    }

    public TaiXiuGameManager getGameManager() { return TaiXiuGameManager.getInstance(); }

    public void openFor(ServerPlayerEntity player) {
        try {
            viewers.add(player);
            String json = TaiXiuNetworking.serializeState(getGameManager(), player,
                    getWorld() != null ? getWorld().getTime() : 0);
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(
                    player, new TaiXiuNetworking.OpenTaiXiuPayload(pos, json));
        } catch (Exception e) {
            System.err.println("[CasinoCraft] Tai Xiu openFor failed: " + e.getMessage());
            player.sendMessage(Text.literal("§c[Tai Xiu] Error opening: " + e.getMessage()), false);
        }
    }

    public static void tick(World world, BlockPos pos, BlockState state, TaiXiuTableBlockEntity be) {
        if (world.isClient()) return;
        try {
            be.viewers.removeIf(p -> !p.isAlive() || p.isDisconnected());

            if (!(world instanceof ServerWorld sw)) return;

            // Game ticks globally via ServerTickEvents
            // Send updates to viewers every tick for smooth realtime display
            TaiXiuGameManager manager = be.getGameManager();
            long worldTime = sw.getTime();

            // Debug: Log tick activity every 100 ticks
            if (worldTime % 100 == 0) {
                System.out.println("[Tai Xiu BE] Block entity tick at " + worldTime + ", viewers: " + be.viewers.size() + ", state: " + manager.getState());
            }

            // Send state update every tick (20 times per second) to all viewers
            for (ServerPlayerEntity player : be.getViewers()) {
                String json = TaiXiuNetworking.serializeState(manager, player, worldTime);
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(
                        player, new TaiXiuNetworking.TaiXiuStatePayload(pos, json));
            }
        } catch (Exception e) {
            System.err.println("[CasinoCraft] Tai Xiu tick error: " + e.getMessage());
            e.printStackTrace();
        }
    }

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
