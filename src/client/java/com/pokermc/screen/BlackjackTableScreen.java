package com.pokermc.screen;

import com.google.gson.*;
import com.pokermc.game.Card;
import com.pokermc.network.BlackjackNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.*;

/** Blackjack table — 8 seats, pink-black theme. Cards face down, click to reveal own. */
public class BlackjackTableScreen extends Screen {

    private static final Identifier TEX_BG = Identifier.of("casinocraft", "textures/gui/blackjack_table_bg.png");
    private static final Identifier TEX_CARD_ATLAS = Identifier.of("casinocraft", "textures/gui/card_atlas.png");
    private static final Identifier TEX_CARD_BACK = Identifier.of("casinocraft", "textures/gui/card_back_blackjack.png");
    private static final int CARD_W = 22, CARD_H = 32;
    private static final int SMALL_W = 14, SMALL_H = 20;
    private static final int ATLAS_W = 286, ATLAS_H = 128;

    private static final double[] SEAT_ANGLES = {90, 45, 0, 315, 270, 225, 180, 135};
    private static final int[] SEAT_CARD_DIRS = {2, -1, -1, -1, 0, +1, +1, +1};

    private static final int C_PINK = 0xFFFF88AA;
    private static final int C_GOLD = 0xFFFFD700;
    private static final int C_WHITE = 0xFFFFFFFF;
    private static final int C_GRAY = 0xFF888888;
    private static final int C_EMPTY = 0xFF333333;
    private static final int C_HIGHLIGHT = 0xFFFFFF44;
    private static final int C_BLUE = 0xFF4488FF;

    private final BlockPos tablePos;
    private String phase = "WAITING";
    private int maxBet = 100;
    private String currentPlayerName = "";
    private String dealerName = "";
    private String statusMessage = "";
    private long statusMessageTime = 0;
    private int bankBalance = 0;
    private final List<String> dealerHand = new ArrayList<>();
    private boolean dealerRevealed = false;
    private final Set<Integer> revealedHeroCards = new HashSet<>();
    private final Set<Integer> revealedDealerCards = new HashSet<>();
    private final List<int[]> heroCardBounds = new ArrayList<>();
    private final List<int[]> dealerCardBounds = new ArrayList<>();
    private final List<PlayerInfo> players = new ArrayList<>();
    private final List<String> pendingPlayers = new ArrayList<>();
    private String myName = "";
    private TextFieldWidget betInput;
    private int betAmount = 0;
    private int betTimeRemaining = 0;
    private ButtonWidget btnStart, btnHit, btnStand, btnBet, btnConfirm, btnDealerHit, btnSoloAll;
    private final List<int[]> soloButtonBounds = new ArrayList<>();
    private int framesOpen = 0;

    private final java.util.Map<String, Long> flipStartTimes = new java.util.HashMap<>();
    private record DealAnim(String key, String cardCode, float fromX, float fromY, float toX, float toY,
                            long startTime, int cardW, int cardH) {}
    private final java.util.List<DealAnim> dealAnims = new java.util.ArrayList<>();
    private java.util.List<String> prevDealerHand = new java.util.ArrayList<>();
    private java.util.List<String> prevHeroHand = new java.util.ArrayList<>();

    private record PlayerInfo(String name, int chips, int currentBet, boolean stood, boolean busted,
                              boolean xilac, boolean xiban, boolean soloDone, String result,
                              int handValue, List<String> hand) {}

    public BlackjackTableScreen(BlockPos pos, String initialJson) {
        super(Text.literal("Blackjack"));
        this.tablePos = pos;
    }

    @Override
    protected void init() {
        myName = MinecraftClient.getInstance().player != null
                ? MinecraftClient.getInstance().player.getName().getString() : "";

        int cx = width / 2, cy = height / 2;
        int tableW = Math.min(width - 20, 360);
        int tableH = Math.min(height - 20, 230);
        int tableTx = cx - tableW / 2;
        int tableTy = cy - tableH / 2;

        if (betAmount <= 0) betAmount = Math.min(maxBet, 100);

        addDrawableChild(ButtonWidget.builder(Text.literal("Leave"),
                b -> { sendAction("LEAVE", 0); close(); })
                .dimensions(tableTx + tableW - 54, tableTy + 2, 52, 13).build());

        btnStart = addDrawableChild(ButtonWidget.builder(Text.literal("▶ Start"),
                b -> sendAction("START", 0))
                .dimensions(cx - 28, tableTy + tableH - 14, 56, 13).build());

        int btnY = cy + 95;
        btnHit = addDrawableChild(ButtonWidget.builder(Text.literal("Hit"),
                b -> sendAction("HIT", 0))
                .dimensions(cx - 100, btnY, 55, 18).build());

        btnStand = addDrawableChild(ButtonWidget.builder(Text.literal("Stand"),
                b -> sendAction("STAND", 0))
                .dimensions(cx - 40, btnY, 55, 18).build());

        betInput = addDrawableChild(new TextFieldWidget(textRenderer, cx + 85, btnY, 40, 18, Text.literal("")));
        betInput.setMaxLength(6);
        betInput.setTextPredicate(s -> s.isEmpty() || s.matches("\\d+"));
        betInput.setPlaceholder(Text.literal("" + maxBet));

        btnBet = addDrawableChild(ButtonWidget.builder(Text.literal("Bet"),
                b -> {
                    int amt = parseBetAmount();
                    if (amt > 0) sendAction("SET_BET", amt);
                })
                .dimensions(cx + 130, btnY, 55, 18).build());

        btnConfirm = addDrawableChild(ButtonWidget.builder(Text.literal("Confirm"),
                b -> sendAction("CONFIRM_BETS", 0))
                .dimensions(cx - 25, btnY + 22, 50, 16).build());

        btnDealerHit = addDrawableChild(ButtonWidget.builder(Text.literal("Hit"),
                b -> sendAction("DEALER_HIT", 0))
                .dimensions(cx - 50, btnY, 45, 18).build());

        btnSoloAll = addDrawableChild(ButtonWidget.builder(Text.literal("Solo All"),
                b -> sendAction("SOLO_ALL", 0))
                .dimensions(cx - 2, btnY, 55, 18).build());

        updateButtonVisibility();
    }

    private void updateButtonVisibility() {
        boolean betting = phase.equals("BETTING");
        boolean betOpen = betting && betTimeRemaining > 0;
        boolean isMyTurn = myName.equals(currentPlayerName) && phase.equals("PLAYING");
        boolean amActive = players.stream().anyMatch(p -> p.name.equals(myName));
        boolean waiting = phase.equals("WAITING");
        boolean isDealer = dealerName.equals(myName);
        long playerCount = players.stream().filter(p -> !p.name.equals(dealerName)).count();

        boolean dealerSolo = phase.equals("DEALER_SOLO");
        boolean settlement = phase.equals("SETTLEMENT");

        if (btnStart != null) btnStart.visible = (waiting || settlement) && isDealer && playerCount >= 1;
        if (btnHit != null) btnHit.visible = isMyTurn;
        if (btnStand != null) btnStand.visible = isMyTurn;
        if (btnBet != null) btnBet.visible = betOpen && amActive && !isDealer;
        if (btnConfirm != null) btnConfirm.visible = betOpen && isDealer;
        if (betInput != null) betInput.visible = betOpen && amActive && !isDealer;

        if (btnDealerHit != null) btnDealerHit.visible = dealerSolo && isDealer;
        if (btnSoloAll != null) btnSoloAll.visible = dealerSolo && isDealer;
    }

    private int parseBetAmount() {
        if (betInput == null) return betAmount;
        String s = betInput.getText().trim();
        if (s.isEmpty()) return betAmount;
        try { return Math.max(0, Math.min(maxBet, Integer.parseInt(s))); } catch (Exception e) { return betAmount; }
    }

    private void sendAction(String action, int amount) {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                new BlackjackNetworking.BlackjackActionPayload(tablePos, action, amount, ""));
    }

    public void updateState(String json) {
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            phase = obj.get("phase").getAsString();
            currentPlayerName = obj.get("currentPlayer").getAsString();
            maxBet = obj.has("maxBet") ? obj.get("maxBet").getAsInt() : 100;
            dealerName = obj.has("dealer") ? obj.get("dealer").getAsString() : "";
            dealerRevealed = obj.has("dealerRevealed") && obj.get("dealerRevealed").getAsBoolean();
            if (phase.equals("WAITING") || phase.equals("BETTING")) {
                revealedHeroCards.clear();
                revealedDealerCards.clear();
            }
            String newStatus = obj.has("status") ? obj.get("status").getAsString() : "";
            statusMessage = newStatus;
            if (!newStatus.isEmpty()) statusMessageTime = System.currentTimeMillis();
            bankBalance = obj.has("bankBalance") ? obj.get("bankBalance").getAsInt() : 0;
            betTimeRemaining = obj.has("betTimeRemaining") ? obj.get("betTimeRemaining").getAsInt() : 0;

            dealerHand.clear();
            for (JsonElement e : obj.getAsJsonArray("dealerHand"))
                dealerHand.add(e.getAsString());

            int cx = width / 2;
            int tableW = Math.min(width - 20, 360);
            int tableH = Math.min(height - 20, 230);
            int tableTx = cx - tableW / 2;
            int tableTy = height / 2 - tableH / 2;
            float deckX = cx - CARD_W / 2f;
            float deckY = tableTy + 50 - CARD_H / 2f;

            for (int i = prevDealerHand.size(); i < dealerHand.size(); i++) {
                int totalW = dealerHand.size() * (CARD_W + 4) - 4;
                int toX = cx - totalW / 2 + i * (CARD_W + 4);
                int toY = tableTy + 50;
                dealAnims.add(new DealAnim("dealer-" + i, dealerHand.get(i),
                        deckX, deckY, toX, toY, System.currentTimeMillis() + i * 100, CARD_W, CARD_H));
            }
            prevDealerHand = new java.util.ArrayList<>(dealerHand);

            if (phase.equals("WAITING") || phase.equals("BETTING")) {
                flipStartTimes.clear();
                dealAnims.clear();
            }

            players.clear();
            for (JsonElement e : obj.getAsJsonArray("players")) {
                JsonObject p = e.getAsJsonObject();
                List<String> hand = new ArrayList<>();
                for (JsonElement c : p.getAsJsonArray("hand")) hand.add(c.getAsString());
                String res = p.has("result") ? p.get("result").getAsString() : null;
                players.add(new PlayerInfo(
                        p.get("name").getAsString(),
                        p.get("chips").getAsInt(),
                        p.get("currentBet").getAsInt(),
                        p.get("stood").getAsBoolean(),
                        p.get("busted").getAsBoolean(),
                        p.has("xilac") && p.get("xilac").getAsBoolean(),
                        p.has("xiban") && p.get("xiban").getAsBoolean(),
                        p.has("soloDone") && p.get("soloDone").getAsBoolean(),
                        res,
                        p.get("handValue").getAsInt(),
                        hand));
            }

            pendingPlayers.clear();
            if (obj.has("pendingPlayers"))
                for (JsonElement e : obj.getAsJsonArray("pendingPlayers"))
                    pendingPlayers.add(e.getAsString());

            PlayerInfo hero = players.stream().filter(p -> p.name.equals(myName)).findFirst().orElse(null);
            if (hero != null) {
                int heroIdx = players.indexOf(hero);
                int tcy = tableTy + tableH / 2;
                int tcx = tableTx + tableW / 2;
                double rx = tableW / 2.0 - 22, ry = tableH / 2.0 - 38;
                double rad = Math.toRadians(SEAT_ANGLES[heroIdx]);
                int sx = tcx + (int)(Math.cos(rad) * rx);
                int sy = tcy + (int)(Math.sin(rad) * ry);
                int cardDir = SEAT_CARD_DIRS[heroIdx];
                int cardX = sx + (cardDir == -1 ? -60 : cardDir == 1 ? 20 : 0);
                int cardY = sy + (cardDir == 0 ? 30 : cardDir == 2 ? -35 : 0);
                for (int i = prevHeroHand.size(); i < hero.hand.size(); i++) {
                    int toX = cardX + i * (SMALL_W + 2);
                    int toY = cardY;
                    dealAnims.add(new DealAnim("hero-" + i, hero.hand.get(i),
                            deckX, deckY, toX, toY, System.currentTimeMillis() + i * 100, SMALL_W, SMALL_H));
                }
                prevHeroHand = new java.util.ArrayList<>(hero.hand);
            } else {
                prevHeroHand.clear();
            }

            if (betAmount <= 0) betAmount = Math.min(maxBet, 100);
            if (betInput != null) betInput.setPlaceholder(Text.literal("" + maxBet));
        } catch (Exception ignored) {}
        updateButtonVisibility();
    }

    @Override public void renderBackground(DrawContext ctx, int mx, int my, float d) {}
    @Override public boolean shouldPause() { return false; }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        if (framesOpen < 10) framesOpen++;
        int cx = width / 2, cy = height / 2;
        int tableW = Math.min(width - 20, 360);
        int tableH = Math.min(height - 20, 230);
        int tableTx = cx - tableW / 2;
        int tableTy = cy - tableH / 2;

        ctx.drawTexture(TEX_BG, tableTx, tableTy, 0, 0, tableW, tableH, 360, 230);

        String phaseLabel = switch (phase) {
            case "BETTING" -> "Betting"; case "PLAYING" -> "Playing";
            case "DEALER_SOLO" -> "Dealer Solo"; case "SETTLEMENT" -> "Settlement";
            default -> "Waiting";
        };
        ctx.drawCenteredTextWithShadow(textRenderer, "Blackjack · " + phaseLabel + " · Max " + maxBet + " ZC",
                cx, tableTy + 5, C_PINK);

        int myHandValue = players.stream().filter(p -> p.name.equals(myName)).findFirst().map(p -> p.handValue).orElse(0);
        ctx.drawTextWithShadow(textRenderer, "Your hand: " + myHandValue, tableTx + 4, tableTy + 18, C_WHITE);

        long now = System.currentTimeMillis();
        if (!statusMessage.isEmpty() && now - statusMessageTime < 3000)
            ctx.drawTextWithShadow(textRenderer, statusMessage, tableTx + tableW - 54, tableTy + 18, C_GRAY);

        int tcy = tableTy + tableH / 2;
        int tcx = tableTx + tableW / 2;
        double rx = tableW / 2.0 - 22;
        double ry = tableH / 2.0 - 38;
        heroCardBounds.clear();
        soloButtonBounds.clear();
        boolean dealerSolo = phase.equals("DEALER_SOLO");
        boolean isDealer = dealerName.equals(myName);

        for (int i = 0; i < 8; i++) {
            double rad = Math.toRadians(SEAT_ANGLES[i]);
            int sx = tcx + (int)(Math.cos(rad) * rx);
            int sy = tcy + (int)(Math.sin(rad) * ry);

            if (i < players.size()) {
                PlayerInfo p = players.get(i);
                boolean isHero = p.name.equals(myName);
                drawSeat(ctx, sx, sy, SEAT_CARD_DIRS[i], p, isHero, i, dealerSolo, isDealer);
            } else {
                int pi = i - players.size();
                String pend = pi < pendingPlayers.size() ? pendingPlayers.get(pi) : null;
                drawEmptySeat(ctx, sx, sy, pend);
            }
        }

        drawDealerHand(ctx, cx, tableTy + 50);

        super.render(ctx, mouseX, mouseY, delta);
    }

    private void drawDealerHand(DrawContext ctx, int cx, int y) {
        int cardW = CARD_W, cardH = CARD_H;
        int totalW = dealerHand.size() * (cardW + 4) - 4;
        int startX = cx - totalW / 2;
        dealerCardBounds.clear();
        boolean isDealer = dealerName.equals(myName);
        for (int i = 0; i < dealerHand.size(); i++) {
            String code = dealerHand.get(i);
            int x = startX + i * (cardW + 4);
            dealerCardBounds.add(new int[]{x, y, cardW, cardH});
            final int slotIdx = i;

            var deal = dealAnims.stream().filter(d -> d.key().equals("dealer-" + slotIdx)).findFirst().orElse(null);
            if (deal != null) {
                float prog = CardAnimationHelper.getDealProgress(deal.startTime());
                if (prog >= 1f) {
                    dealAnims.remove(deal);
                } else {
                    float t = CardAnimationHelper.easeOutQuad(prog);
                    float drawX = CardAnimationHelper.lerpEased(deal.fromX(), deal.toX(), t, true);
                    float drawY = CardAnimationHelper.lerpEased(deal.fromY(), deal.toY(), t, true);
                    ctx.drawTexture(TEX_CARD_BACK, (int) drawX, (int) drawY, 0, 0, cardW, cardH, cardW, cardH);
                    continue;
                }
            }

            boolean show = dealerRevealed || (isDealer && revealedDealerCards.contains(i));
            Long flipStart = flipStartTimes.get("dealer-" + i);
            if ("??".equals(code) || !show) {
                ctx.drawTexture(TEX_CARD_BACK, x, y, 0, 0, cardW, cardH, cardW, cardH);
            } else if (flipStart != null) {
                float prog = CardAnimationHelper.getFlipProgress(flipStart);
                if (prog >= 1f) {
                    flipStartTimes.remove("dealer-" + i);
                    drawCard(ctx, code, x, y, cardW, cardH);
                } else {
                    try {
                        Card c = Card.fromCode(code);
                        int u = (c.rank().ordinal() % 13) * 22;
                        int v = c.suit().ordinal() * 32;
                        CardAnimationHelper.drawCardWithFlip(ctx, x, y, cardW, cardH,
                                TEX_CARD_BACK, TEX_CARD_ATLAS, u, v, ATLAS_W, ATLAS_H, CARD_W, CARD_H, prog);
                    } catch (Exception e) {
                        ctx.drawTexture(TEX_CARD_BACK, x, y, 0, 0, cardW, cardH, cardW, cardH);
                    }
                }
            } else {
                drawCard(ctx, code, x, y, cardW, cardH);
            }
        }
    }

    private void drawCard(DrawContext ctx, String code, int x, int y, int w, int h) {
        if ("??".equals(code)) {
            ctx.drawTexture(TEX_CARD_BACK, x, y, 0, 0, w, h, w, h);
            return;
        }
        try {
            Card c = Card.fromCode(code);
            int u = (c.rank().ordinal() % 13) * 22;
            int v = c.suit().ordinal() * 32;
            ctx.drawTexture(TEX_CARD_ATLAS, x, y, u, v, w, h, ATLAS_W, ATLAS_H);
        } catch (Exception e) {
            ctx.drawTexture(TEX_CARD_BACK, x, y, 0, 0, w, h, w, h);
        }
    }

    private void drawSeat(DrawContext ctx, int sx, int sy, int cardDir, PlayerInfo p, boolean isHero,
                          int playerIndex, boolean dealerSolo, boolean isDealer) {
        int headSz = 14;
        int hx = sx - headSz / 2, hy = sy - headSz / 2;
        drawPlayerHead(ctx, p.name, hx, hy, headSz);

        boolean showSolo = dealerSolo && isDealer && !p.name.equals(dealerName)
                && p.currentBet > 0 && !p.soloDone && (p.stood || p.busted);
        if (showSolo) {
            int soloW = 32, soloH = 14;
            int soloX = sx - soloW / 2, soloY = hy - soloH - 4;
            ctx.fill(soloX - 1, soloY - 1, soloX + soloW + 1, soloY + soloH + 1, 0xFF333333);
            ctx.fill(soloX, soloY, soloX + soloW, soloY + soloH, 0xFF555555);
            ctx.drawCenteredTextWithShadow(textRenderer, "Solo", sx, soloY + 3, C_WHITE);
            soloButtonBounds.add(new int[]{soloX, soloY, soloW, soloH, playerIndex});
        }

        boolean isTurn = p.name.equals(currentPlayerName);
        if (isTurn) ctx.fill(hx - 1, hy - 1, hx + headSz + 1, hy + headSz + 1, 0x44FF0000);

        if (dealerName.equals(p.name))
            ctx.drawCenteredTextWithShadow(textRenderer, "♔", sx, hy - 9, C_GOLD);

        String label = p.name;
        if (textRenderer.getWidth(label) > 46) label = label.substring(0, 5) + "..";
        ctx.drawCenteredTextWithShadow(textRenderer, label, sx, sy + headSz/2 + 2,
                isTurn ? C_HIGHLIGHT : 0xFFFFFFFF);

        int infoY = sy + headSz/2 + 12;
        if (p.currentBet > 0) {
            ctx.drawCenteredTextWithShadow(textRenderer, "Bet: " + p.currentBet, sx, infoY, C_BLUE);
        }

        int cardX = sx + (cardDir == -1 ? -60 : cardDir == 1 ? 20 : 0);
        int cardY = sy + (cardDir == 0 ? 30 : cardDir == 2 ? -35 : 0);
        int cardsW = p.hand.isEmpty() ? 0 : p.hand.size() * (SMALL_W + 2) - 2;
        if (isTurn && !p.hand.isEmpty()) {
            ctx.fill(cardX - 2, cardY - 2, cardX + cardsW + SMALL_W + 2, cardY + SMALL_H + 2, 0x44FF0000);
            ctx.fill(cardX - 2, cardY - 2, cardX + cardsW + SMALL_W + 2, cardY - 1, 0xFFFF0000);
            ctx.fill(cardX - 2, cardY + SMALL_H + 1, cardX + cardsW + SMALL_W + 2, cardY + SMALL_H + 2, 0xFFFF0000);
            ctx.fill(cardX - 2, cardY - 2, cardX - 1, cardY + SMALL_H + 2, 0xFFFF0000);
            ctx.fill(cardX + cardsW + SMALL_W + 1, cardY - 2, cardX + cardsW + SMALL_W + 2, cardY + SMALL_H + 2, 0xFFFF0000);
        }
        for (int i = 0; i < p.hand.size(); i++) {
            int ox = cardX + i * (SMALL_W + 2);
            if (isHero) heroCardBounds.add(new int[]{ox, cardY, SMALL_W, SMALL_H});
            final int cardIdx = i;

            if (isHero) {
                var deal = dealAnims.stream().filter(d -> d.key().equals("hero-" + cardIdx)).findFirst().orElse(null);
                if (deal != null) {
                    float prog = CardAnimationHelper.getDealProgress(deal.startTime());
                    if (prog >= 1f) {
                        dealAnims.remove(deal);
                    } else {
                        float t = CardAnimationHelper.easeOutQuad(prog);
                        float drawX = CardAnimationHelper.lerpEased(deal.fromX(), deal.toX(), t, true);
                        float drawY = CardAnimationHelper.lerpEased(deal.fromY(), deal.toY(), t, true);
                        ctx.drawTexture(TEX_CARD_BACK, (int) drawX, (int) drawY, 0, 0, SMALL_W, SMALL_H, CARD_W, CARD_H);
                        continue;
                    }
                }

                boolean show = revealedHeroCards.contains(i) || phase.equals("SETTLEMENT") || p.stood || p.busted || p.result != null;
                Long flipStart = flipStartTimes.get("hero-" + i);
                if (!show) {
                    ctx.drawTexture(TEX_CARD_BACK, ox, cardY, 0, 0, SMALL_W, SMALL_H, SMALL_W, SMALL_H);
                } else if (flipStart != null) {
                    float prog = CardAnimationHelper.getFlipProgress(flipStart);
                    if (prog >= 1f) {
                        flipStartTimes.remove("hero-" + i);
                        drawCard(ctx, p.hand.get(i), ox, cardY, SMALL_W, SMALL_H);
                    } else {
                        try {
                            Card c = Card.fromCode(p.hand.get(i));
                            int u = (c.rank().ordinal() % 13) * 22;
                            int v = c.suit().ordinal() * 32;
                            CardAnimationHelper.drawCardWithFlip(ctx, ox, cardY, SMALL_W, SMALL_H,
                                    TEX_CARD_BACK, TEX_CARD_ATLAS, u, v, ATLAS_W, ATLAS_H, CARD_W, CARD_H, prog);
                        } catch (Exception e) {
                            drawCard(ctx, p.hand.get(i), ox, cardY, SMALL_W, SMALL_H);
                        }
                    }
                } else {
                    drawCard(ctx, p.hand.get(i), ox, cardY, SMALL_W, SMALL_H);
                }
            } else {
                boolean show = phase.equals("SETTLEMENT") || p.stood || p.busted || p.result != null;
                if (!show) {
                    ctx.drawTexture(TEX_CARD_BACK, ox, cardY, 0, 0, SMALL_W, SMALL_H, SMALL_W, SMALL_H);
                } else {
                    drawCard(ctx, p.hand.get(i), ox, cardY, SMALL_W, SMALL_H);
                }
            }
        }
        if (p.result != null) {
            int resColor = "WIN".equals(p.result) ? 0xFF55FF55 : "LOSE".equals(p.result) ? 0xFFFF5555 : C_GRAY;
            ctx.drawCenteredTextWithShadow(textRenderer, p.result, sx, cardY + SMALL_H + 12, resColor);
        }
    }

    private void drawPlayerHead(DrawContext ctx, String name, int x, int y, int size) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null && mc.player.networkHandler != null) {
            PlayerListEntry entry = mc.player.networkHandler.getPlayerListEntry(name);
            if (entry != null) {
                var skin = entry.getSkinTextures().texture();
                ctx.drawTexture(skin, x, y, size, size, 8f, 8f, 8, 8, 64, 64);
                ctx.drawTexture(skin, x, y, size, size, 40f, 8f, 8, 8, 64, 64);
                return;
            }
        }
        int h = name.isEmpty() ? 0 : name.hashCode();
        int r = 90 + (h & 0x7F), g = 80 + ((h >> 8) & 0x5F), b = 90 + ((h >> 16) & 0x7F);
        ctx.fill(x, y, x + size, y + size, 0xFF000000 | (r << 16) | (g << 8) | b);
        ctx.fill(x, y, x + size, y + 1, 0xFF8888AA);
        ctx.fill(x, y + size - 1, x + size, y + size, 0xFF8888AA);
        ctx.fill(x, y, x + 1, y + size, 0xFF8888AA);
        ctx.fill(x + size - 1, y, x + size, y + size, 0xFF8888AA);
        String initial = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase();
        ctx.drawCenteredTextWithShadow(textRenderer, initial, x + size/2, y + size/2 - 4, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (framesOpen < 5) return true;
        if (button == 0) {
            for (int[] b : soloButtonBounds) {
                if (b.length >= 5 && mouseX >= b[0] && mouseX < b[0] + b[2] && mouseY >= b[1] && mouseY < b[1] + b[3]) {
                    sendAction("SOLO", b[4]);
                    return true;
                }
            }
            for (int j = 0; j < heroCardBounds.size(); j++) {
                int[] b = heroCardBounds.get(j);
                if (mouseX >= b[0] && mouseX < b[0] + b[2] && mouseY >= b[1] && mouseY < b[1] + b[3]) {
                    if (!revealedHeroCards.contains(j)) {
                        flipStartTimes.put("hero-" + j, System.currentTimeMillis());
                    }
                    revealedHeroCards.add(j);
                    return true;
                }
            }
            if (dealerName.equals(myName)) {
                for (int i = 0; i < dealerCardBounds.size(); i++) {
                    int[] b = dealerCardBounds.get(i);
                    if (mouseX >= b[0] && mouseX < b[0] + b[2] && mouseY >= b[1] && mouseY < b[1] + b[3]
                            && i < dealerHand.size() && !"??".equals(dealerHand.get(i))) {
                        if (!revealedDealerCards.contains(i)) {
                            flipStartTimes.put("dealer-" + i, System.currentTimeMillis());
                        }
                        revealedDealerCards.add(i);
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void drawEmptySeat(DrawContext ctx, int sx, int sy, String pendName) {
        int bx = sx - 14, by = sy - 8;
        ctx.fill(bx, by, bx + 28, by + 16, 0x22000000);
        ctx.fill(bx, by, bx + 28, by + 1, C_EMPTY);
        ctx.fill(bx, by + 15, bx + 28, by + 16, C_EMPTY);
        ctx.fill(bx, by, bx + 1, by + 16, C_EMPTY);
        ctx.fill(bx + 27, by, bx + 28, by + 16, C_EMPTY);
        if (pendName != null)
            ctx.drawCenteredTextWithShadow(textRenderer, pendName.length() > 8 ? pendName.substring(0,6)+".." : pendName.replace(" (pending)", ""),
                    sx, sy - 4, 0xFFAA88CC);
    }
}
