package com.pokermc.screen;

import com.pokermc.network.BlackjackNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

/** Create Blackjack room — choose max bet. */
public class CreateBlackjackRoomScreen extends Screen {

    private static final int W = 300, H = 220;
    private static final int C_BG     = 0xFF140A14;
    private static final int C_BORDER = 0xFFCC6699;
    private static final int C_PINK   = 0xFFFF88AA;
    private static final int C_WHITE  = 0xFFFFFFFF;
    private static final int C_GRAY   = 0xFF888888;
    private static final int C_GREEN  = 0xFF55CC55;

    private static final int[] MAX_BETS = {10, 50, 100, 500, 1000};
    private static final int MIN_BALANCE_MULTIPLIER = 10;

    private final BlockPos tablePos;
    private final String stateJson;
    private int bankBalance = 0;
    private int framesOpen = 0;

    public CreateBlackjackRoomScreen(BlockPos tablePos, String stateJson) {
        super(Text.literal("Create Blackjack Room"));
        this.tablePos = tablePos;
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

        addDrawableChild(ButtonWidget.builder(Text.literal("✕"), b -> close())
                .dimensions(x0 + W - 22, y0 + 6, 16, 14).build());

        int btnW = 48, btnH = 28, gap = 6;
        int totalW = MAX_BETS.length * (btnW + gap) - gap;
        int startX = cx - totalW / 2;
        int btnY = y0 + 110;

        for (int i = 0; i < MAX_BETS.length; i++) {
            final int max = MAX_BETS[i];
            int minRequired = max * MIN_BALANCE_MULTIPLIER;
            boolean canAfford = bankBalance >= minRequired;
            String label = max >= 1000 ? "1K" : String.valueOf(max);
            var btn = ButtonWidget.builder(Text.literal(label + " ZC"),
                    b -> {
                        if (!canAfford) return;
                        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                                new BlackjackNetworking.BlackjackActionPayload(tablePos, "CREATE", max, ""));
                        BlackjackTableScreen ts = new BlackjackTableScreen(tablePos, stateJson);
                        client.setScreen(ts);
                        ts.updateState(stateJson);
                    })
                    .dimensions(startX + i * (btnW + gap), btnY, btnW, btnH);
            addDrawableChild(btn.build()).active = canAfford;
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        if (framesOpen < 10) framesOpen++;
        ctx.fill(0, 0, width, height, 0xFF000000);
        int cx = width / 2, cy = height / 2;
        int x0 = cx - W / 2, y0 = cy - H / 2;

        ctx.fill(x0, y0, x0 + W, y0 + H, C_BG);
        drawBorder(ctx, x0, y0, W, H, C_BORDER, 2);
        drawBorder(ctx, x0 + 4, y0 + 4, W - 8, H - 8, 0xFF663355, 1);

        ctx.drawCenteredTextWithShadow(textRenderer, "Create Blackjack Room", cx, y0 + 16, C_PINK);
        ctx.drawCenteredTextWithShadow(textRenderer,
                "Max bet limit · ZCoin: " + bankBalance, cx, y0 + 34, C_WHITE);
        ctx.fill(x0 + 20, y0 + 44, x0 + W - 20, y0 + 45, 0xFF442244);
        ctx.drawCenteredTextWithShadow(textRenderer, "Need max×10 ZC to join", cx, y0 + 52, C_GRAY);

        int btnW = 48, gap = 6;
        int totalW = MAX_BETS.length * (btnW + gap) - gap;
        int startX = cx - totalW / 2;
        for (int i = 0; i < MAX_BETS.length; i++) {
            int minReq = MAX_BETS[i] * MIN_BALANCE_MULTIPLIER;
            String s = (minReq >= 1000 ? (minReq/1000) + "K" : String.valueOf(minReq)) + " ZC";
            int bx = startX + i * (btnW + gap) + btnW / 2;
            ctx.drawCenteredTextWithShadow(textRenderer, s, bx, y0 + 95,
                    bankBalance >= minReq ? C_GREEN : C_GRAY);
        }

        super.render(ctx, mouseX, mouseY, delta);
    }

    private void drawBorder(DrawContext ctx, int x, int y, int w, int h, int color, int t) {
        ctx.fill(x, y, x + w, y + t, color);
        ctx.fill(x, y + h - t, x + w, y + h, color);
        ctx.fill(x, y, x + t, y + h, color);
        ctx.fill(x + w - t, y, x + w, y + h, color);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (framesOpen < 5) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override public void renderBackground(DrawContext ctx, int mx, int my, float d) {}
    @Override public boolean shouldPause() { return false; }
}
