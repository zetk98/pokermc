package com.pokermc.stock.screen;

import com.google.gson.JsonObject;
import com.pokermc.stock.game.StockType;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Stock Trade Screen - Popup for BUY/SELL operations.
 */
public class StockTradeScreen extends Screen {

    private final net.minecraft.util.math.BlockPos pos;
    private final StockType stockType;
    private final int currentPrice;
    private final int playerBalance;
    private final int ownedQuantity;
    private final boolean isBuyMode;
    private final Screen parentScreen;

    private int quantity = 1;
    private int limitPrice = 0;
    private boolean isLimitOrder = false;  // Default: market order

    private TextFieldWidget quantityInput;
    private TextFieldWidget limitPriceInput;

    // Colors
    private static final int BG_DARK = 0xFF1A1A2E;
    private static final int BORDER_GOLD = 0xFFD4AF37;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_TEXT_DIM = 0xFFAAAAAA;
    private static final int COLOR_GREEN = 0xFF00C853;
    private static final int COLOR_RED = 0xFFFF1744;
    private static final int COLOR_AQUA = 0xFF00FFFF;

    // Layout
    private int guiX, guiY;
    private static final int GUI_W = 300;
    private int guiH = 180;  // Will be calculated based on isLimitOrder

    public StockTradeScreen(Screen parent, net.minecraft.util.math.BlockPos pos, StockType type,
                            int price, int balance, int owned, boolean isBuyMode) {
        super(Text.literal(isBuyMode ? "Buy " + type.getTicker() : "Sell " + type.getTicker()));
        this.parentScreen = parent;
        this.pos = pos;
        this.stockType = type;
        this.currentPrice = price;
        this.playerBalance = balance;
        this.ownedQuantity = owned;
        this.isBuyMode = isBuyMode;
        this.limitPrice = price;  // Default limit price = current price
    }

    @Override
    protected void init() {
        super.init();

        guiH = isLimitOrder ? 240 : 200;
        guiX = (width - GUI_W) / 2;
        guiY = (height - guiH) / 2;

        // Title
        String title = isBuyMode ? "BUY " + stockType.getTicker() : "SELL " + stockType.getTicker();
        int titleClr = isBuyMode ? COLOR_GREEN : COLOR_RED;

        // Order type toggle button
        String orderTypeText = isLimitOrder ? "LIMIT ORDER" : "MARKET ORDER";
        addDrawableChild(ButtonWidget.builder(
                Text.literal(orderTypeText).formatted(Formatting.AQUA),
                btn -> {
                    isLimitOrder = !isLimitOrder;
                    clearChildren();
                    init();
                }
        ).dimensions(guiX + 80, guiY + 30, 140, 18).build());

        // Quantity input
        quantityInput = new TextFieldWidget(textRenderer, guiX + 120, guiY + 80, 60, 18, Text.literal("Qty"));
        quantityInput.setText(String.valueOf(quantity));
        quantityInput.setChangedListener(text -> {
            try {
                quantity = Math.max(1, Integer.parseInt(text));
            } catch (NumberFormatException ignored) {}
        });
        quantityInput.setFocused(true);
        addDrawableChild(quantityInput);

        // Limit price input (only for limit orders)
        if (isLimitOrder) {
            limitPriceInput = new TextFieldWidget(textRenderer, guiX + 120, guiY + 105, 60, 18, Text.literal("Limit"));
            limitPriceInput.setText(String.valueOf(limitPrice));
            limitPriceInput.setChangedListener(text -> {
                try {
                    limitPrice = Math.max(1, Integer.parseInt(text));
                } catch (NumberFormatException ignored) {}
            });
            addDrawableChild(limitPriceInput);
        }

        // MAX button
        addDrawableChild(ButtonWidget.builder(
                Text.literal("MAX").formatted(Formatting.GOLD),
                btn -> {
                    int max = isBuyMode ? (currentPrice > 0 ? playerBalance / currentPrice : 0) : ownedQuantity;
                    quantity = Math.max(1, max);
                    quantityInput.setText(String.valueOf(quantity));
                }
        ).dimensions(guiX + 190, guiY + 80, 35, 18).build());

        // CONFIRM button
        String btnText = isBuyMode ? "BUY" : "SELL";
        Formatting btnClr = isBuyMode ? Formatting.GREEN : Formatting.RED;
        int btnY = isLimitOrder ? 180 : 160;

        addDrawableChild(ButtonWidget.builder(
                Text.literal(btnText).formatted(btnClr),
                btn -> {
                    if (quantity > 0) {
                        sendTradeAction();
                    }
                }
        ).dimensions(guiX + 90, btnY, 100, 24).build());

        // CANCEL button
        addDrawableChild(ButtonWidget.builder(
                Text.literal("CANCEL").formatted(Formatting.GRAY),
                btn -> close()
        ).dimensions(guiX + 200, btnY, 60, 24).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        guiH = isLimitOrder ? 240 : 200;
        guiY = (height - guiH) / 2;

        // Darken background
        context.fill(0, 0, width, height, 0xB0000000);

        // Main background
        context.fill(guiX, guiY, guiX + GUI_W, guiY + guiH, BG_DARK);

        // Border
        drawBorder(context, guiX, guiY, GUI_W, guiH, BORDER_GOLD, 2);

        // Title
        String title = isBuyMode ? "BUY " + stockType.getTicker() : "SELL " + stockType.getTicker();
        int titleClr = isBuyMode ? COLOR_GREEN : COLOR_RED;
        context.drawText(textRenderer, title, guiX + 15, guiY + 10, titleClr, false);

        // Stock name
        String name = stockType.getDisplayName();
        context.drawText(textRenderer, name, guiX + GUI_W - 15 - textRenderer.getWidth(name), guiY + 10, COLOR_TEXT_DIM, false);

        // Current price display
        context.drawText(textRenderer, "Current Price:", guiX + 15, guiY + 55, COLOR_TEXT, false);
        context.drawText(textRenderer, currentPrice + " ZC", guiX + 210, guiY + 55, COLOR_TEXT, false);

        // Quantity label
        context.drawText(textRenderer, "Quantity:", guiX + 15, guiY + 82, COLOR_TEXT, false);

        // Limit price label (only for limit orders)
        if (isLimitOrder) {
            context.drawText(textRenderer, "Limit Price:", guiX + 15, guiY + 107, COLOR_TEXT, false);
        }

        // Total calculation with 1% fee
        int subtotal = (isLimitOrder && limitPrice > 0 ? limitPrice : currentPrice) * quantity;
        int fee = Math.max(1, subtotal / 100);  // 1% fee
        int total = isBuyMode ? subtotal + fee : subtotal - fee;
        int totalY = isLimitOrder ? 132 : 107;
        context.drawText(textRenderer, "Subtotal:", guiX + 15, guiY + totalY, COLOR_TEXT_DIM, false);
        context.drawText(textRenderer, subtotal + " ZC", guiX + 210, guiY + totalY, COLOR_TEXT_DIM, false);
        context.drawText(textRenderer, "Fee (1%):", guiX + 15, guiY + totalY + 12, COLOR_TEXT_DIM, false);
        context.drawText(textRenderer, fee + " ZC", guiX + 210, guiY + totalY + 12, COLOR_TEXT_DIM, false);
        context.drawText(textRenderer, "Total:", guiX + 15, guiY + totalY + 24, COLOR_TEXT, false);
        context.drawText(textRenderer, total + " ZC", guiX + 210, guiY + totalY + 24, COLOR_TEXT, false);

        // Balance / Owned info
        if (isBuyMode) {
            context.drawText(textRenderer, "Your Balance:", guiX + 15, guiY + 68, COLOR_TEXT_DIM, false);
            context.drawText(textRenderer, playerBalance + " ZC", guiX + 210, guiY + 68, COLOR_TEXT_DIM, false);
        } else {
            context.drawText(textRenderer, "You Own:", guiX + 15, guiY + 68, COLOR_TEXT_DIM, false);
            context.drawText(textRenderer, ownedQuantity + " shares", guiX + 210, guiY + 68, COLOR_TEXT_DIM, false);
        }

        // Warning if insufficient funds/shares
        int warningY = isLimitOrder ? 165 : 140;
        if (isBuyMode && total > playerBalance) {
            context.drawText(textRenderer, "Insufficient funds!", guiX + 110, guiY + warningY, COLOR_RED, false);
        } else if (!isBuyMode && quantity > ownedQuantity) {
            context.drawText(textRenderer, "Not enough shares!", guiX + 110, guiY + warningY, COLOR_RED, false);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private void drawBorder(DrawContext ctx, int x, int y, int w, int h, int color, int thickness) {
        ctx.fill(x, y, x + w, y + thickness, color);
        ctx.fill(x, y + h - thickness, x + w, y + h, color);
        ctx.fill(x, y, x + thickness, y + h, color);
        ctx.fill(x + w - thickness, y, x + w, y + h, color);
    }

    private void sendTradeAction() {
        String action;
        if (isLimitOrder) {
            action = isBuyMode ? "LIMIT_BUY" : "LIMIT_SELL";
        } else {
            action = isBuyMode ? "BUY" : "SELL";
        }

        com.pokermc.stock.network.StockNetworking.StockActionPayload payload =
                new com.pokermc.stock.network.StockNetworking.StockActionPayload(pos, action, stockType.getTicker(), quantity, limitPrice);
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(payload);
        close();
    }

    @Override
    public void close() {
        client.setScreen(parentScreen);
    }
}
