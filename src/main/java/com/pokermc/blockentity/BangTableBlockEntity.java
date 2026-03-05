package com.pokermc.blockentity;

import com.pokermc.PokerMod;
import com.pokermc.game.bang.BangGame;
import com.pokermc.network.BangNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.LinkedHashSet;
import java.util.Set;

public class BangTableBlockEntity extends BlockEntity {

    private final BangGame game = new BangGame();
    private final Set<ServerPlayerEntity> viewers = new LinkedHashSet<>();
    private int dealTickCounter = 0;
    private static final double MAX_DISTANCE = 5.5;

    public BangTableBlockEntity(BlockPos pos, BlockState state) {
        super(PokerMod.BANG_TABLE_BLOCK_ENTITY, pos, state);
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
            String json = BangNetworking.serializeState(game, player);
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(
                    player, new BangNetworking.OpenBangPayload(pos, json));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void tick(World world, BlockPos pos, BlockState state, BangTableBlockEntity be) {
        if (world.isClient) return;
        be.viewers.removeIf(p -> !p.isAlive() || p.isDisconnected());

        BangGame game = be.getGame();

        if (game.getPhase() == BangGame.Phase.DEALING) {
            be.dealTickCounter++;
            if (be.dealTickCounter >= BangGame.DEAL_TICKS_PER_CARD) {
                be.dealTickCounter = 0;
                if (game.dealOneCard()) {
                    be.markDirty();
                    BangNetworking.broadcastState(be);
                }
            }
            return;
        }

        // Proximity check
        if (world instanceof net.minecraft.server.world.ServerWorld sw && game.getPhase() != BangGame.Phase.WAITING) {
            Vec3d center = Vec3d.ofCenter(pos);
            for (BangGame.PlayerState ps : game.getPlayers()) {
                ServerPlayerEntity sp = sw.getServer().getPlayerManager().getPlayer(ps.name);
                if (sp != null && sp.getPos().distanceTo(center) > MAX_DISTANCE) {
                    game.removePlayer(ps.name);
                    be.viewers.removeIf(p -> p.getName().getString().equals(ps.name));
                    be.markDirty();
                    BangNetworking.broadcastState(be);
                }
            }
        }
    }

    public BangGame getGame() { return game; }
}
