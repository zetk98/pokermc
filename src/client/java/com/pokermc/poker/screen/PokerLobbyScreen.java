package com.pokermc.poker.screen;

import com.google.gson.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

import com.pokermc.common.screen.TradeScreen;

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
            Identifier.of("casinocraft", "textures/gui/lobby_bg.png");
    private static final int BG_W = 260, BG_H = 180;

    private static final int C_GOLD  = 0xFFFFD700;
    private static final int C_WHITE = 0xFFFFFFFF;
    private static final int C_GRAY  = 0xFF888888;
    private static final int C_GREEN = 0xFF55CC55;
    private static final int C_CYAN  = 0xFF88EEFF;

    private final BlockPos tablePos;
    public BlockPos getTablePos() { return tablePos; }
    private String stateJson = "{}";
    private int framesOpen = 0;

    // Parsed state
    private final List<String> playerNames = new ArrayList<>();
    private boolean isEmpty = true;
    private int bankBalance = 0;
    private int betLevel = 10;
    private int minZcToJoin = 10;

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

        int btnW = 120, btnH = 24;
        int btnX = bgX + (BG_W - btnW) / 2;

        // ── Button 1: Create Room / Enter Table / Join Game ────────────────────
        String myName = client.player != null ? client.player.getName().getString() : "";
        boolean alreadyInGame = playerNames.contains(myName);
        if (isEmpty) {
            boolean canCreate = bankBalance >= minZcToJoin;
            addDrawableChild(ButtonWidget.builder(Text.literal("✦ Create Room"),
                    b -> {
                        if (!canCreate) return;
                        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                                new com.pokermc.poker.network.PokerNetworking.PlayerActionPayload(
                                        tablePos, "CREATE", 0, ""));
                        PokerTableScreen ts = new PokerTableScreen(tablePos, stateJson);
                        client.setScreen(ts);
                        ts.updateState(stateJson);
                    })
                    .dimensions(btnX, bgY + 75, btnW, btnH).build()).active = canCreate;
        } else if (alreadyInGame) {
            addDrawableChild(ButtonWidget.builder(Text.literal("▶ Enter Table"),
                    b -> {
                        PokerTableScreen ts = new PokerTableScreen(tablePos, stateJson);
                        client.setScreen(ts);
                        ts.updateState(stateJson);
                    })
                    .dimensions(btnX, bgY + 75, btnW, btnH).build());
        } else {
            boolean canJoin = bankBalance >= minZcToJoin;
            var joinBtn = ButtonWidget.builder(Text.literal("▶ Join Game"),
                    b -> {
                        if (!canJoin) return;
                        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                                new com.pokermc.poker.network.PokerNetworking.PlayerActionPayload(
                                        tablePos, "JOIN", 0, ""));
                        PokerTableScreen ts = new PokerTableScreen(tablePos, stateJson);
                        client.setScreen(ts);
                        ts.updateState(stateJson);
                    })
                    .dimensions(btnX, bgY + 75, btnW, btnH);
            addDrawableChild(joinBtn.build()).active = canJoin;
        }

        // ── Button 2: Exchange ────────────────────────────────────────────────
        addDrawableChild(ButtonWidget.builder(Text.literal("⇄ Exchange"),
                b -> client.setScreen(new TradeScreen(tablePos, stateJson)))
                .dimensions(btnX, bgY + 105, btnW, btnH).build());
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
            if (obj.has("pendingPlayers"))
                for (JsonElement e : obj.getAsJsonArray("pendingPlayers"))
                    playerNames.add(e.getAsString() + " (waiting)");

            isEmpty     = !obj.has("isEmpty") || obj.get("isEmpty").getAsBoolean();
            betLevel    = obj.has("betLevel")     ? obj.get("betLevel").getAsInt()     : 10;
            bankBalance = obj.has("bankBalance")  ? obj.get("bankBalance").getAsInt()  : 0;
            minZcToJoin = obj.has("minZcToJoin")  ? obj.get("minZcToJoin").getAsInt()  : 10;
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
        if (framesOpen < 10) framesOpen++;
        int cx = width / 2, cy = height / 2;
        int bgX = cx - BG_W / 2, bgY = cy - BG_H / 2;

        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, TEX_BG, bgX, bgY, 0, 0, BG_W, BG_H, BG_W, BG_H);

        // Title
        ctx.drawCenteredTextWithShadow(textRenderer, "♠ POKER TABLE ♠", cx, bgY + 18, C_GOLD);

        if (isEmpty) {
            ctx.drawCenteredTextWithShadow(textRenderer,
                    "Empty — Create room to start", cx, bgY + 38, C_GRAY);
        } else {
            String bb = betLevel >= 1000 ? (betLevel/1000) + "K" : String.valueOf(betLevel);
            ctx.drawCenteredTextWithShadow(textRenderer,
                    "BB: " + bb + " ZC  ·  " + playerNames.size() + " seated",
                    cx - 30, bgY + 38, C_CYAN);
        }

        // Player list — right column, balanced
        if (!isEmpty && !playerNames.isEmpty()) {
            int colLeft = bgX + BG_W - 62;
            int lineY = bgY + 52;
            ctx.drawTextWithShadow(textRenderer, "Players:", colLeft, lineY - 12, C_GRAY);
            for (int i = 0; i < Math.min(playerNames.size(), 6); i++) {
                String name = playerNames.get(i);
                if (textRenderer.getWidth(name) > 52) name = name.substring(0, 7) + "..";
                ctx.drawTextWithShadow(textRenderer, "• " + name, colLeft, lineY + i * 10, C_WHITE);
            }
            if (playerNames.size() > 6)
                ctx.drawTextWithShadow(textRenderer, "+" + (playerNames.size() - 6), colLeft, lineY + 60, C_GRAY);
        }

        // ZCoin (min to join)
        ctx.drawCenteredTextWithShadow(textRenderer,
                "ZCoin: " + bankBalance + " (min " + minZcToJoin + " to join)", cx, bgY + BG_H - 16, C_GREEN);

        super.render(ctx, mouseX, mouseY, delta);
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
