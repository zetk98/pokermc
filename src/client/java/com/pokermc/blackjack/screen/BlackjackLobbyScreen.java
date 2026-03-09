package com.pokermc.blackjack.screen;

import com.google.gson.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

import com.pokermc.common.screen.TradeScreen;

/** Blackjack lobby — pink theme, cherry blossom animation. */
public class BlackjackLobbyScreen extends Screen {

    private record PlayerEntry(String name, int chips, boolean isHost) {}

    private static final Identifier TEX_BG = Identifier.of("casinocraft", "textures/gui/lobby_bg_blackjack.png");
    private static final int BG_W = 260, BG_H = 180;
    private static final int NUM_FRAMES = 8;
    private static final int TEX_H = BG_H * NUM_FRAMES;  // 1440

    private static final int C_GOLD  = 0xFFFFD700;
    private static final int C_WHITE = 0xFFFFFFFF;
    private static final int C_GRAY  = 0xFF888888;
    private static final int C_PINK  = 0xFFFF88AA;
    private static final int C_MAGENTA = 0xFFCC66AA;

    private final BlockPos tablePos;
    private String stateJson = "{}";
    private int framesOpen = 0;
    private final List<PlayerEntry> playerEntries = new ArrayList<>();
    private boolean isEmpty = true;
    private int bankBalance = 0;
    private int maxBet = 100;
    private int minZcToJoin = 10;

    public BlackjackLobbyScreen(BlockPos pos, String stateJson) {
        super(Text.literal("Blackjack Lobby"));
        this.tablePos = pos;
        parseState(stateJson);
    }

    /** Blackjack tạm gác — hiển thị "Đang phát triển" khi người chơi nhấn vào. */
    private static final boolean WIP_MODE = true;

    @Override
    protected void init() {
        int cx = width / 2, cy = height / 2;
        int bgX = cx - BG_W / 2, bgY = cy - BG_H / 2;

        addDrawableChild(ButtonWidget.builder(Text.literal("✕"), b -> close())
                .dimensions(bgX + BG_W - 20, bgY + 4, 16, 14).build());

        if (WIP_MODE) {
            addDrawableChild(ButtonWidget.builder(Text.literal("Đóng"),
                    b -> close())
                    .dimensions(cx - 50, bgY + BG_H - 40, 100, 20).build());
            return;
        }

        int btnW = 120, btnH = 24;
        int btnX = bgX + (BG_W - btnW) / 2;
        int exchangeY = bgY + 105;

        String myName = client.player != null ? client.player.getName().getString() : "";
        boolean alreadyInGame = playerEntries.stream().anyMatch(e -> e.name().equals(myName));

        if (isEmpty) {
            boolean canCreate = bankBalance >= minZcToJoin;
            addDrawableChild(ButtonWidget.builder(Text.literal("✦ Create Room"),
                    b -> {
                        if (!canCreate) return;
                        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                                new com.pokermc.blackjack.network.BlackjackNetworking.BlackjackActionPayload(
                                        tablePos, "CREATE", 0, ""));
                        BlackjackTableScreen ts = new BlackjackTableScreen(tablePos, stateJson);
                        client.setScreen(ts);
                        ts.updateState(stateJson);
                    })
                    .dimensions(btnX, bgY + 75, btnW, btnH).build()).active = canCreate;
        } else if (alreadyInGame) {
            boolean isHost = playerEntries.stream().anyMatch(e -> e.name().equals(myName) && e.isHost());
            int playerCount = (int) playerEntries.stream().filter(e -> !e.name().endsWith(" (pending)")).count();
            boolean canStart = isHost && playerCount >= 2;

            addDrawableChild(ButtonWidget.builder(Text.literal("Enter Table"),
                    b -> {
                        BlackjackTableScreen ts = new BlackjackTableScreen(tablePos, stateJson);
                        client.setScreen(ts);
                        ts.updateState(stateJson);
                    })
                    .dimensions(btnX, bgY + 75, btnW, btnH).build());

            if (canStart) {
                addDrawableChild(ButtonWidget.builder(Text.literal("▶ Start"),
                        b -> {
                            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                                    new com.pokermc.blackjack.network.BlackjackNetworking.BlackjackActionPayload(
                                            tablePos, "START", 0, ""));
                            BlackjackTableScreen ts = new BlackjackTableScreen(tablePos, stateJson);
                            client.setScreen(ts);
                            ts.updateState(stateJson);
                        })
                        .dimensions(btnX, bgY + 102, btnW, btnH).build());
                exchangeY = bgY + 130;
            }
        } else {
            boolean canJoin = bankBalance >= minZcToJoin;
            var joinBtn = ButtonWidget.builder(Text.literal("Join Game"),
                    b -> {
                        if (!canJoin) return;
                        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                                new com.pokermc.blackjack.network.BlackjackNetworking.BlackjackActionPayload(
                                        tablePos, "JOIN", 0, ""));
                        BlackjackTableScreen ts = new BlackjackTableScreen(tablePos, stateJson);
                        client.setScreen(ts);
                        ts.updateState(stateJson);
                    })
                    .dimensions(btnX, bgY + 75, btnW, btnH);
            addDrawableChild(joinBtn.build()).active = canJoin;
        }

        addDrawableChild(ButtonWidget.builder(Text.literal("Exchange"),
                b -> client.setScreen(new TradeScreen(tablePos, stateJson, true)))
                .dimensions(btnX, exchangeY, btnW, btnH).build());
    }

    private void parseState(String json) {
        stateJson = json;
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            String dealer = obj.has("dealer") ? obj.get("dealer").getAsString() : "";
            playerEntries.clear();
            if (obj.has("players"))
                for (JsonElement e : obj.getAsJsonArray("players")) {
                    JsonObject p = e.getAsJsonObject();
                    String name = p.get("name").getAsString();
                    int chips = p.has("chips") ? p.get("chips").getAsInt() : 0;
                    playerEntries.add(new PlayerEntry(name, chips, name.equals(dealer)));
                }
            if (obj.has("pendingPlayers"))
                for (JsonElement e : obj.getAsJsonArray("pendingPlayers"))
                    playerEntries.add(new PlayerEntry(e.getAsString() + " (pending)", 0, false));
            isEmpty = !obj.has("isEmpty") || obj.get("isEmpty").getAsBoolean();
            maxBet = obj.has("maxBet") ? obj.get("maxBet").getAsInt() : 100;
            bankBalance = obj.has("bankBalance") ? obj.get("bankBalance").getAsInt() : 0;
            minZcToJoin = obj.has("minZcToJoin") ? obj.get("minZcToJoin").getAsInt() : 10;
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
        if (framesOpen < 10) framesOpen++;
        int cx = width / 2, cy = height / 2;
        int bgX = cx - BG_W / 2, bgY = cy - BG_H / 2;

        int frame = (int) ((System.currentTimeMillis() / 120) % NUM_FRAMES);
        int v = frame * BG_H;
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, TEX_BG, bgX, bgY, 0, v, BG_W, BG_H, BG_W, TEX_H);

        ctx.drawCenteredTextWithShadow(textRenderer, "BLACKJACK", cx, bgY + 18, C_PINK);

        if (WIP_MODE) {
            ctx.drawCenteredTextWithShadow(textRenderer, "Đang phát triển", cx, bgY + 55, C_GOLD);
            ctx.drawCenteredTextWithShadow(textRenderer, "Coming soon...", cx, bgY + 70, C_GRAY);
        } else if (isEmpty) {
            ctx.drawCenteredTextWithShadow(textRenderer, "Empty — Create room to start", cx, bgY + 38, C_GRAY);
        } else {
            String bb = maxBet >= 1000 ? (maxBet/1000) + "K" : String.valueOf(maxBet);
            ctx.drawCenteredTextWithShadow(textRenderer,
                    "Max bet: " + bb + " ZC  ·  " + playerEntries.size() + " players",
                    cx - 30, bgY + 38, C_MAGENTA);
        }

        if (!isEmpty && !playerEntries.isEmpty()) {
            int colLeft = bgX + BG_W - 62;
            int lineY = bgY + 52;
            ctx.drawTextWithShadow(textRenderer, "Players:", colLeft, lineY - 12, C_GRAY);
            int headSz = 10;
            for (int i = 0; i < Math.min(playerEntries.size(), 6); i++) {
                PlayerEntry pe = playerEntries.get(i);
                boolean pending = pe.name().endsWith(" (pending)");
                String name = pending ? pe.name().replace(" (pending)", "") : pe.name();
                if (textRenderer.getWidth(name) > 52) name = name.substring(0, 7) + "..";
                int rowY = lineY + i * 18;
                drawPlayerHead(ctx, pe.name(), colLeft, rowY - 2, headSz);
                ctx.drawTextWithShadow(textRenderer, (pe.isHost() ? "♔ " : "") + name,
                        colLeft + headSz + 2, rowY, C_WHITE);
                if (!pending)
                    ctx.drawTextWithShadow(textRenderer, pe.chips() + " ZC",
                            colLeft + headSz + 2, rowY + 9, C_GRAY);
            }
        }

        ctx.drawCenteredTextWithShadow(textRenderer, "ZCoin: " + bankBalance + " (min " + minZcToJoin + " to join)", cx, bgY + BG_H - 16, C_PINK);
        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (framesOpen < 5) return true;
        return super.mouseClicked(click, doubled);
    }

    private void drawPlayerHead(DrawContext ctx, String name, int x, int y, int size) {
        String lookupName = name.endsWith(" (pending)") ? name.replace(" (pending)", "") : name;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null && mc.player.networkHandler != null) {
            PlayerListEntry entry = mc.player.networkHandler.getPlayerListEntry(lookupName);
            if (entry != null) {
                var skin = entry.getSkinTextures().body().texturePath();
                ctx.drawTexture(RenderPipelines.GUI_TEXTURED, skin, x, y, 8f, 8f, size, size, 8, 8, 64, 64);
                ctx.drawTexture(RenderPipelines.GUI_TEXTURED, skin, x, y, 40f, 8f, size, size, 8, 8, 64, 64);
                return;
            }
        }
        int h = lookupName.isEmpty() ? 0 : lookupName.hashCode();
        int r = 90 + (h & 0x7F), g = 80 + ((h >> 8) & 0x5F), b = 90 + ((h >> 16) & 0x7F);
        ctx.fill(x, y, x + size, y + size, 0xFF000000 | (r << 16) | (g << 8) | b);
        ctx.fill(x, y, x + size, y + 1, 0xFF8888AA);
        ctx.fill(x, y + size - 1, x + size, y + size, 0xFF8888AA);
        ctx.fill(x, y, x + 1, y + size, 0xFF8888AA);
        ctx.fill(x + size - 1, y, x + size, y + size, 0xFF8888AA);
        String initial = lookupName.isEmpty() ? "?" : lookupName.substring(0, 1).toUpperCase();
        ctx.drawCenteredTextWithShadow(textRenderer, initial, x + size/2, y + size/2 - 4, 0xFFFFFFFF);
    }
}
