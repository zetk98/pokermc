package com.pokermc.screen;

import com.pokermc.network.PokerNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

/**
 * Opaque "Create Room" screen — lets the first player choose a big-blind level.
 * Currency unit: ZC (ZetCoin).
 */
public class CreateRoomScreen extends Screen {

    private static final int W = 300, H = 220;

    private static final int C_BG     = 0xFF0A0A14;
    private static final int C_BORDER = 0xFFD4AF37;
    private static final int C_GOLD   = 0xFFFFD700;
    private static final int C_WHITE  = 0xFFFFFFFF;
    private static final int C_GRAY   = 0xFF888888;
    private static final int C_GREEN  = 0xFF55CC55;
    private static final int C_CYAN   = 0xFF88EEFF;

    private static final int[] BET_LEVELS = {1, 10, 100, 1_000, 10_000};
    private static final int MIN_BALANCE_MULTIPLIER = 10;

    private final BlockPos tablePos;
    private final String stateJson;
    private int bankBalance = 0;
    private int framesOpen = 0;

    public CreateRoomScreen(BlockPos tablePos, String stateJson) {
        super(Text.literal("Create Room"));
        this.tablePos  = tablePos;
        this.stateJson = stateJson;
        try {
            var obj = com.google.gson.JsonParser.parseString(stateJson).getAsJsonObject();
            bankBalance = obj.has("bankBalance") ? obj.get("bankBalance").getAsInt() : 0;
        } catch (Exception ignored) {}
    }

    @Override
    protected void init() {
        int cx = width / 2, cy = height / 2;
        int x0 = cx - W / 2, y0 = cy - H / 2;

        // Cancel
        addDrawableChild(ButtonWidget.builder(Text.literal("✕"),
                b -> close())
                .dimensions(x0 + W - 22, y0 + 6, 16, 14).build());

        // 5 bet-level buttons arranged horizontally
        int btnW = 48, btnH = 28, gap = 6;
        int totalW = BET_LEVELS.length * (btnW + gap) - gap;
        int startX = cx - totalW / 2;
        int btnY = y0 + 110;

        for (int i = 0; i < BET_LEVELS.length; i++) {
            final int lvl = BET_LEVELS[i];
            int minRequired = lvl * MIN_BALANCE_MULTIPLIER;
            boolean canAfford = bankBalance >= minRequired;
            String label = lvl >= 10_000 ? "10K" : lvl >= 1_000 ? "1K" : String.valueOf(lvl);
            var btn = ButtonWidget.builder(Text.literal(label + " ZC"),
                    b -> {
                        if (!canAfford) return;
                        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                                new PokerNetworking.PlayerActionPayload(tablePos, "CREATE", lvl, ""));
                        PokerTableScreen ts = new PokerTableScreen(tablePos, stateJson);
                        client.setScreen(ts);
                        ts.updateState(stateJson);
                    })
                    .dimensions(startX + i * (btnW + gap), btnY, btnW, btnH);
            addDrawableChild(btn.build()).active = canAfford;
        }

        // Confirm button (disabled until level selected — but since we auto-create on click, not needed)
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        if (framesOpen < 10) framesOpen++;
        ctx.fill(0, 0, width, height, 0xFF000000);

        int cx = width / 2, cy = height / 2;
        int x0 = cx - W / 2, y0 = cy - H / 2;

        // Panel fill
        ctx.fill(x0, y0, x0 + W, y0 + H, C_BG);

        // Gold border — 2 layers
        drawBorder(ctx, x0,     y0,     W,     H,     C_BORDER, 2);
        drawBorder(ctx, x0 + 4, y0 + 4, W - 8, H - 8, 0xFF5C4A1A, 1);

        ctx.drawCenteredTextWithShadow(textRenderer, "♠ CREATE ROOM ♠", cx, y0 + 16, C_GOLD);
        ctx.drawCenteredTextWithShadow(textRenderer,
                "Bet level (BB) · ZCoin: " + bankBalance, cx, y0 + 34, C_WHITE);
        ctx.fill(x0 + 20, y0 + 44, x0 + W - 20, y0 + 45, 0xFF444400);
        ctx.drawCenteredTextWithShadow(textRenderer,
                "Min BB×10 ZC to enter", cx, y0 + 52, C_GRAY);

        int btnW = 48, gap = 6;
        int totalW = BET_LEVELS.length * (btnW + gap) - gap;
        int startX = cx - totalW / 2;
        int infoY = y0 + 95;
        for (int i = 0; i < BET_LEVELS.length; i++) {
            int minReq = BET_LEVELS[i] * MIN_BALANCE_MULTIPLIER;
            String s = (minReq >= 1_000 ? (minReq / 1000) + "K" : String.valueOf(minReq)) + " ZC";
            int bx = startX + i * (btnW + gap) + btnW / 2;
            ctx.drawCenteredTextWithShadow(textRenderer, s, bx, infoY,
                    bankBalance >= minReq ? C_GREEN : C_GRAY);
        }

        super.render(ctx, mouseX, mouseY, delta);
    }

    private void drawBorder(DrawContext ctx, int x, int y, int w, int h, int color, int t) {
        ctx.fill(x,         y,         x + w,     y + t,     color);
        ctx.fill(x,         y + h - t, x + w,     y + h,     color);
        ctx.fill(x,         y,         x + t,     y + h,     color);
        ctx.fill(x + w - t, y,         x + w,     y + h,     color);
    }

    @Override public void renderBackground(DrawContext ctx, int mx, int my, float d) {}
    @Override public boolean shouldPause() { return false; }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (framesOpen < 5) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { close(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
