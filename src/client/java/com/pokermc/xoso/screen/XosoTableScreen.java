package com.pokermc.xoso.screen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Lottery - buy tickets, view results, claim.
 * GUI strictly 260x180 - no overflow.
 */
public class XosoTableScreen extends Screen {

    private static final Identifier TEX_BG = Identifier.of("casinocraft", "textures/gui/lobby_bg.png");
    private static final int BG_W = 260, BG_H = 180;
    private static final int PAD = 12;
    private static final int MAX_TEXT_W = BG_W - PAD * 2; // 236px
    private static final int C_GOLD = 0xFFFFD700, C_WHITE = 0xFFFFFFFF, C_GRAY = 0xFF888888, C_ORANGE = 0xFFFF8844;

    private final BlockPos tablePos;
    public BlockPos getTablePos() { return tablePos; }
    private String stateJson = "{}";
    private String status = "";
    private int ticketPrice = 5;
    private long worldTime = 0;
    private long worldTimeReceivedAtMs = 0; // for realtime countdown interpolation
    private long ticksPerDraw = 12000;
    private int bankBalance = 0;
    private String resultSpecial = "", result1st = "", result2nd = "", result3rd = "";
    private int prizeSpecial = 1000, prize1st = 500, prize2nd = 200, prize3rd = 50;
    private final List<RecentDraw> recentDraws = new ArrayList<>();
    private TextFieldWidget numberInput;

    private record RecentDraw(long period, String special, String first, String second, String third) {}

    public XosoTableScreen(BlockPos pos, String stateJson) {
        super(Text.literal("Lottery"));
        this.tablePos = pos;
        parseState(stateJson);
    }

    @Override
    protected void init() {
        int cx = width / 2, cy = height / 2;
        int bgX = cx - BG_W / 2, bgY = cy - BG_H / 2;

        addDrawableChild(ButtonWidget.builder(Text.literal("✕"), b -> close())
                .dimensions(bgX + BG_W - 20, bgY + 6, 14, 12).build());

        // Row: Number input + Buy + Random (Random only fills input, Buy purchases)
        numberInput = addDrawableChild(new TextFieldWidget(textRenderer, bgX + PAD, bgY + 62, 58, 20, Text.literal("")));
        numberInput.setMaxLength(4);
        numberInput.setTextPredicate(s -> s.isEmpty() || s.matches("\\d{0,4}"));
        numberInput.setPlaceholder(Text.literal("0000"));

        addDrawableChild(ButtonWidget.builder(Text.literal("Buy"), b -> buyTicket())
                .dimensions(bgX + PAD + 62, bgY + 60, 48, 24).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Random"), b -> fillRandom())
                .dimensions(bgX + PAD + 114, bgY + 60, 62, 24).build());
    }

    private void buyTicket() {
        String num = numberInput != null ? numberInput.getText().trim() : "";
        if (num.length() != 4) {
            if (client != null) client.player.sendMessage(Text.literal("Enter 4 digits (0000-9999)."), false);
            return;
        }
        sendAction("BUY", num);
    }

    /** Random: only fill 4 digits in input, do not buy. */
    private void fillRandom() {
        if (numberInput != null) {
            numberInput.setText(String.format("%04d", java.util.concurrent.ThreadLocalRandom.current().nextInt(10000)));
        }
    }

    private void sendAction(String action, String data) {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                new com.pokermc.xoso.network.XosoNetworking.XosoActionPayload(tablePos, action, data));
    }

    private void parseState(String json) {
        stateJson = json;
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            status = obj.has("status") ? obj.get("status").getAsString() : "";
            ticketPrice = obj.has("ticketPrice") ? obj.get("ticketPrice").getAsInt() : 5;
            worldTime = obj.has("worldTime") ? obj.get("worldTime").getAsLong() : 0;
            worldTimeReceivedAtMs = System.currentTimeMillis();
            ticksPerDraw = obj.has("ticksPerDraw") ? obj.get("ticksPerDraw").getAsLong() : 12000;
            bankBalance = obj.has("bankBalance") ? obj.get("bankBalance").getAsInt() : 0;
            resultSpecial = obj.has("resultSpecial") ? obj.get("resultSpecial").getAsString() : "";
            result1st = obj.has("result1st") ? obj.get("result1st").getAsString() : "";
            result2nd = obj.has("result2nd") ? obj.get("result2nd").getAsString() : "";
            result3rd = obj.has("result3rd") ? obj.get("result3rd").getAsString() : "";
            if (obj.has("prizes")) {
                JsonArray arr = obj.getAsJsonArray("prizes");
                if (arr.size() >= 4) {
                    prizeSpecial = arr.get(0).getAsInt();
                    prize1st = arr.get(1).getAsInt();
                    prize2nd = arr.get(2).getAsInt();
                    prize3rd = arr.get(3).getAsInt();
                }
            }
            recentDraws.clear();
            if (obj.has("recentDraws")) {
                for (JsonElement e : obj.getAsJsonArray("recentDraws")) {
                    JsonObject o = e.getAsJsonObject();
                    recentDraws.add(new RecentDraw(
                            o.has("period") ? o.get("period").getAsLong() : (o.has("day") ? o.get("day").getAsLong() : 0),
                            o.has("special") ? o.get("special").getAsString() : "",
                            o.has("first") ? o.get("first").getAsString() : "",
                            o.has("second") ? o.get("second").getAsString() : "",
                            o.has("third") ? o.get("third").getAsString() : ""));
                }
            }
        } catch (Exception ignored) {}
    }

    public void updateState(String json) {
        parseState(json);
        if (client != null) {
            clearChildren();
            init();
        }
    }

    @Override
    public void renderBackground(DrawContext ctx, int mx, int my, float d) {}
    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        int cx = width / 2, cy = height / 2;
        int bgX = cx - BG_W / 2, bgY = cy - BG_H / 2;

        ctx.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, TEX_BG, bgX, bgY, 0, 0, BG_W, BG_H, BG_W, BG_H);

        // Title - top
        ctx.drawCenteredTextWithShadow(textRenderer, "Lottery", cx, bgY + 12, C_ORANGE);

        // Balance + countdown on one line: "5 ZC | Bal: 49 | 4p30s" (realtime interpolation)
        long estimatedWorldTime = worldTime;
        if (worldTimeReceivedAtMs > 0) {
            long elapsedMs = System.currentTimeMillis() - worldTimeReceivedAtMs;
            estimatedWorldTime = worldTime + (elapsedMs * 20L / 1000L); // ~20 TPS
        }
        long currentPeriod = estimatedWorldTime / ticksPerDraw;
        long nextDrawTick = (currentPeriod + 1) * ticksPerDraw;
        long ticksUntil = Math.max(0, nextDrawTick - estimatedWorldTime);
        int mins = (int) (ticksUntil / 1200);
        int secs = (int) ((ticksUntil % 1200) * 60 / 1200);
        String countdown = mins > 0 ? mins + "p" + secs + "s" : secs + "s";
        String balPart = ticketPrice + " ZC | Bal: " + bankBalance + " | ";
        ctx.drawTextWithShadow(textRenderer, balPart, bgX + PAD, bgY + 34, C_GOLD);
        int balW = textRenderer.getWidth(balPart);
        ctx.drawTextWithShadow(textRenderer, countdown, bgX + PAD + balW, bgY + 34, 0xFF55FF55);

        // Number label + input (input rendered by child)
        ctx.drawTextWithShadow(textRenderer, "Number (0000-9999):", bgX + PAD, bgY + 52, C_WHITE);
        if (numberInput != null) numberInput.render(ctx, mouseX, mouseY, delta);

        // Buy + Random buttons rendered as children

        // Last 7 results (English, 1 line down)
        int y = bgY + 92;
        ctx.drawTextWithShadow(textRenderer, "Last 7 periods:", bgX + PAD, y, C_ORANGE);
        y += 10;
        for (int i = 0; i < recentDraws.size(); i++) {
            RecentDraw rd = recentDraws.get(i);
            String line = "Period #" + rd.period() + ": 3rd " + rd.third() + " 2nd " + rd.second() + " 1st " + rd.first() + " Sp " + rd.special();
            int color = (i == 0) ? 0xFFFFFF00 : C_GRAY; // mới nhất = vàng
            ctx.drawTextWithShadow(textRenderer, line, bgX + PAD, y, color);
            y += 8;
        }

        super.render(ctx, mouseX, mouseY, delta);
    }
}
