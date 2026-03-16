package com.pokermc.xoso.network;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.pokermc.common.config.CasinoCraftConfig;
import com.pokermc.common.config.ZCoinStorage;
import com.pokermc.xoso.blockentity.XosoBlockEntity;
import com.pokermc.xoso.game.LotteryDrawStorage;
import com.pokermc.xoso.game.XosoGame;
import net.minecraft.server.MinecraftServer;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class XosoNetworking {

    private static final Gson GSON = new Gson();

    public record OpenXosoPayload(BlockPos pos, String stateJson) implements CustomPayload {
        public static final CustomPayload.Id<OpenXosoPayload> ID =
                new CustomPayload.Id<>(Identifier.of("casinocraft", "open_xoso"));
        public static final PacketCodec<PacketByteBuf, OpenXosoPayload> CODEC = PacketCodec.tuple(
                BlockPos.PACKET_CODEC, OpenXosoPayload::pos,
                PacketCodecs.STRING, OpenXosoPayload::stateJson,
                OpenXosoPayload::new);
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    public record XosoStatePayload(BlockPos pos, String stateJson) implements CustomPayload {
        public static final CustomPayload.Id<XosoStatePayload> ID =
                new CustomPayload.Id<>(Identifier.of("casinocraft", "xoso_state"));
        public static final PacketCodec<PacketByteBuf, XosoStatePayload> CODEC = PacketCodec.tuple(
                BlockPos.PACKET_CODEC, XosoStatePayload::pos,
                PacketCodecs.STRING, XosoStatePayload::stateJson,
                XosoStatePayload::new);
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    public record XosoActionPayload(BlockPos pos, String action, String data) implements CustomPayload {
        public static final CustomPayload.Id<XosoActionPayload> ID =
                new CustomPayload.Id<>(Identifier.of("casinocraft", "xoso_action"));
        public static final PacketCodec<PacketByteBuf, XosoActionPayload> CODEC = PacketCodec.tuple(
                BlockPos.PACKET_CODEC, XosoActionPayload::pos,
                PacketCodecs.STRING, XosoActionPayload::action,
                PacketCodecs.STRING, XosoActionPayload::data,
                XosoActionPayload::new);
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    public static String serializeState(XosoGame game, ServerPlayerEntity viewer, long worldTime, MinecraftServer server) {
        JsonObject root = new JsonObject();
        root.addProperty("status", game.getStatusMessage());
        root.addProperty("ticketPrice", game.getTicketPrice());
        root.addProperty("worldTime", worldTime);
        root.addProperty("lastDrawPeriod", game.getLastDrawPeriod());
        root.addProperty("ticksPerDraw", XosoGame.getTicksPerDraw());
        root.addProperty("resultSpecial", game.getResultSpecial());
        root.addProperty("result1st", game.getResult1st());
        root.addProperty("result2nd", game.getResult2nd());
        root.addProperty("result3rd", game.getResult3rd());
        root.addProperty("bankBalance", ZCoinStorage.getBalance(viewer));

        // 7 kỳ xổ gần nhất (mới nhất trước)
        JsonArray recentArr = new JsonArray();
        if (server != null) {
            long lastPeriod = game.getLastDrawPeriod();
            for (long d = lastPeriod; d >= lastPeriod - 6 && d >= 0; d--) {
                var r = LotteryDrawStorage.get(server, d);
                if (r != null) {
                    JsonObject o = new JsonObject();
                    o.addProperty("period", d);
                    o.addProperty("special", r.special());
                    o.addProperty("first", r.first());
                    o.addProperty("second", r.second());
                    o.addProperty("third", r.third());
                    recentArr.add(o);
                }
            }
        }
        root.add("recentDraws", recentArr);

        JsonArray prizeInfo = new JsonArray();
        var cfg = CasinoCraftConfig.get();
        prizeInfo.add(cfg.xosoPrizeDacBiet);
        prizeInfo.add(cfg.xosoPrizeNhat);
        prizeInfo.add(cfg.xosoPrizeNhi);
        prizeInfo.add(cfg.xosoPrizeBa);
        root.add("prizes", prizeInfo);

        return GSON.toJson(root);
    }

    public static void broadcastToViewers(XosoBlockEntity be) {
        if (!(be.getWorld() instanceof net.minecraft.server.world.ServerWorld sw)) return;
        BlockPos pos = be.getPos();
        XosoGame game = be.getGame();
        long worldTime = sw.getTime();
        MinecraftServer server = sw.getServer();
        if (server != null) {
            var overworld = server.getWorld(net.minecraft.world.World.OVERWORLD);
            if (overworld != null) worldTime = overworld.getTime();
        }
        for (ServerPlayerEntity p : sw.getPlayers()) {
            if (p.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64 * 64) {
                String json = serializeState(game, p, worldTime, server);
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(
                        p, new XosoStatePayload(pos, json));
            }
        }
    }

    public static void handleAction(ServerPlayerEntity player, XosoActionPayload payload) {
        net.minecraft.block.entity.BlockEntity be = player.getEntityWorld().getBlockEntity(payload.pos());
        if (!(be instanceof XosoBlockEntity xoso)) return;
        XosoGame game = xoso.getGame();
        String name = player.getName().getString();

        switch (payload.action()) {
            case "BUY" -> {
                String msg = game.buyTicket(player, payload.data());
                game.setStatusMessage(msg);
                xoso.markDirty();
                broadcastToViewers(xoso);
            }
            case "RANDOM" -> {
                String msg = game.buyRandomTicket(player);
                game.setStatusMessage(msg);
                xoso.markDirty();
                broadcastToViewers(xoso);
            }
            default -> {}
        }
    }
}
