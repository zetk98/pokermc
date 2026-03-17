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
 * Tai Xiu (Sic Bo) History Screen - Shows last 20 dice roll results in 2 columns.
 */
public class TaiXiuHistoryScreen extends Screen {

    // Colors
    private static final int BG_DARK = 0xFF1A1A2E;
    private static final int BORDER_GOLD = 0xFFD4AF37;
    private static final int COLOR_GREEN = 0xFF00C853;
    private static final int COLOR_RED = 0xFFFF1744;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_TEXT_DIM = 0xFFAAAAAA;
    private static final int COLOR_AQUA = 0xFF00FFFF;
    private static final int COLOR_ORANGE = 0xFFFF6600;

    private final net.minecraft.util.math.BlockPos pos;
    private JsonObject stateJson;
    private List<DiceResult> history = new ArrayList<>();

    // Layout - wider to accommodate 2 columns
    private int guiX, guiY;
    private static final int GUI_W = 350;
    private static final int GUI_H = 200;

    // Help tooltip bounds
    private int helpBtnX, helpBtnY, helpBtnW, helpBtnH;

    public TaiXiuHistoryScreen(net.minecraft.util.math.BlockPos pos, String initialStateJson) {
        super(Text.literal("Tai Xiu History"));
        this.pos = pos;
        updateState(initialStateJson);
    }

    @Override
    protected void init() {
        super.init();

        guiX = (width - GUI_W) / 2;
        guiY = (height - GUI_H) / 2;

        // Tab buttons (top area, no title)
        addDrawableChild(ButtonWidget.builder(
                Text.literal("TABLE").formatted(Formatting.GOLD),
                btn -> client.setScreen(new TaiXiuTableScreen(pos, stateJson != null ? stateJson.toString() : "{}"))
        ).dimensions(guiX + 5, guiY + 5, 45, 18).build());

        addDrawableChild(ButtonWidget.builder(
                Text.literal("HISTORY").formatted(Formatting.AQUA),
                btn -> {} // Already on history
        ).dimensions(guiX + 55, guiY + 5, 55, 18).build());

        addDrawableChild(ButtonWidget.builder(
                Text.literal("MY BETS").formatted(Formatting.LIGHT_PURPLE),
                btn -> client.setScreen(new TaiXiuMyBetsScreen(pos, stateJson != null ? stateJson.toString() : "{}"))
        ).dimensions(guiX + 115, guiY + 5, 65, 18).build());

        // Help button (?)
        addDrawableChild(ButtonWidget.builder(
                Text.literal("?").formatted(Formatting.YELLOW),
                btn -> {} // Tooltip shown on hover
        ).dimensions(guiX + 290, guiY + 5, 20, 18).build());
        helpBtnX = guiX + 290;
        helpBtnY = guiY + 5;
        helpBtnW = 20;
        helpBtnH = 18;

        // Close button
        addDrawableChild(ButtonWidget.builder(
                Text.literal("✕").formatted(Formatting.RED),
                btn -> close()
        ).dimensions(guiX + 315, guiY + 5, 25, 18).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xA0000000);

        // Main background
        context.fill(guiX, guiY, guiX + GUI_W, guiY + GUI_H, BG_DARK);
        drawBorder(context, guiX, guiY, GUI_W, GUI_H, BORDER_GOLD, 2);

        // Header (below tabs)
        int headerY = guiY + 28;
        int leftColX = guiX + 10;
        int rightColX = guiX + 180;

        // Left column header
        context.drawText(textRenderer, "#", leftColX, headerY, COLOR_TEXT_DIM, false);
        context.drawText(textRenderer, "Dice", leftColX + 25, headerY, COLOR_TEXT_DIM, false);
        context.drawText(textRenderer, "Result", leftColX + 75, headerY, COLOR_TEXT_DIM, false);

        // Right column header
        context.drawText(textRenderer, "#", rightColX, headerY, COLOR_TEXT_DIM, false);
        context.drawText(textRenderer, "Dice", rightColX + 25, headerY, COLOR_TEXT_DIM, false);
        context.drawText(textRenderer, "Result", rightColX + 75, headerY, COLOR_TEXT_DIM, false);

        // Divider
        context.fill(guiX + 6, headerY + 12, guiX + GUI_W - 6, headerY + 13, BORDER_GOLD);

        // History entries - 20 results in 2 columns
        int entryY = headerY + 18;
        int entryHeight = 14;

        // Show up to 20 results (10 per column)
        int maxEntries = Math.min(20, history.size());

        for (int i = 0; i < maxEntries; i++) {
            DiceResult result = history.get(history.size() - 1 - i);

            // Determine column (left for first 10, right for next 10)
            boolean isLeftColumn = i < 10;
            int colX = isLeftColumn ? leftColX : rightColX;
            int rowIndex = isLeftColumn ? i : (i - 10);
            int y = entryY + rowIndex * entryHeight;

            // Entry number
            context.drawText(textRenderer, String.valueOf(i + 1), colX, y + 2, COLOR_TEXT_DIM, false);

            // Dice values (compact: "1,2,3")
            String diceStr = result.d1 + "," + result.d2 + "," + result.d3;
            int diceColor = COLOR_TEXT;
            if (result.isTriple) diceColor = COLOR_GREEN;
            else if (result.isPair) diceColor = COLOR_ORANGE;
            context.drawText(textRenderer, diceStr, colX + 25, y + 2, diceColor, false);

            // Result string
            String resultStr = result.resultString;
            int resultColor;
            if (result.isTriple) resultColor = COLOR_GREEN;
            else if (result.isPair) resultColor = COLOR_ORANGE;
            else if (result.total >= 11) resultColor = COLOR_GREEN;
            else resultColor = COLOR_RED;
            context.drawText(textRenderer, resultStr, colX + 75, y + 2, resultColor, false);
        }

        // Empty message
        if (history.isEmpty()) {
            String empty = "No history yet";
            int ew = textRenderer.getWidth(empty);
            context.drawText(textRenderer, empty, guiX + GUI_W/2 - ew/2, guiY + 90, COLOR_TEXT_DIM, false);
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

    public void updateState(String json) {
        try {
            stateJson = new Gson().fromJson(json, JsonObject.class);

            if (stateJson.has("history")) {
                JsonArray histArray = stateJson.getAsJsonArray("history");
                history.clear();
                for (JsonElement e : histArray) {
                    JsonObject r = e.getAsJsonObject();
                    history.add(new DiceResult(
                            r.get("die1").getAsInt(),
                            r.get("die2").getAsInt(),
                            r.get("die3").getAsInt(),
                            r.get("total").getAsInt(),
                            r.get("isTriple").getAsBoolean(),
                            r.get("resultString").getAsString()
                    ));
                }
            }
        } catch (Exception e) {
            System.err.println("[Tai Xiu] Error updating state: " + e.getMessage());
        }
    }

    @Override
    public void close() {
        client.setScreen(new TaiXiuTableScreen(pos, stateJson != null ? stateJson.toString() : "{}"));
    }

    private static class DiceResult {
        final int d1, d2, d3, total;
        final boolean isTriple;
        final boolean isPair;
        final String resultString;

        DiceResult(int d1, int d2, int d3, int total, boolean isTriple, String resultString) {
            this.d1 = d1;
            this.d2 = d2;
            this.d3 = d3;
            this.total = total;
            this.isTriple = isTriple;
            this.isPair = (d1 == d2 || d2 == d3 || d1 == d3) && !isTriple;
            this.resultString = resultString;
        }
    }
}
