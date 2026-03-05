package com.pokermc.network;

import com.google.gson.*;
import com.pokermc.blockentity.BangTableBlockEntity;
import com.pokermc.game.bang.BangCard;
import com.pokermc.game.bang.BangGame;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Map;

public class BangNetworking {

    private static final Gson GSON = new Gson();

    public record OpenBangPayload(BlockPos pos, String stateJson) implements CustomPayload {
        public static final CustomPayload.Id<OpenBangPayload> ID =
                new CustomPayload.Id<>(Identifier.of("casinocraft", "open_bang"));
        public static final PacketCodec<PacketByteBuf, OpenBangPayload> CODEC = PacketCodec.tuple(
                BlockPos.PACKET_CODEC, OpenBangPayload::pos,
                PacketCodecs.STRING, OpenBangPayload::stateJson,
                OpenBangPayload::new);
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    public record BangStatePayload(BlockPos pos, String stateJson) implements CustomPayload {
        public static final CustomPayload.Id<BangStatePayload> ID =
                new CustomPayload.Id<>(Identifier.of("casinocraft", "bang_state"));
        public static final PacketCodec<PacketByteBuf, BangStatePayload> CODEC = PacketCodec.tuple(
                BlockPos.PACKET_CODEC, BangStatePayload::pos,
                PacketCodecs.STRING, BangStatePayload::stateJson,
                BangStatePayload::new);
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    public record BangActionPayload(BlockPos pos, String action, int amount, String data) implements CustomPayload {
        public static final CustomPayload.Id<BangActionPayload> ID =
                new CustomPayload.Id<>(Identifier.of("casinocraft", "bang_action"));
        public static final PacketCodec<PacketByteBuf, BangActionPayload> CODEC = PacketCodec.tuple(
                BlockPos.PACKET_CODEC, BangActionPayload::pos,
                PacketCodecs.STRING, BangActionPayload::action,
                PacketCodecs.VAR_INT, BangActionPayload::amount,
                PacketCodecs.STRING, BangActionPayload::data,
                BangActionPayload::new);
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    public static String serializeState(BangGame game, ServerPlayerEntity viewer) {
        String viewerName = viewer.getName().getString();
        JsonObject root = new JsonObject();
        root.addProperty("phase", game.getPhase().name());
        root.addProperty("status", game.getStatusMessage());
        root.addProperty("currentPlayer", game.getCurrentPlayerName());
        root.addProperty("currentPlayerIndex", game.getCurrentPlayerIndex());
        root.addProperty("sheriffIndex", game.getSheriffIndex());
        root.addProperty("maxPlayers", game.getMaxPlayers());
        root.addProperty("isEmpty", game.isEmpty());

        JsonArray playersArr = new JsonArray();
        int heroIdx = -1;
        for (int i = 0; i < game.getPlayers().size(); i++) {
            BangGame.PlayerState p = game.getPlayers().get(i);
            JsonObject po = new JsonObject();
            po.addProperty("name", p.name);
            po.addProperty("seatIndex", p.seatIndex);
            po.addProperty("role", p.role != null ? p.role.name() : "");
            po.addProperty("maxHp", p.maxHp);
            po.addProperty("hp", p.hp);
            po.addProperty("gapShoot", p.gapShoot);
            po.addProperty("gapUse", p.gapUse);
            po.addProperty("gap", p.gap);
            po.addProperty("isAlive", p.isAlive);
            po.addProperty("jailing", p.jailing);
            po.addProperty("barreling", p.barreling);

            boolean isHero = p.name.equals(viewerName);
            if (isHero) heroIdx = i;

            JsonArray handArr = new JsonArray();
            if (isHero) {
                for (BangCard c : p.hand) handArr.add(c.toCode());
            } else {
                for (int j = 0; j < p.hand.size(); j++) handArr.add("??");
            }
            po.add("hand", handArr);

            JsonArray equipArr = new JsonArray();
            for (BangCard c : p.equipment) equipArr.add(c.toCode());
            po.add("equipment", equipArr);

            playersArr.add(po);
        }
        root.add("players", playersArr);
        root.addProperty("heroIndex", heroIdx);

        // Distance from hero to each player (for testing)
        if (heroIdx >= 0 && game.getPlayers().size() > 1) {
            JsonObject distMap = new JsonObject();
            for (int i = 0; i < game.getPlayers().size(); i++) {
                int d = game.getDistanceTo(heroIdx, i);
                distMap.addProperty(game.getPlayers().get(i).name, d);
            }
            root.add("distancesFromHero", distMap);
        }

        JsonArray pendingArr = new JsonArray();
        for (String n : game.getPendingPlayers()) pendingArr.add(n);
        root.add("pendingPlayers", pendingArr);

        return GSON.toJson(root);
    }

    public static void broadcastState(BangTableBlockEntity be) {
        for (ServerPlayerEntity sp : be.getViewers()) {
            String json = serializeState(be.getGame(), sp);
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(
                    sp, new BangStatePayload(be.getPos(), json));
        }
    }

    public static void handleAction(ServerPlayerEntity player, BangActionPayload payload) {
        if (!(player.getWorld().getBlockEntity(payload.pos()) instanceof BangTableBlockEntity be)) return;
        String name = player.getName().getString();
        String action = payload.action();
        int amount = payload.amount();
        String data = payload.data() != null ? payload.data() : "";

        boolean changed = false;
        switch (action) {
            case "CREATE" -> changed = handleCreate(be, player, amount);
            case "JOIN" -> changed = handleJoin(be, player);
            case "LEAVE" -> changed = handleLeave(be, player);
            case "START" -> changed = be.getGame().startGame();
            case "END_TURN" -> {
                be.getGame().nextTurn();
                changed = true;
            }
            case "PLAY_CARD" -> changed = be.getGame().playCard(name, amount, data);
            default -> {}
        }
        if (changed) {
            be.markDirty();
            broadcastState(be);
        }
    }

    private static boolean handleCreate(BangTableBlockEntity be, ServerPlayerEntity player, int maxPlayers) {
        if (!be.getGame().getPlayers().isEmpty()) return false;
        be.getGame().setMaxPlayers(Math.max(4, Math.min(6, maxPlayers)));
        return be.getGame().addPlayer(player.getName().getString());
    }

    private static boolean handleJoin(BangTableBlockEntity be, ServerPlayerEntity player) {
        String name = player.getName().getString();
        if (be.getGame().hasPlayer(name)) {
            be.addViewer(player);
            return true;
        }
        if (be.getGame().getPlayers().isEmpty()) return false;
        boolean changed = be.getGame().addPlayer(name);
        if (changed) be.addViewer(player);
        return changed;
    }

    private static boolean handleLeave(BangTableBlockEntity be, ServerPlayerEntity player) {
        be.removeViewer(player);
        return be.getGame().removePlayer(player.getName().getString());
    }
}
