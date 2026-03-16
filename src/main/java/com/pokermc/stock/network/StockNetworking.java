package com.pokermc.stock.network;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.pokermc.PokerMod;
import com.pokermc.stock.blockentity.StockExchangeBlockEntity;
import com.pokermc.stock.game.StockMarketGame;
import com.pokermc.stock.game.StockType;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Map;

/**
 * Networking for Stock Exchange.
 * Handles S2C packets for opening GUI and broadcasting state updates.
 */
public class StockNetworking {

    public static final Identifier OPEN_STOCK_EXCHANGE_ID =
            Identifier.of(PokerMod.MOD_ID, "open_stock_exchange");
    public static final Identifier STOCK_STATE_ID =
            Identifier.of(PokerMod.MOD_ID, "stock_state");

    private static final Gson GSON = new GsonBuilder().create();

    /**
     * Payload to open the stock exchange screen.
     */
    public record OpenStockExchangePayload(BlockPos pos, String stateJson)
            implements CustomPayload {

        public static final Id<OpenStockExchangePayload> ID = new Id<>(OPEN_STOCK_EXCHANGE_ID);
        public static final PacketCodec<PacketByteBuf, OpenStockExchangePayload> CODEC = PacketCodec.tuple(
                BlockPos.PACKET_CODEC, OpenStockExchangePayload::pos,
                PacketCodecs.STRING, OpenStockExchangePayload::stateJson,
                OpenStockExchangePayload::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /**
     * Payload to update stock state on client.
     */
    public record StockStatePayload(BlockPos pos, String stateJson)
            implements CustomPayload {

        public static final Id<StockStatePayload> ID = new Id<>(STOCK_STATE_ID);
        public static final PacketCodec<PacketByteBuf, StockStatePayload> CODEC = PacketCodec.tuple(
                BlockPos.PACKET_CODEC, StockStatePayload::pos,
                PacketCodecs.STRING, StockStatePayload::stateJson,
                StockStatePayload::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /**
     * Payload for player actions (BUY, SELL, LIMIT_BUY, LIMIT_SELL, CANCEL).
     */
    public record StockActionPayload(BlockPos pos, String action, String ticker, int quantity, int limitPrice)
            implements CustomPayload {

        public static final Id<StockActionPayload> ID = new Id<>(Identifier.of(PokerMod.MOD_ID, "stock_action"));
        public static final PacketCodec<PacketByteBuf, StockActionPayload> CODEC = PacketCodec.tuple(
                BlockPos.PACKET_CODEC, StockActionPayload::pos,
                PacketCodecs.STRING, StockActionPayload::action,
                PacketCodecs.STRING, StockActionPayload::ticker,
                PacketCodecs.VAR_INT, StockActionPayload::quantity,
                PacketCodecs.VAR_INT, StockActionPayload::limitPrice,
                StockActionPayload::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /**
     * Serialize the stock market state to JSON.
     */
    public static String serializeState(StockMarketGame game, ServerPlayerEntity viewer, long worldTime) {
        JsonObject root = new JsonObject();

        // Status
        root.addProperty("status", game.getStatusMessage());
        root.addProperty("lastUpdate", game.getLastUpdateTick());
        root.addProperty("currentTime", worldTime);

        // Player balance
        root.addProperty("playerBalance", com.pokermc.common.config.ZCoinStorage.getBalance(viewer));

        // Market event
        JsonObject event = new JsonObject();
        event.addProperty("name", game.getCurrentEvent().name());
        event.addProperty("display", game.getCurrentEvent().getDisplayName());
        root.add("event", event);

        // All stock prices
        JsonObject prices = new JsonObject();
        for (Map.Entry<StockType, StockMarketGame.StockPrice> entry : game.getCurrentPrices().entrySet()) {
            JsonObject priceData = new JsonObject();
            priceData.addProperty("ticker", entry.getKey().getTicker());
            priceData.addProperty("name", entry.getKey().getDisplayName());
            priceData.addProperty("tier", entry.getKey().getTier().name());
            priceData.addProperty("price", entry.getValue().price);
            priceData.addProperty("change", entry.getValue().changePercent);
            prices.add(entry.getKey().getTicker(), priceData);
        }
        root.add("prices", prices);

        // Price history for each stock (24 hours)
        JsonObject history = new JsonObject();
        for (Map.Entry<StockType, List<Integer>> entry : game.getPriceHistory().entrySet()) {
            JsonArray histArray = new JsonArray();
            for (Integer price : entry.getValue()) {
                histArray.add(price);
            }
            history.add(entry.getKey().getTicker(), histArray);
        }
        root.add("history", history);

        // Player portfolio
        JsonObject portfolio = new JsonObject();
        for (StockMarketGame.StockHolding holding : game.getPortfolio(viewer.getUuid())) {
            JsonObject holdingData = new JsonObject();
            holdingData.addProperty("ticker", holding.stockType.getTicker());
            holdingData.addProperty("name", holding.stockType.getDisplayName());
            holdingData.addProperty("quantity", holding.quantity);
            holdingData.addProperty("avgCost", holding.avgCost);
            StockMarketGame.StockPrice currentPrice = game.getPrice(holding.stockType);
            holdingData.addProperty("currentValue", holding.getCurrentValue(currentPrice));
            holdingData.addProperty("profitLoss", holding.getProfitLoss(currentPrice));
            portfolio.add(holding.stockType.getTicker(), holdingData);
        }
        root.add("portfolio", portfolio);

        // Pending orders
        JsonObject orders = new JsonObject();
        for (StockMarketGame.StockOrder order : game.getPendingOrders(viewer.getUuid())) {
            JsonObject orderData = new JsonObject();
            orderData.addProperty("ticker", order.stockType.getTicker());
            orderData.addProperty("type", order.orderType.name());
            orderData.addProperty("quantity", order.quantity);
            orderData.addProperty("limitPrice", order.limitPrice);
            orderData.addProperty("stopPrice", order.stopPrice);
            orders.add(order.stockType.getTicker() + "_" + System.identityHashCode(order), orderData);
        }
        root.add("orders", orders);

        return GSON.toJson(root);
    }

    /**
     * Broadcast state to all viewers of the block entity.
     */
    public static void broadcastState(StockExchangeBlockEntity be) {
        for (ServerPlayerEntity player : be.getViewers()) {
            String json = serializeState(be.getGame(), player,
                    be.getWorld() != null ? be.getWorld().getTime() : 0);
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(
                    player, new StockStatePayload(be.getPos(), json));
        }
    }

    /**
     * Send state to a specific player.
     */
    public static void sendState(StockExchangeBlockEntity be, ServerPlayerEntity player) {
        String json = serializeState(be.getGame(), player,
                be.getWorld() != null ? be.getWorld().getTime() : 0);
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(
                player, new StockStatePayload(be.getPos(), json));
    }

    /**
     * Handle player action from client.
     */
    public static void handleAction(ServerPlayerEntity player, StockActionPayload payload) {
        if (!(player.getEntityWorld().getBlockEntity(payload.pos()) instanceof StockExchangeBlockEntity be)) {
            return;
        }

        StockType stockType = StockType.byTicker(payload.ticker());
        if (stockType == null) {
            player.sendMessage(net.minecraft.text.Text.literal("§cUnknown stock: " + payload.ticker()), false);
            return;
        }

        StockMarketGame game = be.getGame();
        String result = switch (payload.action()) {
            case "BUY" -> game.buyStock(player, stockType, payload.quantity());
            case "SELL" -> game.sellStock(player, stockType, payload.quantity());
            case "LIMIT_BUY" -> game.placeLimitOrder(player, stockType,
                    StockMarketGame.OrderType.LIMIT_BUY, payload.quantity(), payload.limitPrice());
            case "LIMIT_SELL" -> game.placeLimitOrder(player, stockType,
                    StockMarketGame.OrderType.LIMIT_SELL, payload.quantity(), payload.limitPrice());
            case "CANCEL" -> game.cancelOrders(player);
            default -> "Unknown action: " + payload.action();
        };

        player.sendMessage(net.minecraft.text.Text.literal("§6[Stock] §f" + result), false);
        sendState(be, player);
    }
}
