package com.pokermc.goldenticket.network;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.pokermc.common.config.CasinoCraftConfig;
import com.pokermc.common.config.ZCoinStorage;
import com.pokermc.goldenticket.blockentity.GoldenTicketBlockEntity;
import com.pokermc.goldenticket.game.GoldenTicketTierConfig;
import com.pokermc.goldenticket.item.GoldenTicketItem;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public class GoldenTicketNetworking {

    private static final Gson GSON = new Gson();

    public record OpenGoldenTicketPayload(BlockPos pos, String stateJson) implements CustomPayload {
        public static final CustomPayload.Id<OpenGoldenTicketPayload> ID =
                new CustomPayload.Id<>(Identifier.of("casinocraft", "open_golden_ticket"));
        public static final PacketCodec<PacketByteBuf, OpenGoldenTicketPayload> CODEC = PacketCodec.tuple(
                BlockPos.PACKET_CODEC, OpenGoldenTicketPayload::pos,
                PacketCodecs.STRING, OpenGoldenTicketPayload::stateJson,
                OpenGoldenTicketPayload::new);
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    public record GoldenTicketActionPayload(BlockPos pos, int tierIndex, int amount) implements CustomPayload {
        public static final CustomPayload.Id<GoldenTicketActionPayload> ID =
                new CustomPayload.Id<>(Identifier.of("casinocraft", "golden_ticket_action"));
        public static final PacketCodec<PacketByteBuf, GoldenTicketActionPayload> CODEC = PacketCodec.tuple(
                BlockPos.PACKET_CODEC, GoldenTicketActionPayload::pos,
                PacketCodecs.VAR_INT, GoldenTicketActionPayload::tierIndex,
                PacketCodecs.VAR_INT, GoldenTicketActionPayload::amount,
                GoldenTicketActionPayload::new);
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    public static String serializeState(ServerPlayerEntity viewer) {
        JsonObject root = new JsonObject();
        root.addProperty("bankBalance", ZCoinStorage.getBalance(viewer));

        JsonArray tiers = new JsonArray();
        for (GoldenTicketTierConfig t : CasinoCraftConfig.get().goldenTicketTiers) {
            JsonObject o = new JsonObject();
            o.addProperty("price", t.price);
            o.addProperty("rewardMin", t.rewardMin);
            o.addProperty("rewardMax", t.rewardMax);
            o.addProperty("jackpotThreshold", t.jackpotThreshold);
            o.addProperty("upgradeChance", t.upgradeChance);
            tiers.add(o);
        }
        root.add("tiers", tiers);

        return GSON.toJson(root);
    }

    public static void handleAction(ServerPlayerEntity player, GoldenTicketActionPayload payload) {
        net.minecraft.block.entity.BlockEntity be = player.getEntityWorld().getBlockEntity(payload.pos());
        if (!(be instanceof GoldenTicketBlockEntity gt)) return;

        var tiers = CasinoCraftConfig.get().goldenTicketTiers;
        int tierIdx = payload.tierIndex();
        int amount = Math.max(1, Math.min(64, payload.amount()));

        if (tierIdx < 0 || tierIdx >= tiers.size()) return;

        GoldenTicketTierConfig cfg = tiers.get(tierIdx);
        int totalCost = cfg.price * amount;

        if (ZCoinStorage.getBalance(player) < totalCost) {
            player.sendMessage(net.minecraft.text.Text.literal("§cNot enough ZCoin. Need " + totalCost + " ZC."), true);
            return;
        }

        if (!ZCoinStorage.deduct(player, totalCost)) {
            player.sendMessage(net.minecraft.text.Text.literal("§cFailed to deduct ZCoin."), true);
            return;
        }

        for (int i = 0; i < amount; i++) {
            var stack = GoldenTicketItem.create(tierIdx);
            if (!player.getInventory().insertStack(stack)) {
                player.dropItem(stack, false);
            }
        }

        player.sendMessage(net.minecraft.text.Text.literal("§aBought " + amount + "x Golden Ticket (" + cfg.price + " ZC)!"), true);
    }
}
