package com.pokermc.goldenticket.screen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.pokermc.goldenticket.network.GoldenTicketNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Golden Ticket buy screen - like TradeScreen SELL mode.
 * Select ticket tier, choose quantity, buy with ZCoin.
 */
public class GoldenTicketScreen extends Screen {

    private static final int W = 280, H = 210;

    private static final int C_BG     = 0xFF0A0A14;
    private static final int C_BORDER = 0xFFD4AF37;
    private static final int C_GOLD   = 0xFFFFD700;
    private static final int C_WHITE  = 0xFFFFFFFF;
    private static final int C_GRAY   = 0xFF888888;
    private static final int C_GREEN  = 0xFF55CC55;
    private static final int C_RED    = 0xFFFF5555;
    private static final int C_CYAN   = 0xFF88EEFF;
    private static final int C_SEL    = 0xFF1A1A2E;

    private record TicketTier(int price, int rewardMin, int rewardMax, int jackpotThreshold, double upgradeChance) {}

    private final BlockPos tablePos;
    public BlockPos getTablePos() { return tablePos; }
    private final List<TicketTier> tiers = new ArrayList<>();
    private int bankBalance;
    private int selectedIdx = 0;
    private int amount = 1;

    public GoldenTicketScreen(BlockPos tablePos, String stateJson) {
        super(Text.literal("Golden Ticket"));
        this.tablePos = tablePos;
        parseState(stateJson);
    }

    private void parseState(String json) {
        tiers.clear();
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            bankBalance = obj.has("bankBalance") ? obj.get("bankBalance").getAsInt() : 0;
            if (obj.has("tiers")) {
                for (JsonElement e : obj.getAsJsonArray("tiers")) {
                    JsonObject t = e.getAsJsonObject();
                    tiers.add(new TicketTier(
                            t.has("price") ? t.get("price").getAsInt() : 5,
                            t.has("rewardMin") ? t.get("rewardMin").getAsInt() : 0,
                            t.has("rewardMax") ? t.get("rewardMax").getAsInt() : 20,
                            t.has("jackpotThreshold") ? t.get("jackpotThreshold").getAsInt() : 15,
                            t.has("upgradeChance") ? t.get("upgradeChance").getAsDouble() : 0));
                }
            }
        } catch (Exception ignored) {}
        if (tiers.isEmpty()) {
            tiers.add(new TicketTier(5, 0, 20, 15, 0.01));
            tiers.add(new TicketTier(10, 5, 50, 35, 0.01));
            tiers.add(new TicketTier(20, 10, 100, 75, 0.0));
        }
        if (selectedIdx >= tiers.size()) selectedIdx = 0;
    }

    public void updateState(String json) {
        parseState(json);
    }

    @Override
    protected void init() {
        int cx = width / 2, cy = height / 2;
        int x0 = cx - W / 2, y0 = cy - H / 2;

        // Ticket tier rows (click to select)
        int rowY = y0 + 48;
        for (int i = 0; i < tiers.size(); i++) {
            final int idx = i;
            addDrawableChild(ButtonWidget.builder(Text.literal(""),
                    b -> { selectedIdx = idx; amount = 1; })
                    .dimensions(x0 + 8, rowY + i * 24, W - 16, 22).build());
        }

        // Amount controls
        int ctrlY = rowY + tiers.size() * 24 + 12;

        addDrawableChild(ButtonWidget.builder(Text.literal("−"),
                b -> { if (amount > 1) amount--; })
                .dimensions(cx - 20, ctrlY, 20, 16).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("+"),
                b -> amount++)
                .dimensions(cx + 4, ctrlY, 20, 16).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Max"),
                b -> {
                    if (!tiers.isEmpty()) {
                        int price = tiers.get(selectedIdx).price();
                        amount = price > 0 ? bankBalance / price : 0;
                    }
                    amount = Math.max(1, Math.min(64, amount));
                })
                .dimensions(cx + 28, ctrlY, 36, 16).build());

        // Confirm / Back
        int footY = y0 + H - 28;
        addDrawableChild(ButtonWidget.builder(Text.literal("✔ Buy"),
                b -> {
                    if (tiers.isEmpty()) return;
                    TicketTier t = tiers.get(selectedIdx);
                    int cost = amount * t.price();
                    if (bankBalance < cost) return;
                    net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                            new GoldenTicketNetworking.GoldenTicketActionPayload(tablePos, selectedIdx, amount));
                    close();
                })
                .dimensions(cx - 52, footY, 64, 18).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("✕ Back"),
                b -> close())
                .dimensions(cx + 18, footY, 64, 18).build());
    }

    @Override public void renderBackground(DrawContext ctx, int mx, int my, float d) {}
    @Override public boolean shouldPause() { return false; }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        int cx = width / 2, cy = height / 2;
        int x0 = cx - W / 2, y0 = cy - H / 2;

        ctx.fill(0, 0, width, height, 0xAA000000);
        ctx.fill(x0, y0, x0 + W, y0 + H, C_BG);
        drawBorder(ctx, x0, y0, W, H, C_BORDER, 2);
        drawBorder(ctx, x0 + 4, y0 + 4, W - 8, H - 8, 0xFF3A2A0A, 1);

        ctx.drawCenteredTextWithShadow(textRenderer, "Golden Ticket", cx, y0 + 8, C_GOLD);

        String bankStr = "ZCoin: " + bankBalance;
        ctx.drawTextWithShadow(textRenderer, bankStr, x0 + W - 8 - textRenderer.getWidth(bankStr), y0 + 8, C_GREEN);

        // Ticket rows - background only
        int rowY = y0 + 48;
        for (int i = 0; i < tiers.size(); i++) {
            int ry = rowY + i * 24;
            boolean sel = (i == selectedIdx);
            ctx.fill(x0 + 8, ry, x0 + W - 8, ry + 22, sel ? 0xFF1A2A3A : 0xFF121220);
            if (sel) drawBorder(ctx, x0 + 8, ry, W - 16, 22, C_GOLD, 1);
        }

        super.render(ctx, mouseX, mouseY, delta);

        // Draw ticket names + jackpot ON TOP of buttons (so they're visible)
        for (int i = 0; i < tiers.size(); i++) {
            TicketTier t = tiers.get(i);
            int ry = rowY + i * 24;
            // Format: "5 ZC Ticket (Jackpot ≥15 ZC)"
            String name = t.price() + " ZC Ticket";
            String jackpot = "Jackpot ≥" + t.jackpotThreshold() + " ZC";
            ctx.drawTextWithShadow(textRenderer, name, x0 + 14, ry + 5, C_WHITE);
            ctx.drawTextWithShadow(textRenderer, jackpot, x0 + 14, ry + 13, C_GOLD);
            ctx.drawTextWithShadow(textRenderer, t.rewardMin() + "-" + t.rewardMax() + " ZC", x0 + 130, ry + 9, C_GRAY);
            int maxBuy = t.price() > 0 ? bankBalance / t.price() : 0;
            String maxStr = "Max: " + Math.min(64, maxBuy);
            ctx.drawTextWithShadow(textRenderer, maxStr,
                    x0 + W - 14 - textRenderer.getWidth(maxStr), ry + 9,
                    maxBuy > 0 ? C_CYAN : C_GRAY);
        }

        // Amount label
        int ctrlY = rowY + tiers.size() * 24 + 12;
        ctx.drawCenteredTextWithShadow(textRenderer, "Qty: " + amount, cx - 70, ctrlY + 4, C_WHITE);

        // Preview cost
        if (!tiers.isEmpty()) {
            TicketTier t = tiers.get(selectedIdx);
            int cost = amount * t.price();
            String preview = "= " + cost + " ZC";
            int pw = textRenderer.getWidth(preview);
            ctx.drawTextWithShadow(textRenderer, preview, x0 + W - 8 - pw, y0 + H - 24,
                    bankBalance >= cost ? C_GREEN : C_RED);
        }
    }

    private void drawBorder(DrawContext ctx, int x, int y, int w, int h, int color, int t) {
        ctx.fill(x, y, x+w, y+t, color);
        ctx.fill(x, y+h-t, x+w, y+h, color);
        ctx.fill(x, y, x+t, y+h, color);
        ctx.fill(x+w-t, y, x+w, y+h, color);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.getKeycode() == 256) { close(); return true; }
        return super.keyPressed(input);
    }
}
