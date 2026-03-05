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
 * Bang! lobby: Create (select 4/5/6 players) or Join.
 */
public class BangLobbyScreen extends Screen {

    private static final Identifier TEX_BG = Identifier.of("casinocraft", "textures/gui/lobby_bg.png");
    private static final int BG_W = 260, BG_H = 180;
    private static final int C_GOLD = 0xFFFFD700, C_WHITE = 0xFFFFFFFF, C_GRAY = 0xFF888888, C_ORANGE = 0xFFFF8844;

    private final BlockPos tablePos;
    private String stateJson = "{}";
    private final List<String> playerNames = new ArrayList<>();
    private boolean isEmpty = true;
    private int maxPlayers = 4;
    private int selectedMaxPlayers = 4;

    public BangLobbyScreen(BlockPos pos, String stateJson) {
        super(Text.literal("Bang! Lobby"));
        this.tablePos = pos;
        parseState(stateJson);
    }

    @Override
    protected void init() {
        int cx = width / 2, cy = height / 2;
        int bgX = cx - BG_W / 2, bgY = cy - BG_H / 2;

        addDrawableChild(ButtonWidget.builder(Text.literal("✕"), b -> close())
                .dimensions(bgX + BG_W - 20, bgY + 4, 16, 14).build());

        String myName = client != null && client.player != null ? client.player.getName().getString() : "";
        boolean alreadyInGame = playerNames.contains(myName);

        int btnW = 120, btnH = 24;
        int btnX = bgX + (BG_W - btnW) / 2;

        if (isEmpty) {
            // Create: first select player count
            addDrawableChild(ButtonWidget.builder(Text.literal("4 Players"), b -> selectedMaxPlayers = 4)
                    .dimensions(btnX - 70, bgY + 55, 50, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("5 Players"), b -> selectedMaxPlayers = 5)
                    .dimensions(btnX - 15, bgY + 55, 50, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("6 Players"), b -> selectedMaxPlayers = 6)
                    .dimensions(btnX + 40, bgY + 55, 50, 20).build());

            addDrawableChild(ButtonWidget.builder(Text.literal("✦ Create (" + selectedMaxPlayers + "p)"),
                    b -> {
                        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                                new com.pokermc.network.BangNetworking.BangActionPayload(
                                        tablePos, "CREATE", selectedMaxPlayers, ""));
                        BangTableScreen ts = new BangTableScreen(tablePos, stateJson);
                        client.setScreen(ts);
                        ts.updateState(stateJson);
                    })
                    .dimensions(btnX, bgY + 85, btnW, btnH).build());
        } else if (alreadyInGame) {
            addDrawableChild(ButtonWidget.builder(Text.literal("▶ Enter Table"),
                    b -> {
                        BangTableScreen ts = new BangTableScreen(tablePos, stateJson);
                        client.setScreen(ts);
                        ts.updateState(stateJson);
                    })
                    .dimensions(btnX, bgY + 75, btnW, btnH).build());
        } else {
            addDrawableChild(ButtonWidget.builder(Text.literal("▶ Join Game"),
                    b -> {
                        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                                new com.pokermc.network.BangNetworking.BangActionPayload(
                                        tablePos, "JOIN", 0, ""));
                        BangTableScreen ts = new BangTableScreen(tablePos, stateJson);
                        client.setScreen(ts);
                        ts.updateState(stateJson);
                    })
                    .dimensions(btnX, bgY + 75, btnW, btnH).build());
        }
    }

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
            isEmpty = !obj.has("isEmpty") || obj.get("isEmpty").getAsBoolean();
            maxPlayers = obj.has("maxPlayers") ? obj.get("maxPlayers").getAsInt() : 4;
        } catch (Exception ignored) {}
    }

    public void updateState(String json) {
        parseState(json);
        if (client != null) { clearChildren(); init(); }
    }

    @Override public void renderBackground(DrawContext ctx, int mx, int my, float d) {}
    @Override public boolean shouldPause() { return false; }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        int cx = width / 2, cy = height / 2;
        int bgX = cx - BG_W / 2, bgY = cy - BG_H / 2;

        ctx.drawTexture(TEX_BG, bgX, bgY, 0, 0, BG_W, BG_H, BG_W, BG_H);

        ctx.drawCenteredTextWithShadow(textRenderer, "♠ BANG! TABLE ♠", cx, bgY + 18, C_ORANGE);

        if (isEmpty) {
            ctx.drawCenteredTextWithShadow(textRenderer, "Empty — Create room to start", cx, bgY + 38, C_GRAY);
        } else {
            ctx.drawCenteredTextWithShadow(textRenderer,
                    "Players: " + playerNames.size() + " / " + maxPlayers, cx, bgY + 38, C_GOLD);
        }

        if (!isEmpty && !playerNames.isEmpty()) {
            int colLeft = bgX + BG_W - 62;
            int lineY = bgY + 52;
            ctx.drawTextWithShadow(textRenderer, "Players:", colLeft, lineY - 12, C_GRAY);
            for (int i = 0; i < Math.min(playerNames.size(), 6); i++) {
                String name = playerNames.get(i);
                if (textRenderer.getWidth(name) > 52) name = name.substring(0, 7) + "..";
                ctx.drawTextWithShadow(textRenderer, "• " + name, colLeft, lineY + i * 10, C_WHITE);
            }
        }

        super.render(ctx, mouseX, mouseY, delta);
    }
}
