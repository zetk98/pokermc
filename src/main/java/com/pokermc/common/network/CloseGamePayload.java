package com.pokermc.common.network;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.Collection;

/** S2C: Server tells client to close the game screen for this table (e.g. block broken). */
public record CloseGamePayload(BlockPos pos) implements CustomPayload {

    public static final CustomPayload.Id<CloseGamePayload> ID =
            new CustomPayload.Id<>(Identifier.of("casinocraft", "close_game"));
    public static final PacketCodec<PacketByteBuf, CloseGamePayload> CODEC = PacketCodec.tuple(
            BlockPos.PACKET_CODEC, CloseGamePayload::pos,
            CloseGamePayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }

    /** Send close to all players (e.g. when block is broken). */
    public static void sendToAll(Collection<ServerPlayerEntity> players, BlockPos pos) {
        var payload = new CloseGamePayload(pos);
        for (var p : players) {
            ServerPlayNetworking.send(p, payload);
        }
    }
}
