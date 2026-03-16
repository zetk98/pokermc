package com.pokermc.stock.screen;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.pokermc.PokerMod;
import com.pokermc.stock.game.StockType;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Stock Exchange GUI - Clean, professional design.
 */
public class StockExchangeScreen extends Screen {

    private static final Identifier BACKGROUND_TEXTURE =
            Identifier.of(PokerMod.MOD_ID, "textures/gui/stock_exchange.png");

    // Colors
    private static final int BG_DARK = 0xFF1A1A2E;
    private static final int BG_CARD = 0xFF16213E;
    private static final int BORDER_GOLD = 0xFFD4AF37;
    private static final int COLOR_UP = 0xFF00C853;
    private static final int COLOR_DOWN = 0xFFFF1744;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_TEXT_DIM = 0xFFAAAAAA;

    private final net.minecraft.util.math.BlockPos pos;
    private JsonObject stateJson;

    public net.minecraft.util.math.BlockPos getPos() { return pos; }
    private Map<String, StockData> stocks = new HashMap<>();
    private Map<String, List<Integer>> priceHistory = new HashMap<>();
    private List<HoldingData> portfolio = new ArrayList<>();
    private String selectedTicker = null;
    private int playerBalance = 0;

    // Panel system
    private static final int PANEL_MARKET = 0;
    private static final int PANEL_DETAIL = 1;
    private static final int PANEL_PORTFOLIO = 2;
    private int currentPanel = PANEL_MARKET;

    // Pagination
    private int marketPage = 0;
    private int portfolioPage = 0;
    private static final int STOCKS_PER_PAGE = 6;
    private static final int PORTFOLIO_PER_PAGE = 8;

    // Layout
    private int guiX, guiY;
    private static final int GUI_W = 380;
    private static final int GUI_H = 220;
    private static final int PANEL_Y = 35;
    private static final int PANEL_H = 175;

    public StockExchangeScreen(net.minecraft.util.math.BlockPos pos, String initialStateJson) {
        super(Text.literal("Stock Exchange"));
        this.pos = pos;
        updateState(initialStateJson);
    }

    @Override
    protected void init() {
        super.init();

        guiX = (width - GUI_W) / 2;
        guiY = (height - GUI_H) / 2;

        // Close button
        addDrawableChild(ButtonWidget.builder(
                Text.literal("✕").formatted(Formatting.RED),
                btn -> close()
        ).dimensions(guiX + 345, guiY + 5, 25, 18).build());

        // Tab buttons
        addDrawableChild(ButtonWidget.builder(
                Text.literal("MARKET").formatted(currentPanel == PANEL_MARKET ? Formatting.GOLD : Formatting.GRAY),
                btn -> switchPanel(PANEL_MARKET)
        ).dimensions(guiX + 10, guiY + 5, 60, 18).build());

        addDrawableChild(ButtonWidget.builder(
                Text.literal("DETAIL").formatted(currentPanel == PANEL_DETAIL ? Formatting.GOLD : Formatting.GRAY),
                btn -> switchPanel(PANEL_DETAIL)
        ).dimensions(guiX + 80, guiY + 5, 60, 18).build());

        addDrawableChild(ButtonWidget.builder(
                Text.literal("PORTFOLIO").formatted(currentPanel == PANEL_PORTFOLIO ? Formatting.GOLD : Formatting.GRAY),
                btn -> switchPanel(PANEL_PORTFOLIO)
        ).dimensions(guiX + 150, guiY + 5, 80, 18).build());

        // Quantity input (only added in detail panel via buildPanelContent)
        buildPanelContent();
    }

    private void switchPanel(int panel) {
        currentPanel = panel;
        clearChildren();
        init();
    }

    private void buildPanelContent() {
        int y = guiY + PANEL_Y;
        switch (currentPanel) {
            case PANEL_MARKET -> buildMarketPanel(y);
            case PANEL_DETAIL -> buildDetailPanel(y);
            case PANEL_PORTFOLIO -> buildPortfolioPanel(y);
        }
    }

    // ============================================================
    // MARKET PANEL - Mini cards with sparkline
    // ============================================================
    private void buildMarketPanel(int y) {
        StockType[] all = StockType.values();
        int pages = (int) Math.ceil((double) all.length / STOCKS_PER_PAGE);
        marketPage = Math.max(0, Math.min(marketPage, pages - 1));
        int start = marketPage * STOCKS_PER_PAGE;

        int cardW = 115, cardH = 70, gap = 6;

        for (int i = 0; i < STOCKS_PER_PAGE; i++) {
            int idx = start + i;
            if (idx >= all.length) break;

            StockType type = all[idx];

            int row = i / 3, col = i % 3;
            int cardX = guiX + 8 + col * (cardW + gap);
            int cardY = y + 5 + row * (cardH + gap);

            final StockType st = type;

            // VIEW button (bottom right, just triangle arrow)
            addDrawableChild(ButtonWidget.builder(
                    Text.literal("►").formatted(Formatting.AQUA),
                    btn -> { selectedTicker = st.getTicker(); switchPanel(PANEL_DETAIL); }
            ).dimensions(cardX + cardW - 16, cardY + cardH - 14, 14, 12).build());
        }

        // Page navigation
        int navY = y + PANEL_H - 20;

        addDrawableChild(ButtonWidget.builder(
                Text.literal("◄").formatted(marketPage > 0 ? Formatting.WHITE : Formatting.DARK_GRAY),
                btn -> { if (marketPage > 0) { marketPage--; switchPanel(PANEL_MARKET); } }
        ).dimensions(guiX + 130, navY, 30, 16).build());

        addDrawableChild(ButtonWidget.builder(
                Text.literal("►").formatted(marketPage < pages - 1 ? Formatting.WHITE : Formatting.DARK_GRAY),
                btn -> { if (marketPage < pages - 1) { marketPage++; switchPanel(PANEL_MARKET); } }
        ).dimensions(guiX + 220, navY, 30, 16).build());
    }

    // ============================================================
    // DETAIL PANEL - Clean layout
    // ============================================================
    private void buildDetailPanel(int y) {
        // Back button (top right) - always present
        addDrawableChild(ButtonWidget.builder(
                Text.literal("BACK").formatted(Formatting.YELLOW),
                btn -> switchPanel(PANEL_MARKET)
        ).dimensions(guiX + 300, y + 5, 60, 16).build());

        if (selectedTicker == null) {
            return;  // Will show "No result" in render
        }

        StockData data = stocks.get(selectedTicker);
        StockType type = StockType.byTicker(selectedTicker);
        if (type == null) {
            switchPanel(PANEL_MARKET);
            return;
        }

        // Get owned quantity
        int ownedQty = 0;
        for (HoldingData h : portfolio) {
            if (h.ticker.equals(selectedTicker)) {
                ownedQty = h.quantity;
                break;
            }
        }
        final int owned = ownedQty;
        final int price = (data != null && data.price > 0) ? data.price : 100;

        // BUY button - opens popup
        addDrawableChild(ButtonWidget.builder(
                Text.literal("BUY").formatted(Formatting.GREEN),
                btn -> {
                    client.setScreen(new StockTradeScreen(this, pos, type, price, playerBalance, owned, true));
                }
        ).dimensions(guiX + 60, y + 125, 100, 24).build());

        // SELL button - opens popup
        addDrawableChild(ButtonWidget.builder(
                Text.literal("SELL").formatted(Formatting.RED),
                btn -> {
                    client.setScreen(new StockTradeScreen(this, pos, type, price, playerBalance, owned, false));
                }
        ).dimensions(guiX + 180, y + 125, 100, 24).build());
    }

    // ============================================================
    // RENDER
    // ============================================================
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xA0000000);

        int panelY = guiY + PANEL_Y;

        // Main background
        context.fill(guiX, guiY, guiX + GUI_W, guiY + 30, BG_DARK);
        context.fill(guiX + 5, panelY, guiX + GUI_W - 5, panelY + PANEL_H - 5, BG_DARK);

        // Gold border
        drawBorder(context, guiX, guiY, GUI_W, GUI_H, BORDER_GOLD, 2);

        // Render panel content
        switch (currentPanel) {
            case PANEL_MARKET -> renderMarketPanel(context, panelY);
            case PANEL_DETAIL -> renderDetailPanel(context, panelY);
            case PANEL_PORTFOLIO -> renderPortfolioPanel(context, panelY);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private void renderMarketPanel(DrawContext context, int y) {
        StockType[] all = StockType.values();
        int start = marketPage * STOCKS_PER_PAGE;

        int cardW = 115, cardH = 70, gap = 6;

        for (int i = 0; i < STOCKS_PER_PAGE; i++) {
            int idx = start + i;
            if (idx >= all.length) break;

            StockType type = all[idx];
            StockData data = stocks.get(type.getTicker());

            int row = i / 3, col = i % 3;
            int cardX = guiX + 8 + col * (cardW + gap);
            int cardY = y + 5 + row * (cardH + gap);

            // Card background
            context.fill(cardX, cardY, cardX + cardW, cardY + cardH, BG_CARD);

            // Ticker (large, colored)
            int tickerClr = getTickerColor(type);
            context.drawText(textRenderer, type.getTicker(), cardX + 4, cardY + 3, tickerClr, false);

            if (data != null) {
                // Price
                int priceClr = data.change >= 0 ? COLOR_UP : COLOR_DOWN;
                context.drawText(textRenderer, data.price + " ZC", cardX + 4, cardY + 16, priceClr, false);

                // Change %
                String changeStr = (data.change > 0 ? "+" : "") + data.change + "%";
                context.drawText(textRenderer, changeStr, cardX + 4, cardY + 28, priceClr, false);

                // Mini sparkline
                List<Integer> hist = priceHistory.get(type.getTicker());
                if (hist != null && !hist.isEmpty()) {
                    drawSparkline(context, hist, cardX + 50, cardY + 16, 55, 28);
                }
            }
        }

        // Page indicator
        int pages = (int) Math.ceil((double) all.length / STOCKS_PER_PAGE);
        String pageTxt = (marketPage + 1) + "/" + pages;
        int pw = textRenderer.getWidth(pageTxt);
        context.drawText(textRenderer, pageTxt, guiX + GUI_W/2 - pw/2, y + PANEL_H - 16, COLOR_TEXT_DIM, false);
    }

    private void renderDetailPanel(DrawContext context, int y) {
        if (selectedTicker == null) {
            String noResult = "No result";
            int nw = textRenderer.getWidth(noResult);
            context.drawText(textRenderer, noResult, guiX + GUI_W/2 - nw/2, y + PANEL_H/2 - 10, COLOR_DOWN, false);
            return;
        }

        StockData data = stocks.get(selectedTicker);
        StockType type = StockType.byTicker(selectedTicker);
        if (type == null) return;

        // Header row: [Name] [Description] [Back is button]
        int tickerClr = getTickerColor(type);
        context.drawText(textRenderer, type.getTicker(), guiX + 15, y + 8, tickerClr, false);
        context.drawText(textRenderer, type.getDisplayName(), guiX + 70, y + 8, COLOR_TEXT_DIM, false);

        // Price row
        if (data != null) {
            int priceClr = data.change >= 0 ? COLOR_UP : COLOR_DOWN;
            context.drawText(textRenderer, data.price + " ZC", guiX + 15, y + 26, priceClr, false);

            String changeStr = (data.change > 0 ? "+" : "") + data.change + "%";
            int changeW = textRenderer.getWidth(changeStr);
            context.drawText(textRenderer, changeStr, guiX + 80, y + 26, priceClr, false);
        }

        // Chart area
        int chartX = guiX + 15;
        int chartY = y + 42;
        int chartW = 350;
        int chartH = 70;

        context.fill(chartX, chartY, chartX + chartW, chartY + chartH, 0xFF0D0D1A);
        drawBorder(context, chartX, chartY, chartW, chartH, 0xFF444444, 1);

        List<Integer> hist = priceHistory.get(selectedTicker);
        if (hist != null && !hist.isEmpty()) {
            int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
            for (int p : hist) {
                min = Math.min(min, p);
                max = Math.max(max, p);
            }
            int range = Math.max(1, max - min);

            int numBars = Math.min(10, hist.size());
            int barW = (chartW - 4) / 10;
            int startIdx = hist.size() - numBars;

            for (int i = 0; i < numBars; i++) {
                int p = hist.get(startIdx + i);
                int barH = Math.max(2, (int)((p - min) * (double)(chartH - 20) / range));
                int barX = chartX + 2 + i * barW;
                int barY = chartY + 3 + (chartH - 20 - barH);

                int barClr = 0xFF888888;
                if (i > 0) {
                    barClr = p >= hist.get(startIdx + i - 1) ? COLOR_UP : COLOR_DOWN;
                }

                context.fill(barX, barY, barX + barW - 1, barY + barH, barClr);

                // Price label under each bar
                String priceLabel = String.valueOf(p);
                int labelW = textRenderer.getWidth(priceLabel);
                context.drawText(textRenderer, priceLabel, barX + (barW - labelW) / 2, chartY + chartH - 12, COLOR_TEXT_DIM, false);
            }

            context.drawText(textRenderer, max + " ZC", chartX + 2, chartY + 1, COLOR_TEXT_DIM, false);
            context.drawText(textRenderer, min + " ZC", chartX + 2, chartY + chartH - 24, COLOR_TEXT_DIM, false);
        }
    }

    // ============================================================
    // PORTFOLIO PANEL - Show all owned stocks
    // ============================================================
    private void buildPortfolioPanel(int y) {
        int pages = (int) Math.ceil((double) portfolio.size() / PORTFOLIO_PER_PAGE);
        portfolioPage = Math.max(0, Math.min(portfolioPage, Math.max(1, pages) - 1));
        int start = portfolioPage * PORTFOLIO_PER_PAGE;

        // Display portfolio items
        int rowH = 38;
        int listY = y + 48;

        for (int i = 0; i < Math.min(PORTFOLIO_PER_PAGE, portfolio.size() - start); i++) {
            int idx = start + i;
            if (idx >= portfolio.size()) break;

            HoldingData h = portfolio.get(idx);
            int rowY = listY + i * rowH;

            final String ticker = h.ticker;
            final int qty = h.quantity;

            // VIEW button
            addDrawableChild(ButtonWidget.builder(
                    Text.literal("►").formatted(Formatting.AQUA),
                    btn -> { selectedTicker = ticker; switchPanel(PANEL_DETAIL); }
            ).dimensions(guiX + 300, rowY + 8, 20, 18).build());

            // SELL button
            addDrawableChild(ButtonWidget.builder(
                    Text.literal("SELL").formatted(Formatting.RED),
                    btn -> { selectedTicker = ticker; openSellPopup(ticker, qty); }
            ).dimensions(guiX + 330, rowY + 8, 40, 18).build());
        }

        // Page navigation
        int navY = y + PANEL_H - 25;

        addDrawableChild(ButtonWidget.builder(
                Text.literal("◄").formatted(portfolioPage > 0 ? Formatting.WHITE : Formatting.DARK_GRAY),
                btn -> { if (portfolioPage > 0) { portfolioPage--; switchPanel(PANEL_PORTFOLIO); } }
        ).dimensions(guiX + 130, navY, 30, 16).build());

        addDrawableChild(ButtonWidget.builder(
                Text.literal("►").formatted(portfolioPage < pages - 1 ? Formatting.WHITE : Formatting.DARK_GRAY),
                btn -> { if (portfolioPage < pages - 1) { portfolioPage++; switchPanel(PANEL_PORTFOLIO); } }
        ).dimensions(guiX + 220, navY, 30, 16).build());
    }

    private void renderPortfolioPanel(DrawContext context, int y) {
        // Title line
        context.drawText(textRenderer, "Your Portfolio", guiX + 15, y + 8, 0xFF00FFFF, false);

        // Calculate totals
        int totalVal = 0, totalCost = 0, totalPL = 0;
        for (HoldingData h : portfolio) {
            totalVal += h.value;
            totalCost += h.quantity * h.avgCost;
            totalPL += h.profit;
        }

        // Total Value line
        String totalValText = "Total Value: " + totalVal + " ZC";
        int tvW = textRenderer.getWidth(totalValText);
        context.drawText(textRenderer, totalValText, guiX + GUI_W/2 - tvW/2, y + 24, BORDER_GOLD, false);

        // P/L line
        int plClr = totalPL >= 0 ? COLOR_UP : COLOR_DOWN;
        String plText = "P/L: " + (totalPL >= 0 ? "+" : "") + totalPL + " ZC";
        int plW = textRenderer.getWidth(plText);
        context.drawText(textRenderer, plText, guiX + GUI_W/2 - plW/2, y + 38, plClr, false);

        // Portfolio list
        int rowH = 38;
        int listY = y + 48;
        int start = portfolioPage * PORTFOLIO_PER_PAGE;

        for (int i = 0; i < Math.min(PORTFOLIO_PER_PAGE, portfolio.size() - start); i++) {
            int idx = start + i;
            if (idx >= portfolio.size()) break;

            HoldingData h = portfolio.get(idx);
            int rowY = listY + i * rowH;

            // Row background
            context.fill(guiX + 8, rowY, guiX + GUI_W - 8, rowY + rowH - 2, BG_CARD);

            // Ticker
            StockType type = StockType.byTicker(h.ticker);
            int tickerClr = type != null ? getTickerColor(type) : COLOR_TEXT;
            context.drawText(textRenderer, h.ticker, guiX + 15, rowY + 6, tickerClr, false);

            // Quantity
            context.drawText(textRenderer, "Qty: " + h.quantity, guiX + 70, rowY + 6, COLOR_TEXT, false);

            // Avg cost
            context.drawText(textRenderer, "Avg: " + h.avgCost + " ZC", guiX + 70, rowY + 20, COLOR_TEXT_DIM, false);

            // Current value
            context.drawText(textRenderer, h.value + " ZC", guiX + 160, rowY + 6, COLOR_TEXT, false);

            // P/L
            int hPlClr = h.profit >= 0 ? COLOR_UP : COLOR_DOWN;
            String hPlText = (h.profit >= 0 ? "+" : "") + h.profit;
            context.drawText(textRenderer, hPlText, guiX + 160, rowY + 20, hPlClr, false);

            // P/L percent
            int plPercent = h.avgCost > 0 ? (h.profit * 100 / (h.quantity * h.avgCost)) : 0;
            String plPctText = (plPercent >= 0 ? "+" : "") + plPercent + "%";
            context.drawText(textRenderer, plPctText, guiX + 230, rowY + 13, hPlClr, false);
        }

        // Empty message
        if (portfolio.isEmpty()) {
            String empty = "No stocks owned";
            int ew = textRenderer.getWidth(empty);
            context.drawText(textRenderer, empty, guiX + GUI_W/2 - ew/2, y + 100, COLOR_TEXT_DIM, false);
        }

        // Page indicator
        int pages = (int) Math.ceil((double) portfolio.size() / PORTFOLIO_PER_PAGE);
        if (pages > 0) {
            String pageTxt = (portfolioPage + 1) + "/" + Math.max(1, pages);
            int pw = textRenderer.getWidth(pageTxt);
            context.drawText(textRenderer, pageTxt, guiX + GUI_W/2 - pw/2, y + PANEL_H - 20, COLOR_TEXT_DIM, false);
        }
    }

    private void openSellPopup(String ticker, int maxQty) {
        StockType type = StockType.byTicker(ticker);
        if (type == null) return;

        StockData data = stocks.get(ticker);
        int price = (data != null && data.price > 0) ? data.price : 100;

        client.setScreen(new StockTradeScreen(this, pos, type, price, playerBalance, maxQty, false));
    }

    // ============================================================
    // HELPERS
    // ============================================================

    private void drawSparkline(DrawContext ctx, List<Integer> hist, int x, int y, int w, int h) {
        int bars = Math.min(5, hist.size());
        if (bars == 0) return;

        int start = hist.size() - bars;

        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (int i = start; i < hist.size(); i++) {
            int p = hist.get(i);
            min = Math.min(min, p);
            max = Math.max(max, p);
        }
        int range = Math.max(1, max - min);

        int barW = w / bars;
        for (int i = 0; i < bars; i++) {
            int p = hist.get(start + i);
            int barH = Math.max(1, (int)((p - min) * (double)(h - 1) / range));
            int barX = x + i * barW;
            int barY = y + (h - barH);

            int clr = 0xFF888888;
            if (i > 0) {
                clr = p >= hist.get(start + i - 1) ? COLOR_UP : COLOR_DOWN;
            }

            ctx.fill(barX, barY, barX + barW - 1, barY + barH, clr);
        }
    }

    private void drawBorder(DrawContext ctx, int x, int y, int w, int h, int color, int thickness) {
        ctx.fill(x, y, x + w, y + thickness, color);
        ctx.fill(x, y + h - thickness, x + w, y + h, color);
        ctx.fill(x, y, x + thickness, y + h, color);
        ctx.fill(x + w - thickness, y, x + w, y + h, color);
    }

    private int getTickerColor(StockType type) {
        return switch (type.getColor()) {
            case RED -> 0xFFFF5555;
            case GOLD -> 0xFFD4AF37;
            case GREEN -> 0xFF55FF55;
            case AQUA -> 0xFF55FFFF;
            case DARK_PURPLE -> 0xFFAA00AA;
            case BLUE -> 0xFF5555FF;
            case DARK_RED -> 0xFFAA0000;
            case YELLOW -> 0xFFFFFF55;
            case GRAY -> 0xFFAAAAAA;
            default -> 0xFFFFFFFF;
        };
    }

    @Override
    public void close() {
        super.close();
    }

    public void updateState(String json) {
        try {
            stateJson = new Gson().fromJson(json, JsonObject.class);

            if (stateJson.has("playerBalance")) {
                playerBalance = stateJson.get("playerBalance").getAsInt();
            }

            JsonObject prices = stateJson.getAsJsonObject("prices");
            if (prices != null) {
                stocks.clear();
                for (StockType type : StockType.values()) {
                    if (prices.has(type.getTicker())) {
                        JsonObject pd = prices.getAsJsonObject(type.getTicker());
                        stocks.put(type.getTicker(), new StockData(
                                type.getTicker(),
                                pd.get("price").getAsInt(),
                                pd.get("change").getAsInt()
                        ));
                    }
                }
            }

            JsonObject histJson = stateJson.getAsJsonObject("history");
            if (histJson != null) {
                priceHistory.clear();
                for (String ticker : histJson.keySet()) {
                    JsonArray arr = histJson.getAsJsonArray(ticker);
                    List<Integer> hist = new ArrayList<>();
                    for (JsonElement e : arr) {
                        hist.add(e.getAsInt());
                    }
                    priceHistory.put(ticker, hist);
                }
            }

            JsonObject port = stateJson.getAsJsonObject("portfolio");
            if (port != null) {
                portfolio.clear();
                for (String ticker : port.keySet()) {
                    JsonObject hd = port.getAsJsonObject(ticker);
                    portfolio.add(new HoldingData(
                            ticker,
                            hd.get("quantity").getAsInt(),
                            hd.get("currentValue").getAsInt(),
                            hd.get("profitLoss").getAsInt(),
                            hd.get("avgCost").getAsInt()
                    ));
                }
            }
        } catch (Exception e) {
            System.err.println("[Stock Exchange] Error: " + e.getMessage());
        }
    }

    private void sendAction(String action, String ticker, int qty, int limitPrice) {
        com.pokermc.stock.network.StockNetworking.StockActionPayload payload =
                new com.pokermc.stock.network.StockNetworking.StockActionPayload(pos, action, ticker, qty, limitPrice);
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
        final int avgCost;
        HoldingData(String ticker, int quantity, int value, int profit, int avgCost) {
            this.ticker = ticker;
            this.quantity = quantity;
            this.value = value;
            this.profit = profit;
            this.avgCost = avgCost;
        }
    }
}
