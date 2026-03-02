package com.pokermc.screen;

import com.google.gson.*;
import com.pokermc.game.Card;
import com.pokermc.game.HandEvaluator;
import com.pokermc.network.PokerNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.*;

/**
 * Poker table main screen.
 *
 * 8 fixed seats arranged on the oval (starting at bottom-center, going clockwise).
 * Hero occupies their natural seat based on player-list index.
 * Community cards are drawn at the center of the felt.
 * Timed status notifications fade after configurable duration.
 */
public class PokerTableScreen extends Screen {

    // ── Textures ─────────────────────────────────────────────────────────────
    private static final Identifier TEX_BG        = Identifier.of("casinocraft", "textures/gui/poker_table_bg.png");
    private static final Identifier TEX_CARD_ATLAS = Identifier.of("casinocraft", "textures/gui/card_atlas.png");
    private static final Identifier TEX_CARD_BACK  = Identifier.of("casinocraft", "textures/gui/card_back.png");
    private static final int ATLAS_W = 286, ATLAS_H = 128;
    private static final int CARD_W  = 22,  CARD_H  = 32;
    private static final int SMALL_W = 14,  SMALL_H = 20;

    private static final List<String> RANK_ORDER =
            List.of("A","2","3","4","5","6","7","8","9","T","J","Q","K");
    private static final List<String> SUIT_ORDER = List.of("S","H","D","C");

    // ── 8 seat angles (degrees), starting at bottom-center going clockwise ───
    // Screen coords: 90° = down (bottom), 270° = up (top)
    // Clockwise on screen = 90 → 45 → 0 → 315 → 270 → 225 → 180 → 135
    private static final double[] SEAT_ANGLES   = {90, 45, 0, 315, 270, 225, 180, 135};
    // Card direction per seat: +1=right, -1=left, 0=below(top seat), 2=above(bottom seat)
    private static final int[]    SEAT_CARD_DIRS = {2, -1, -1, -1, 0, +1, +1, +1};

    // ── Colors ────────────────────────────────────────────────────────────────
    private static final int C_GOLD      = 0xFFFFD700;
    private static final int C_WHITE     = 0xFFFFFFFF;
    private static final int C_HIGHLIGHT = 0xFFFFFF44;
    private static final int C_GRAY      = 0xFF888888;
    private static final int C_EMPTY     = 0xFF333333;
    private static final int C_PENDING   = 0xFFAA88CC;
    private static final int C_RED_SLOT  = 0xFFCC2222;
    private static final int C_GREEN     = 0xFF55CC55;

    // ── State ─────────────────────────────────────────────────────────────────
    private final BlockPos tablePos;
    private int framesOpen = 0;
    private String phase = "WAITING";
    private int pot = 0, currentBet = 0, betLevel = 10;
    private String currentPlayerName = "", lastWinner = "", lastWinningHand = "";
    private int lastPotWon = 0;
    private String ownerName = "";
    private int bankBalance = 0;
    private int turnTimeRemaining = 0;
    private long turnTimeReceivedAt = 0;
    private String prevPhase = "";
    private final Set<Integer> revealedHeroCards = new java.util.HashSet<>();
    private final List<int[]> heroCardBounds = new ArrayList<>();
    private final Set<Integer> revealedCommunityIndices = new java.util.HashSet<>();
    private final List<int[]> communityCardBounds = new ArrayList<>();
    private final List<String> communityCards  = new ArrayList<>();
    private final List<PlayerInfo> players     = new ArrayList<>();
    private final List<String> pendingPlayers  = new ArrayList<>();
    private String myName = "";

    // ── Notifications ─────────────────────────────────────────────────────────
    private record Notification(String message, long expiresAt) {}
    private final LinkedList<Notification> notifications = new LinkedList<>();
    private String prevStatus = "";
    private static final long NOTIF_DURATION_MS = 3000;

    // ── Buttons ───────────────────────────────────────────────────────────────
    private ButtonWidget btnLeave, btnStart, btnReset;
    private ButtonWidget btnFold, btnCheck, btnCall, btnAllIn, btnRaise;
    private ButtonWidget btnMinus, btnPlus;
    private TextFieldWidget raiseInput;
    private int raiseAmount = 0;

    // ── Cached geometry ───────────────────────────────────────────────────────
    private int tableTx, tableTy, tableW, tableH;

    public PokerTableScreen(BlockPos pos, String initialJson) {
        super(Text.literal("Poker Table"));
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

        if (raiseAmount <= 0) raiseAmount = Math.max(1, betLevel);

        // Leave button — top right
        btnLeave = addDrawableChild(ButtonWidget.builder(Text.literal("✕ Leave"),
                b -> { sendAction("LEAVE", 0); close(); })
                .dimensions(tableTx + tableW - 54, tableTy + 2, 52, 13).build());

        // Start / Reset — bottom center, thin
        btnStart = addDrawableChild(ButtonWidget.builder(Text.literal("▶ Start"),
                b -> sendAction("START", 0))
                .dimensions(cx - 28, tableTy + tableH - 14, 56, 13).build());

        btnReset = addDrawableChild(ButtonWidget.builder(Text.literal("Next Round"),
                b -> sendAction("RESET", 0))
                .dimensions(cx - 35, tableTy + tableH - 14, 70, 13).build());

        // Action buttons — below hero seat (bottom of screen)
        int btnY = cy + 100, btnH = 18;

        btnFold  = addDrawableChild(ButtonWidget.builder(Text.literal("Fold"),
                b -> sendAction("FOLD", 0))
                .dimensions(cx - 130, btnY, 40, btnH).build());

        btnCheck = addDrawableChild(ButtonWidget.builder(Text.literal("Check"),
                b -> sendAction("CHECK", 0))
                .dimensions(cx - 85, btnY, 42, btnH).build());

        btnCall  = addDrawableChild(ButtonWidget.builder(Text.literal("Call"),
                b -> sendAction("CALL", 0))
                .dimensions(cx - 38, btnY, 45, btnH).build());

        btnAllIn = addDrawableChild(ButtonWidget.builder(Text.literal("All In"),
                b -> sendAction("ALLIN", 0))
                .dimensions(cx + 12, btnY, 44, btnH).build());

        btnRaise = addDrawableChild(ButtonWidget.builder(Text.literal("Raise"),
                b -> {
                    int amt = parseRaiseAmount();
                    if (amt > 0) sendAction("RAISE", amt);
                })
                .dimensions(cx + 61, btnY, 40, btnH).build());

        raiseInput = addDrawableChild(new TextFieldWidget(textRenderer, cx + 104, btnY, 42, btnH,
                Text.literal("")));
        raiseInput.setMaxLength(8);
        raiseInput.setTextPredicate(s -> s.isEmpty() || s.matches("\\d+"));
        raiseInput.setPlaceholder(Text.literal("+" + betLevel));

        btnMinus = addDrawableChild(ButtonWidget.builder(Text.literal("−"),
                b -> { raiseAmount = Math.max(1, raiseAmount - betLevel); syncRaiseInput(); })
                .dimensions(cx + 149, btnY, 12, btnH).build());
        btnPlus = addDrawableChild(ButtonWidget.builder(Text.literal("+"),
                b -> { raiseAmount += betLevel; syncRaiseInput(); })
                .dimensions(cx + 163, btnY, 12, btnH).build());

        updateButtonVisibility();
    }

    private void updateButtonVisibility() {
        if (btnLeave == null) return;
        boolean inGame   = !phase.equals("WAITING") && !phase.equals("SHOWDOWN");
        boolean isMyTurn = myName.equals(currentPlayerName) && inGame;
        boolean amActive = players.stream().anyMatch(p -> p.name.equals(myName));
        boolean amPending= pendingPlayers.contains(myName);
        boolean waiting  = phase.equals("WAITING");
        boolean showdown = phase.equals("SHOWDOWN");
        boolean isOwner  = !players.isEmpty() && players.get(0).name.equals(myName);

        btnLeave.visible  = amActive || amPending;
        btnStart.visible  = waiting && isOwner && players.size() >= 2;
        btnReset.visible  = showdown && isOwner && amActive;

        boolean showRaiseControls = isMyTurn;

        btnFold.visible   = isMyTurn;
        btnAllIn.visible  = isMyTurn;
        btnRaise.visible  = showRaiseControls;
        if (raiseInput != null) raiseInput.visible = showRaiseControls;
        if (btnMinus != null) btnMinus.visible = showRaiseControls;
        if (btnPlus != null) btnPlus.visible = showRaiseControls;

        PlayerInfo me = players.stream().filter(p -> p.name.equals(myName)).findFirst().orElse(null);
        int toCall = me != null ? Math.max(0, currentBet - me.currentBet) : 0;

        btnCheck.visible = isMyTurn;
        btnCheck.active  = toCall == 0;

        btnCall.visible = isMyTurn;
        btnCall.active  = toCall > 0;
        btnCall.setMessage(Text.literal(toCall > 0 ? "Call " + toCall : "Call"));

        boolean hasAllInPlayer = players.stream().anyMatch(p -> p.allIn);
        if (me != null) {
            btnAllIn.active = !me.allIn && me.chips > 0 && !hasAllInPlayer;
            btnRaise.active = me.chips > 0 && !hasAllInPlayer;
        }

        if (btnRaise.visible) syncRaiseInput();
    }

    private int parseRaiseAmount() {
        if (raiseInput == null) return raiseAmount;
        String s = raiseInput.getText().trim();
        if (s.isEmpty()) return raiseAmount;
        try { return Math.max(betLevel, Integer.parseInt(s)); } catch (NumberFormatException e) { return raiseAmount; }
    }

    private void syncRaiseInput() {
        if (raiseInput != null) raiseInput.setText(String.valueOf(raiseAmount));
    }

    // ── State update ─────────────────────────────────────────────────────────

    public void updateState(String json) {
        try {
            JsonObject obj     = JsonParser.parseString(json).getAsJsonObject();
            phase              = obj.get("phase").getAsString();
            pot                = obj.get("pot").getAsInt();
            currentBet         = obj.get("currentBet").getAsInt();
            currentPlayerName  = obj.get("currentPlayer").getAsString();
            lastWinner         = obj.get("lastWinner").getAsString();
            lastWinningHand    = obj.get("lastWinningHand").getAsString();
            lastPotWon         = obj.has("lastPotWon") ? obj.get("lastPotWon").getAsInt() : 0;
            ownerName          = obj.has("owner")       ? obj.get("owner").getAsString()    : "";
            betLevel           = obj.has("betLevel")    ? obj.get("betLevel").getAsInt()     : 10;
            bankBalance        = obj.has("bankBalance") ? obj.get("bankBalance").getAsInt()  : 0;
            turnTimeRemaining  = obj.has("turnTimeRemaining") ? obj.get("turnTimeRemaining").getAsInt() : 0;
            if (turnTimeRemaining > 0 && currentPlayerName.equals(myName)) turnTimeReceivedAt = System.currentTimeMillis();

            if (raiseAmount <= 0) raiseAmount = Math.max(1, betLevel);
            if (raiseInput != null) raiseInput.setPlaceholder(Text.literal("+" + betLevel));

            String newStatus = obj.get("status").getAsString();
            if (!newStatus.equals(prevStatus) && !newStatus.isEmpty()) {
                notifications.addFirst(new Notification(newStatus,
                        System.currentTimeMillis() + NOTIF_DURATION_MS));
                prevStatus = newStatus;
            }

            boolean phaseChanged = !phase.equals(prevPhase);
            if (phaseChanged) {
                if (phase.equals("SHOWDOWN")) {
                    int numAlreadyShown = switch (prevPhase) {
                        case "FLOP" -> 3; case "TURN" -> 4; case "RIVER" -> 5;
                        default -> 0;
                    };
                    for (int i = 0; i < numAlreadyShown; i++) revealedCommunityIndices.add(i);
                }
                prevPhase = phase;
                if (phase.equals("WAITING") || phase.equals("PRE_FLOP")) revealedHeroCards.clear();
            }

            communityCards.clear();
            for (JsonElement e : obj.getAsJsonArray("community"))
                communityCards.add(e.getAsString());

            players.clear();
            for (JsonElement e : obj.getAsJsonArray("players")) {
                JsonObject p = e.getAsJsonObject();
                List<String> cards = new ArrayList<>();
                for (JsonElement c : p.getAsJsonArray("holeCards")) cards.add(c.getAsString());
                String pname = p.get("name").getAsString();
                players.add(new PlayerInfo(
                        pname, p.get("chips").getAsInt(),
                        p.get("currentBet").getAsInt(), p.get("folded").getAsBoolean(),
                        p.get("allIn").getAsBoolean(), cards));
            }

            pendingPlayers.clear();
            if (obj.has("pendingPlayers"))
                for (JsonElement e : obj.getAsJsonArray("pendingPlayers"))
                    pendingPlayers.add(e.getAsString());

        } catch (Exception e) {
            notifications.addFirst(new Notification("State error: " + e.getMessage(),
                    System.currentTimeMillis() + 4000));
        }
        updateButtonVisibility();
    }

    @Override public void renderBackground(DrawContext ctx, int mx, int my, float d) {}
    @Override public boolean shouldPause() { return false; }

    // ── Main render ───────────────────────────────────────────────────────────

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        if (framesOpen < 10) framesOpen++;
        int cx = width / 2, cy = height / 2;
        tableW = Math.min(width - 20, 360);
        tableH = Math.min(height - 20, 230);
        tableTx = cx - tableW / 2;
        tableTy = cy - tableH / 2;
        int tx = tableTx, ty = tableTy;

        // Background texture
        ctx.drawTexture(TEX_BG, tx, ty, 0, 0, tableW, tableH, 360, 230);

        // ── Title: table name + bet level ─────────────────────────────────────
        String bbStr = betLevel >= 1000 ? (betLevel/1000) + "K" : String.valueOf(betLevel);
        String title = "♠ Poker Table  BB:" + bbStr + " ZC ♣";
        ctx.drawCenteredTextWithShadow(textRenderer, title, cx, ty + 5, C_GOLD);

        // Phase + pot
        String phaseLabel = switch (phase) {
            case "PRE_FLOP" -> "Pre-Flop"; case "FLOP"  -> "Flop";
            case "TURN"     -> "Turn";     case "RIVER" -> "River";
            case "SHOWDOWN" -> "Showdown"; default      -> "Waiting";
        };
        ctx.drawCenteredTextWithShadow(textRenderer,
                phaseLabel + "  —  Pot: " + pot + " ZC", cx, ty + 16, C_GOLD);

        // ── Community cards (center of felt) ──────────────────────────────────
        drawCommunityCards(ctx, cx, ty);

        // ── Showdown winner banner (above 5 cards) ─────────────────────────────
        if (phase.equals("SHOWDOWN") && !lastWinner.isEmpty()) {
            int bw = 240, bh = 28;
            int bannerY = ty + tableH / 2 - 55;
            ctx.fill(cx - bw/2, bannerY, cx + bw/2, bannerY + bh, 0xDD000000);
            drawBorder(ctx, cx - bw/2, bannerY, bw, bh, C_GOLD, 2);
            String winText = lastWinner + " wins " + lastPotWon + " ZC!  " + lastWinningHand;
            ctx.drawCenteredTextWithShadow(textRenderer, winText, cx, bannerY + 8, C_GOLD);
        }

        // ── 8 seats on oval ───────────────────────────────────────────────────
        renderAllSeats(ctx, tx, ty);

        // ── Turn indicator ────────────────────────────────────────────────────
        if (!currentPlayerName.isEmpty() && !phase.equals("WAITING") && !phase.equals("SHOWDOWN")) {
            boolean myTurn = currentPlayerName.equals(myName);
            String txt = myTurn ? "▶ YOUR TURN ◀" : currentPlayerName + "'s turn";
            if (myTurn && turnTimeRemaining > 0) {
                int elapsed = (int) ((System.currentTimeMillis() - turnTimeReceivedAt) / 1000);
                int display = Math.max(0, turnTimeRemaining - elapsed);
                txt += "  (" + display + "s)";
            }
            int tw = textRenderer.getWidth(txt) + 12;
            int turnY = ty + tableH / 2 - 48;
            ctx.fill(cx - tw / 2, turnY - 2, cx + tw / 2, turnY + 10, 0xAA000000);
            ctx.drawCenteredTextWithShadow(textRenderer, txt, cx, turnY,
                    myTurn ? C_HIGHLIGHT : C_WHITE);
        }

        // ── Best hand (top-left) ─────────────────────────────────────────────
        renderBestHand(ctx, tx, ty);

        // ── ZCoin (top-left, below best hand) ────────────────────────────────────
        if (bankBalance > 0) {
            ctx.drawTextWithShadow(textRenderer, "ZCoin: " + bankBalance,
                    tx + 4, ty + 22, C_GREEN);
        }

        // ── Notifications (below Leave, 3s) ───────────────────────────────────
        renderNotifications(ctx, tx, ty);

        super.render(ctx, mouseX, mouseY, delta);
    }

    // ── Seats ─────────────────────────────────────────────────────────────────

    private record Seat(int x, int y, int cardDir) {}

    private List<Seat> buildSeats(int tx, int ty) {
        List<Seat> result = new ArrayList<>(8);
        int tcx = tx + tableW / 2;
        int tcy = ty + tableH / 2;
        double rx = tableW / 2.0 - 22;   // matches visual oval radii
        double ry = tableH / 2.0 - 38;   // 75px for 230px tall table

        for (int i = 0; i < 8; i++) {
            double rad = Math.toRadians(SEAT_ANGLES[i]);
            int sx = tcx + (int)(Math.cos(rad) * rx);
            int sy = tcy + (int)(Math.sin(rad) * ry);
            result.add(new Seat(sx, sy, SEAT_CARD_DIRS[i]));
        }
        return result;
    }

    private void renderAllSeats(DrawContext ctx, int tx, int ty) {
        List<Seat> seats = buildSeats(tx, ty);

        // Build full occupant list: active players then pending
        List<String> allNames = new ArrayList<>();
        for (PlayerInfo p : players) allNames.add(p.name);
        for (String nm : pendingPlayers) allNames.add(nm);

        for (int s = 0; s < seats.size(); s++) {
            Seat seat = seats.get(s);
            if (s < players.size()) {
                PlayerInfo p = players.get(s);
                boolean isHero = p.name.equals(myName);
                drawSeat(ctx, seat.x(), seat.y(), seat.cardDir(), p, null, isHero);
            } else {
                int pi = s - players.size();
                String pendName = pi < pendingPlayers.size() ? pendingPlayers.get(pi) : null;
                drawSeat(ctx, seat.x(), seat.y(), seat.cardDir(), null, pendName, false);
            }
        }
    }

    /**
     * Draw a single seat.
     * @param pi     PlayerInfo (null if empty or pending)
     * @param pendName  name if pending (null if empty)
     * @param isHero true = local player; shows full-size cards face-up
     * @param cardDir +1=right, -1=left, 0=below head, 2=above head
     */
    private void drawSeat(DrawContext ctx, int sx, int sy,
                          int cardDir, PlayerInfo pi, String pendName, boolean isHero) {
        int headSz = 14;

        // Empty slot
        if (pi == null && pendName == null) {
            int bx = sx - 14, by = sy - 8;
            ctx.fill(bx, by, bx + 28, by + 16, 0x22000000);
            drawBorder(ctx, bx, by, 28, 16, C_EMPTY, 1);
            return;
        }

        boolean isPending = (pi == null);
        String name = isPending ? pendName : pi.name;
        boolean isTurn = !isPending && name.equals(currentPlayerName);

        // Head
        int hx = sx - headSz / 2, hy = sy - headSz / 2;
        drawPlayerHead(ctx, name, hx, hy, headSz);

        if (isTurn) drawBorder(ctx, hx - 1, hy - 1, headSz + 2, headSz + 2, C_RED_SLOT, 1);

        // Crown above head (owner)
        if (!isPending && name.equals(ownerName))
            ctx.drawCenteredTextWithShadow(textRenderer, "♛", sx, hy - 9, C_GOLD);

        // Name below head
        int nameY = sy + headSz / 2 + 2;
        int nc = isPending ? C_PENDING : (isTurn ? C_HIGHLIGHT : (pi.folded ? C_GRAY : C_WHITE));
        String rawLabel = isPending ? name + " ★"
                : (pi.folded ? name + " F" : (pi.allIn ? name + " A" : name));
        String label = textRenderer.getWidth(rawLabel) > 46 ? rawLabel.substring(0, 5) + ".." : rawLabel;
        ctx.drawCenteredTextWithShadow(textRenderer, label, sx, nameY, nc);

        // Chips below name
        if (!isPending) {
            String chipStr = pi.chips + " ZC";
            ctx.drawCenteredTextWithShadow(textRenderer, chipStr, sx, nameY + 9, C_GOLD);
            if (pi.currentBet > 0)
                ctx.drawCenteredTextWithShadow(textRenderer, "↑" + pi.currentBet, sx, nameY + 18, 0xFFFFAA00);
        } else {
            ctx.drawCenteredTextWithShadow(textRenderer, "next round", sx, nameY + 9, C_PENDING);
        }

        // Cards
        if (!isPending && !pi.holeCards.isEmpty()) {
            drawSeatCards(ctx, sx, sy, headSz, pi.holeCards, cardDir, isHero);
            if (isHero) cacheHeroCardBounds(sx, sy, headSz, pi.holeCards.size(), cardDir);
        }
    }

    private void cacheHeroCardBounds(int sx, int sy, int headSz, int num, int cardDir) {
        heroCardBounds.clear();
        int totalW = num * (CARD_W + 3) - 3;
        int cx2, cy2;
        if (cardDir == 2) {
            cx2 = sx - totalW / 2;
            cy2 = sy - headSz / 2 - 3 - CARD_H;
        } else if (cardDir == 0) {
            cx2 = sx - totalW / 2;
            cy2 = sy + headSz / 2 + 28;
        } else if (cardDir == +1) {
            cx2 = sx + headSz / 2 + 3;
            cy2 = sy - CARD_H / 2;
        } else {
            cx2 = sx - headSz / 2 - 3 - totalW;
            cy2 = sy - CARD_H / 2;
        }
        for (int j = 0; j < num; j++) {
            int bx = cx2 + j * (CARD_W + 3), by = cy2;
            heroCardBounds.add(new int[]{bx - 1, by - 1, CARD_W + 2, CARD_H + 2});
        }
    }

    private void drawSeatCards(DrawContext ctx, int sx, int sy, int headSz,
                                List<String> holeCards, int cardDir, boolean isHero) {
        int num = holeCards.size();
        if (isHero) {
            int totalW = num * (CARD_W + 3) - 3;
            int cx2, cy2;
            if (cardDir == 2) {
                cx2 = sx - totalW / 2;
                cy2 = sy - headSz / 2 - 3 - CARD_H;
            } else if (cardDir == 0) {
                cx2 = sx - totalW / 2;
                cy2 = sy + headSz / 2 + 28;
            } else if (cardDir == +1) {
                cx2 = sx + headSz / 2 + 3;
                cy2 = sy - CARD_H / 2;
            } else {
                cx2 = sx - headSz / 2 - 3 - totalW;
                cy2 = sy - CARD_H / 2;
            }
            for (int j = 0; j < num; j++) {
                int bx = cx2 + j * (CARD_W + 3), by = cy2;
                boolean revealed = revealedHeroCards.contains(j) || phase.equals("SHOWDOWN");
                if (revealed) drawCard(ctx, bx, by, holeCards.get(j));
                else drawCardBack(ctx, bx, by);
            }
        } else {
            // Small cards for opponents
            int cx2, cy2;
            if (cardDir == 2) { // UP
                cx2 = sx - num * (SMALL_W + 1) / 2;
                cy2 = sy - headSz / 2 - 2 - SMALL_H;
            } else if (cardDir == 0) { // DOWN
                cx2 = sx - num * (SMALL_W + 1) / 2;
                cy2 = sy + headSz / 2 + 26;
            } else if (cardDir == +1) { // RIGHT
                cx2 = sx + headSz / 2 + 3;
                cy2 = sy - SMALL_H / 2;
            } else { // LEFT
                cx2 = sx - headSz / 2 - 3 - num * (SMALL_W + 1);
                cy2 = sy - SMALL_H / 2;
            }
            for (int j = 0; j < num; j++) {
                int bx = cx2 + j * (SMALL_W + 1);
                if (holeCards.get(j).equals("??")) drawCardBackSmall(ctx, bx, cy2);
                else                               drawCardSmall(ctx, bx, cy2, holeCards.get(j));
            }
        }
    }

    // ── Community cards ───────────────────────────────────────────────────────

    private void drawCommunityCards(DrawContext ctx, int cx, int ty) {
        int slotY = ty + tableH / 2 - 18;
        int totalW = 5 * (CARD_W + 3) - 3;
        int startX = cx - totalW / 2;
        communityCardBounds.clear();
        for (int i = 0; i < 5; i++) {
            int sx = startX + i * (CARD_W + 3);
            boolean revealed = revealedCommunityIndices.contains(i) || !phase.equals("SHOWDOWN");
            if (i < communityCards.size()) {
                if (revealed) drawCard(ctx, sx, slotY, communityCards.get(i));
                else drawCardBack(ctx, sx, slotY);
            } else {
                drawCardBack(ctx, sx, slotY);
            }
            communityCardBounds.add(new int[]{sx - 1, slotY - 1, CARD_W + 2, CARD_H + 2});
        }
    }

    // ── Best hand ─────────────────────────────────────────────────────────────

    private void renderBestHand(DrawContext ctx, int tx, int ty) {
        PlayerInfo me = players.stream().filter(p -> p.name.equals(myName)).findFirst().orElse(null);
        if (me == null || me.holeCards.isEmpty()) return;
        try {
            List<Card> all = new ArrayList<>();
            for (String code : me.holeCards)   if (!code.equals("??")) all.add(Card.fromCode(code));
            for (String code : communityCards) if (!code.equals("??")) all.add(Card.fromCode(code));
            if (all.size() < 5) return;
            String handName = HandEvaluator.evaluate(all).getDisplayName();
            String txt = handName;
            int tw = Math.min(textRenderer.getWidth(txt) + 8, tableW - 80);
            int bx = tx + 4, by = ty + 4;
            ctx.fill(bx, by, bx + tw, by + 14, 0xDD000000);
            drawBorder(ctx, bx, by, tw, 14, C_GOLD, 1);
            ctx.drawTextWithShadow(textRenderer, txt, bx + 4, by + 3, C_GOLD);
        } catch (Exception ignored) {}
    }

    // ── Notifications (below Leave, 3s) ────────────────────────────────────────

    private void renderNotifications(DrawContext ctx, int tx, int ty) {
        long now = System.currentTimeMillis();
        notifications.removeIf(n -> n.expiresAt() < now);
        int notifX = tx + tableW - 54;
        int notifY = ty + 18;
        int maxShow = 3;
        int i = 0;
        for (Notification n : notifications) {
            if (i >= maxShow) break;
            String msg = n.message().length() > 36 ? n.message().substring(0, 33) + "..." : n.message();
            int mw = textRenderer.getWidth(msg) + 8;
            int my = notifY + i * 12;
            long remaining = n.expiresAt() - now;
            int alpha = remaining < 500 ? (int)(0xAA * remaining / 500) : 0xAA;
            ctx.fill(notifX - mw + 52, my - 1, notifX + 52, my + 9, (alpha << 24) | 0x000000);
            int textAlpha = remaining < 500 ? (int)(0xFF * remaining / 500) : 0xFF;
            int color = (textAlpha << 24) | 0xCCCCCC;
            ctx.drawTextWithShadow(textRenderer, msg, notifX - mw + 56, my, color);
            i++;
        }
    }

    // ── Player head ───────────────────────────────────────────────────────────

    private void drawPlayerHead(DrawContext ctx, String name, int x, int y, int size) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && client.player.networkHandler != null) {
            PlayerListEntry entry = client.player.networkHandler.getPlayerListEntry(name);
            if (entry != null) {
                Identifier skin = entry.getSkinTextures().texture();
                ctx.drawTexture(skin, x, y, size, size, 8f, 8f, 8, 8, 64, 64);
                ctx.drawTexture(skin, x, y, size, size, 40f, 8f, 8, 8, 64, 64);
                return;
            }
        }
        int h = name.hashCode();
        int r = 90 + (h & 0x7F), g = 80 + ((h >> 8) & 0x5F), b = 90 + ((h >> 16) & 0x7F);
        ctx.fill(x, y, x + size, y + size, 0xFF000000 | (r << 16) | (g << 8) | b);
        drawBorder(ctx, x, y, size, size, C_WHITE, 1);
        if (!name.isEmpty())
            ctx.drawCenteredTextWithShadow(textRenderer,
                    String.valueOf(name.charAt(0)).toUpperCase(),
                    x + size / 2, y + size / 4, C_WHITE);
    }

    // ── Card rendering ────────────────────────────────────────────────────────

    private void drawCard(DrawContext ctx, int x, int y, String code) {
        if (code == null || code.equals("??")) { drawCardBack(ctx, x, y); return; }
        int[] uv = atlasUV(code);
        if (uv == null) { drawCardBack(ctx, x, y); return; }
        ctx.drawTexture(TEX_CARD_ATLAS, x, y, uv[0], uv[1], CARD_W, CARD_H, ATLAS_W, ATLAS_H);
    }

    private void drawCardBack(DrawContext ctx, int x, int y) {
        ctx.drawTexture(TEX_CARD_BACK, x, y, 0, 0, CARD_W, CARD_H, CARD_W, CARD_H);
    }

    private void drawCardSmall(DrawContext ctx, int x, int y, String code) {
        int[] uv = atlasUV(code);
        if (uv == null) { drawCardBackSmall(ctx, x, y); return; }
        ctx.drawTexture(TEX_CARD_ATLAS, x, y, SMALL_W, SMALL_H,
                (float)uv[0], (float)uv[1], CARD_W, CARD_H, ATLAS_W, ATLAS_H);
    }

    private void drawCardBackSmall(DrawContext ctx, int x, int y) {
        ctx.drawTexture(TEX_CARD_BACK, x, y, SMALL_W, SMALL_H,
                0f, 0f, CARD_W, CARD_H, CARD_W, CARD_H);
    }

    private int[] atlasUV(String code) {
        if (code == null || code.length() < 2) return null;
        String suit = String.valueOf(code.charAt(code.length() - 1)).toUpperCase();
        String rank = code.substring(0, code.length() - 1).toUpperCase();
        if (rank.equals("10")) rank = "T";
        int ri = RANK_ORDER.indexOf(rank), si = SUIT_ORDER.indexOf(suit);
        if (ri < 0 || si < 0) return null;
        return new int[]{ ri * CARD_W, si * CARD_H };
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    private void drawBorder(DrawContext ctx, int x, int y, int w, int h, int color, int t) {
        ctx.fill(x,         y,         x + w,     y + t,     color);
        ctx.fill(x,         y + h - t, x + w,     y + h,     color);
        ctx.fill(x,         y,         x + t,     y + h,     color);
        ctx.fill(x + w - t, y,         x + w,     y + h,     color);
    }

    private void sendAction(String action, int amount) {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                new PokerNetworking.PlayerActionPayload(tablePos, action, amount, ""));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (framesOpen < 5) return true;
        if (button == 0) {
            for (int j = 0; j < heroCardBounds.size(); j++) {
                int[] b = heroCardBounds.get(j);
                if (mouseX >= b[0] && mouseX < b[0] + b[2] && mouseY >= b[1] && mouseY < b[1] + b[3]) {
                    revealedHeroCards.add(j);
                    return true;
                }
            }
            if (phase.equals("SHOWDOWN")) {
                for (int i = 0; i < communityCardBounds.size(); i++) {
                    int[] b = communityCardBounds.get(i);
                    if (mouseX >= b[0] && mouseX < b[0] + b[2] && mouseY >= b[1] && mouseY < b[1] + b[3]) {
                        revealedCommunityIndices.add(i);
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { close(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    public record PlayerInfo(
            String name, int chips, int currentBet,
            boolean folded, boolean allIn, List<String> holeCards) {}
}
