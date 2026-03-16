package com.pokermc.xoso.blockentity;

import com.pokermc.PokerMod;
import com.pokermc.xoso.game.XosoGame;
import com.pokermc.xoso.network.XosoNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class XosoBlockEntity extends BlockEntity {

    private final XosoGame game = new XosoGame();

    public XosoBlockEntity(BlockPos pos, BlockState state) {
        super(PokerMod.XOSO_BLOCK_ENTITY, pos, state);
    }

    public void openFor(ServerPlayerEntity player) {
        try {
            long worldTime = 0;
            net.minecraft.server.MinecraftServer server = null;
            if (getWorld() instanceof ServerWorld sw) {
                server = sw.getServer();
                if (server != null) {
                    var overworld = server.getWorld(World.OVERWORLD);
                    worldTime = overworld != null ? overworld.getTime() : sw.getTime();
                } else {
                    worldTime = sw.getTime();
                }
            }
            String json = XosoNetworking.serializeState(game, player, worldTime, server);
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(
                    player, new XosoNetworking.OpenXosoPayload(pos, json));
        } catch (Exception e) {
            System.err.println("[XOSO] openFor failed: " + e.getMessage());
            player.sendMessage(net.minecraft.text.Text.literal("[XOSO] Lỗi: " + e.getMessage()), false);
        }
    }

    public static void tick(World world, BlockPos pos, BlockState state, XosoBlockEntity be) {
        if (world.isClient()) return;
        XosoGame g = be.game;
        ServerWorld sw = (ServerWorld) world;
        net.minecraft.server.MinecraftServer server = sw.getServer();
        if (g.tick(world.getTime(), server, sw)) {
            be.markDirty();
            XosoNetworking.broadcastToViewers(be);
        }
    }

    public XosoGame getGame() { return game; }

    @Override
    public net.minecraft.nbt.NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
        return createNbt(registries);
    }

    @Nullable
    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }
}
