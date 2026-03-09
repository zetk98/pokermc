package com.pokermc.screen;

import com.google.gson.*;
import com.pokermc.game.bang.BangCard;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.*;

/**
 * Bang! game table - fullscreen, clean layout.
 * POV at bottom. Cards 22x32px. Poker-style deal animation (one card at a time).
 */
public class BangTableScreen extends Screen {

    private static final Identifier TEX_CARD_BACK = Identifier.of("bang", "textures/bang_card_back.png");
    private static final int CARD_W = 22, CARD_H = 32;
    private static final int SLOT_W = 22, SLOT_H = 32;
    private static final int C_GOLD = 0xFFFFD700, C_WHITE = 0xFFFFFFFF, C_GRAY = 0xFF888888;
    private static final int C_ORANGE = 0xFFFF8844, C_RED = 0xFFFF4444, C_GREEN = 0xFF55CC55;
    private static final int SLOT_RED = 0xCCAA2222, SLOT_BLUE = 0xCC2222AA, SLOT_GREEN = 0xCC22AA22, SLOT_YELLOW = 0xCCAAAA22;

    private static final String BANG_TEX_SPLIT = "textures/cards/split/";
    /** Chỉ dùng texture tách (textures/cards/split/, textures/roles/split/) */

    private static final int MAX_EQUIP_SLOTS = 4;

    private final BlockPos tablePos;
    private String stateJson = "{}";
    private String phase = "WAITING";
    private String statusMessage = "";
    private String reactingTarget = "";
    private String jailDrawnCard = "";
    private String dynamiteDrawnCard = "";
    private boolean canUseBarrel = false;
    private String chooseTarget = "";
    private boolean chooseIsPanic = false;
    private String duelCurrentPlayer = "";
    private String dynamiteCard = "";
    private int dynamitePlayerIndex = -1;
    private int jailPlayerIndex = -1;
    private final List<String> logEntries = new ArrayList<>();
    private static final int LOG_W = 130, LOG_LINES = 3;
    private int deckCount = 0;
    private String currentPlayerName = "";
    private int currentPlayerIndex = -1;
    private int sheriffIndex = -1;
    private int heroIndex = -1;
    private int maxPlayers = 2;
    private String myName = "";
    private final List<PlayerInfo> players = new ArrayList<>();
    private final Map<String, Integer> distancesFromHero = new HashMap<>();
    private final List<String> pendingPlayers = new ArrayList<>();

    private ButtonWidget btnLeave, btnSettings, btnEndTurn, btnDiscard, btnPlay;
    private final Set<Integer> selectedCardIndices = new HashSet<>();
    private int selectedEquipIndex = -1; // for CHOOSE_CARD: which equipment to give

    /** Player hitbox: compact, 6 green + 6 blue slots. Slot size = card ratio 22x32 (do not change). */
    private static final int PANEL_W = 155, PANEL_H = 95;
    private static final int PANEL_SLOT_W = 22, PANEL_SLOT_H = 32;
    private record PlayerPosition(int hitboxX, int hitboxY, int hitboxW, int hitboxH) {}
    private final List<PlayerPosition> positionCache = new ArrayList<>();
    private final List<Integer> displayOrder = new ArrayList<>();

    /** Deal animation: card flies from deck to target (Poker-style, one by one) */
    private record DealAnim(String key, String cardCode, float fromX, float fromY, float toX, float toY,
                            long startTime, boolean isHero) {}
    private final List<DealAnim> dealAnims = new ArrayList<>();
    private long lastReactionSendTime = 0; // Lock to prevent Gatling/Indians spam
    private String lastNotification = "";
    private long lastNotificationTime = 0;
    private static final long NOTIFICATION_DURATION_MS = 3000;
    private List<String> prevHeroCards = new ArrayList<>();
    private boolean roleRevealed = false;
    private long roleRevealStartTime = 0;
    private static final long ROLE_REVEAL_DURATION_MS = 2500;
    private static final long ROLE_ANIM_DURATION_MS = 600;

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

        btnLeave = addDrawableChild(ButtonWidget.builder(Text.literal(BangLang.tr("Leave", "Rời bàn")),
                b -> { sendAction("LEAVE", 0, ""); close(); })
                .dimensions(0, 0, 55, 14).build());

        btnSettings = addDrawableChild(ButtonWidget.builder(Text.literal(BangLang.tr("Settings", "Cài đặt")),
                b -> { if (client != null) client.setScreen(new BangSettingsScreen(this)); })
                .dimensions(0, 0, 60, 14).build());

        btnEndTurn = addDrawableChild(ButtonWidget.builder(Text.literal("End"),
                b -> sendAction("END_TURN", 0, ""))
                .dimensions(0, 0, 48, 14).build());

        btnDiscard = addDrawableChild(ButtonWidget.builder(Text.literal(BangLang.tr("Discard", "Bỏ bài")),
                b -> discardSelectedCards())
                .dimensions(0, 0, 50, 14).build());

        btnPlay = addDrawableChild(ButtonWidget.builder(Text.literal("Play"),
                b -> playSelectedCards())
                .dimensions(0, 0, 40, 14).build());

        updateButtons();
    }

    private void sendAction(String action, int amount, String data) {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                new com.pokermc.network.BangNetworking.BangActionPayload(tablePos, action, amount, data));
    }

    private void updateButtons() {
        if (btnLeave == null) return;
        boolean waiting = "WAITING".equals(phase);
        boolean playing = "PLAYING".equals(phase) || "DISCARD".equals(phase) || "ROLE_REVEAL".equals(phase) || "DEALING".equals(phase) || "DEAL_PAUSE".equals(phase) || "DEAL_FIRST".equals(phase);
        boolean reacting = "REACTING".equals(phase) || "GATLING_REACT".equals(phase);
        boolean indiansReacting = "INDIANS_REACT".equals(phase);
        boolean isOwner = !players.isEmpty() && players.get(0).name.equals(myName);
        boolean isMyTurn = myName.equals(currentPlayerName) && playing;
        boolean isReacting = reacting && myName.equals(reactingTarget);
        boolean isReactingToIndians = indiansReacting && reactingTarget.equals(myName);
        boolean isChoosing = "CHOOSE_CARD".equals(phase) && myName.equals(chooseTarget);
        boolean isDuelMyTurn = "DUEL_PLAY".equals(phase) && myName.equals(duelCurrentPlayer);

        btnLeave.visible = true;
        btnLeave.setMessage(Text.literal(BangLang.tr("Leave", "Rời bàn")));
        if (btnSettings != null) btnSettings.setMessage(Text.literal(BangLang.tr("Settings", "Cài đặt")));
        PlayerInfo hero = players.stream().filter(p -> p.name.equals(myName)).findFirst().orElse(null);
        int handLimit = hero != null && hero.maxHp > 0 ? hero.maxHp : 5;
        boolean overLimit = hero != null && hero.hand.size() > handLimit;
        boolean inDiscardPhase = "DISCARD".equals(phase) && isMyTurn;
        boolean canEndTurn = (playing && isMyTurn && ("PLAYING".equals(phase) || inDiscardPhase) && !overLimit);
        btnEndTurn.visible = true;
        btnEndTurn.active = canEndTurn;
        btnEndTurn.setMessage(Text.literal(overLimit && isMyTurn ? BangLang.tr("Discard first", "Loại bỏ trước") : BangLang.tr("End turn", "Kết lượt")));
        boolean hasSelection = !selectedCardIndices.isEmpty();
        boolean selIsBlue = hasSelection && isMyTurn && hero != null && isSelectedBlue(hero);
        boolean selIsGunReplace = selIsBlue && heroHasGun(hero) && isSelectedGun(hero);
        boolean hasChooseSelection = isChoosing && hero != null && (hasSelection || (selectedEquipIndex >= 0 && selectedEquipIndex < hero.equipment.size()) || (chooseIsPanic && !hero.hand.isEmpty()));
        boolean selIsBang = hasSelection && hero != null && isSelectedBang(hero);
        boolean selIsMissed = hasSelection && hero != null && isSelectedMissedOrBeer(hero);
        boolean selNeedsTarget = hasSelection && isMyTurn && hero != null && isSelectedNeedsTarget(hero);
        boolean canDiscard = inDiscardPhase && hasSelection && overLimit;
        boolean reactionLocked = (System.currentTimeMillis() - lastReactionSendTime) < 300;
        boolean canPlay = reactionLocked ? false : ((isMyTurn && hasSelection && !selNeedsTarget && !inDiscardPhase) || isReacting || isReactingToIndians || hasChooseSelection || isDuelMyTurn || (isReacting && canUseBarrel));
        btnPlay.visible = true;
        btnPlay.active = canPlay;
        if (btnDiscard != null) {
            btnDiscard.visible = inDiscardPhase && overLimit;
            btnDiscard.active = canDiscard;
        }
        String playLabel = isChoosing ? BangLang.tr("Choose card", "Chọn lá")
                : isReactingToIndians ? (selIsBang ? BangLang.tr("Play Bang", "Đánh Bang") : BangLang.tr("Take hit", "Nhận đạn"))
                : isReacting ? (canUseBarrel && !hasSelection ? BangLang.tr("Use Barrel", "Dùng Barrel") : selIsMissed ? BangLang.tr("Use Miss", "Dùng Miss") : BangLang.tr("Take hit", "Nhận đạn"))
                : isDuelMyTurn ? (selIsBang ? BangLang.tr("Bang", "Bang") : BangLang.tr("Pass", "Bỏ qua"))
                : selIsGunReplace ? BangLang.tr("Replace gun", "Đổi súng") : selIsBlue ? BangLang.tr("Equip", "Trang bị") : BangLang.tr("Play", "Đánh");
        btnPlay.setMessage(Text.literal(playLabel));

        int pad = 8, btnH = 14, btnGap = 1;
        int btnWEnd = 48, btnWDiscard = 50, btnWPlay = 40;
        int btnWLeave = 55, btnWSettings = 60;
        int logH = LOG_LINES * 10 + 4;
        int btnY = height - pad - btnH;
        int logY = btnY - btnGap - logH;
        int topRowY = logY - btnGap - btnH;
        int rightEdge = width - pad;
        btnPlay.setPosition(rightEdge - btnWPlay, btnY);
        if (btnDiscard != null) {
            btnDiscard.setPosition(rightEdge - btnWPlay - btnGap - btnWDiscard, btnY);
            int endTurnX = (inDiscardPhase && overLimit)
                    ? rightEdge - btnWPlay - btnGap - btnWDiscard - btnGap - btnWEnd
                    : rightEdge - btnWPlay - btnGap - btnWEnd;
            btnEndTurn.setPosition(endTurnX, btnY);
        } else {
            btnEndTurn.setPosition(rightEdge - btnWPlay - btnGap - btnWEnd, btnY);
        }
        btnLeave.setPosition(rightEdge - btnWLeave, topRowY);
        btnSettings.setPosition(rightEdge - btnWLeave - btnGap - btnWSettings, topRowY);
    }

    private boolean isSelectedBlue(PlayerInfo hero) {
        if (selectedCardIndices.isEmpty()) return false;
        int idx = selectedCardIndices.iterator().next();
        if (idx >= hero.hand.size()) return false;
        BangCard c = BangCard.fromCode(hero.hand.get(idx));
        return c != null && c.isBlue();
    }

    private boolean isSelectedGun(PlayerInfo hero) {
        if (selectedCardIndices.isEmpty()) return false;
        int idx = selectedCardIndices.iterator().next();
        if (idx >= hero.hand.size()) return false;
        BangCard c = BangCard.fromCode(hero.hand.get(idx));
        return c != null && c.isGun();
    }

    private boolean isSelectedBang(PlayerInfo hero) {
        if (selectedCardIndices.isEmpty()) return false;
        int idx = selectedCardIndices.iterator().next();
        if (idx >= hero.hand.size()) return false;
        BangCard c = BangCard.fromCode(hero.hand.get(idx));
        return c != null && BangCard.BANG.equals(c.typeId());
    }

    private boolean isSelectedMissedOrBeer(PlayerInfo hero) {
        if (selectedCardIndices.isEmpty()) return false;
        int idx = selectedCardIndices.iterator().next();
        if (idx >= hero.hand.size()) return false;
        BangCard c = BangCard.fromCode(hero.hand.get(idx));
        if (c == null) return false;
        if (BangCard.MISSED.equals(c.typeId())) return true;
        return BangCard.BEER.equals(c.typeId()) && hero.hp == 1;
    }

    private boolean isSelectedNeedsTarget(PlayerInfo hero) {
        if (selectedCardIndices.isEmpty()) return false;
        int idx = selectedCardIndices.iterator().next();
        if (idx >= hero.hand.size()) return false;
        BangCard c = BangCard.fromCode(hero.hand.get(idx));
        return c != null && needsTarget(c.typeId());
    }

    private boolean heroHasGun(PlayerInfo hero) {
        if (hero == null) return false;
        for (String code : hero.equipment) {
            BangCard c = BangCard.fromCode(code);
            if (c != null && c.isGun()) return true;
        }
        return false;
    }

    private void discardSelectedCards() {
        PlayerInfo hero = players.stream().filter(p -> p.name.equals(myName)).findFirst().orElse(null);
        if (hero == null) return;
        boolean inDiscardPhase = "DISCARD".equals(phase) && myName.equals(currentPlayerName);
        int handLimit = hero.maxHp > 0 ? hero.maxHp : 5;
        boolean overLimit = hero.hand.size() > handLimit;
        if (inDiscardPhase && !selectedCardIndices.isEmpty() && overLimit) {
            String data = selectedCardIndices.stream().sorted().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("");
            sendAction("DISCARD_CARDS", 0, data);
            selectedCardIndices.clear();
        }
    }

    private void playSelectedCards() {
        PlayerInfo hero = players.stream().filter(p -> p.name.equals(myName)).findFirst().orElse(null);
        if (hero == null) return;
        boolean isMyTurn = myName.equals(currentPlayerName) && ("PLAYING".equals(phase) || "DISCARD".equals(phase));
        boolean isReacting = ("REACTING".equals(phase) || "GATLING_REACT".equals(phase)) && myName.equals(reactingTarget);
        boolean isReactingToIndians = "INDIANS_REACT".equals(phase) && myName.equals(reactingTarget);
        boolean isChoosing = "CHOOSE_CARD".equals(phase) && myName.equals(chooseTarget);
        int handLimit = Math.min(5, hero.maxHp > 0 ? hero.maxHp : 5);
        boolean inDiscardPhase = "DISCARD".equals(phase) && myName.equals(currentPlayerName);
        boolean overLimit = hero.hand.size() > handLimit;
        if (inDiscardPhase && overLimit) return; // Use Discard button instead
        if (isChoosing) {
            if (!selectedCardIndices.isEmpty()) {
                int idx = selectedCardIndices.iterator().next();
                sendAction("CHOOSE_CARD", 0, "hand:" + idx);
                selectedCardIndices.clear();
                selectedEquipIndex = -1;
            } else if (chooseIsPanic && !hero.hand.isEmpty()) {
                sendAction("CHOOSE_CARD", 0, "hand:random");
                selectedCardIndices.clear();
            } else if (selectedEquipIndex >= 0 && selectedEquipIndex < hero.equipment.size()) {
                sendAction("CHOOSE_CARD", 0, "equip:" + selectedEquipIndex);
                selectedCardIndices.clear();
                selectedEquipIndex = -1;
            }
            return;
        }
        if (isReacting) {
            lastReactionSendTime = System.currentTimeMillis();
            if (canUseBarrel && selectedCardIndices.isEmpty()) {
                sendAction("USE_BARREL", 0, "");
                return;
            }
            int idx = selectedCardIndices.isEmpty() ? -1 : selectedCardIndices.iterator().next();
            if (idx >= 0 && idx < hero.hand.size()) {
                BangCard c = BangCard.fromCode(hero.hand.get(idx));
                if (c != null && (BangCard.MISSED.equals(c.typeId()) || (BangCard.BEER.equals(c.typeId()) && hero.hp == 1))) {
                    sendAction("USE_MISS", idx, "");
                    selectedCardIndices.clear();
                    return;
                }
            }
            sendAction("USE_MISS", -1, "");
            selectedCardIndices.clear();
            return;
        }
        if (isReactingToIndians) {
            lastReactionSendTime = System.currentTimeMillis();
            int idx = selectedCardIndices.isEmpty() ? -1 : selectedCardIndices.iterator().next();
            if (idx >= 0 && idx < hero.hand.size() && isSelectedBang(hero)) {
                sendAction("INDIANS_REACT", idx, "");
            } else {
                sendAction("INDIANS_REACT", -1, "");
            }
            selectedCardIndices.clear();
            return;
        }
        boolean isDuelMyTurn = "DUEL_PLAY".equals(phase) && myName.equals(duelCurrentPlayer);
        if (isDuelMyTurn) {
            if (!selectedCardIndices.isEmpty() && isSelectedBang(hero)) {
                int idx = selectedCardIndices.iterator().next();
                sendAction("DUEL_BANG", idx, "");
            } else {
                sendAction("PASS_DUEL", 0, "");
            }
            selectedCardIndices.clear();
            return;
        }
        if (isMyTurn && !selectedCardIndices.isEmpty()) {
            int idx = selectedCardIndices.iterator().next();
            BangCard card = BangCard.fromCode(hero.hand.get(idx));
            if (card != null && card.isBlue()) {
                boolean replace = card.isGun() && heroHasGun(hero);
                sendAction("EQUIP_BLUE", idx, replace ? "replace" : "");
                selectedCardIndices.clear();
                return;
            }
            if (card != null && needsTarget(card.typeId())) {
                return;
            }
            sendAction("PLAY_CARD", idx, "");
            selectedCardIndices.clear();
        }
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
            reactingTarget = obj.has("reactingTarget") ? obj.get("reactingTarget").getAsString() : "";
            jailDrawnCard = obj.has("jailDrawnCard") ? obj.get("jailDrawnCard").getAsString() : "";
            dynamiteDrawnCard = obj.has("dynamiteDrawnCard") ? obj.get("dynamiteDrawnCard").getAsString() : "";
            canUseBarrel = obj.has("canUseBarrel") && obj.get("canUseBarrel").getAsBoolean();
            chooseTarget = obj.has("chooseTarget") ? obj.get("chooseTarget").getAsString() : "";
            chooseIsPanic = obj.has("chooseIsPanic") && obj.get("chooseIsPanic").getAsBoolean();
            boolean duelAttackerTurn = obj.has("duelAttackerTurn") && obj.get("duelAttackerTurn").getAsBoolean();
            String duelAttacker = obj.has("duelAttacker") ? obj.get("duelAttacker").getAsString() : "";
            String duelDefender = obj.has("duelDefender") ? obj.get("duelDefender").getAsString() : "";
            duelCurrentPlayer = duelAttackerTurn ? duelAttacker : duelDefender;
            currentPlayerName = obj.has("currentPlayer") ? obj.get("currentPlayer").getAsString() : "";
            currentPlayerIndex = obj.has("currentPlayerIndex") ? obj.get("currentPlayerIndex").getAsInt() : -1;
            sheriffIndex = obj.has("sheriffIndex") ? obj.get("sheriffIndex").getAsInt() : -1;
            heroIndex = obj.has("heroIndex") ? obj.get("heroIndex").getAsInt() : -1;
            maxPlayers = obj.has("maxPlayers") ? obj.get("maxPlayers").getAsInt() : 2;
            deckCount = obj.has("deckCount") ? obj.get("deckCount").getAsInt() : 0;
            dynamiteCard = obj.has("dynamiteCard") ? obj.get("dynamiteCard").getAsString() : "";
            dynamitePlayerIndex = obj.has("dynamitePlayerIndex") ? obj.get("dynamitePlayerIndex").getAsInt() : -1;
            jailPlayerIndex = obj.has("jailPlayerIndex") ? obj.get("jailPlayerIndex").getAsInt() : -1;

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

            // Detect new cards → start deal animation (Poker-style, one by one)
            PlayerInfo hero = players.stream().filter(p -> p.name.equals(myName)).findFirst().orElse(null);
            if (hero != null && ("DEALING".equals(phase) || "DEAL_PAUSE".equals(phase) || "DEAL_FIRST".equals(phase))) {
                int[] dc = getDeckCenter();
                float deckX = dc[0];
                float deckY = dc[1];
                int[] handArea = getHandArea();
                int handX = handArea[0], handY = handArea[1];
                int cardGap = 1;
                for (int i = prevHeroCards.size(); i < hero.hand.size(); i++) {
                    int[] pos = cardIndexToPosition(i, handX, handY);
                    float toX = pos[0];
                    float toY = pos[1];
                    String code = hero.hand.get(i);
                    dealAnims.add(new DealAnim("hero-" + i, code, deckX, deckY, toX, toY,
                            System.currentTimeMillis() + i * 100, true));
                }
                prevHeroCards = new ArrayList<>(hero.hand);
            } else if (hero != null) {
                prevHeroCards = new ArrayList<>(hero.hand);
            } else {
                prevHeroCards.clear();
            }

            if ("WAITING".equals(phase)) {
                dealAnims.clear();
                prevHeroCards.clear();
                roleRevealed = false;
            }
            PlayerInfo heroForRole = players.stream().filter(p -> p.name.equals(myName)).findFirst().orElse(null);
            if (heroForRole != null && "ROLE_REVEAL".equals(phase) && !heroForRole.role.isEmpty() && !roleRevealed) {
                roleRevealed = true;
                roleRevealStartTime = System.currentTimeMillis();
            }
            if ("GAME_OVER".equals(phase)) {
                logEntries.clear();
            }
            if (!statusMessage.isEmpty()) {
                String prev = logEntries.isEmpty() ? "" : logEntries.get(logEntries.size() - 1);
                if (!statusMessage.equals(prev)) {
                    logEntries.add(statusMessage);
                    while (logEntries.size() > 100) logEntries.remove(0);
                }
                if (!statusMessage.equals(lastNotification) || (System.currentTimeMillis() - lastNotificationTime) > NOTIFICATION_DURATION_MS) {
                    lastNotification = statusMessage;
                    lastNotificationTime = System.currentTimeMillis();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** Rectangular hitboxes: hero bottom-left, player2 top-right (POV), others in grid. */
    private void computePositions() {
        positionCache.clear();
        displayOrder.clear();
        int n = players.size();
        if (n == 0) return;

        int hi = -1;
        for (int i = 0; i < n; i++) {
            if (players.get(i).name.equals(myName)) { hi = i; break; }
        }
        if (hi < 0) hi = 0;
        displayOrder.add(hi);
        for (int i = 0; i < n; i++) if (i != hi) displayOrder.add(i);

        int pad = 6;
        if (n == 2) {
            positionCache.add(new PlayerPosition(12, height - PANEL_H - 10, PANEL_W, PANEL_H));
            positionCache.add(new PlayerPosition(width - PANEL_W - pad, pad, PANEL_W, PANEL_H));
        } else {
            int cols = Math.min(n, 4);
            int rows = (n + cols - 1) / cols;
            int startY = pad;
            int availW = width - pad * 2;
            int availH = height - 120;
            int cellW = Math.max(PANEL_W + pad, (availW - pad * (cols - 1)) / cols);
            int cellH = Math.max(PANEL_H + pad, (availH - pad * (rows - 1)) / rows);
            for (int i = 0; i < n; i++) {
                int col = i % cols;
                int row = i / cols;
                int hx = pad + col * (cellW + pad);
                int hy = startY + row * (cellH + pad);
                positionCache.add(new PlayerPosition(hx, hy, PANEL_W, PANEL_H));
            }
        }
    }

    /** Hand cards: bottom, right of hero hitbox. Gap 3px to hitbox. Max 15 cards, 3 rows x 5. */
    private int[] getHandArea() {
        int heroX = 12, heroW = PANEL_W, gap = 3;
        int handX = heroX + heroW + gap;
        int cardsPerRow = 5;
        int cardGap = 1;
        int totalW = cardsPerRow * (CARD_W + cardGap) - cardGap;
        int totalH = 3 * (CARD_H + 2) - 2;
        int handY = height - totalH - 10;
        return new int[]{handX, handY, totalW, totalH};
    }

    /** Card index i -> (row, col). Row 2=bottom(1-5), row 1=6-10, row 0=11-15. */
    private int[] cardIndexToPosition(int i, int handX, int handY) {
        int cardsPerRow = 5;
        int cardGap = 1;
        int visualRow = 2 - (i / cardsPerRow);
        int visualCol = i % cardsPerRow;
        int x = handX + visualCol * (CARD_W + cardGap);
        int y = handY + visualRow * (CARD_H + 2);
        return new int[]{x, y};
    }

    @Override public void renderBackground(DrawContext ctx, int mx, int my, float d) {}
    @Override public boolean shouldPause() { return false; }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        computePositions();

        Identifier bgTex = Identifier.of("casinocraft", "textures/gui/bang_desert_bg.png");
        ctx.drawTexture(bgTex, 0, 0, 0, 0, width, height, 256, 256);

        int n = Math.min(players.size(), Math.min(positionCache.size(), displayOrder.size()));
        for (int i = 0; i < n; i++) {
            int playerIdx = displayOrder.get(i);
            PlayerInfo p = players.get(playerIdx);
            PlayerPosition pos = positionCache.get(i);
            renderPlayerPanel(ctx, p, pos, playerIdx == sheriffIndex, p.name.equals(currentPlayerName), p.name.equals(myName), playerIdx == jailPlayerIndex);
        }

        renderDeckCenter(ctx, mouseX, mouseY);

        if ("JAIL_CHECK".equals(phase) && !jailDrawnCard.isEmpty()) {
            int jcx = width / 2, jcy = height / 2;
            ctx.drawTextWithShadow(textRenderer, BangLang.tr("Jail check - Card drawn:", "Kiểm tra Jail - Lá rút:"), jcx - 60, jcy - 50, C_WHITE);
            drawBangCard(ctx, jcx - CARD_W / 2, jcy - 30, jailDrawnCard);
        }
        if ("DYNAMITE_CHECK".equals(phase) && !dynamiteDrawnCard.isEmpty()) {
            int jcx = width / 2, jcy = height / 2;
            ctx.drawTextWithShadow(textRenderer, BangLang.tr("Dynamite check - Card drawn:", "Kiểm tra Dynamite - Lá rút:"), jcx - 70, jcy - 50, C_WHITE);
            drawBangCard(ctx, jcx - CARD_W / 2, jcy - 30, dynamiteDrawnCard);
        }

        renderRoleRevealOverlay(ctx);
        renderCenterNotification(ctx);
        renderHeroPanel(ctx, mouseX, mouseY);
        renderLogPanel(ctx, mouseX, mouseY);
        updateButtons();
        super.render(ctx, mouseX, mouseY, delta);
    }

    private void renderCenterNotification(DrawContext ctx) {
        if (lastNotification.isEmpty()) return;
        long elapsed = System.currentTimeMillis() - lastNotificationTime;
        if (elapsed > NOTIFICATION_DURATION_MS) return;
        String display = BangLang.translateLog(lastNotification);
        if (display.length() > 40) display = display.substring(0, 37) + "...";
        int tw = textRenderer.getWidth(display);
        int cx = width / 2;
        int cy = height / 2 - 80;
        ctx.fill(cx - tw / 2 - 8, cy - 4, cx + tw / 2 + 8, cy + 12, 0xCC000000);
        ctx.drawCenteredTextWithShadow(textRenderer, display, cx, cy, C_WHITE);
    }

    private String getRoleDescription(String role) {
        return switch (role.toLowerCase()) {
            case "sheriff" -> BangLang.tr("Sheriff: +1 HP, eliminate all Outlaws and Renegade.", "Sheriff: +1 HP, tiêu diệt Outlaw và Renegade.");
            case "deputy" -> BangLang.tr("Deputy: Find and protect the Sheriff.", "Deputy: Tìm và bảo vệ Sheriff.");
            case "outlaw" -> BangLang.tr("Outlaw: Kill the Sheriff and find allies.", "Outlaw: Giết Sheriff và tìm đồng minh.");
            case "renegade" -> BangLang.tr("Renegade (Third faction): Kill everyone.", "Renegade (Phe thứ ba): Giết tất cả.");
            default -> role;
        };
    }

    private void renderRoleRevealOverlay(DrawContext ctx) {
        PlayerInfo hero = players.stream().filter(p -> p.name.equals(myName)).findFirst().orElse(null);
        if (hero == null || hero.role.isEmpty() || roleRevealStartTime == 0) return;
        long elapsed = System.currentTimeMillis() - roleRevealStartTime;
        if (elapsed > ROLE_REVEAL_DURATION_MS + ROLE_ANIM_DURATION_MS) return;

        float deckX = width / 2f - CARD_W / 2f;
        float deckY = height / 2f - CARD_H / 2f - 40;
        int heroHx = 12, heroHy = height - PANEL_H - 10;
        int slotX = heroHx + 3;
        int slotY = heroHy + 18 + PANEL_SLOT_H + 1;

        if (elapsed < ROLE_REVEAL_DURATION_MS) {
            ctx.fill(0, 0, width, height, 0x88000000);
            drawRoleCard(ctx, (int) deckX, (int) deckY, hero.role);
            String desc = getRoleDescription(hero.role);
            int descW = textRenderer.getWidth(desc);
            ctx.drawTextWithShadow(textRenderer, desc, width / 2 - descW / 2, (int) deckY + CARD_H + 8, C_WHITE);
        } else {
            float t = (elapsed - ROLE_REVEAL_DURATION_MS) / (float) ROLE_ANIM_DURATION_MS;
            t = Math.min(1f, CardAnimationHelper.easeOutQuad(t));
            int x = (int) (deckX + (slotX - deckX) * t);
            int y = (int) (deckY + (slotY - deckY) * t);
            ctx.fill(0, 0, width, height, (int) (0x88 * (1 - t)) << 24);
            drawRoleCard(ctx, x, y, hero.role);
            if (t < 0.5f) {
                String desc = getRoleDescription(hero.role);
                int descW = textRenderer.getWidth(desc);
                ctx.drawTextWithShadow(textRenderer, desc, width / 2 - descW / 2, (int) deckY + CARD_H + 8, C_WHITE);
            }
        }
    }

    /** Deck center position: 22x32, chính giữa GUI. Returns [x, y]. */
    private int[] getDeckCenter() {
        int slotGap = 2;
        int deckX = width / 2 - CARD_W - slotGap / 2;
        int deckY = height / 2 - CARD_H / 2;
        return new int[]{deckX, deckY};
    }

    /** Deck center: 22x32 hitbox. Next to it: blue dynamite slot (22x32) if current player has it. */
    private void renderDeckCenter(DrawContext ctx, int mouseX, int mouseY) {
        int[] dc = getDeckCenter();
        int deckX = dc[0], deckY = dc[1];
        int slotGap = 2;
        ctx.fill(deckX, deckY, deckX + CARD_W, deckY + CARD_H, 0xCC000000);
        ctx.drawBorder(deckX, deckY, CARD_W, CARD_H, 0xFF8B7355);
        String countStr = String.valueOf(deckCount);
        int tw = textRenderer.getWidth(countStr);
        ctx.drawTextWithShadow(textRenderer, countStr, deckX + (CARD_W - tw) / 2, deckY + (CARD_H - 8) / 2, C_WHITE);

        int dynX = width / 2 + slotGap / 2;
        ctx.fill(dynX, deckY, dynX + CARD_W, deckY + CARD_H, SLOT_BLUE);
        ctx.drawBorder(dynX, deckY, CARD_W, CARD_H, 0xFF555555);
        String dynamiteCode = getSharedDynamiteCard();
        if (dynamiteCode != null) {
            drawBangCardScaled(ctx, dynX, deckY, dynamiteCode, CARD_W, CARD_H);
        }

        if (mouseX >= deckX && mouseX <= deckX + CARD_W && mouseY >= deckY && mouseY <= deckY + CARD_H) {
            ctx.drawTooltip(textRenderer, List.of(Text.literal(BangLang.tr("Deck", "Bộ bài"))), mouseX, mouseY);
        } else if (mouseX >= dynX && mouseX <= dynX + CARD_W && mouseY >= deckY && mouseY <= deckY + CARD_H) {
            String ownerName = dynamitePlayerIndex >= 0 && dynamitePlayerIndex < players.size()
                    ? players.get(dynamitePlayerIndex).name : "";
            String tip = dynamiteCode != null
                    ? BangLang.tr("Dynamite", "Thuốc nổ") + (ownerName.isEmpty() ? "" : " (" + ownerName + ")")
                    : BangLang.tr("Dynamite slot", "Ô thuốc nổ");
            ctx.drawTooltip(textRenderer, List.of(Text.literal(tip)), mouseX, mouseY);
        }
    }

    private String getSharedDynamiteCard() {
        return dynamiteCard.isEmpty() ? null : dynamiteCard;
    }

    private void renderLogPanel(DrawContext ctx, int mouseX, int mouseY) {
        int pad = 8, btnH = 14, gap = 1;
        int logH = LOG_LINES * 10 + 4;
        int totalPlayBtnW = 26 + 1 + 48 + 1 + 40;
        int btnY = height - pad - btnH;
        int logY = btnY - gap - logH;
        int logX = width - LOG_W - pad;
        ctx.fill(logX, logY, logX + LOG_W, logY + logH, 0xDD000000);
        ctx.drawBorder(logX, logY, LOG_W, logH, 0xFF555555);
        int lineH = 10;
        int startIdx = Math.max(0, logEntries.size() - LOG_LINES);
        int maxChars = 18;
        for (int i = 0; i < LOG_LINES && startIdx + i < logEntries.size(); i++) {
            String line = BangLang.translateLog(logEntries.get(startIdx + i));
            if (line.length() > maxChars) line = line.substring(0, maxChars - 2) + "..";
            ctx.drawTextWithShadow(textRenderer, line, logX + 4, logY + 2 + i * lineH, C_GRAY);
        }
    }

    /** Rectangular hitbox enclosing: name, HP, role, equipment. Hero at bottom-left. */
    private void renderPlayerPanel(DrawContext ctx, PlayerInfo p, PlayerPosition pos, boolean isSheriff, boolean isCurrent, boolean isHero, boolean isJailed) {
        int hx = isHero ? 12 : pos.hitboxX();
        int hy = isHero ? height - PANEL_H - 10 : pos.hitboxY();

        ctx.drawBorder(hx, hy, PANEL_W, PANEL_H, isCurrent ? C_ORANGE : 0xFF555555);
        ctx.fill(hx + 1, hy + 1, hx + PANEL_W - 1, hy + PANEL_H - 1, 0x44000000);

        int color = !p.isAlive ? C_GRAY : isCurrent ? C_ORANGE : C_WHITE;
        String name = p.name.length() > 10 ? p.name.substring(0, 8) + ".." : p.name;
        int nameW = textRenderer.getWidth(name);
        int rowY = hy + 3;
        int colGap = 3;
        int curX = hx + 3;

        ctx.drawTextWithShadow(textRenderer, name, curX, rowY, color);
        curX += nameW + colGap;

        int segW = 4, segH = 5, segGap = 1;
        int barY = rowY - 1;
        for (int i = 0; i < 5; i++) {
            int sx = curX + i * (segW + segGap);
            int segColor = i < p.hp ? (p.hp > 2 ? C_GREEN : C_RED) : 0xFF333333;
            ctx.fill(sx, barY, sx + segW, barY + segH, segColor);
        }
        curX += 5 * (segW + segGap) - segGap + colGap;

        if (!isHero && distancesFromHero.containsKey(p.name)) {
            String gapStr = BangLang.tr("gap:", "khoảng:") + distancesFromHero.get(p.name);
            MatrixStack ms = ctx.getMatrices();
            ms.push();
            ms.translate(curX, rowY, 0);
            ms.scale(0.75f, 0.75f, 1f);
            ctx.drawTextWithShadow(textRenderer, gapStr, 0, 0, 0xFFE8C547);
            ms.pop();
            curX += (int)(textRenderer.getWidth(gapStr) * 0.75f) + colGap;
        }

        if (isJailed) {
            String jailStr = BangLang.tr("Jail", "Tù");
            ctx.drawTextWithShadow(textRenderer, jailStr, curX, rowY, 0xFFAAAA00);
        }

        int innerPad = 3;
        int slotGap = 1;
        int sw = PANEL_SLOT_W, sh = PANEL_SLOT_H;
        int rowGreenY = hy + 18;
        int rowBlueY = hy + 18 + sh + slotGap;

        for (int s = 0; s < 6; s++) {
            int sx = hx + innerPad + s * (sw + slotGap);
            ctx.fill(sx, rowGreenY, sx + sw, rowGreenY + sh, SLOT_GREEN);
            ctx.drawBorder(sx, rowGreenY, sw, sh, isCurrent ? C_ORANGE : 0xFF555555);
        }

        int totalSlots = 6; // role + 4 equipment + jail
        for (int s = 0; s < totalSlots; s++) {
            int sx = hx + innerPad + s * (sw + slotGap);
            int slotColor = (s == 0) ? SLOT_RED : (s <= MAX_EQUIP_SLOTS) ? SLOT_BLUE : SLOT_YELLOW;
            ctx.fill(sx, rowBlueY, sx + sw, rowBlueY + sh, slotColor);
            ctx.drawBorder(sx, rowBlueY, sw, sh, isCurrent ? C_ORANGE : 0xFF555555);

            if (s == 0) {
                boolean inRoleReveal = isHero && roleRevealStartTime > 0
                        && (System.currentTimeMillis() - roleRevealStartTime) < ROLE_REVEAL_DURATION_MS + ROLE_ANIM_DURATION_MS;
                if (!p.role.isEmpty() && (isSheriff || isHero) && !inRoleReveal) {
                    drawRoleCardScaled(ctx, sx, rowBlueY, p.role, sw, sh);
                } else if (!p.role.isEmpty() && !inRoleReveal) {
                    ctx.drawCenteredTextWithShadow(textRenderer, "?", sx + sw/2, rowBlueY + sh/2 - 4, C_GRAY);
                }
            } else if (s <= MAX_EQUIP_SLOTS && s - 1 < p.equipment.size()) {
                drawBangCardScaled(ctx, sx, rowBlueY, p.equipment.get(s - 1), sw, sh);
            }
        }
    }

    private void drawRoleCardScaled(DrawContext ctx, int x, int y, String role, int w, int h) {
        Identifier tex = Identifier.of("bang", "textures/roles/split/role_" + role.toLowerCase() + ".png");
        ctx.drawTexture(tex, x, y, 0, 0, w, h, CARD_W, CARD_H);
    }

    private void drawBangCardScaled(DrawContext ctx, int x, int y, String code, int w, int h) {
        BangCard card = BangCard.fromCode(code);
        if (card == null) {
            ctx.drawTexture(TEX_CARD_BACK, x, y, 0, 0, w, h, CARD_W, CARD_H);
            return;
        }
        String path = BANG_TEX_SPLIT + card.typeId() + "_" + card.rankSuit().toLowerCase() + ".png";
        ctx.drawTexture(Identifier.of("bang", path), x, y, 0, 0, w, h, CARD_W, CARD_H);
    }

    /** Hero hand: center, max 14 cards, 2 rows, packed. */
    private void renderHeroPanel(DrawContext ctx, int mouseX, int mouseY) {
        PlayerInfo hero = players.stream().filter(p -> p.name.equals(myName)).findFirst().orElse(null);
        if (hero == null) return;

        int[] area = getHandArea();
        int handX = area[0], handY = area[1];

        for (int i = 0; i < hero.hand.size(); i++) {
            final int cardIdx = i;
            int[] pos = cardIndexToPosition(i, handX, handY);
            int toX = pos[0];
            int toY = pos[1];
            DealAnim deal = dealAnims.stream().filter(d -> d.key().equals("hero-" + cardIdx)).findFirst().orElse(null);
            if (deal != null) {
                float prog = CardAnimationHelper.getDealProgress(deal.startTime());
                if (prog >= 1f) {
                    dealAnims.removeIf(d -> d.key().equals("hero-" + cardIdx));
                } else {
                    float t = CardAnimationHelper.easeOutQuad(prog);
                    float drawX = CardAnimationHelper.lerpEased(deal.fromX(), toX, t, true);
                    float drawY = CardAnimationHelper.lerpEased(deal.fromY(), toY, t, true);
                    drawCardBackAt(ctx, drawX, drawY);
                    continue;
                }
            }

            int x = toX;
            int cardY = toY;
            if (selectedCardIndices.contains(i)) {
                int pad = 2;
                ctx.fill(x - pad, cardY - pad, x + CARD_W + pad, cardY + CARD_H + pad, 0x80FF8800);
                ctx.drawBorder(x - pad, cardY - pad, CARD_W + pad * 2, CARD_H + pad * 2, C_ORANGE);
            }
            String code = hero.hand.get(i);
            if ("??".equals(code)) drawCardBack(ctx, x, cardY);
            else drawBangCard(ctx, x, cardY, code);
        }

        if (chooseTarget.equals(myName)) {
            ctx.drawTextWithShadow(textRenderer, chooseIsPanic ? BangLang.tr("Choose card to give", "Chọn lá để đưa") : BangLang.tr("Choose card to discard", "Chọn lá để loại"), handX, handY - 14, C_ORANGE);
        } else if (!selectedCardIndices.isEmpty()) {
            int selIdx = selectedCardIndices.iterator().next();
            if (selIdx < hero.hand.size()) {
                BangCard card = BangCard.fromCode(hero.hand.get(selIdx));
                if (card != null && needsTarget(card.typeId()))
                    ctx.drawTextWithShadow(textRenderer, BangLang.tr("Click player to target", "Nhấn người chơi để nhắm"), handX, handY - 14, C_ORANGE);
            }
        }

        renderCardTooltip(ctx, mouseX, mouseY, hero);
        renderAllPlayerPanelTooltips(ctx, mouseX, mouseY);
    }

    private void renderAllPlayerPanelTooltips(DrawContext ctx, int mouseX, int mouseY) {
        int n = Math.min(players.size(), Math.min(positionCache.size(), displayOrder.size()));
        for (int i = 0; i < n; i++) {
            int playerIdx = displayOrder.get(i);
            PlayerInfo p = players.get(playerIdx);
            boolean isHero = p.name.equals(myName);
            int hx = isHero ? 12 : positionCache.get(i).hitboxX();
            int hy = isHero ? height - PANEL_H - 10 : positionCache.get(i).hitboxY();
            int rowBlueY = hy + 18 + PANEL_SLOT_H + 1;
            int rowGreenY = hy + 18;
            for (int s = 0; s < 6; s++) {
                int sx = hx + 3 + s * (PANEL_SLOT_W + 1);
                if (mouseX >= sx && mouseX <= sx + PANEL_SLOT_W && mouseY >= rowBlueY && mouseY <= rowBlueY + PANEL_SLOT_H) {
                    if (s == 0 && !p.role.isEmpty()) {
                        ctx.drawTooltip(textRenderer, List.of(Text.literal(getRoleDescription(p.role))), mouseX, mouseY);
                        return;
                    }
                    if (s > 0 && s <= MAX_EQUIP_SLOTS && s - 1 < p.equipment.size()) {
                        BangCard card = BangCard.fromCode(p.equipment.get(s - 1));
                        if (card != null)
                            ctx.drawTooltip(textRenderer, getCardTooltip(card), mouseX, mouseY);
                        return;
                    }
                }
            }
            for (int s = 0; s < 6; s++) {
                int sx = hx + 3 + s * (PANEL_SLOT_W + 1);
                if (mouseX >= sx && mouseX <= sx + PANEL_SLOT_W && mouseY >= rowGreenY && mouseY <= rowGreenY + PANEL_SLOT_H) {
                    ctx.drawTooltip(textRenderer, List.of(Text.literal(BangLang.tr("Character slot", "Ô nhân vật"))), mouseX, mouseY);
                    return;
                }
            }
        }
    }

    private void renderCardTooltip(DrawContext ctx, int mouseX, int mouseY, PlayerInfo hero) {
        int[] area = getHandArea();
        int handX = area[0], handY = area[1];
        for (int i = 0; i < hero.hand.size(); i++) {
            int[] pos = cardIndexToPosition(i, handX, handY);
            int x = pos[0];
            int cardY = pos[1];
            if (mouseX >= x && mouseX <= x + CARD_W && mouseY >= cardY && mouseY <= cardY + CARD_H) {
                String code = hero.hand.get(i);
                if ("??".equals(code)) {
                    ctx.drawTooltip(textRenderer, List.of(Text.literal("???")), mouseX, mouseY);
                    return;
                }
                BangCard card = BangCard.fromCode(code);
                if (card != null) {
                    List<Text> lines = getCardTooltip(card);
                    ctx.drawTooltip(textRenderer, lines, mouseX, mouseY);
                }
                return;
            }
        }
    }

    private List<Text> getCardTooltip(BangCard card) {
        String desc = getCardDescription(card.typeId());
        return List.of(
                Text.literal(card.typeId().replace("_", " ").toUpperCase()),
                Text.literal(desc)
        );
    }

    private String getCardDescription(String typeId) {
        return switch (typeId) {
            case "bang" -> BangLang.tr("Shoot 1 player in range. Target uses Miss or loses 1 HP. 1 Bang/turn (except Volcanic).", "Bắn 1 người trong tầm. Mục tiêu dùng Miss hoặc mất 1 HP. 1 Bang/lượt (trừ Volcanic).");
            case "missed" -> BangLang.tr("When shot: use to dodge 1 bullet.", "Khi bị bắn: dùng để né 1 viên đạn.");
            case "beer" -> BangLang.tr("+1 HP. Can replace Missed when taking lethal. Not when 2 players left or full HP.", "+1 HP. Có thể thay Missed khi chống sát thương chết. Không khi còn 2 người hoặc full HP.");
            case "panic" -> BangLang.tr("Steal 1 card from hand in range 1 (gap affected). Click target hitbox.", "Cướp 1 lá từ hand trong tầm 1 (chịu gap). Bấm hitbox mục tiêu.");
            case "cat_balou" -> BangLang.tr("Discard 1 from hand or equipment (no gap). Click hitbox or specific card.", "Hủy 1 từ hand hoặc trang bị (không gap). Bấm hitbox hoặc lá cụ thể.");
            case "stagecoach" -> BangLang.tr("Draw 2 cards from deck.", "Rút 2 lá từ bộ bài.");
            case "wells_fargo" -> BangLang.tr("Draw 3 cards from deck.", "Rút 3 lá từ bộ bài.");
            case "barrel" -> BangLang.tr("When shot: draw 1. Hearts = nullify Bang. Else may use Miss.", "Khi bị bắn: rút 1. Cơ = vô hiệu Bang. Không thì dùng Miss.");
            case "mustang" -> BangLang.tr("Others see you at +1 distance.", "Người khác nhìn bạn +1 khoảng cách.");
            case "appaloosa" -> BangLang.tr("You see others at -1 distance.", "Bạn nhìn người khác -1 khoảng cách.");
            case "volcanic" -> BangLang.tr("Equipment: Gun range 1, unlimited Bang/turn.", "Trang bị: Súng tầm 1, Bang không giới hạn/lượt.");
            case "schofield" -> BangLang.tr("Equipment: Gun, range 2.", "Trang bị: Súng, tầm 2.");
            case "remington" -> BangLang.tr("Equipment: Gun, range 3.", "Trang bị: Súng, tầm 3.");
            case "rev_carbine" -> BangLang.tr("Equipment: Gun, range 4.", "Trang bị: Súng, tầm 4.");
            case "winchester" -> BangLang.tr("Equipment: Gun, range 5.", "Trang bị: Súng, tầm 5.");
            case "jail" -> BangLang.tr("Jail on target. Turn start: draw 1. Hearts = escape + draw more. Else skip turn.", "Tù lên mục tiêu. Đầu lượt: rút 1. Cơ = thoát + rút thêm. Không = mất lượt.");
            case "dynamite" -> BangLang.tr("Equipment by deck. Turn start: draw 1. Spades 2-9 = 3 damage, lose Dynamite.", "Trang bị ở ô cạnh bài. Đầu lượt: rút 1. Bích 2-9 = 3 sát thương, mất Dynamite.");
            case "scope" -> BangLang.tr("Equipment: +1 range.", "Trang bị: +1 tầm.");
            case "gatling" -> BangLang.tr("Shoot all others. Each may Miss or Barrel. Cannot use Bang same turn.", "Bắn tất cả. Mỗi người Miss hoặc Barrel. Không dùng Bang cùng lượt.");
            case "indians" -> BangLang.tr("All must play Bang (not Missed). No Bang = lose 1 HP. No Barrel.", "Tất cả phải đánh Bang (không Missed). Không Bang = mất 1 HP. Không Barrel.");
            case "duel" -> BangLang.tr("Choose target. Duel Bang: target plays first. Loser loses 1 HP.", "Chọn mục tiêu. Đấu Bang: mục tiêu đánh trước. Thua mất 1 HP.");
            case "saloon" -> BangLang.tr("All alive +1 HP. Can replace Missed when taking lethal.", "Tất cả còn sống +1 HP. Có thể thay Missed khi chống sát thương chết.");
            case "general_store" -> BangLang.tr("Reveal cards = player count. Each picks 1, starting from player who played.", "Lật lá = số người. Mỗi người chọn 1, bắt đầu từ người đánh.");
            default -> typeId.replace("_", " ");
        };
    }

    /** Role card */
    private void drawRoleCard(DrawContext ctx, int x, int y, String role) {
        String roleName = role.toUpperCase();
        Identifier tex = Identifier.of("bang", "textures/roles/split/role_" + roleName.toLowerCase() + ".png");
        ctx.drawTexture(tex, x, y, 0, 0, CARD_W, CARD_H, CARD_W, CARD_H);
    }

    /** Draw card: chỉ dùng texture tách split/typeId_rankSuit.png */
    private void drawBangCard(DrawContext ctx, int x, int y, String code) {
        BangCard card = BangCard.fromCode(code);
        if (card == null) { drawCardBack(ctx, x, y); return; }
        String splitPath = BANG_TEX_SPLIT + card.typeId() + "_" + card.rankSuit().toLowerCase() + ".png";
        ctx.drawTexture(Identifier.of("bang", splitPath), x, y, 0, 0, CARD_W, CARD_H, CARD_W, CARD_H);
    }

    private void drawBangCardAt(DrawContext ctx, float x, float y, String code) {
        BangCard card = BangCard.fromCode(code);
        if (card == null) { drawCardBackAt(ctx, x, y); return; }
        String splitPath = BANG_TEX_SPLIT + card.typeId() + "_" + card.rankSuit().toLowerCase() + ".png";
        ctx.drawTexture(Identifier.of("bang", splitPath), (int) x, (int) y, 0, 0, CARD_W, CARD_H, CARD_W, CARD_H);
    }

    private void drawCardBack(DrawContext ctx, int x, int y) {
        ctx.drawTexture(TEX_CARD_BACK, x, y, 0, 0, CARD_W, CARD_H, CARD_W, CARD_H);
    }

    private void drawCardBackAt(DrawContext ctx, float x, float y) {
        ctx.drawTexture(TEX_CARD_BACK, (int) x, (int) y, 0, 0, CARD_W, CARD_H, CARD_W, CARD_H);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        PlayerInfo hero = players.stream().filter(p -> p.name.equals(myName)).findFirst().orElse(null);
        boolean isMyTurn = myName.equals(currentPlayerName) && "PLAYING".equals(phase);
        boolean isReacting = ("REACTING".equals(phase) || "GATLING_REACT".equals(phase)) && myName.equals(reactingTarget);
        boolean isChoosing = "CHOOSE_CARD".equals(phase) && myName.equals(chooseTarget);

        if (!selectedCardIndices.isEmpty() && hero != null && (isMyTurn || isReacting)) {
            int selIdx = selectedCardIndices.iterator().next();
            if (isMyTurn && selIdx < hero.hand.size()) {
                BangCard card = BangCard.fromCode(hero.hand.get(selIdx));
                if (card != null && needsTarget(card.typeId())) {
                    int n = Math.min(players.size(), positionCache.size());
                    for (int i = 0; i < n; i++) {
                        PlayerPosition pos = positionCache.get(i);
                        int hx = pos.hitboxX(), hy = pos.hitboxY(), hw = pos.hitboxW(), hh = pos.hitboxH();
                        if (mouseX >= hx && mouseX <= hx + hw && mouseY >= hy && mouseY <= hy + hh) {
                            int targetIdx = displayOrder.get(i);
                            PlayerInfo target = players.get(targetIdx);
                            if (!target.name.equals(myName) && target.isAlive) {
                                sendAction("PLAY_CARD", selIdx, target.name);
                                selectedCardIndices.clear();
                                return true;
                            }
                        }
                    }
                }
            }
        }

        int[] area = getHandArea();
        int handX = area[0], handY = area[1];
        boolean isDuelMyTurn = "DUEL_PLAY".equals(phase) && myName.equals(duelCurrentPlayer);
        boolean canInteract = hero != null && (isMyTurn || isReacting || isChoosing || isDuelMyTurn);
        if (hero != null && !hero.hand.isEmpty() && canInteract) {
            for (int i = 0; i < hero.hand.size(); i++) {
                int[] pos = cardIndexToPosition(i, handX, handY);
                int x = pos[0];
                int cardY = pos[1];
                if (mouseX >= x && mouseX <= x + CARD_W && mouseY >= cardY && mouseY <= cardY + CARD_H) {
                    if (isChoosing) {
                        selectedCardIndices.clear();
                        selectedCardIndices.add(i);
                        selectedEquipIndex = -1;
                    } else {
                        if (selectedCardIndices.contains(i)) selectedCardIndices.remove(i);
                        else selectedCardIndices.add(i);
                    }
                    return true;
                }
            }
        }
        if (isChoosing && hero != null && !chooseIsPanic) {
            int heroHx = 12, heroHy = height - PANEL_H - 10;
            int rowBlueY = heroHy + 18 + PANEL_SLOT_H + 1;
            for (int s = 1; s <= MAX_EQUIP_SLOTS; s++) {
                int eqIdx = s - 1;
                if (eqIdx >= hero.equipment.size()) break;
                int sx = heroHx + 3 + s * (PANEL_SLOT_W + 1);
                if (mouseX >= sx && mouseX <= sx + PANEL_SLOT_W && mouseY >= rowBlueY && mouseY <= rowBlueY + PANEL_SLOT_H) {
                    selectedEquipIndex = eqIdx;
                    selectedCardIndices.clear();
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean needsTarget(String typeId) {
        return BangCard.BANG.equals(typeId) || BangCard.PANIC.equals(typeId) || BangCard.CAT_BALOU.equals(typeId)
                || BangCard.DUEL.equals(typeId) || BangCard.JAIL.equals(typeId);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { selectedCardIndices.clear(); close(); return true; } // ESC: đóng màn hình, không leave
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
