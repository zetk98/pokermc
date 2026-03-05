package com.pokermc.screen;

import com.google.gson.*;
import com.pokermc.game.bang.BangCard;
import com.pokermc.game.bang.BangGame;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.*;

/**
 * Bang! game table. Uses Bang! card textures, role display, health bar.
 */
public class BangTableScreen extends Screen {

    private static final Identifier TEX_BG = Identifier.of("casinocraft", "textures/gui/poker_table_bg.png");
    private static final Identifier TEX_CARD_BACK = Identifier.of("bang", "textures/bang_card_back.png");
    private static final Identifier TEX_ROLE = Identifier.of("bang", "textures/roles/role.png");
    private static final int CARD_W = 22, CARD_H = 32;
    private static final int ROLE_W = 24, ROLE_H = 32;
    private static final int C_GOLD = 0xFFFFD700, C_WHITE = 0xFFFFFFFF, C_GRAY = 0xFF888888;
    private static final int C_ORANGE = 0xFFFF8844, C_RED = 0xFFFF4444, C_GREEN = 0xFF55CC55;

    /** Bang card typeId -> texture path (bang namespace) */
    private static final Map<String, String> BANG_CARD_TEX = Map.ofEntries(
            Map.entry("bang", "textures/cards/bang.png"),
            Map.entry("missed", "textures/cards/missed.png"),
            Map.entry("beer", "textures/cards/beer.png"),
            Map.entry("panic", "textures/cards/panic.png"),
            Map.entry("cat_balou", "textures/cards/cat_balou.png"),
            Map.entry("stagecoach", "textures/cards/stagecoach.png"),
            Map.entry("wells_fargo", "textures/cards/wells_fargo.png"),
            Map.entry("gatling", "textures/cards/gatling.png"),
            Map.entry("indians", "textures/cards/indians.png"),
            Map.entry("duel", "textures/cards/duel.png"),
            Map.entry("general_store", "textures/cards/general_store.png"),
            Map.entry("saloon", "textures/cards/saloon.png"),
            Map.entry("remington", "textures/cards/remington.png"),
            Map.entry("rev_carbine", "textures/cards/rev.carbine.png"),
            Map.entry("schofield", "textures/cards/schofield.png"),
            Map.entry("volcanic", "textures/cards/vocalnic.png"),
            Map.entry("winchester", "textures/cards/winchester.png"),
            Map.entry("mustang", "textures/cards/mustang.png"),
            Map.entry("appaloosa", "textures/cards/appaloosa.png"),
            Map.entry("barrel", "textures/cards/barrel.png"),
            Map.entry("dynamite", "textures/cards/dynamite.png"),
            Map.entry("jail", "textures/cards/jail.png")
    );

    private final BlockPos tablePos;
    private String stateJson = "{}";
    private String phase = "WAITING";
    private String statusMessage = "";
    private String currentPlayerName = "";
    private int currentPlayerIndex = -1;
    private int sheriffIndex = -1;
    private int heroIndex = -1;
    private int maxPlayers = 4;
    private String myName = "";
    private final List<PlayerInfo> players = new ArrayList<>();
    private final Map<String, Integer> distancesFromHero = new HashMap<>();
    private final List<String> pendingPlayers = new ArrayList<>();

    private ButtonWidget btnLeave, btnStart, btnEndTurn;
    private int tableTx, tableTy, tableW, tableH;
    private int selectedCardIndex = -1;

    private record PlayerInfo(String name, int seatIndex, String role, int maxHp, int hp,
                              boolean isAlive, List<String> hand, List<String> equipment) {}

    public BangTableScreen(BlockPos pos, String initialJson) {
        super(Text.literal("Bang! Table"));
        this.tablePos = pos;
    }

    @Override
    protected void init() {
        myName = MinecraftClient.getInstance().player != null
                ? MinecraftClient.getInstance().player.getName().getString() : "";

        int cx = width / 2, cy = height / 2;
        tableW = Math.min(width - 20, 360);
        tableH = Math.min(height - 20, 230);
        tableTx = cx - tableW / 2;
        tableTy = cy - tableH / 2;

        btnLeave = addDrawableChild(ButtonWidget.builder(Text.literal("✕ Leave"),
                b -> { sendAction("LEAVE", 0, ""); close(); })
                .dimensions(tableTx + tableW - 54, tableTy + 2, 52, 13).build());

        btnStart = addDrawableChild(ButtonWidget.builder(Text.literal("▶ Start"),
                b -> sendAction("START", 0, ""))
                .dimensions(cx - 28, tableTy + tableH - 14, 56, 13).build());

        btnEndTurn = addDrawableChild(ButtonWidget.builder(Text.literal("End Turn"),
                b -> sendAction("END_TURN", 0, ""))
                .dimensions(cx - 35, tableTy + tableH - 14, 70, 13).build());

        updateButtons();
    }

    private void sendAction(String action, int amount, String data) {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                new com.pokermc.network.BangNetworking.BangActionPayload(tablePos, action, amount, data));
    }

    private void updateButtons() {
        if (btnLeave == null) return;
        boolean waiting = "WAITING".equals(phase);
        boolean playing = "PLAYING".equals(phase) || "DEALING".equals(phase);
        boolean isOwner = !players.isEmpty() && players.get(0).name.equals(myName);
        boolean isMyTurn = myName.equals(currentPlayerName) && playing;

        btnLeave.visible = true;
        btnStart.visible = waiting && isOwner && players.size() >= 4;
        btnEndTurn.visible = playing && isMyTurn && "PLAYING".equals(phase);
    }

    public void updateState(String json) {
        stateJson = json;
        parseState(json);
        if (client != null) { updateButtons(); }
    }

    private void parseState(String json) {
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            phase = obj.has("phase") ? obj.get("phase").getAsString() : "WAITING";
            statusMessage = obj.has("status") ? obj.get("status").getAsString() : "";
            currentPlayerName = obj.has("currentPlayer") ? obj.get("currentPlayer").getAsString() : "";
            currentPlayerIndex = obj.has("currentPlayerIndex") ? obj.get("currentPlayerIndex").getAsInt() : -1;
            sheriffIndex = obj.has("sheriffIndex") ? obj.get("sheriffIndex").getAsInt() : -1;
            heroIndex = obj.has("heroIndex") ? obj.get("heroIndex").getAsInt() : -1;
            maxPlayers = obj.has("maxPlayers") ? obj.get("maxPlayers").getAsInt() : 4;

            players.clear();
            if (obj.has("players")) {
                for (JsonElement e : obj.getAsJsonArray("players")) {
                    JsonObject p = e.getAsJsonObject();
                    List<String> hand = new ArrayList<>();
                    for (JsonElement c : p.getAsJsonArray("hand")) hand.add(c.getAsString());
                    List<String> equip = new ArrayList<>();
                    for (JsonElement c : p.getAsJsonArray("equipment")) equip.add(c.getAsString());
                    players.add(new PlayerInfo(
                            p.get("name").getAsString(),
                            p.get("seatIndex").getAsInt(),
                            p.has("role") ? p.get("role").getAsString() : "",
                            p.get("maxHp").getAsInt(),
                            p.get("hp").getAsInt(),
                            p.get("isAlive").getAsBoolean(),
                            hand, equip));
                }
            }

            distancesFromHero.clear();
            if (obj.has("distancesFromHero")) {
                JsonObject d = obj.getAsJsonObject("distancesFromHero");
                for (Map.Entry<String, JsonElement> entry : d.entrySet())
                    distancesFromHero.put(entry.getKey(), entry.getValue().getAsInt());
            }

            pendingPlayers.clear();
            if (obj.has("pendingPlayers"))
                for (JsonElement e : obj.getAsJsonArray("pendingPlayers"))
                    pendingPlayers.add(e.getAsString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override public void renderBackground(DrawContext ctx, int mx, int my, float d) {}
    @Override public boolean shouldPause() { return false; }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        int cx = width / 2, cy = height / 2;

        ctx.drawTexture(TEX_BG, tableTx, tableTy, 0, 0, tableW, tableH, tableW, tableH);

        ctx.drawCenteredTextWithShadow(textRenderer, "♠ BANG! ♠", cx, tableTy + 8, C_ORANGE);
        ctx.drawCenteredTextWithShadow(textRenderer, statusMessage, cx, tableTy + 20, C_WHITE);

        int n = Math.min(players.size(), 6);
        double centerX = cx;
        double centerY = cy;
        double radius = Math.min(tableW, tableH) * 0.35;

        for (int i = 0; i < n; i++) {
            double angle = Math.toRadians(90 + i * (360.0 / Math.max(n, 4)));
            int sx = (int) (centerX + radius * Math.cos(angle));
            int sy = (int) (centerY + radius * Math.sin(angle));

            PlayerInfo p = players.get(i);
            boolean isHero = p.name.equals(myName);
            boolean isSheriff = i == sheriffIndex;
            boolean isCurrent = p.name.equals(currentPlayerName);
            boolean showRole = isSheriff || isHero; // Sheriff visible to all, hero sees own role

            int color = !p.isAlive ? C_GRAY : isCurrent ? C_ORANGE : C_WHITE;
            ctx.fill(sx - 30, sy - 20, sx + 55, sy + 35, 0x88000000);
            ctx.drawBorder(sx - 30, sy - 20, 85, 55, isCurrent ? C_ORANGE : C_GRAY);

            String name = p.name.length() > 8 ? p.name.substring(0, 6) + ".." : p.name;
            ctx.drawCenteredTextWithShadow(textRenderer, name, sx + 12, sy - 18, color);

            // Health bar (max 5)
            int barX = sx - 28, barY = sy - 8;
            int barW = 50, barH = 6;
            ctx.fill(barX, barY, barX + barW, barY + barH, 0xFF333333);
            int fillW = (int) (barW * (p.hp / 5.0));
            ctx.fill(barX, barY, barX + fillW, barY + barH, p.hp > 2 ? C_GREEN : C_RED);

            if (heroIndex >= 0 && distancesFromHero.containsKey(p.name)) {
                ctx.drawCenteredTextWithShadow(textRenderer, "d:" + distancesFromHero.get(p.name), sx + 12, sy + 2, C_GRAY);
            }

            // Role card - to the right of player (Role.png: 4 cells)
            int roleX = sx + 28;
            if (showRole && !p.role.isEmpty()) {
                int roleCell = roleToCellIndex(p.role);
                int cellW = 64;
                ctx.drawTexture(TEX_ROLE, roleX, sy - 12, roleCell * cellW, 0, 32, 32, 256, 64);
            } else if (!p.role.isEmpty()) {
                ctx.fill(roleX, sy - 12, roleX + ROLE_W, sy + 20, 0xFF444444);
                ctx.drawCenteredTextWithShadow(textRenderer, "?", roleX + ROLE_W/2, sy, C_GRAY);
            }
        }

        // Hero's hand - Bang cards
        PlayerInfo hero = players.stream().filter(p -> p.name.equals(myName)).findFirst().orElse(null);
        if (hero != null && !hero.hand.isEmpty()) {
            int handY = cy + tableH / 2 + 20;
            int totalW = Math.min(hero.hand.size(), 5) * (CARD_W + 4) - 4;
            int startX = cx - totalW / 2;
            for (int i = 0; i < hero.hand.size(); i++) {
                int x = startX + i * (CARD_W + 4);
                String code = hero.hand.get(i);
                if (selectedCardIndex == i) ctx.drawBorder(x - 1, handY - 1, CARD_W + 2, CARD_H + 2, C_ORANGE);
                if ("??".equals(code)) drawCardBack(ctx, x, handY);
                else drawBangCard(ctx, x, handY, code);
            }
        }

        if (selectedCardIndex >= 0)
            ctx.drawCenteredTextWithShadow(textRenderer, "Click a player to target", cx, tableTy + tableH - 30, C_ORANGE);

        updateButtons();
        super.render(ctx, mouseX, mouseY, delta);
    }

    private int roleToCellIndex(String role) {
        return switch (role.toUpperCase()) {
            case "SHERIFF" -> 3;
            case "DEPUTY" -> 1;
            case "OUTLAW" -> 2;
            case "RENEGADE" -> 0;
            default -> 0;
        };
    }

    private void drawBangCard(DrawContext ctx, int x, int y, String code) {
        BangCard card = BangCard.fromCode(code);
        if (card == null) { drawCardBack(ctx, x, y); return; }
        String path = BANG_CARD_TEX.get(card.typeId());
        if (path == null) { drawCardBack(ctx, x, y); return; }
        Identifier tex = Identifier.of("bang", path);
        int texW = CARD_W, texH = CARD_H;
        if ("bang".equals(card.typeId())) { texW = 110; texH = 160; } // 5x5 atlas
        else if ("missed".equals(card.typeId())) { texW = 88; texH = 96; } // 4x3 atlas
        ctx.drawTexture(tex, x, y, 0, 0, CARD_W, CARD_H, texW, texH);
    }

    private void drawCardBack(DrawContext ctx, int x, int y) {
        ctx.drawTexture(TEX_CARD_BACK, x, y, 0, 0, CARD_W, CARD_H, CARD_W, CARD_H);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int cx = width / 2, cy = height / 2;
        PlayerInfo hero = players.stream().filter(p -> p.name.equals(myName)).findFirst().orElse(null);
        boolean isMyTurn = myName.equals(currentPlayerName) && "PLAYING".equals(phase);

        if (selectedCardIndex >= 0 && hero != null && isMyTurn) {
            int n = Math.min(players.size(), 6);
            double radius = Math.min(tableW, tableH) * 0.35;
            for (int i = 0; i < n; i++) {
                double angle = Math.toRadians(90 + i * (360.0 / Math.max(n, 4)));
                int sx = (int) (cx + radius * Math.cos(angle));
                int sy = (int) (cy + radius * Math.sin(angle));
                if (mouseX >= sx - 30 && mouseX <= sx + 55 && mouseY >= sy - 20 && mouseY <= sy + 35) {
                    PlayerInfo target = players.get(i);
                    if (!target.name.equals(myName) && target.isAlive) {
                        String code = hero.hand.get(selectedCardIndex);
                        BangCard card = BangCard.fromCode(code);
                        String targetName = (card != null && needsTarget(card.typeId())) ? target.name : "";
                        sendAction("PLAY_CARD", selectedCardIndex, targetName);
                        selectedCardIndex = -1;
                        return true;
                    }
                }
            }
        }

        if (hero != null && !hero.hand.isEmpty() && isMyTurn) {
            int handY = cy + tableH / 2 + 20;
            int totalW = Math.min(hero.hand.size(), 5) * (CARD_W + 4) - 4;
            int startX = cx - totalW / 2;
            for (int i = 0; i < hero.hand.size(); i++) {
                int x = startX + i * (CARD_W + 4);
                if (mouseX >= x && mouseX <= x + CARD_W && mouseY >= handY && mouseY <= handY + CARD_H) {
                    String code = hero.hand.get(i);
                    BangCard card = BangCard.fromCode(code);
                    if (card == null) return super.mouseClicked(mouseX, mouseY, button);
                    if (needsTarget(card.typeId())) {
                        selectedCardIndex = i;
                    } else {
                        sendAction("PLAY_CARD", i, "");
                    }
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean needsTarget(String typeId) {
        return BangCard.BANG.equals(typeId) || BangCard.PANIC.equals(typeId) || BangCard.CAT_BALOU.equals(typeId)
                || BangCard.DUEL.equals(typeId);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { selectedCardIndex = -1; close(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
