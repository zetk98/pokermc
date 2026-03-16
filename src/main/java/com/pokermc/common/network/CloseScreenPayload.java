package com.pokermc.common.network;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/** S2C: Server tells client to close any open screen. Fixes inventory sync when giving items. */
public record CloseScreenPayload() implements CustomPayload {

    public static final CustomPayload.Id<CloseScreenPayload> ID =
            new CustomPayload.Id<>(Identifier.of("casinocraft", "close_screen"));
    public static final PacketCodec<PacketByteBuf, CloseScreenPayload> CODEC =
            PacketCodec.unit(new CloseScreenPayload());

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }

    public static void sendTo(ServerPlayerEntity player) {
        ServerPlayNetworking.send(player, new CloseScreenPayload());
    }
}
