package com.pokermc.blockentity;

import com.pokermc.PokerMod;
import com.pokermc.game.PokerGame;
import com.pokermc.network.PokerNetworking;
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

public class PokerTableBlockEntity extends BlockEntity {

    private final PokerGame game = new PokerGame();
    /** Players currently viewing the poker screen (online at this table). */
    private final Set<ServerPlayerEntity> viewers = new LinkedHashSet<>();

    public PokerTableBlockEntity(BlockPos pos, BlockState state) {
        super(PokerMod.POKER_TABLE_BLOCK_ENTITY, pos, state);
    }

    // ── Viewer management ──────────────────────────────────────────────────────

    public void addViewer(ServerPlayerEntity player) {
        viewers.add(player);
    }

    public void removeViewer(ServerPlayerEntity player) {
        viewers.remove(player);
    }

    public void removeViewerByName(String name) {
        viewers.removeIf(p -> p.getName().getString().equals(name));
    }

    public List<ServerPlayerEntity> getViewers() {
        viewers.removeIf(p -> !p.isAlive() || p.isDisconnected());
        return new ArrayList<>(viewers);
    }

    // ── Open screen for a player ───────────────────────────────────────────────

    public void openFor(ServerPlayerEntity player) {
        try {
            viewers.add(player);
            String name = player.getName().getString();
            long worldTime = getWorld() != null ? getWorld().getTime() : 0;
            String json = PokerNetworking.serializeState(game, player, worldTime);
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(
                    player, new PokerNetworking.OpenTablePayload(pos, json));
            System.out.println("[PokerMC] Sent OpenTablePayload to " + player.getName().getString()
                    + " | phase=" + game.getPhase() + " | players=" + game.getPlayers().size());
        } catch (Exception e) {
            System.err.println("[PokerMC] openFor failed: " + e.getMessage());
            e.printStackTrace();
            player.sendMessage(net.minecraft.text.Text.literal("[PokerMC] Error opening table: " + e.getMessage()), false);
        }
    }

    private static final double MAX_DISTANCE = 5.5;
    private String lastTimedPlayer = "";
    private int tickCounter = 0;
    private int dealTickCounter = 0;

    // ── Tick ───────────────────────────────────────────────────────────────────

    public static void tick(World world, BlockPos pos, BlockState state, PokerTableBlockEntity be) {
        if (world.isClient) return;
        be.viewers.removeIf(p -> !p.isAlive() || p.isDisconnected());

        PokerGame game = be.getGame();

        // DEALING phase: deal one card every DEAL_TICKS_PER_CARD ticks
        if (game.getPhase() == PokerGame.Phase.DEALING) {
            be.dealTickCounter++;
            if (be.dealTickCounter >= PokerGame.DEAL_TICKS_PER_CARD) {
                be.dealTickCounter = 0;
                if (game.dealOneCard()) {
                    be.markDirty();
                    PokerNetworking.broadcastState(be);
                }
            }
            return;
        }

        int turnSec = com.pokermc.config.PokerConfig.get().turnTimeSeconds;
        if (turnSec > 0) {
            String cur = game.getCurrentPlayerName();
            var ph = game.getPhase();
            if (!cur.isEmpty() && ph != PokerGame.Phase.WAITING && ph != PokerGame.Phase.SHOWDOWN) {
                if (!cur.equals(be.lastTimedPlayer)) {
                    be.lastTimedPlayer = cur;
                    game.setTurnStartTick(world.getTime());
                } else if (world.getTime() - game.getTurnStartTick() >= turnSec * 20L) {
                    game.performAction(cur, PokerGame.Action.FOLD, 0);
                    game.setTurnStartTick(0);
                    be.lastTimedPlayer = "";
                    be.markDirty();
                    PokerNetworking.broadcastState(be);
                }
            } else {
                be.lastTimedPlayer = "";
            }
        }

        be.tickCounter++;
        if (be.tickCounter >= 20) {
            be.tickCounter = 0;
            if (!game.getCurrentPlayerName().isEmpty()
                    && game.getPhase() != PokerGame.Phase.WAITING
                    && game.getPhase() != PokerGame.Phase.SHOWDOWN) {
                PokerNetworking.broadcastState(be);
            }
        }

        if (game.getPhase() == PokerGame.Phase.WAITING
                || game.getPhase() == PokerGame.Phase.SHOWDOWN) return;
        if (!(world instanceof ServerWorld sw)) return;

        Vec3d tableCenter = Vec3d.ofCenter(pos);
        List<String> toForce = new ArrayList<>();

        // Collect all participant names (active + pending)
        List<String> participants = new ArrayList<>();
        for (PokerGame.PlayerState ps : game.getPlayers()) participants.add(ps.name);
        participants.addAll(game.getPendingPlayers());

        for (String name : participants) {
            ServerPlayerEntity sp = sw.getServer().getPlayerManager().getPlayer(name);

            // Bị out bàn: disconnect / chết / mất điện
            if (sp == null || sp.isDisconnected() || !sp.isAlive()) {
                toForce.add(name);
                continue;
            }

            // Bị out bàn: đẩy ra khỏi khu vực 5 ô
            if (sp.getPos().distanceTo(tableCenter) > MAX_DISTANCE) {
                toForce.add(name);
                sp.sendMessage(Text.literal("§e[Poker] §fBị out bàn (quá xa) - chip đã trả lại."), true);
                continue;
            }
        }

        // Out: online → giveBack; offline (văng game) → addToPot
        if (!toForce.isEmpty()) {
            var server = sw.getServer();
            for (String name : toForce) {
                if (name.equals(game.getCurrentPlayerName()))
                    game.performAction(name, PokerGame.Action.FOLD, 0);
                game.getPlayers().stream().filter(p -> p.name.equals(name)).findFirst()
                        .ifPresent(ps -> {
                            if (ps.chips > 0) {
                                var sp = server.getPlayerManager().getPlayer(name);
                                if (sp != null) {
                                    com.pokermc.config.ZCoinStorage.giveBack(sp, ps.chips);
                                } else {
                                    game.addToPot(ps.chips);
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
            PokerNetworking.broadcastState(be);
        }
    }

    // ── NBT persistence ────────────────────────────────────────────────────────

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        // We don't persist mid-game state for simplicity (game resets on server restart)
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
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

    /** When block is broken: give back chips to all players. Pot is lost. */
    public void refundAllPlayers() {
        if (!(getWorld() instanceof ServerWorld sw)) return;
        var server = sw.getServer();
        if (server == null) return;
        for (PokerGame.PlayerState ps : game.getPlayers()) {
            if (ps.chips > 0) {
                var sp = server.getPlayerManager().getPlayer(ps.name);
                if (sp != null) com.pokermc.config.ZCoinStorage.giveBack(sp, ps.chips);
            }
        }
    }

    // ── Getters ────────────────────────────────────────────────────────────────

    public PokerGame getGame() {
        return game;
    }

    public Text getDisplayName() {
        return Text.literal("Poker Table");
    }
}
