package com.pokermc.screen;

import com.google.gson.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Landing lobby shown when a player right-clicks the poker table.
 *
 * Always 3 buttons:
 *  1. "Create Room" (isEmpty) or "Join Game" (has players)
 *  2. "Exchange" (item ↔ ZC trade screen)
 *  3. "Top Players" (leaderboard toggle)
 */
public class PokerLobbyScreen extends Screen {

    private static final Identifier TEX_BG =
            Identifier.of("pokermc", "textures/gui/lobby_bg.png");
    private static final int BG_W = 260, BG_H = 180;

    private static final int C_GOLD  = 0xFFFFD700;
    private static final int C_WHITE = 0xFFFFFFFF;
    private static final int C_GRAY  = 0xFF888888;
    private static final int C_GREEN = 0xFF55CC55;
    private static final int C_CYAN  = 0xFF88EEFF;

    private final BlockPos tablePos;
    private String stateJson = "{}";

    // Parsed state
    private final List<String> playerNames = new ArrayList<>();
    private final List<LeaderEntry> leaderboard = new ArrayList<>();
    private boolean isEmpty = true;
    private int bankBalance = 0;
    private int betLevel = 10;
    private boolean showLeaderboard = false;

    public PokerLobbyScreen(BlockPos pos, String stateJson) {
        super(Text.literal("Poker Lobby"));
        this.tablePos = pos;
        parseState(stateJson);
    }

    @Override
    protected void init() {
        int cx = width / 2, cy = height / 2;
        int bgX = cx - BG_W / 2, bgY = cy - BG_H / 2;

        // Close button
        addDrawableChild(ButtonWidget.builder(Text.literal("✕"),
                b -> close())
                .dimensions(bgX + BG_W - 20, bgY + 4, 16, 14).build());

        int btnW = 110, btnH = 22;
        int btnX = bgX + (BG_W - btnW) / 2;

        // ── Button 1: Create Room / Join Game ─────────────────────────────────
        if (isEmpty) {
            addDrawableChild(ButtonWidget.builder(Text.literal("✦ Create Room"),
                    b -> client.setScreen(new CreateRoomScreen(tablePos, stateJson)))
                    .dimensions(btnX, bgY + 80, btnW, btnH).build());
        } else {
            addDrawableChild(ButtonWidget.builder(Text.literal("▶ Join Game"),
                    b -> {
                        PokerTableScreen ts = new PokerTableScreen(tablePos, stateJson);
                        client.setScreen(ts);
                        ts.updateState(stateJson);
                    })
                    .dimensions(btnX, bgY + 80, btnW, btnH).build());
        }

        // ── Button 2: Exchange ────────────────────────────────────────────────
        addDrawableChild(ButtonWidget.builder(Text.literal("⇄ Exchange"),
                b -> client.setScreen(new TradeScreen(tablePos, stateJson)))
                .dimensions(btnX, bgY + 107, btnW, btnH).build());

        // ── Button 3: Top Players ─────────────────────────────────────────────
        addDrawableChild(ButtonWidget.builder(Text.literal("★ Top Players"),
                b -> showLeaderboard = !showLeaderboard)
                .dimensions(btnX, bgY + 134, btnW, btnH).build());
    }

    // ── State ─────────────────────────────────────────────────────────────────

    private void parseState(String json) {
        stateJson = json;
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();

            playerNames.clear();
            if (obj.has("players"))
                for (JsonElement e : obj.getAsJsonArray("players"))
                    playerNames.add(e.getAsJsonObject().get("name").getAsString());

            isEmpty     = !obj.has("isEmpty") || obj.get("isEmpty").getAsBoolean();
            betLevel    = obj.has("betLevel")     ? obj.get("betLevel").getAsInt()     : 10;
            bankBalance = obj.has("bankBalance")  ? obj.get("bankBalance").getAsInt()  : 0;

            leaderboard.clear();
            if (obj.has("leaderboard"))
                for (JsonElement e : obj.getAsJsonArray("leaderboard")) {
                    JsonObject lo = e.getAsJsonObject();
                    leaderboard.add(new LeaderEntry(
                            lo.get("name").getAsString(), lo.get("chips").getAsInt()));
                }
        } catch (Exception ignored) {}
    }

    public void updateState(String json) {
        boolean wasEmpty = isEmpty;
        parseState(json);
        if (client != null) { clearChildren(); init(); }
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    @Override public void renderBackground(DrawContext ctx, int mx, int my, float d) {}
    @Override public boolean shouldPause() { return false; }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        int cx = width / 2, cy = height / 2;
        int bgX = cx - BG_W / 2, bgY = cy - BG_H / 2;

        ctx.drawTexture(TEX_BG, bgX, bgY, 0, 0, BG_W, BG_H, BG_W, BG_H);

        // Title
        ctx.drawCenteredTextWithShadow(textRenderer, "♠ POKER TABLE ♠", cx, bgY + 18, C_GOLD);

        if (isEmpty) {
            ctx.drawCenteredTextWithShadow(textRenderer,
                    "Empty — be the first to create a room", cx, bgY + 34, C_GRAY);
        } else {
            String bb = betLevel >= 1000 ? (betLevel/1000) + "K" : String.valueOf(betLevel);
            ctx.drawCenteredTextWithShadow(textRenderer,
                    "BB: " + bb + " ZC  ·  " + playerNames.size() + " seated",
                    cx, bgY + 34, C_CYAN);
            int lineY = bgY + 46;
            for (int i = 0; i < Math.min(playerNames.size(), 4); i++)
                ctx.drawCenteredTextWithShadow(textRenderer, "• " + playerNames.get(i),
                        cx, lineY + i * 8, C_WHITE);
            if (playerNames.size() > 4)
                ctx.drawCenteredTextWithShadow(textRenderer,
                        "+" + (playerNames.size() - 4) + " more", cx, lineY + 32, C_GRAY);
        }

        // Bank balance
        ctx.drawCenteredTextWithShadow(textRenderer,
                "Wallet: " + bankBalance + " ZC", cx, bgY + BG_H - 14, C_GREEN);

        super.render(ctx, mouseX, mouseY, delta);
        if (showLeaderboard) renderLeaderboard(ctx, cx, cy);
    }

    private void renderLeaderboard(DrawContext ctx, int cx, int cy) {
        int rows = Math.max(leaderboard.size(), 1);
        int lw = 170, lh = 28 + rows * 12 + 6;
        int lx = cx - lw / 2, ly = cy - lh / 2;

        ctx.fill(lx, ly, lx + lw, ly + lh, 0xEE050510);
        for (int i = 0; i < 2; i++) {
            ctx.fill(lx+i, ly+i, lx+lw-i, ly+i+1, C_GOLD);
            ctx.fill(lx+i, ly+lh-i-1, lx+lw-i, ly+lh-i, C_GOLD);
            ctx.fill(lx+i, ly+i, lx+i+1, ly+lh-i, C_GOLD);
            ctx.fill(lx+lw-i-1, ly+i, lx+lw-i, ly+lh-i, C_GOLD);
        }
        ctx.drawCenteredTextWithShadow(textRenderer, "★ Top Players ★", cx, ly + 8, C_GOLD);
        ctx.fill(lx + 8, ly + 19, lx + lw - 8, ly + 20, 0xFF444400);

        if (leaderboard.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer, "No data yet", cx, ly + 26, C_GRAY);
        } else {
            for (int i = 0; i < leaderboard.size(); i++) {
                String line = (i+1) + ". " + leaderboard.get(i).name()
                        + "  " + leaderboard.get(i).chips() + " ZC";
                int col = i==0 ? C_GOLD : i==1 ? 0xFFCCCCCC : i==2 ? 0xFFCD7F32 : C_GRAY;
                ctx.drawCenteredTextWithShadow(textRenderer, line, cx, ly + 24 + i * 12, col);
            }
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            if (showLeaderboard) { showLeaderboard = false; return true; }
            close(); return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    public record LeaderEntry(String name, int chips) {}
}
