package com.pokermc.taixiu.screen;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

/**
 * Tai Xiu My Bets Screen - Shows player's bet history (win/loss records).
 */
public class TaiXiuMyBetsScreen extends Screen {

    // Colors
    private static final int BG_DARK = 0xFF1A1A2E;
    private static final int BORDER_GOLD = 0xFFD4AF37;
    private static final int COLOR_GREEN = 0xFF00C853;
    private static final int COLOR_RED = 0xFFFF1744;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_TEXT_DIM = 0xFFAAAAAA;

    private final net.minecraft.util.math.BlockPos pos;
    private final String stateJson;
    private List<PlayerBetResultItem> betHistory = new ArrayList<>();
    private int totalBet = 0;
    private int totalWin = 0;

    // Layout
    private int guiX, guiY;
    private static final int GUI_W = 280;
    private static final int GUI_H = 220;

    // Help button bounds for tooltip
    private int helpBtnX, helpBtnY, helpBtnW, helpBtnH;

    public TaiXiuMyBetsScreen(net.minecraft.util.math.BlockPos pos, String initialStateJson) {
        super(Text.literal("My Bets"));
        this.pos = pos;
        this.stateJson = initialStateJson;
        parseState(initialStateJson);
    }

    public net.minecraft.util.math.BlockPos getPos() { return pos; }

    @Override
    protected void init() {
        super.init();

        guiX = (width - GUI_W) / 2;
        guiY = (height - GUI_H) / 2;

        // Tab buttons
        addDrawableChild(ButtonWidget.builder(
                Text.literal("TABLE").formatted(Formatting.GOLD),
                btn -> client.setScreen(new TaiXiuTableScreen(pos, stateJson))
        ).dimensions(guiX + 5, guiY + 5, 45, 18).build());

        addDrawableChild(ButtonWidget.builder(
                Text.literal("HISTORY").formatted(Formatting.AQUA),
                btn -> client.setScreen(new TaiXiuHistoryScreen(pos, stateJson))
        ).dimensions(guiX + 55, guiY + 5, 55, 18).build());

        addDrawableChild(ButtonWidget.builder(
                Text.literal("MY BETS").formatted(Formatting.LIGHT_PURPLE),
                btn -> {} // Already on MY BETS
        ).dimensions(guiX + 115, guiY + 5, 65, 18).build());

        // Help button (?) for payout info
        addDrawableChild(ButtonWidget.builder(
                Text.literal("?").formatted(Formatting.YELLOW),
                btn -> {} // Tooltip shown on hover
        ).dimensions(guiX + 220, guiY + 5, 20, 18).build());
        helpBtnX = guiX + 220;
        helpBtnY = guiY + 5;
        helpBtnW = 20;
        helpBtnH = 18;

        // Close button
        addDrawableChild(ButtonWidget.builder(
                Text.literal("✕").formatted(Formatting.RED),
                btn -> close()
        ).dimensions(guiX + 245, guiY + 5, 25, 18).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xA0000000);

        // Main background
        context.fill(guiX, guiY, guiX + GUI_W, guiY + GUI_H, BG_DARK);
        drawBorder(context, guiX, guiY, GUI_W, GUI_H, BORDER_GOLD, 2);

        // Title and summary
        context.drawText(textRenderer, "My Bet History", guiX + 10, guiY + 30, COLOR_TEXT, false);

        int profit = totalWin - totalBet;
        String profitText = "Total Bet: " + totalBet + " ZC | Won: " + totalWin + " ZC | Profit: " + (profit >= 0 ? "+" : "") + profit + " ZC";
        int profitColor = profit >= 0 ? COLOR_GREEN : COLOR_RED;
        context.drawText(textRenderer, profitText, guiX + 10, guiY + 45, profitColor, false);

        // Draw bet history entries
        int y = guiY + 65;
        if (betHistory.isEmpty()) {
            context.drawText(textRenderer, "No bets placed yet.", guiX + 10, y, COLOR_TEXT_DIM, false);
        } else {
            // Header
            context.drawText(textRenderer, "Bet", guiX + 10, y, COLOR_TEXT_DIM, false);
            context.drawText(textRenderer, "Result", guiX + 120, y, COLOR_TEXT_DIM, false);
            y += 15;

            // Entries (show last 15)
            int start = Math.max(0, betHistory.size() - 15);
            for (int i = start; i < betHistory.size() && y < guiY + GUI_H - 20; i++) {
                PlayerBetResultItem item = betHistory.get(i);

                // Bet info
                String betText = item.betDisplay;
                context.drawText(textRenderer, betText, guiX + 10, y, COLOR_TEXT, false);

                // Result
                String resultText;
                int resultColor;
                if (item.won) {
                    resultText = "+" + item.winAmount + " ZC";
                    resultColor = COLOR_GREEN;
                } else {
                    resultText = "-" + item.amount + " ZC";
                    resultColor = COLOR_RED;
                }
                context.drawText(textRenderer, resultText, guiX + 120, y, resultColor, false);

                y += 14;
            }
        }

        // Draw tooltip if hovering over help button
        if (mouseX >= helpBtnX && mouseX <= helpBtnX + helpBtnW &&
            mouseY >= helpBtnY && mouseY <= helpBtnY + helpBtnH) {
            drawPayoutTooltip(context, mouseX, mouseY);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private void drawPayoutTooltip(DrawContext context, int mouseX, int mouseY) {
        String[] payouts = {
            "TAI/XIU: 1:1",
            "ODD/EVEN: 1:1",
            "PAIR: 8:1",
            "TRIPLE: 150:1",
            "NUMBER (1-6): 1:1 per die"
        };

        int tooltipW = 150;
        int tooltipH = payouts.length * 12 + 10;
        int tooltipX = mouseX - tooltipW - 5;
        int tooltipY = mouseY + 10;

        // Adjust if off screen
        if (tooltipX < 0) tooltipX = mouseX + 15;
        if (tooltipY + tooltipH > height) tooltipY = mouseY - tooltipH - 5;

        // Tooltip background
        context.fill(tooltipX, tooltipY, tooltipX + tooltipW, tooltipY + tooltipH, 0xD0000000);
        drawBorder(context, tooltipX, tooltipY, tooltipW, tooltipH, BORDER_GOLD, 1);

        // Payout text
        int y = tooltipY + 6;
        for (String payout : payouts) {
            context.drawText(textRenderer, payout, tooltipX + 8, y, COLOR_TEXT, false);
            y += 12;
        }
    }

    private void drawBorder(DrawContext ctx, int x, int y, int w, int h, int color, int thickness) {
        ctx.fill(x, y, x + w, y + thickness, color);
        ctx.fill(x, y + h - thickness, x + w, y + h, color);
        ctx.fill(x, y, x + thickness, y + h, color);
        ctx.fill(x + w - thickness, y, x + w, y + h, color);
    }

    private void parseState(String json) {
        try {
            JsonObject state = new Gson().fromJson(json, JsonObject.class);

            totalBet = 0;
            totalWin = 0;

            if (state.has("playerBetHistory")) {
                JsonArray betHistArray = state.getAsJsonArray("playerBetHistory");
                betHistory.clear();
                for (JsonElement e : betHistArray) {
                    JsonObject r = e.getAsJsonObject();
                    PlayerBetResultItem item = new PlayerBetResultItem(
                            r.get("betDisplay").getAsString(),
                            r.get("amount").getAsInt(),
                            r.get("won").getAsBoolean(),
                            r.get("winAmount").getAsInt()
                    );
                    betHistory.add(item);
                    totalBet += item.amount;
                    if (item.won) {
                        totalWin += item.winAmount;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[Tai Xiu My Bets] Error parsing state: " + e.getMessage());
        }
    }

    private static class PlayerBetResultItem {
        final String betDisplay;
        final int amount;
        final boolean won;
        final int winAmount;

        PlayerBetResultItem(String betDisplay, int amount, boolean won, int winAmount) {
            this.betDisplay = betDisplay;
            this.amount = amount;
            this.won = won;
            this.winAmount = winAmount;
        }
    }
}
