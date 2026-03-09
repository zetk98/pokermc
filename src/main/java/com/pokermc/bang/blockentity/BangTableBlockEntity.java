package com.pokermc.bang.blockentity;

import com.pokermc.PokerMod;
import com.pokermc.bang.game.BangGame;
import com.pokermc.bang.network.BangNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BangTableBlockEntity extends BlockEntity {

    private static final Map<UUID, BlockPos> PLAYER_OPEN_TABLE = new ConcurrentHashMap<>();

    private final BangGame game = new BangGame();
    private final Set<ServerPlayerEntity> viewers = new LinkedHashSet<>();
    private int dealTickCounter = 0;
    private int jailCheckTicks = 0;
    private int jailSkipDelayTicks = 0;
    private int nextTurnDelayTicks = 0;
    private static final int JAIL_CHECK_DELAY_TICKS = 60; // 3 seconds to show drawn card
    private static final int JAIL_SKIP_DELAY_TICKS = 40; // 2 seconds before next player
    private static final int NEXT_TURN_DELAY_TICKS = 40; // 2 seconds before next player
    private static final double MAX_DISTANCE = 5.5;

    public BangTableBlockEntity(BlockPos pos, BlockState state) {
        super(PokerMod.BANG_TABLE_BLOCK_ENTITY, pos, state);
    }

    public void addViewer(ServerPlayerEntity player) { viewers.add(player); }
    public void removeViewer(ServerPlayerEntity player) {
        viewers.remove(player);
        clearPlayerTable(player.getUuid());
    }
    public static void clearPlayerTable(UUID playerUuid) { PLAYER_OPEN_TABLE.remove(playerUuid); }

    public java.util.List<ServerPlayerEntity> getViewers() {
        viewers.removeIf(p -> {
            if (!p.isAlive() || p.isDisconnected()) {
                clearPlayerTable(p.getUuid());
                return true;
            }
            return false;
        });
        return new java.util.ArrayList<>(viewers);
    }

    public void openFor(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        BlockPos existing = PLAYER_OPEN_TABLE.get(uuid);
        if (existing != null && !existing.equals(pos)) {
            player.sendMessage(net.minecraft.text.Text.literal("You already have a room open. Leave it first."), false);
            return;
        }
        try {
            PLAYER_OPEN_TABLE.put(uuid, pos);
            viewers.add(player);
            String json = BangNetworking.serializeState(game, player);
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(
                    player, new BangNetworking.OpenBangPayload(pos, json));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void tick(World world, BlockPos pos, BlockState state, BangTableBlockEntity be) {
        if (world.isClient()) return;
        be.viewers.removeIf(p -> {
            if (!p.isAlive() || p.isDisconnected()) {
                clearPlayerTable(p.getUuid());
                return true;
            }
            return false;
        });

        BangGame game = be.getGame();

        if (game.getPhase() == BangGame.Phase.ROLE_REVEAL) {
            game.tickRoleReveal();
            be.markDirty();
            BangNetworking.broadcastState(be);
            return;
        }
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
        if (game.getPhase() == BangGame.Phase.DEAL_PAUSE) {
            game.tickDealPause();
            be.markDirty();
            BangNetworking.broadcastState(be);
            return;
        }
        if (game.getPhase() == BangGame.Phase.DEAL_FIRST) {
            be.dealTickCounter++;
            if (be.dealTickCounter >= BangGame.DEAL_TICKS_PER_CARD) {
                be.dealTickCounter = 0;
                game.dealFirstPlayerCard();
                be.markDirty();
                BangNetworking.broadcastState(be);
            }
            return;
        }
        if (game.getPhase() == BangGame.Phase.JAIL_CHECK) {
            be.jailCheckTicks++;
            if (be.jailCheckTicks >= JAIL_CHECK_DELAY_TICKS) {
                be.jailCheckTicks = 0;
                if (game.processJailCheck()) {
                    be.markDirty();
                    BangNetworking.broadcastState(be);
                }
            }
            return;
        }
        if (game.getPhase() == BangGame.Phase.JAIL_SKIP_DELAY) {
            be.jailSkipDelayTicks++;
            if (be.jailSkipDelayTicks >= JAIL_SKIP_DELAY_TICKS) {
                be.jailSkipDelayTicks = 0;
                game.startTurnForCurrentPlayer(); // Already advanced in processJailCheck; start next player's turn
                be.markDirty();
                BangNetworking.broadcastState(be);
            }
            return;
        }
        if (game.getPhase() == BangGame.Phase.NEXT_TURN_DELAY) {
            be.nextTurnDelayTicks++;
            if (be.nextTurnDelayTicks >= NEXT_TURN_DELAY_TICKS) {
                be.nextTurnDelayTicks = 0;
                game.nextTurn();
                be.markDirty();
                BangNetworking.broadcastState(be);
            }
            return;
        }
        if (game.getPhase() == BangGame.Phase.DYNAMITE_CHECK) {
            be.dealTickCounter++;
            if (be.dealTickCounter >= BangGame.DEAL_TICKS_PER_CARD) {
                be.dealTickCounter = 0;
                if (game.processDynamiteCheck()) {
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
                if (sp != null && sp.getSyncedPos().distanceTo(center) > MAX_DISTANCE) {
                    game.removePlayer(ps.name);
                    be.viewers.removeIf(p -> p.getName().getString().equals(ps.name));
                    be.clearPlayerTable(sp.getUuid());
                    be.markDirty();
                    BangNetworking.broadcastState(be);
                }
            }
        }
    }

    public BangGame getGame() { return game; }
}
