package com.pokermc.stock.screen;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.pokermc.PokerMod;
import com.pokermc.stock.game.StockMarketGame;
import com.pokermc.stock.game.StockType;
import com.pokermc.stock.network.StockNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Stock Exchange GUI Screen.
 * Displays 15 stocks with buy/sell functionality.
 */
public class StockExchangeScreen extends Screen {

    private static final Identifier BACKGROUND_TEXTURE =
            Identifier.of(PokerMod.MOD_ID, "textures/gui/stock_exchange.png");

    private final net.minecraft.util.math.BlockPos pos;
    private JsonObject stateJson;

    public net.minecraft.util.math.BlockPos getPos() { return pos; }
    private Map<String, StockData> stocks = new HashMap<>();
    private List<HoldingData> portfolio = new ArrayList<>();
    private String statusMessage = "Loading...";
    private String selectedTicker = null;

    public StockExchangeScreen(net.minecraft.util.math.BlockPos pos, String initialStateJson) {
        super(Text.literal("Stock Exchange"));
        this.pos = pos;
        updateState(initialStateJson);
    }

    @Override
    protected void init() {
        super.init();

        int x = (width - 400) / 2;
        int y = (height - 220) / 2;

        // Title
        addDrawableChild(new TextWidget(
                x + 10, y + 10,
                200, 20,
                Text.literal("📊 MINECRAFT STOCK EXCHANGE").formatted(Formatting.GOLD, Formatting.BOLD),
                textRenderer
        ));

        // Status message
        addDrawableChild(new TextWidget(
                x + 10, y + 35,
                380, 20,
                Text.literal(statusMessage).formatted(Formatting.YELLOW),
                textRenderer
        ));

        // Stock list buttons
        int listY = y + 60;
        for (StockType type : StockType.values()) {
            int rowY = listY + (type.ordinal() % 15) * 12;
            if (rowY > y + 200) break;

            int colX = x + 10 + (type.ordinal() / 15) * 200;

            final StockType stockType = type;
            ButtonWidget stockButton = ButtonWidget.builder(
                    Text.literal(type.getTicker() + " - " + type.getDisplayName())
                            .formatted(stockType.getColor()),
                    btn -> {
                        selectedTicker = stockType.getTicker();
                        refreshPortfolio();
                    }
            ).dimensions(colX, rowY, 180, 10).build();

            addDrawableChild(stockButton);
        }

        // Action buttons (shown when stock selected)
        int actionY = y + 250;
        ButtonWidget buyButton = ButtonWidget.builder(
                Text.literal("BUY").formatted(Formatting.GREEN),
                btn -> sendBuyOrder()
        ).dimensions(x + 10, actionY, 80, 20).build();
        addDrawableChild(buyButton);

        ButtonWidget sellButton = ButtonWidget.builder(
                Text.literal("SELL").formatted(Formatting.RED),
                btn -> sendSellOrder()
        ).dimensions(x + 100, actionY, 80, 20).build();
        addDrawableChild(sellButton);

        ButtonWidget limitBuyButton = ButtonWidget.builder(
                Text.literal("LIMIT BUY").formatted(Formatting.BLUE),
                btn -> sendLimitBuyOrder()
        ).dimensions(x + 190, actionY, 90, 20).build();
        addDrawableChild(limitBuyButton);

        ButtonWidget limitSellButton = ButtonWidget.builder(
                Text.literal("LIMIT SELL").formatted(Formatting.BLUE),
                btn -> sendLimitSellOrder()
        ).dimensions(x + 290, actionY, 90, 20).build();
        addDrawableChild(limitSellButton);

        // Portfolio summary
        addDrawableChild(new TextWidget(
                x + 220, y + 35,
                180, 20,
                Text.literal("Portfolio:").formatted(Formatting.AQUA),
                textRenderer
        ));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Draw semi-transparent dark background
        context.fill(0, 0, width, height, 0xA0000000);
        super.render(context, mouseX, mouseY, delta);

        int x = (width - 400) / 2;
        int y = (height - 220) / 2;

        // Draw stock prices
        int listY = y + 60;
        for (StockType type : StockType.values()) {
            int rowY = listY + (type.ordinal() % 15) * 12;
            if (rowY > y + 200) break;

            int colX = x + 10 + (type.ordinal() / 15) * 200;
            StockData data = stocks.get(type.getTicker());

            if (data != null) {
                // Price
                Formatting priceColor = data.change >= 0 ? Formatting.GREEN : Formatting.RED;
                String priceText = data.price + " ZC";
                if (data.change > 0) priceText = "▲ +" + data.change + "%";
                else if (data.change < 0) priceText = "▼ " + data.change + "%";

                context.drawText(textRenderer,
                        Text.literal(priceText).formatted(priceColor),
                        colX + 120, rowY, 0xFFFFFF, false);
            }
        }

        // Draw portfolio
        int portY = y + 280;
        int portX = x + 220;
        context.drawText(textRenderer,
                Text.literal("Your Holdings:").formatted(Formatting.AQUA),
                portX, portY + 25, 0xFFFFFF, false);

        int yOffset = 40;
        for (HoldingData holding : portfolio) {
            context.drawText(textRenderer,
                    Text.literal(holding.ticker + ": " + holding.quantity + " shares")
                            .formatted(Formatting.WHITE),
                    portX, portY + yOffset, 0xFFFFFF, false);
            context.drawText(textRenderer,
                    Text.literal("  Value: " + holding.value + " ZC (P/L: " +
                            (holding.profit >= 0 ? "+" : "") + holding.profit + ")")
                            .formatted(holding.profit >= 0 ? Formatting.GREEN : Formatting.RED),
                    portX, portY + yOffset + 10, 0xFFFFFF, false);
            yOffset += 22;
        }
    }

    @Override
    public boolean shouldBlur() {
        // Disable default blur since we draw our own semi-transparent background
        return false;
    }

    @Override
    public void close() {
        // Send close packet if needed
        super.close();
    }

    /**
     * Update state from JSON received from server.
     */
    public void updateState(String json) {
        try {
            stateJson = new Gson().fromJson(json, JsonObject.class);

            statusMessage = stateJson.get("status").getAsString();

            // Parse stocks
            JsonObject prices = stateJson.getAsJsonObject("prices");
            stocks.clear();
            for (StockType type : StockType.values()) {
                if (prices.has(type.getTicker())) {
                    JsonObject priceData = prices.getAsJsonObject(type.getTicker());
                    stocks.put(type.getTicker(), new StockData(
                            type.getTicker(),
                            priceData.get("price").getAsInt(),
                            priceData.get("change").getAsInt()
                    ));
                }
            }

            // Parse portfolio
            JsonObject port = stateJson.getAsJsonObject("portfolio");
            portfolio.clear();
            for (String ticker : port.keySet()) {
                JsonObject holdingData = port.getAsJsonObject(ticker);
                portfolio.add(new HoldingData(
                        ticker,
                        holdingData.get("quantity").getAsInt(),
                        holdingData.get("currentValue").getAsInt(),
                        holdingData.get("profitLoss").getAsInt()
                ));
            }
        } catch (Exception e) {
            System.err.println("[Stock Exchange] Error parsing state: " + e.getMessage());
        }
    }

    private void refreshPortfolio() {
        // Portfolio updated in updateState()
    }

    private void sendBuyOrder() {
        if (selectedTicker == null) return;
        StockData data = stocks.get(selectedTicker);
        if (data == null) return;

        // Send buy order packet (10 shares default)
        sendAction("BUY", selectedTicker, 10, 0);
    }

    private void sendSellOrder() {
        if (selectedTicker == null) return;
        // Send sell order packet (10 shares default)
        sendAction("SELL", selectedTicker, 10, 0);
    }

    private void sendLimitBuyOrder() {
        if (selectedTicker == null) return;
        StockData data = stocks.get(selectedTicker);
        if (data == null) return;

        // Send limit buy at 5% below current price
        int limitPrice = (int)(data.price * 0.95);
        sendAction("LIMIT_BUY", selectedTicker, 10, limitPrice);
    }

    private void sendLimitSellOrder() {
        if (selectedTicker == null) return;
        StockData data = stocks.get(selectedTicker);
        if (data == null) return;

        // Send limit sell at 5% above current price
        int limitPrice = (int)(data.price * 1.05);
        sendAction("LIMIT_SELL", selectedTicker, 10, limitPrice);
    }

    private void sendAction(String action, String ticker, int quantity, int limitPrice) {
        com.pokermc.stock.network.StockNetworking.StockActionPayload payload =
                new com.pokermc.stock.network.StockNetworking.StockActionPayload(pos, action, ticker, quantity, limitPrice);
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(payload);
    }

    private static class StockData {
        final String ticker;
        final int price;
        final int change;

        StockData(String ticker, int price, int change) {
            this.ticker = ticker;
            this.price = price;
            this.change = change;
        }
    }

    private static class HoldingData {
        final String ticker;
        final int quantity;
        final int value;
        final int profit;

        HoldingData(String ticker, int quantity, int value, int profit) {
            this.ticker = ticker;
            this.quantity = quantity;
            this.value = value;
            this.profit = profit;
        }
    }
}
