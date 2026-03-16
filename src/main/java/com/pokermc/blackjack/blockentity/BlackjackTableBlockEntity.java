package com.pokermc.blackjack.blockentity;

import com.pokermc.PokerMod;
import com.pokermc.blackjack.game.BlackjackGame;
import com.pokermc.blackjack.network.BlackjackNetworking;
import com.pokermc.common.config.ZCoinStorage;
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
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class BlackjackTableBlockEntity extends BlockEntity {

    private final BlackjackGame game = new BlackjackGame();
    private final Set<ServerPlayerEntity> viewers = new LinkedHashSet<>();

    public BlackjackTableBlockEntity(BlockPos pos, BlockState state) {
        super(PokerMod.BLACKJACK_TABLE_BLOCK_ENTITY, pos, state);
    }

    public void addViewer(ServerPlayerEntity player) { viewers.add(player); }
    public void removeViewer(ServerPlayerEntity player) { viewers.remove(player); }
    public void removeViewerByName(String name) { viewers.removeIf(p -> p.getName().getString().equals(name)); }

    public List<ServerPlayerEntity> getViewers() {
        viewers.removeIf(p -> !p.isAlive() || p.isDisconnected());
        return new ArrayList<>(viewers);
    }

    public void openFor(ServerPlayerEntity player) {
        try {
            viewers.add(player);
            String json = BlackjackNetworking.serializeState(game, player,
                    getWorld() != null ? getWorld().getTime() : 0);
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(
                    player, new BlackjackNetworking.OpenBlackjackPayload(pos, json));
        } catch (Exception e) {
            System.err.println("[CasinoCraft] Blackjack openFor failed: " + e.getMessage());
            player.sendMessage(Text.literal("§c[CasinoCraft] Error opening table: " + e.getMessage()), false);
        }
    }

    private static final double MAX_DISTANCE = 5.5;

    public static void tick(World world, BlockPos pos, BlockState state, BlackjackTableBlockEntity be) {
        if (world.isClient()) return;
        try {
            be.viewers.removeIf(p -> !p.isAlive() || p.isDisconnected());

            BlackjackGame game = be.getGame();
            if (game.getPhase() == BlackjackGame.Phase.BETTING) {
                boolean expired = game.tickBetTimer();
                if (expired) {
                    game.confirmBets();
                    be.markDirty();
                    if (!be.getViewers().isEmpty())
                        BlackjackNetworking.broadcastState(be);
                }
                return;
            }
            if (game.getPhase() == BlackjackGame.Phase.WAITING
                    || game.getPhase() == BlackjackGame.Phase.SETTLEMENT) return;
            if (!(world instanceof ServerWorld sw)) return;

        Vec3d tableCenter = Vec3d.ofCenter(pos);
        List<String> participants = new ArrayList<>();
        for (BlackjackGame.PlayerState ps : game.getPlayers()) participants.add(ps.name);
        participants.addAll(game.getPendingPlayers());

        List<String> toForce = new ArrayList<>();
        boolean anyoneTooFar = false;

        for (String name : participants) {
            ServerPlayerEntity sp = sw.getServer().getPlayerManager().getPlayer(name);
            if (sp == null || sp.isDisconnected() || !sp.isAlive()) {
                toForce.add(name);
                continue;
            }
            if (sp.getSyncedPos().distanceTo(tableCenter) > MAX_DISTANCE) {
                anyoneTooFar = true;
                break;
            }
        }
        if (anyoneTooFar) {
            toForce.addAll(participants);
            for (String name : participants) {
                var sp = sw.getServer().getPlayerManager().getPlayer(name);
                if (sp != null) sp.sendMessage(Text.literal("§d[Blackjack] §fBàn đóng (có người quá xa) - chips trả lại."), true);
            }
        }

        if (!toForce.isEmpty()) {
            var server = sw.getServer();
            for (String name : toForce) {
                game.getPlayers().stream().filter(p -> p.name.equals(name)).findFirst()
                        .ifPresent(ps -> {
                            if (ps.chips > 0) {
                                var sp = server.getPlayerManager().getPlayer(name);
                                if (sp != null) {
                                    ZCoinStorage.giveBack(sp, ps.chips);
                                } else {
                                    game.addDisconnectChipsToDealer(ps.chips);
                                }
                            }
                        });
                game.removePlayer(name);
                be.viewers.removeIf(p -> p.getName().getString().equals(name));
            }
            if (game.getPlayers().isEmpty() && game.getPendingPlayers().isEmpty()) {
                game.resetWhenEmpty();
            }
            be.markDirty();
            BlackjackNetworking.broadcastState(be);
        }
        } catch (Exception e) {
            System.err.println("[CasinoCraft] Blackjack tick error: " + e.getMessage());
        }
    }

    // No custom persistence

    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
        return createNbt(registries);
    }

    @Nullable
    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    /** When block is broken: give back chips to all players. */
    public void refundAllPlayers() {
        if (!(getWorld() instanceof ServerWorld sw)) return;
        var server = sw.getServer();
        if (server == null) return;
        for (BlackjackGame.PlayerState ps : game.getPlayers()) {
            if (ps.chips > 0) {
                var sp = server.getPlayerManager().getPlayer(ps.name);
                if (sp != null) ZCoinStorage.giveBack(sp, ps.chips);
            }
        }
    }

    public BlackjackGame getGame() { return game; }
}
