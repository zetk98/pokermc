package com.pokermc.bang.screen;

import com.google.gson.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Bang! lobby: Sảnh chờ - hiển thị tên + head người chơi. Chủ phòng Start khi đủ người.
 */
public class BangLobbyScreen extends Screen {

    private static final Identifier TEX_BG = Identifier.of("casinocraft", "textures/gui/lobby_bg.png");
    private static final int BG_W = 260, BG_H = 180;
    private static final int C_GOLD = 0xFFFFD700, C_WHITE = 0xFFFFFFFF, C_GRAY = 0xFF888888, C_ORANGE = 0xFFFF8844;

    private final BlockPos tablePos;
    private String stateJson = "{}";
    private final List<String> playerNames = new ArrayList<>();
    private boolean isEmpty = true;
    private int maxPlayers = 2;
    private int selectedMaxPlayers = 2;
    private String phase = "WAITING";
    private String gameOverWinner = "";
    private boolean isHost = false;

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
            // Create: chọn số người 2-7
            addDrawableChild(ButtonWidget.builder(Text.literal("2"), b -> selectedMaxPlayers = 2)
                    .dimensions(btnX - 100, bgY + 55, 28, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("4"), b -> selectedMaxPlayers = 4)
                    .dimensions(btnX - 68, bgY + 55, 28, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("5"), b -> selectedMaxPlayers = 5)
                    .dimensions(btnX - 36, bgY + 55, 28, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("6"), b -> selectedMaxPlayers = 6)
                    .dimensions(btnX - 4, bgY + 55, 28, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("7"), b -> selectedMaxPlayers = 7)
                    .dimensions(btnX + 28, bgY + 55, 28, 20).build());

            addDrawableChild(ButtonWidget.builder(Text.literal("✦ Create (" + selectedMaxPlayers + "p)"),
                    b -> {
                        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                                new com.pokermc.bang.network.BangNetworking.BangActionPayload(
                                        tablePos, "CREATE", selectedMaxPlayers, ""));
                        // Stay in lobby; server will broadcast state
                    })
                    .dimensions(btnX, bgY + 85, btnW, btnH).build());
        } else if (alreadyInGame) {
            if (isHost && "GAME_OVER".equals(phase)) {
                addDrawableChild(ButtonWidget.builder(Text.literal("New Game"),
                        b -> {
                            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                                    new com.pokermc.bang.network.BangNetworking.BangActionPayload(tablePos, "NEW_GAME", 0, ""));
                        })
                        .dimensions(btnX + 60, bgY + 75, 80, btnH).build());
            } else if (isHost && playerNames.size() >= maxPlayers && "WAITING".equals(phase)) {
                addDrawableChild(ButtonWidget.builder(Text.literal("▶ Start"),
                        b -> {
                            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                                    new com.pokermc.bang.network.BangNetworking.BangActionPayload(tablePos, "START", 0, ""));
                        })
                        .dimensions(btnX + 60, bgY + 75, 80, btnH).build());
            }
        } else {
            addDrawableChild(ButtonWidget.builder(Text.literal("▶ Join"),
                    b -> {
                        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                                new com.pokermc.bang.network.BangNetworking.BangActionPayload(
                                        tablePos, "JOIN", 0, ""));
                        // Stay in lobby; server will broadcast state
                    })
                    .dimensions(btnX, bgY + 75, btnW, btnH).build());
        }
    }

    private void parseState(String json) {
        stateJson = json;
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            playerNames.clear();
            if (obj.has("players")) {
                for (JsonElement e : obj.getAsJsonArray("players")) {
                    playerNames.add(e.getAsJsonObject().get("name").getAsString());
                }
            }
            if (obj.has("pendingPlayers"))
                for (JsonElement e : obj.getAsJsonArray("pendingPlayers"))
                    playerNames.add(e.getAsString());
            isEmpty = !obj.has("isEmpty") || obj.get("isEmpty").getAsBoolean();
            maxPlayers = obj.has("maxPlayers") ? obj.get("maxPlayers").getAsInt() : 2;
            phase = obj.has("phase") ? obj.get("phase").getAsString() : "WAITING";
            gameOverWinner = obj.has("gameOverWinner") ? obj.get("gameOverWinner").getAsString() : "";
            String myName = client != null && client.player != null ? client.player.getName().getString() : "";
            isHost = !playerNames.isEmpty() && playerNames.get(0).equals(myName);
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

        ctx.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, TEX_BG, bgX, bgY, 0, 0, BG_W, BG_H, BG_W, BG_H);

        ctx.drawCenteredTextWithShadow(textRenderer, "♠ BANG! TABLE ♠", cx, bgY + 18, C_ORANGE);

        if (isEmpty) {
            ctx.drawCenteredTextWithShadow(textRenderer, "Empty — Create room to start", cx, bgY + 38, C_GRAY);
        } else if ("GAME_OVER".equals(phase) && !gameOverWinner.isEmpty()) {
            String msg = "Outlaws".equals(gameOverWinner) ? "Outlaws win!" : "Renegade".equals(gameOverWinner) ? "Renegade wins!" : "Law wins!";
            ctx.drawCenteredTextWithShadow(textRenderer, msg, cx, bgY + 38, C_ORANGE);
        } else {
            ctx.drawCenteredTextWithShadow(textRenderer,
                    "Players: " + playerNames.size() + " / " + maxPlayers, cx, bgY + 38, C_GOLD);
        }

        if (!isEmpty && !playerNames.isEmpty()) {
            int headSize = 20;
            int startX = bgX + 20;
            int startY = bgY + 52;
            ctx.drawTextWithShadow(textRenderer, "Players:", startX, startY - 12, C_GRAY);
            for (int i = 0; i < Math.min(playerNames.size(), 7); i++) {
                String name = playerNames.get(i);
                int x = startX + (i % 4) * (headSize + 28);
                int y = startY + (i / 4) * (headSize + 14);
                PlayerListEntry entry = client != null && client.player != null
                        ? client.player.networkHandler.getPlayerListEntry(name) : null;
                if (entry != null) {
                    var skin = entry.getSkinTextures().body().texturePath();
                    ctx.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, skin, x, y, 8f, 8f, headSize, headSize, 8, 8, 64, 64);
                    ctx.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, skin, x, y, 40f, 8f, headSize, headSize, 8, 8, 64, 64);
                } else {
                    ctx.fill(x, y, x + headSize, y + headSize, 0xFF555555);
                    ctx.drawStrokedRectangle(x, y, headSize, headSize, 0xFF888888);
                }
                String display = name.length() > 10 ? name.substring(0, 8) + ".." : name;
                ctx.drawTextWithShadow(textRenderer, display, x + headSize + 4, y + 4, C_WHITE);
            }
        }

        super.render(ctx, mouseX, mouseY, delta);
    }
}
