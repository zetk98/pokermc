package com.pokermc.market.network;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.pokermc.common.config.ZCoinStorage;
import com.pokermc.market.blockentity.MarketBlockEntity;
import com.pokermc.market.game.MarketConfig;
import com.pokermc.market.game.MarketPersistentState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.List;

public class MarketNetworking {

    private static final Gson GSON = new Gson();

    public record OpenMarketPayload(BlockPos pos, String stateJson) implements CustomPayload {
        public static final CustomPayload.Id<OpenMarketPayload> ID =
                new CustomPayload.Id<>(Identifier.of("casinocraft", "open_market"));
        public static final PacketCodec<PacketByteBuf, OpenMarketPayload> CODEC = PacketCodec.tuple(
                BlockPos.PACKET_CODEC, OpenMarketPayload::pos,
                PacketCodecs.STRING, OpenMarketPayload::stateJson,
                OpenMarketPayload::new);
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    public record MarketStatePayload(BlockPos pos, String stateJson) implements CustomPayload {
        public static final CustomPayload.Id<MarketStatePayload> ID =
                new CustomPayload.Id<>(Identifier.of("casinocraft", "market_state"));
        public static final PacketCodec<PacketByteBuf, MarketStatePayload> CODEC = PacketCodec.tuple(
                BlockPos.PACKET_CODEC, MarketStatePayload::pos,
                PacketCodecs.STRING, MarketStatePayload::stateJson,
                MarketStatePayload::new);
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    public record RequestMarketRefreshPayload(BlockPos pos) implements CustomPayload {
        public static final CustomPayload.Id<RequestMarketRefreshPayload> ID =
                new CustomPayload.Id<>(Identifier.of("casinocraft", "market_refresh"));
        public static final PacketCodec<PacketByteBuf, RequestMarketRefreshPayload> CODEC = PacketCodec.tuple(
                BlockPos.PACKET_CODEC, RequestMarketRefreshPayload::pos,
                RequestMarketRefreshPayload::new);
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    public record MarketActionPayload(BlockPos pos, String action, String itemId, int amount) implements CustomPayload {
        public static final CustomPayload.Id<MarketActionPayload> ID =
                new CustomPayload.Id<>(Identifier.of("casinocraft", "market_action"));
        public static final PacketCodec<PacketByteBuf, MarketActionPayload> CODEC = PacketCodec.tuple(
                BlockPos.PACKET_CODEC, MarketActionPayload::pos,
                PacketCodecs.STRING, MarketActionPayload::action,
                PacketCodecs.STRING, MarketActionPayload::itemId,
                PacketCodecs.VAR_INT, MarketActionPayload::amount,
                MarketActionPayload::new);
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    public static String serializeState(ServerPlayerEntity viewer, MarketPersistentState state, long worldTime) {
        JsonObject root = new JsonObject();
        root.addProperty("bankBalance", ZCoinStorage.getBalance(viewer));
        root.addProperty("worldTime", worldTime);
        root.addProperty("lastUpdateTick", state.getLastUpdateTick());

        var cfg = com.pokermc.common.config.CasinoCraftConfig.get();
        List<String> itemIds = MarketConfig.getMarketItemIds(cfg);

        JsonArray items = new JsonArray();
        for (String id : itemIds) {
            JsonObject o = new JsonObject();
            o.addProperty("id", id);
            o.addProperty("sellPrice", state.getSellPrice(id));
            o.addProperty("buyPrice", state.getBuyPrice(id));
            JsonArray hist = new JsonArray();
            for (int p : state.getPriceHistory(id)) hist.add(p);
            o.add("history", hist);
            items.add(o);
        }
        root.add("items", items);

        return GSON.toJson(root);
    }

    public static void handleAction(ServerPlayerEntity player, MarketActionPayload payload) {
        var be = player.getEntityWorld().getBlockEntity(payload.pos());
        if (!(be instanceof MarketBlockEntity)) return;

        var server = player.getEntityWorld() instanceof net.minecraft.server.world.ServerWorld sw ? sw.getServer() : null;
        if (server == null) return;
        MarketPersistentState state = MarketPersistentState.get(server);
        if (state == null) return;

        var cfg = com.pokermc.common.config.CasinoCraftConfig.get();
        if (!MarketConfig.getMarketItemIds(cfg).contains(payload.itemId())) return;

        Identifier id = Identifier.tryParse(payload.itemId());
        if (id == null) return;
        Item item = Registries.ITEM.get(id);
        if (item == null || item == net.minecraft.item.Items.AIR) return;

        int amount = Math.max(1, Math.min(64, payload.amount()));

        if ("SELL".equals(payload.action())) {
            int count = countItem(player, item);
            if (count < amount) {
                player.sendMessage(net.minecraft.text.Text.literal("§cNot enough items. You have " + count + "."), true);
                return;
            }
            int price = state.getSellPrice(payload.itemId());
            int total = price * amount;
            takeItems(player, item, amount);
            com.pokermc.common.config.ZCoinStorage.add(player, total);
            state.recordSell(payload.itemId(), amount);
            player.sendMessage(net.minecraft.text.Text.literal("§aSold " + amount + " for " + total + " ZC!"), true);
        } else if ("BUY".equals(payload.action())) {
            int price = state.getBuyPrice(payload.itemId());
            int cost = price * amount;
            if (ZCoinStorage.getBalance(player) < cost) {
                player.sendMessage(net.minecraft.text.Text.literal("§cNot enough ZCoin. Need " + cost + " ZC."), true);
                return;
            }
            if (!ZCoinStorage.deduct(player, cost)) {
                player.sendMessage(net.minecraft.text.Text.literal("§cFailed to deduct ZCoin."), true);
                return;
            }
            ItemStack stack = new ItemStack(item, amount);
            if (!player.getInventory().insertStack(stack)) {
                player.dropItem(stack, false);
            }
            state.recordBuy(payload.itemId(), amount);
            player.sendMessage(net.minecraft.text.Text.literal("§aBought " + amount + " for " + cost + " ZC!"), true);
        }
        state.markDirty();
        var overworld = server.getWorld(net.minecraft.world.World.OVERWORLD);
        long worldTime = overworld != null ? overworld.getTime() : 0;
        String json = serializeState(player, state, worldTime);
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(
                player, new MarketStatePayload(payload.pos(), json));
    }

    public static void handleRefreshRequest(ServerPlayerEntity player, RequestMarketRefreshPayload payload) {
        var server = player.getEntityWorld() instanceof net.minecraft.server.world.ServerWorld sw ? sw.getServer() : null;
        if (server == null) return;
        MarketPersistentState state = MarketPersistentState.get(server);
        if (state == null) return;
        state.initFromConfig(com.pokermc.common.config.CasinoCraftConfig.get());
        var overworld = server.getWorld(net.minecraft.world.World.OVERWORLD);
        long worldTime = overworld != null ? overworld.getTime() : 0;
        String json = serializeState(player, state, worldTime);
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(
                player, new MarketStatePayload(payload.pos(), json));
    }

    private static int countItem(ServerPlayerEntity player, Item item) {
        int c = 0;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack s = player.getInventory().getStack(i);
            if (s.getItem() == item) c += s.getCount();
        }
        return c;
    }

    private static void takeItems(ServerPlayerEntity player, Item item, int amount) {
        int left = amount;
        for (int i = 0; i < player.getInventory().size() && left > 0; i++) {
            ItemStack s = player.getInventory().getStack(i);
            if (s.getItem() == item) {
                int take = Math.min(s.getCount(), left);
                s.decrement(take);
                left -= take;
            }
        }
    }
}
