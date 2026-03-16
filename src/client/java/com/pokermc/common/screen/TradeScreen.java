package com.pokermc.common.screen;

import com.google.gson.*;
import com.pokermc.poker.network.PokerNetworking;
import com.pokermc.blackjack.network.BlackjackNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.KeyInput;
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
 * Items and rates come from config/casinocraft.json (sent via state JSON).
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
    public BlockPos getTablePos() { return tablePos; }
    private final boolean isBlackjack;
    private int framesOpen = 0;
    private final List<TradeItem> items = new ArrayList<>();
    private int bankBalance;
    private boolean buyMode = true;       // true = BUY, false = SELL
    private int selectedIdx = 0;
    private int amount = 1;

    public TradeScreen(BlockPos tablePos, String stateJson) {
        this(tablePos, stateJson, false);
    }

    public TradeScreen(BlockPos tablePos, String stateJson, boolean isBlackjack) {
        super(Text.literal("Trade"));
        this.tablePos = tablePos;
        this.isBlackjack = isBlackjack;
        parseState(stateJson);
    }

    private void parseState(String json) {
        items.clear();
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            bankBalance = obj.has("bankBalance") ? obj.get("bankBalance").getAsInt() : 0;
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
        } catch (Exception ignored) {}
        // Always show defaults if empty (iron, gold, emerald, diamond)
        if (items.isEmpty()) {
            items.add(new TradeItem("minecraft:iron_ingot",  2,  2,  1));
            items.add(new TradeItem("minecraft:gold_ingot",  3,  3,  1));
            items.add(new TradeItem("minecraft:emerald",    7,  7,  1));
            items.add(new TradeItem("minecraft:diamond",    13, 13, 1));
        }
        if (selectedIdx >= items.size()) selectedIdx = 0;
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
                        if (isBlackjack)
                            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                                    new com.pokermc.blackjack.network.BlackjackNetworking.BlackjackActionPayload(
                                            tablePos, "DEPOSIT", actual, ti.id()));
                        else
                            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                                    new PokerNetworking.PlayerActionPayload(
                                            tablePos, "DEPOSIT", actual, ti.id()));
                    } else {
                        int cost = amount * ti.sellRate();
                        if (bankBalance < cost) return;
                        if (isBlackjack)
                            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                                    new com.pokermc.blackjack.network.BlackjackNetworking.BlackjackActionPayload(
                                            tablePos, "WITHDRAW", amount, ti.id()));
                        else
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
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == target) count += stack.getCount();
        }
        return count;
    }

    @Override public void renderBackground(DrawContext ctx, int mx, int my, float d) {}
    @Override public boolean shouldPause() { return false; }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        if (framesOpen < 10) framesOpen++;
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

        // ── Item rows (background only; text drawn after super so it's on top) ──
        int rowY = y0 + 52;
        for (int i = 0; i < items.size(); i++) {
            TradeItem ti = items.get(i);
            int ry = rowY + i * 22;
            boolean sel = (i == selectedIdx);

            // Row background
            ctx.fill(x0 + 8, ry, x0 + W - 8, ry + 20, sel ? 0xFF1A2A3A : 0xFF121220);
            if (sel) drawBorder(ctx, x0 + 8, ry, W - 16, 20, C_GOLD, 1);
        }

        super.render(ctx, mouseX, mouseY, delta);

        // ── Item text (drawn after buttons so text is visible on top) ───────────
        for (int i = 0; i < items.size(); i++) {
            TradeItem ti = items.get(i);
            int ry = rowY + i * 22;

            ctx.drawTextWithShadow(textRenderer, ti.displayName(), x0 + 14, ry + 6, C_WHITE);

            int avail = availableInInventory(i);
            if (buyMode) {
                String rate = "1 " + ti.displayName() + " = " + ti.buyRate() + " ZC";
                ctx.drawTextWithShadow(textRenderer, rate, x0 + 100, ry + 6, C_GOLD);
                String maxStr = "Max: " + avail;
                ctx.drawTextWithShadow(textRenderer, maxStr,
                        x0 + W - 14 - textRenderer.getWidth(maxStr), ry + 6,
                        avail > 0 ? C_CYAN : C_GRAY);
            } else {
                int gives = ti.itemsPerUnit();
                String rate = ti.sellRate() + " ZC = " + gives;
                ctx.drawTextWithShadow(textRenderer, rate, x0 + 100, ry + 6, C_GOLD);
                int maxUnits = ti.sellRate() > 0 ? bankBalance / ti.sellRate() : 0;
                String maxStr = "Max: " + maxUnits;
                ctx.drawTextWithShadow(textRenderer, maxStr,
                        x0 + W - 14 - textRenderer.getWidth(maxStr), ry + 6,
                        maxUnits > 0 ? C_CYAN : C_GRAY);
            }
        }

        // ── Amount controls label ─────────────────────────────────────────────
        int ctrlY = rowY + items.size() * 22 + 10;
        ctx.drawCenteredTextWithShadow(textRenderer, "Qty: " + amount, cx - 70, ctrlY + 4, C_WHITE);

        // ── Bottom right: preview result ───────────────────────────────────────
        if (!items.isEmpty()) {
            TradeItem ti = items.get(selectedIdx);
            String preview;
            if (buyMode) {
                int actual = Math.min(amount, availableInInventory(selectedIdx));
                int zcReceived = actual * ti.buyRate();
                preview = "→ " + zcReceived + " ZC";
            } else {
                int cost = amount * ti.sellRate();
                preview = "= " + cost + " ZC";
            }
            int pw = textRenderer.getWidth(preview);
            ctx.drawTextWithShadow(textRenderer, preview, x0 + W - 8 - pw, y0 + H - 24, C_GREEN);
        }
    }

    private void drawBorder(DrawContext ctx, int x, int y, int w, int h, int color, int t) {
        ctx.fill(x, y, x+w, y+t, color);
        ctx.fill(x, y+h-t, x+w, y+h, color);
        ctx.fill(x, y, x+t, y+h, color);
        ctx.fill(x+w-t, y, x+w, y+h, color);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (framesOpen < 5) return true;
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.getKeycode() == 256) { close(); return true; }
        return super.keyPressed(input);
    }
}
