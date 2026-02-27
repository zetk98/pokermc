package com.pokermc.screen;

import com.google.gson.*;
import com.pokermc.network.PokerNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Item ↔ ZC exchange screen.
 *
 *  BUY tab  → deposit items, receive ZC
 *  SELL tab → spend ZC, receive items
 *
 * Items and rates come from config/pokermc_trades.json (sent via state JSON).
 */
public class TradeScreen extends Screen {

    private static final int W = 280, H = 210;

    private static final int C_BG     = 0xFF0A0A14;
    private static final int C_BORDER = 0xFFD4AF37;
    private static final int C_GOLD   = 0xFFFFD700;
    private static final int C_WHITE  = 0xFFFFFFFF;
    private static final int C_GRAY   = 0xFF888888;
    private static final int C_GREEN  = 0xFF55CC55;
    private static final int C_RED    = 0xFFFF5555;
    private static final int C_CYAN   = 0xFF88EEFF;
    private static final int C_SEL    = 0xFF1A1A2E; // selected row bg

    // ── Trade item data from server ───────────────────────────────────────────
    private record TradeItem(String id, int buyRate, int sellRate, int sellGives) {
        String shortName() { return id.contains(":") ? id.split(":")[1] : id; }
        String displayName() {
            String s = shortName().replace("_ingot", "").replace("_", " ");
            return s.substring(0, 1).toUpperCase() + s.substring(1);
        }
        int itemsPerUnit() { return sellGives > 0 ? sellGives : sellRate; }
    }

    private final BlockPos tablePos;
    private final List<TradeItem> items = new ArrayList<>();
    private int bankBalance;
    private boolean buyMode = true;       // true = BUY, false = SELL
    private int selectedIdx = 0;
    private int amount = 1;

    public TradeScreen(BlockPos tablePos, String stateJson) {
        super(Text.literal("Trade"));
        this.tablePos = tablePos;
        parseState(stateJson);
    }

    private void parseState(String json) {
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            bankBalance = obj.has("bankBalance") ? obj.get("bankBalance").getAsInt() : 0;
            items.clear();
            if (obj.has("tradeItems")) {
                for (JsonElement e : obj.getAsJsonArray("tradeItems")) {
                    JsonObject to = e.getAsJsonObject();
                    int sellRate = to.has("sellRate") ? to.get("sellRate").getAsInt() : 10;
                    items.add(new TradeItem(
                            to.get("id").getAsString(),
                            to.has("buyRate")  ? to.get("buyRate").getAsInt()  : 10,
                            sellRate,
                            to.has("sellGives") ? to.get("sellGives").getAsInt() : sellRate));
                }
            }
            // Default items if server didn't send any
            if (items.isEmpty()) {
                items.add(new TradeItem("minecraft:iron_ingot",  1,  1,  2));
                items.add(new TradeItem("minecraft:gold_ingot",  2,  2,  4));
                items.add(new TradeItem("minecraft:emerald",    5,  5,  8));
                items.add(new TradeItem("minecraft:diamond",    10, 10, 13));
            }
            if (selectedIdx >= items.size()) selectedIdx = 0;
        } catch (Exception ignored) {}
    }

    public void updateState(String json) {
        parseState(json);
    }

    @Override
    protected void init() {
        int cx = width / 2, cy = height / 2;
        int x0 = cx - W / 2, y0 = cy - H / 2;

        // ── BUY / SELL tabs (shifted right) ───────────────────────────────────
        addDrawableChild(ButtonWidget.builder(Text.literal("BUY"),
                b -> { buyMode = true;  clearChildren(); init(); })
                .dimensions(x0 + 24, y0 + 28, 50, 16).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("SELL"),
                b -> { buyMode = false; clearChildren(); init(); })
                .dimensions(x0 + 78, y0 + 28, 50, 16).build());

        // ── Item list rows (click to select) ──────────────────────────────────
        int rowY = y0 + 52;
        for (int i = 0; i < items.size(); i++) {
            final int idx = i;
            addDrawableChild(ButtonWidget.builder(Text.literal(""),  // label drawn manually
                    b -> { selectedIdx = idx; amount = 1; })
                    .dimensions(x0 + 8, rowY + i * 22, W - 16, 20).build());
        }

        // ── Amount controls (shifted right) ────────────────────────────────────
        int ctrlY = y0 + 52 + items.size() * 22 + 10;

        addDrawableChild(ButtonWidget.builder(Text.literal("−"),
                b -> { if (amount > 1) amount--; })
                .dimensions(cx - 20, ctrlY, 20, 16).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("+"),
                b -> amount++)
                .dimensions(cx + 4, ctrlY, 20, 16).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Max"),
                b -> {
                    if (buyMode) {
                        amount = availableInInventory(selectedIdx);
                    } else {
                        if (!items.isEmpty()) {
                            int rate = items.get(selectedIdx).sellRate();
                            amount = rate > 0 ? bankBalance / rate : 0;  // max units
                        }
                    }
                    amount = Math.max(1, amount);
                })
                .dimensions(cx + 28, ctrlY, 36, 16).build());

        // ── Accept (shifted right) ────────────────────────────────────────────
        int footY = y0 + H - 28;
        addDrawableChild(ButtonWidget.builder(Text.literal("✔ Confirm"),
                b -> {
                    if (items.isEmpty()) return;
                    TradeItem ti = items.get(selectedIdx);
                    if (buyMode) {
                        int avail = availableInInventory(selectedIdx);
                        if (avail <= 0) return;
                        int actual = Math.min(amount, avail);
                        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                                new PokerNetworking.PlayerActionPayload(
                                        tablePos, "DEPOSIT", actual, ti.id()));
                    } else {
                        int cost = amount * ti.sellRate();
                        if (bankBalance < cost) return;
                        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                                new PokerNetworking.PlayerActionPayload(
                                        tablePos, "WITHDRAW", amount, ti.id()));
                    }
                    close();
                })
                .dimensions(cx - 52, footY, 64, 18).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("✕ Back"),
                b -> close())
                .dimensions(cx + 18, footY, 64, 18).build());
    }

    private int availableInInventory(int idx) {
        if (idx < 0 || idx >= items.size()) return 0;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return 0;
        Identifier id = Identifier.tryParse(items.get(idx).id());
        if (id == null) return 0;
        Item target = Registries.ITEM.get(id);
        int count = 0;
        for (ItemStack stack : mc.player.getInventory().main)
            if (stack.getItem() == target) count += stack.getCount();
        return count;
    }

    @Override public void renderBackground(DrawContext ctx, int mx, int my, float d) {}
    @Override public boolean shouldPause() { return false; }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        int cx = width / 2, cy = height / 2;
        int x0 = cx - W / 2, y0 = cy - H / 2;

        // Solid background + panel
        ctx.fill(0, 0, width, height, 0xAA000000);
        ctx.fill(x0, y0, x0 + W, y0 + H, C_BG);
        drawBorder(ctx, x0, y0, W, H, C_BORDER, 2);
        drawBorder(ctx, x0 + 4, y0 + 4, W - 8, H - 8, 0xFF3A2A0A, 1);

        // Title
        ctx.drawCenteredTextWithShadow(textRenderer, "⇄ EXCHANGE", cx, y0 + 8, C_GOLD);

        String bankStr = "ZCoin: " + bankBalance;
        ctx.drawTextWithShadow(textRenderer, bankStr, x0 + W - 8 - textRenderer.getWidth(bankStr), y0 + 8, C_GREEN);

        // Tab highlight
        ctx.fill(x0 + (buyMode ? 24 : 78), y0 + 44, x0 + (buyMode ? 74 : 128), y0 + 46, C_GOLD);

        // ── Item rows ─────────────────────────────────────────────────────────
        int rowY = y0 + 52;
        for (int i = 0; i < items.size(); i++) {
            TradeItem ti = items.get(i);
            int ry = rowY + i * 22;
            boolean sel = (i == selectedIdx);

            // Row background
            ctx.fill(x0 + 8, ry, x0 + W - 8, ry + 20, sel ? 0xFF1A2A3A : 0xFF121220);
            if (sel) drawBorder(ctx, x0 + 8, ry, W - 16, 20, C_GOLD, 1);

            // Item name
            ctx.drawTextWithShadow(textRenderer, ti.displayName(), x0 + 14, ry + 6, C_WHITE);

            // Available / rate
            int avail = availableInInventory(i);
            if (buyMode) {
                ctx.drawTextWithShadow(textRenderer,
                        "In bag: " + avail, x0 + 100, ry + 6, avail > 0 ? C_CYAN : C_GRAY);
                String rate = "1 = " + ti.buyRate() + " ZC";
                ctx.drawTextWithShadow(textRenderer, rate,
                        x0 + W - 14 - textRenderer.getWidth(rate), ry + 6, C_GOLD);
            } else {
                int gives = ti.itemsPerUnit();
                String rate = ti.sellRate() + " ZC = " + gives;
                ctx.drawTextWithShadow(textRenderer, rate, x0 + 100, ry + 6, C_GOLD);
                int maxUnits = ti.sellRate() > 0 ? bankBalance / ti.sellRate() : 0;
                String canStr = "Max: " + maxUnits;
                ctx.drawTextWithShadow(textRenderer, canStr,
                        x0 + W - 14 - textRenderer.getWidth(canStr), ry + 6,
                        maxUnits > 0 ? C_CYAN : C_GRAY);
            }
        }

        // ── Amount controls ────────────────────────────────────────────────────
        int ctrlY = rowY + items.size() * 22 + 10;
        ctx.drawCenteredTextWithShadow(textRenderer, "Qty: " + amount, cx - 70, ctrlY + 4, C_WHITE);

        // Preview — bottom-left corner (no overlap)
        if (!items.isEmpty()) {
            TradeItem ti = items.get(selectedIdx);
            int previewY = y0 + H - 12;
            int previewX = x0 + 10;
            if (buyMode) {
                int avail = availableInInventory(selectedIdx);
                int actual = Math.min(amount, avail);
                int gain = actual * ti.buyRate();
                String preview = actual + " " + ti.displayName() + " → +" + gain + " ZC";
                ctx.drawTextWithShadow(textRenderer, preview, previewX, previewY,
                        avail > 0 ? C_GREEN : C_RED);
            } else {
                int cost = amount * ti.sellRate();
                int itemsOut = amount * ti.itemsPerUnit();
                boolean canAfford = cost <= bankBalance;
                String preview = cost + " ZC → " + itemsOut + " " + ti.displayName();
                ctx.drawTextWithShadow(textRenderer, preview, previewX, previewY,
                        canAfford ? C_GREEN : C_RED);
            }
        }

        super.render(ctx, mouseX, mouseY, delta);
    }

    private void drawBorder(DrawContext ctx, int x, int y, int w, int h, int color, int t) {
        ctx.fill(x, y, x+w, y+t, color);
        ctx.fill(x, y+h-t, x+w, y+h, color);
        ctx.fill(x, y, x+t, y+h, color);
        ctx.fill(x+w-t, y, x+w, y+h, color);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { close(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
