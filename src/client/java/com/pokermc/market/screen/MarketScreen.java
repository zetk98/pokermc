package com.pokermc.market.screen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.pokermc.market.network.MarketNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Market screen - buy/sell minerals for ZCoin. Realtime, 5min refresh countdown.
 * Layout: [Market][ZCoin] [Buy][Sell][Refresh: Xm Xs] | [Item Xzc Max/Have] | [Qty][-][+][Max][=] | [History][Now] | [Confirm][Back]
 */
public class MarketScreen extends Screen {

    private static final int W = 300, H = 260;
    private static final int TICKS_PER_UPDATE = 6000;

    private static final int C_BG     = 0xFF0A0A14;
    private static final int C_BORDER = 0xFFD4AF37;
    private static final int C_GOLD   = 0xFFFFD700;
    private static final int C_WHITE  = 0xFFFFFFFF;
    private static final int C_GRAY   = 0xFF888888;
    private static final int C_GREEN  = 0xFF55CC55;
    private static final int C_RED    = 0xFFFF5555;
    private static final int C_CYAN   = 0xFF88EEFF;

    private record MarketItem(String id, int sellPrice, int buyPrice, List<Integer> history) {
        String displayName() {
            String s = id.contains(":") ? id.split(":")[1] : id;
            s = s.replace("_ingot", "").replace("_", " ");
            return s.substring(0, 1).toUpperCase() + s.substring(1);
        }
    }

    private final net.minecraft.util.math.BlockPos tablePos;
    public net.minecraft.util.math.BlockPos getTablePos() { return tablePos; }
    private final List<MarketItem> items = new ArrayList<>();
    private int bankBalance;
    private boolean buyMode = true;
    private int selectedIdx = 0;
    private int amount = 1;
    private long worldTime = 0;
    private long lastUpdateTick = 0;
    private long stateReceivedAtMs = 0;
    private int refreshRequestTicks = 0;

    public MarketScreen(net.minecraft.util.math.BlockPos tablePos, String stateJson) {
        super(Text.literal("Market"));
        this.tablePos = tablePos;
        parseState(stateJson);
    }

    private void parseState(String json) {
        items.clear();
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            bankBalance = obj.has("bankBalance") ? obj.get("bankBalance").getAsInt() : 0;
            worldTime = obj.has("worldTime") ? obj.get("worldTime").getAsLong() : 0;
            lastUpdateTick = obj.has("lastUpdateTick") ? obj.get("lastUpdateTick").getAsLong() : 0;
            stateReceivedAtMs = System.currentTimeMillis();
            if (obj.has("items")) {
                for (JsonElement e : obj.getAsJsonArray("items")) {
                    JsonObject o = e.getAsJsonObject();
                    List<Integer> hist = new ArrayList<>();
                    if (o.has("history")) {
                        for (JsonElement h : o.getAsJsonArray("history")) hist.add(h.getAsInt());
                    }
                    items.add(new MarketItem(
                            o.get("id").getAsString(),
                            o.has("sellPrice") ? o.get("sellPrice").getAsInt() : 2,
                            o.has("buyPrice") ? o.get("buyPrice").getAsInt() : 3,
                            hist));
                }
            }
        } catch (Exception ignored) {}
        if (items.isEmpty()) {
            items.add(new MarketItem("minecraft:iron_ingot", 2, 3, List.of(2,2,2,2,2,2,2)));
            items.add(new MarketItem("minecraft:copper_ingot", 2, 3, List.of(2,2,2,2,2,2,2)));
            items.add(new MarketItem("minecraft:gold_ingot", 3, 4, List.of(3,3,3,3,3,3,3)));
            items.add(new MarketItem("minecraft:emerald", 7, 8, List.of(7,7,7,7,7,7,7)));
            items.add(new MarketItem("minecraft:diamond", 13, 14, List.of(13,13,13,13,13,13,13)));
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

        addDrawableChild(ButtonWidget.builder(Text.literal("Buy"),
                b -> { buyMode = true;  clearChildren(); init(); })
                .dimensions(x0 + 8, y0 + 28, 50, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Sell"),
                b -> { buyMode = false; clearChildren(); init(); })
                .dimensions(x0 + 62, y0 + 28, 50, 18).build());

        int rowY = y0 + 52;
        for (int i = 0; i < items.size(); i++) {
            final int idx = i;
            addDrawableChild(ButtonWidget.builder(Text.literal(""),
                    b -> { selectedIdx = idx; amount = 1; })
                    .dimensions(x0 + 8, rowY + i * 20, W - 16, 18).build());
        }

        int ctrlY = rowY + items.size() * 20 + 8;
        addDrawableChild(ButtonWidget.builder(Text.literal("−"),
                b -> { if (amount > 1) amount--; })
                .dimensions(cx - 50, ctrlY, 20, 16).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("+"),
                b -> amount++)
                .dimensions(cx - 26, ctrlY, 20, 16).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Max"),
                b -> {
                    if (buyMode && !items.isEmpty()) {
                        MarketItem mi = items.get(selectedIdx);
                        amount = Math.min(bankBalance / Math.max(1, mi.buyPrice()), 64);
                    } else if (!items.isEmpty()) {
                        amount = Math.min(availableInInventory(selectedIdx), 64);
                    }
                    amount = Math.max(1, amount);
                })
                .dimensions(cx - 2, ctrlY, 36, 16).build());

        int footY = y0 + H - 28;
        addDrawableChild(ButtonWidget.builder(Text.literal("Confirm"),
                b -> confirm()).dimensions(cx - 60, footY, 64, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Back"),
                b -> close()).dimensions(cx + 10, footY, 64, 18).build());
    }

    private void confirm() {
        if (items.isEmpty()) return;
        MarketItem mi = items.get(selectedIdx);
        if (buyMode) {
            int cost = amount * mi.buyPrice();
            if (bankBalance < cost) return;
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                    new MarketNetworking.MarketActionPayload(tablePos, "BUY", mi.id(), amount));
        } else {
            int avail = availableInInventory(selectedIdx);
            if (avail < amount) return;
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                    new MarketNetworking.MarketActionPayload(tablePos, "SELL", mi.id(), amount));
        }
        close();
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
        refreshRequestTicks++;
        if (refreshRequestTicks >= 100) {
            refreshRequestTicks = 0;
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                    new MarketNetworking.RequestMarketRefreshPayload(tablePos));
        }

        int cx = width / 2, cy = height / 2;
        int x0 = cx - W / 2, y0 = cy - H / 2;

        ctx.fill(0, 0, width, height, 0xAA000000);
        ctx.fill(x0, y0, x0 + W, y0 + H, C_BG);
        drawBorder(ctx, x0, y0, W, H, C_BORDER, 2);
        drawBorder(ctx, x0 + 4, y0 + 4, W - 8, H - 8, 0xFF3A2A0A, 1);

        ctx.drawTextWithShadow(textRenderer, "Market", x0 + 8, y0 + 8, C_GOLD);
        ctx.drawTextWithShadow(textRenderer, "ZCoin: " + bankBalance, x0 + W - 8 - textRenderer.getWidth("ZCoin: " + bankBalance), y0 + 8, C_GREEN);

        long elapsedTicks = (System.currentTimeMillis() - stateReceivedAtMs) * 20L / 1000L;
        long estWorldTime = worldTime + elapsedTicks;
        long nextRefresh = lastUpdateTick + TICKS_PER_UPDATE;
        long ticksLeft = Math.max(0, nextRefresh - estWorldTime);
        int secs = (int) (ticksLeft / 20);
        int mins = secs / 60;
        secs = secs % 60;
        String countdown = "Refresh: " + mins + "m " + secs + "s";
        ctx.drawTextWithShadow(textRenderer, countdown, x0 + 120, y0 + 32, C_CYAN);

        ctx.fill(x0 + (buyMode ? 8 : 62), y0 + 46, x0 + (buyMode ? 58 : 112), y0 + 48, C_GOLD);

        int rowY = y0 + 52;
        for (int i = 0; i < items.size(); i++) {
            int ry = rowY + i * 20;
            boolean sel = (i == selectedIdx);
            ctx.fill(x0 + 8, ry, x0 + W - 8, ry + 18, sel ? 0xFF1A2A3A : 0xFF121220);
            if (sel) drawBorder(ctx, x0 + 8, ry, W - 16, 18, C_GOLD, 1);
        }

        super.render(ctx, mouseX, mouseY, delta);

        for (int i = 0; i < items.size(); i++) {
            MarketItem mi = items.get(i);
            int ry = rowY + i * 20;
            int price = buyMode ? mi.buyPrice() : mi.sellPrice();
            int avail = availableInInventory(i);
            int maxVal = buyMode ? Math.min(64, bankBalance / Math.max(1, price)) : avail;
            String left = mi.displayName();
            String center = price + "zc";
            String right = buyMode ? "Max: " + maxVal : "Have: " + maxVal;
            ctx.drawTextWithShadow(textRenderer, left, x0 + 14, ry + 5, C_WHITE);
            ctx.drawTextWithShadow(textRenderer, center, x0 + W / 2 - textRenderer.getWidth(center) / 2, ry + 5, C_GOLD);
            ctx.drawTextWithShadow(textRenderer, right, x0 + W - 14 - textRenderer.getWidth(right), ry + 5, C_CYAN);
        }

        int ctrlY = rowY + items.size() * 20 + 8;
        ctx.drawTextWithShadow(textRenderer, "Qty: " + amount, x0 + 8, ctrlY + 4, C_WHITE);

        if (!items.isEmpty()) {
            MarketItem mi = items.get(selectedIdx);
            int total = buyMode ? amount * mi.buyPrice() : amount * mi.sellPrice();
            ctx.drawTextWithShadow(textRenderer, "= " + total, x0 + W - 50, ctrlY + 4,
                    buyMode ? (bankBalance >= total ? C_GREEN : C_RED) : C_GREEN);
        }

        int chartAreaY = y0 + H - 52;
        int chartH = 18;

        if (!items.isEmpty()) {
            MarketItem mi = items.get(selectedIdx);
            List<Integer> sellHist = mi.history();
            List<Integer> displayHist = new ArrayList<>();
            for (int v : sellHist) {
                displayHist.add(buyMode ? v + 1 : v);
            }
            if (!displayHist.isEmpty()) {
                int barW = (W - 24) / 7;
                int maxP = displayHist.stream().max(Integer::compare).orElse(1);
                int minP = displayHist.stream().min(Integer::compare).orElse(0);
                int range = Math.max(1, maxP - minP);
                int pad = 4;
                for (int j = 0; j < displayHist.size(); j++) {
                    int p = displayHist.get(j);
                    int bx = x0 + 12 + j * barW + pad / 2;
                    int bw = barW - pad;
                    int barH = Math.max(2, (p - minP) * (chartH - 10) / range);
                    int by = chartAreaY + chartH - 10 - barH;
                    ctx.fill(bx, by, bx + bw, by + barH, 0xFF55AA55);
                    ctx.drawTextWithShadow(textRenderer, String.valueOf(p), bx + (bw - textRenderer.getWidth(String.valueOf(p))) / 2, chartAreaY + chartH - 8, C_WHITE);
                }
            }
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
