package com.pokermc.bang.game;

import java.util.*;

/**
 * Bang! game logic. Circular distance, player stats, card effects.
 */
public class BangGame {

    public enum Phase { WAITING, ROLE_REVEAL, ROLE_TO_CHARACTER_DELAY, CHARACTER_SELECT, CHARACTER_TO_DEAL_DELAY, DEALING, DEAL_PAUSE, DEAL_FIRST, PLAYING, DISCARD, KIT_DISCARD, DRAW_CHOICE, NEXT_TURN_DELAY, REACTING, BARREL_REACT, JAIL_CHECK, JAIL_SKIP_DELAY, DYNAMITE_CHECK, CHOOSE_CARD, GATLING_REACT, INDIANS_REACT, DUEL_PLAY, GAME_OVER }
    public enum Role { SHERIFF, DEPUTY, OUTLAW, RENEGADE }

    public static class PlayerState {
        public final String name;
        public int seatIndex;
        public Role role;
        public int characterId; // 1-16, 0 = not chosen
        public int maxHp;
        public int hp;
        public int useBang; // 0 = unlimited
        public int dieToEnd; // 1 = sheriff dies -> game over
        public int gapShoot;
        public int gapUse;
        public int gap; // distance others see to me (base 1, +Mustang)
        public boolean jail; // can be jailed
        public boolean jailing;
        public boolean barreling;
        public boolean canHeal;
        public final List<BangCard> hand = new ArrayList<>();
        public final List<BangCard> equipment = new ArrayList<>(); // guns, horses, barrel, etc.
        public boolean isAlive = true;

        public PlayerState(String name, int seatIndex) {
            this.name = name;
            this.seatIndex = seatIndex;
        }

        public void applyRole(Role r) {
            role = r;
            maxHp = 5;
            hp = (r == Role.SHERIFF) ? 5 : 4;
            if (r == Role.SHERIFF) {
                dieToEnd = 1;
                jail = false;
            } else {
                dieToEnd = 0;
                jail = true;
            }
            useBang = 1;
            gapShoot = 1;
            gapUse = 1;
            gap = 1;
            jailing = false;
            barreling = false;
            canHeal = true;
        }

        /** Apply character HP (call after character chosen). Role base + character override. */
        public void applyCharacterHp() {
            var c = BangCharacter.get(characterId);
            if (c != null) {
                maxHp = c.hp();
                hp = Math.min(maxHp, hp);
            } else {
                maxHp = 5;
                hp = (role == Role.SHERIFF) ? 5 : 4;
            }
        }
    }

    private final List<PlayerState> players = new ArrayList<>();
    private final List<String> pendingPlayers = new ArrayList<>();
    private final List<BangCard> drawPile = new ArrayList<>();
    private final List<BangCard> discardPile = new ArrayList<>();
    private Phase phase = Phase.WAITING;
    private int currentPlayerIndex = -1;
    private int sheriffIndex = -1;
    private int maxPlayers = 2;
    private String statusMessage = "Waiting for players...";
    private int pendingBangTarget = -1;
    private int pendingBangSource = -1;
    private boolean pendingBarrelFailed = false; // Barrel was tried, not Hearts; must UseMiss or TakeHit
    private BangCard pendingJailDrawnCard; // card drawn for jail check (shown to all)
    private int jailPlayerIndex = -1; // who has jail (badge only, not in equipment)
    private BangCard pendingDynamiteDrawnCard; // card drawn for dynamite check (shown to all)
    private int pendingChooseTarget = -1;
    private int pendingChooseSource = -1;
    private boolean pendingChooseIsPanic; // true=steal, false=discard
    private int gatlingCurrentTarget = -1; // index trong players, -1 = chưa bắt đầu
    private int indiansCurrentTarget = -1;
    private BangCard dynamiteCard = null; // Shared slot by deck; -1 = none
    private int dynamitePlayerIndex = -1; // Who has dynamite (index in players)
    private int indiansSource = -1;
    private int duelAttacker = -1, duelDefender = -1; // duel: 2 người đang đấu
    private boolean duelAttackerTurn = true; // true = attacker đánh Bang, false = defender
    private boolean usedGatlingThisTurn = false; // Gatling and Bang mutually exclusive per turn
    private int dealCardIndex = 0;
    private int dealFirstCount = 0;
    private int dealPauseTicks = 0;
    public static final int DEAL_TICKS_PER_CARD = 3;
    public static final int DEAL_PAUSE_TICKS = 40;

    // Character selection: sheriff first, then clockwise. 3 options, pick 1, 30s, 2s delay between players.
    private int characterSelectPlayerIndex = -1;
    private int[] characterSelectOptions = new int[0]; // 3 character IDs
    private int characterSelectTicks = 0;
    private int characterSelectDelayTicks = 0;
    public static final int CHARACTER_SELECT_TIMEOUT_TICKS = 600; // 30 seconds
    public static final int CHARACTER_SELECT_DELAY_TICKS = 40; // 2 seconds between players

    // ── Distance (circular): min steps going clockwise or counter-clockwise ─────────────────
    /** Distance from player at fromIdx to player at toIdx (1-based). */
    public int getDistance(int fromIdx, int toIdx) {
        if (players.isEmpty()) return 0;
        int n = players.size();
        int clockwise = (toIdx - fromIdx + n) % n;
        int counter = (fromIdx - toIdx + n) % n;
        return Math.min(clockwise, counter);
    }

    /** Distance from fromIdx to toIdx. Mustang/Paul Regret on target: +1. Appaloosa/Rose Doolan on from: -1. */
    public int getDistanceTo(int fromIdx, int toIdx) {
        int base = getDistance(fromIdx, toIdx);
        if (base <= 0) return base;
        PlayerState target = players.get(toIdx);
        PlayerState from = players.get(fromIdx);
        for (BangCard c : target.equipment) {
            if (BangCard.MUSTANG.equals(c.typeId())) base++;
        }
        if (target.characterId == 1) base++; // Paul Regret: others see +1
        for (BangCard c : from.equipment) {
            if (BangCard.APPALOOSA.equals(c.typeId())) { base = Math.max(0, base - 1); break; }
        }
        if (from.characterId == 9) base = Math.max(0, base - 1); // Rose Doolan: see others -1
        return base;
    }

    /** Distance from playerIdx to targetIdx for shooting (uses gapShoot). */
    public boolean canShoot(int fromIdx, int toIdx) {
        if (fromIdx == toIdx) return false;
        PlayerState from = players.get(fromIdx);
        return getDistanceTo(fromIdx, toIdx) <= from.gapShoot;
    }

    /** Distance for Panic!/Cat Balou: Panic uses gapUse (1), Cat Balou infinite. */
    public boolean canUseOn(int fromIdx, int toIdx, boolean isCatBalou) {
        if (isCatBalou) return true;
        return getDistanceTo(fromIdx, toIdx) <= players.get(fromIdx).gapUse;
    }

    // ── Player management ────────────────────────────────────────────────────────────────

    public boolean addPlayer(String name) {
        if (players.stream().anyMatch(p -> p.name.equals(name))) return false;
        if (pendingPlayers.contains(name)) return false;
        if (phase != Phase.WAITING) {
            pendingPlayers.add(name);
            return true;
        }
        if (players.size() >= maxPlayers) return false;
        players.add(new PlayerState(name, players.size()));
        statusMessage = name + " joined.";
        return true;
    }

    /** Leave = mark as dead (không xóa khỏi danh sách). */
    public boolean playerLeave(String name) {
        pendingPlayers.remove(name);
        int idx = indexOf(name);
        if (idx < 0) return false;
        PlayerState p = players.get(idx);
        p.isAlive = false;
        p.hp = 0;
        statusMessage = name + " left (dead).";
        checkGameOver();
        return true;
    }

    /** Remove player entirely (for reset). */
    public boolean removePlayer(String name) {
        pendingPlayers.remove(name);
        int idx = indexOf(name);
        if (idx < 0) return false;
        players.remove(idx);
        if (idx <= currentPlayerIndex) currentPlayerIndex = Math.max(-1, currentPlayerIndex - 1);
        if (sheriffIndex == idx) sheriffIndex = -1;
        else if (sheriffIndex > idx) sheriffIndex--;
        return true;
    }

    /** Reset game to WAITING, clear all. */
    public void resetGame() {
        phase = Phase.WAITING;
        players.clear();
        pendingPlayers.clear();
        drawPile.clear();
        discardPile.clear();
        currentPlayerIndex = -1;
        sheriffIndex = -1;
        dynamiteCard = null;
        dynamitePlayerIndex = -1;
        jailPlayerIndex = -1;
        gameOverWinner = "";
        statusMessage = "Waiting for players...";
    }

    /** Reset when all left. */
    public boolean allPlayersLeft() {
        return players.stream().noneMatch(p -> p.isAlive);
    }

    public boolean hasPlayer(String name) {
        return players.stream().anyMatch(p -> p.name.equals(name)) || pendingPlayers.contains(name);
    }

    public int indexOf(String name) {
        for (int i = 0; i < players.size(); i++)
            if (players.get(i).name.equals(name)) return i;
        return -1;
    }

    public void setMaxPlayers(int n) { maxPlayers = Math.max(2, Math.min(7, n)); }
    public int getMaxPlayers() { return maxPlayers; }

    // ── Game start ───────────────────────────────────────────────────────────────────────

    public boolean startGame() {
        if (phase != Phase.WAITING || players.size() < maxPlayers) return false;
        if (players.size() > maxPlayers) return false;

        drawPile.clear();
        discardPile.clear();
        dynamiteCard = null;
        dynamitePlayerIndex = -1;
        jailPlayerIndex = -1;
        drawPile.addAll(BangCard.buildDeck());

        // 2p: Sheriff+Outlaw. 3p: Sheriff+Outlaw+Deputy. 4p: Sheriff+Deputy+2Outlaws. 5p+: +Renegade
        List<Role> roles = new ArrayList<>();
        int n = players.size();
        roles.add(Role.SHERIFF);
        if (n >= 3) roles.add(Role.DEPUTY);
        if (n >= 5) roles.add(Role.RENEGADE);
        int outlaws = n - 1 - (n >= 3 ? 1 : 0) - (n >= 5 ? 1 : 0);
        for (int i = 0; i < outlaws; i++) roles.add(Role.OUTLAW);
        Collections.shuffle(roles);

        for (int i = 0; i < players.size(); i++) {
            PlayerState p = players.get(i);
            p.hand.clear();
            p.equipment.clear();
            p.isAlive = true;
            p.characterId = 0;
            p.applyRole(roles.get(i));
            if (p.role == Role.SHERIFF) sheriffIndex = i;
        }
        jailPlayerIndex = -1;
        characterSelectPlayerIndex = -1;
        characterSelectOptions = new int[0];

        phase = Phase.ROLE_REVEAL;
        roleRevealTicks = 0;
        dealCardIndex = 0;
        currentPlayerIndex = -1;
        statusMessage = "Your role...";
        return true;
    }

    private int roleRevealTicks = 0;
    public static final int ROLE_REVEAL_TICKS = 20;

    /** Tick during ROLE_REVEAL. After 5s, go to ROLE_TO_CHARACTER_DELAY (2s). */
    public void tickRoleReveal() {
        if (phase != Phase.ROLE_REVEAL) return;
        roleRevealTicks++;
        if (roleRevealTicks >= ROLE_REVEAL_TICKS) {
            phase = Phase.ROLE_TO_CHARACTER_DELAY;
            characterSelectDelayTicks = 0;
            statusMessage = "Choosing characters...";
        }
    }

    /** Tick ROLE_TO_CHARACTER_DELAY: 2s then CHARACTER_SELECT (all at once). */
    public void tickRoleToCharacterDelay() {
        if (phase != Phase.ROLE_TO_CHARACTER_DELAY) return;
        characterSelectDelayTicks++;
        if (characterSelectDelayTicks >= CHARACTER_SELECT_DELAY_TICKS) {
            phase = Phase.CHARACTER_SELECT;
            startCharacterSelectAllAtOnce();
        }
    }

    /** Per-player options for simultaneous character select. */
    private final java.util.Map<String, int[]> characterSelectOptionsByPlayer = new java.util.HashMap<>();

    /** All players choose at once. 30s total. Each gets 3 unique options (no overlap). */
    private void startCharacterSelectAllAtOnce() {
        characterSelectOptionsByPlayer.clear();
        List<Integer> pool = new ArrayList<>();
        for (int i = BangCharacter.MIN_ID; i <= BangCharacter.MAX_ID; i++) pool.add(i);
        Collections.shuffle(pool);
        int idx = 0;
        for (PlayerState p : players) {
            int[] opts = new int[3];
            for (int i = 0; i < 3; i++) {
                opts[i] = idx < pool.size() ? pool.get(idx++) : (i + 1);
            }
            characterSelectOptionsByPlayer.put(p.name, opts);
        }
        characterSelectTicks = 0;
        statusMessage = "Everyone choose your character! (30s)";
    }

    /** Player selects character (all-at-once mode). Returns true if valid. */
    public boolean selectCharacter(String playerName, int characterId) {
        if (phase != Phase.CHARACTER_SELECT) return false;
        int idx = indexOf(playerName);
        if (idx < 0) return false;
        PlayerState p = players.get(idx);
        if (p.characterId > 0) return false; // already chose
        if (!BangCharacter.isValidId(characterId)) return false;
        int[] opts = characterSelectOptionsByPlayer.get(playerName);
        if (opts == null) return false;
        boolean valid = false;
        for (int opt : opts) {
            if (opt == characterId) { valid = true; break; }
        }
        if (!valid) return false;
        if (players.stream().anyMatch(pl -> pl.characterId == characterId)) return false; // already taken
        p.characterId = characterId;
        statusMessage = p.name + " chose " + BangCharacter.get(characterId).name() + "!";
        return true;
    }

    /** Tick CHARACTER_SELECT: 30s timeout or all chosen → apply HP, go to DEALING. */
    public void tickCharacterSelect() {
        if (phase != Phase.CHARACTER_SELECT) return;
        characterSelectTicks++;
        boolean allChosen = players.stream().allMatch(p -> p.characterId > 0);
        if (allChosen || characterSelectTicks >= CHARACTER_SELECT_TIMEOUT_TICKS) {
            for (PlayerState p : players) {
                if (p.characterId <= 0) {
                    int[] opts = characterSelectOptionsByPlayer.get(p.name);
                    int pick = (opts != null && opts.length > 0) ? opts[0] : 1;
                    for (int o : opts != null ? opts : new int[0]) {
                        if (players.stream().noneMatch(pl -> pl.characterId == o)) { pick = o; break; }
                    }
                    p.characterId = pick;
                    statusMessage = p.name + " auto-picked " + BangCharacter.get(p.characterId).name();
                }
            }
            for (PlayerState p : players) p.applyCharacterHp();
            phase = Phase.CHARACTER_TO_DEAL_DELAY;
            characterSelectOptionsByPlayer.clear();
            characterSelectDelayTicks = 0;
            statusMessage = "Dealing cards...";
        }
    }

    /** Get options for a specific player (for serialization). */
    public int[] getCharacterSelectOptionsFor(String playerName) {
        return characterSelectOptionsByPlayer.getOrDefault(playerName, new int[0]);
    }

    /** True if any player still needs to choose (for client to show overlay). */
    public boolean isCharacterSelectActive() {
        return phase == Phase.CHARACTER_SELECT && players.stream().anyMatch(p -> p.characterId <= 0);
    }

    public int getCharacterSelectTicks() { return characterSelectTicks; }

    /** Tick CHARACTER_TO_DEAL_DELAY: 2s then DEALING. */
    public void tickCharacterToDealDelay() {
        if (phase != Phase.CHARACTER_TO_DEAL_DELAY) return;
        characterSelectDelayTicks++;
        if (characterSelectDelayTicks >= CHARACTER_SELECT_DELAY_TICKS) {
            phase = Phase.DEALING;
            statusMessage = "Dealing cards...";
        }
    }


    /** Deal one card. Sheriff first, then counter-clockwise. Cards = hp each, max 5 per player. */
    public boolean dealOneCard() {
        if (phase != Phase.DEALING || players.isEmpty()) return false;
        int n = players.size();
        int totalCards = 0;
        for (PlayerState p : players) totalCards += Math.min(p.hp, 5); // max 5 cards each
        if (dealCardIndex >= totalCards) {
            finishDealing();
            return false;
        }
        int count = 0;
        int targetIdx = -1;
        for (int slot = 0; slot < n * 10 && count <= dealCardIndex; slot++) {
            int idx = (sheriffIndex - (slot % n) + n) % n;
            int maxCards = Math.min(players.get(idx).hp, MAX_HAND_SIZE);
            if (players.get(idx).hand.size() < maxCards) {
                if (count == dealCardIndex) {
                    targetIdx = idx;
                    break;
                }
                count++;
            }
        }
        if (targetIdx >= 0) {
            BangCard c = drawOne();
            if (c != null) players.get(targetIdx).hand.add(c);
        }
        dealCardIndex++;
        if (dealCardIndex >= totalCards) finishDealing();
        return true;
    }

    private void finishDealing() {
        phase = Phase.DEAL_PAUSE;
        dealPauseTicks = 0;
        dealFirstCount = 0;
        statusMessage = "Dealing to first player...";
    }

    /** Called each tick during DEAL_PAUSE. After pause, transitions to DEAL_FIRST. */
    public void tickDealPause() {
        if (phase != Phase.DEAL_PAUSE) return;
        dealPauseTicks++;
        if (dealPauseTicks >= DEAL_PAUSE_TICKS) {
            phase = Phase.DEAL_FIRST;
            dealFirstCount = 0;
        }
    }

    /** Deal one card to first player (sheriff). Called each tick during DEAL_FIRST. Returns true when done. */
    public boolean dealFirstPlayerCard() {
        if (phase != Phase.DEAL_FIRST || players.isEmpty()) return false;
        if (dealFirstCount >= 2) {
            phase = Phase.PLAYING;
            currentPlayerIndex = sheriffIndex;
            statusMessage = "Your turn, " + currentPlayer().name + "!";
            return false;
        }
        BangCard c = drawOne();
        if (c != null) {
            players.get(sheriffIndex).hand.add(c);
            dealFirstCount++;
        }
        if (dealFirstCount >= 2) {
            phase = Phase.PLAYING;
            currentPlayerIndex = sheriffIndex;
            statusMessage = "Your turn, " + currentPlayer().name + "!";
        }
        return true;
    }

    /** Advance to next alive player and start their turn. Used when player ends turn (NEXT_TURN_DELAY). */
    public void nextTurn() {
        int n = players.size();
        for (int i = 1; i <= n; i++) {
            int next = (currentPlayerIndex + i) % n;
            if (players.get(next).isAlive) {
                currentPlayerIndex = next;
                startTurnForCurrentPlayer();
                return;
            }
        }
        endGame();
    }

    /** Start turn for current player: jail check, dynamite check, or draw. Used after NEXT_TURN_DELAY or JAIL_SKIP_DELAY. */
    public void startTurnForCurrentPlayer() {
        PlayerState p = players.get(currentPlayerIndex);
        p.useBang = 1;
        usedGatlingThisTurn = false;
        if (p.characterId == 6) p.useBang = 0;
        for (BangCard c : p.equipment) {
            if (BangCard.VOLCANIC.equals(c.typeId())) p.useBang = 0;
        }
        if (jailPlayerIndex == currentPlayerIndex) {
            phase = Phase.JAIL_CHECK;
            if (p.characterId == 7) {
                BangCard c1 = drawOne();
                BangCard c2 = drawOne();
                pendingJailDrawnCard = (c1 != null && c1.isHearts()) ? c1 : (c2 != null && c2.isHearts()) ? c2 : c1;
                if (c1 != null && c1 != pendingJailDrawnCard) discardPile.add(c1);
                if (c2 != null && c2 != pendingJailDrawnCard) discardPile.add(c2);
            } else {
                pendingJailDrawnCard = drawOne();
            }
            statusMessage = p.name + " in Jail! Drawing...";
            return;
        }
        if (dynamitePlayerIndex == currentPlayerIndex && dynamiteCard != null) {
            phase = Phase.DYNAMITE_CHECK;
            if (p.characterId == 7) {
                BangCard c1 = drawOne();
                BangCard c2 = drawOne();
                if (c1 != null && c1.isSpades2to9()) { pendingDynamiteDrawnCard = c1; if (c2 != null) discardPile.add(c2); }
                else if (c2 != null && c2.isSpades2to9()) { pendingDynamiteDrawnCard = c2; if (c1 != null) discardPile.add(c1); }
                else { if (c1 != null) discardPile.add(c1); if (c2 != null) discardPile.add(c2); }
            } else {
                pendingDynamiteDrawnCard = drawOne();
            }
            statusMessage = p.name + " has Dynamite! Drawing...";
            return;
        }
        if (p.characterId == 8) {
            pendingKitCarlsonCards.clear();
            for (int i = 0; i < 3; i++) {
                BangCard c = drawOne();
                if (c != null) pendingKitCarlsonCards.add(c);
            }
            phase = Phase.KIT_DISCARD;
            kitDiscardTicks = 0;
            statusMessage = p.name + " (Kit Carlson): pick 2 cards to keep, 1 goes on deck.";
            return;
        }
        if (p.characterId == 12) {
            phase = Phase.DRAW_CHOICE;
            drawChoiceCharacter = 12;
            drawChoiceTicks = 0;
            statusMessage = p.name + " (Jesse Jones): draw 2 from deck, or 1 from deck + 1 from a player's hand.";
            return;
        }
        if (p.characterId == 16) {
            phase = Phase.DRAW_CHOICE;
            drawChoiceCharacter = 16;
            drawChoiceTicks = 0;
            statusMessage = p.name + " (Pedro Ramirez): draw 2 from deck, or 1 from deck + top of discard.";
            return;
        }
        if (p.characterId == 5) {
            BangCard c1 = drawOne();
            BangCard c2 = drawOne();
            if (c1 != null) p.hand.add(c1);
            if (c2 != null) {
                p.hand.add(c2);
                if (c2.isHeartsOrDiamonds()) {
                    BangCard c3 = drawOne();
                    if (c3 != null) p.hand.add(c3);
                }
            }
            statusMessage = p.name + "'s turn.";
            phase = Phase.PLAYING;
            return;
        }
        drawCards(p.name, 2);
        phase = Phase.PLAYING;
        statusMessage = p.name + "'s turn.";
    }

    private int drawChoiceCharacter = 0; // 12=Jesse, 16=Pedro

    /** Jesse Jones (12): choice = "deck" or "player:name". Pedro (16): "deck" or "discard". */
    public boolean drawChoice(String playerName, String choice) {
        if (phase != Phase.DRAW_CHOICE || currentPlayerIndex < 0) return false;
        PlayerState p = players.get(currentPlayerIndex);
        if (!p.name.equals(playerName)) return false;
        if ("deck".equals(choice)) {
            drawCards(p.name, 2);
            phase = Phase.PLAYING;
            statusMessage = p.name + "'s turn.";
            return true;
        }
        if (drawChoiceCharacter == 12 && choice != null && choice.startsWith("player:")) {
            String targetName = choice.substring(7);
            int toIdx = indexOf(targetName);
            if (toIdx < 0 || toIdx == currentPlayerIndex) return false;
            PlayerState target = players.get(toIdx);
            if (!target.isAlive || target.hand.isEmpty()) return false;
            drawCards(p.name, 1);
            BangCard taken = target.hand.remove(0);
            p.hand.add(taken);
            phase = Phase.PLAYING;
            statusMessage = p.name + " took from " + target.name + "'s hand!";
            checkSuzyLafayette(target.name);
            return true;
        }
        if (drawChoiceCharacter == 16 && "discard".equals(choice)) {
            if (discardPile.isEmpty()) return false;
            drawCards(p.name, 1);
            BangCard top = discardPile.remove(discardPile.size() - 1);
            p.hand.add(top);
            phase = Phase.PLAYING;
            statusMessage = p.name + " took from discard!";
            return true;
        }
        return false;
    }

    public int getDrawChoiceCharacter() { return drawChoiceCharacter; }

    private int kitDiscardTicks = 0;
    private final List<BangCard> pendingKitCarlsonCards = new ArrayList<>();
    private int drawChoiceTicks = 0;

    /** Tick KIT_DISCARD: 30s timeout → auto-pick first 2. */
    public void tickKitDiscard() {
        if (phase != Phase.KIT_DISCARD || currentPlayerIndex < 0) return;
        kitDiscardTicks++;
        if (kitDiscardTicks >= CHARACTER_SELECT_TIMEOUT_TICKS) {
            PlayerState p = players.get(currentPlayerIndex);
            if (pendingKitCarlsonCards.size() == 3) kitCarlsonPick(p.name, 0, 1);
            kitDiscardTicks = 0;
        }
    }

    /** Tick DRAW_CHOICE: 30s timeout → auto draw from deck. */
    public void tickDrawChoice() {
        if (phase != Phase.DRAW_CHOICE || currentPlayerIndex < 0) return;
        drawChoiceTicks++;
        if (drawChoiceTicks >= CHARACTER_SELECT_TIMEOUT_TICKS) {
            PlayerState p = players.get(currentPlayerIndex);
            drawChoice(p.name, "deck");
            drawChoiceTicks = 0;
        }
    }

    /** Hand limit = current HP (số máu hiện tại). Character có thể override (max 10). */
    public int getHandLimit(PlayerState p) {
        return Math.max(1, p.hp); // hp hiện tại, tối thiểu 1
    }

    /** Request to end turn. If hand > limit, go to DISCARD. Else go to NEXT_TURN_DELAY. */
    public boolean requestEndTurn(String playerName) {
        if (phase != Phase.PLAYING && phase != Phase.DISCARD) return false;
        int idx = indexOf(playerName);
        if (idx < 0 || idx != currentPlayerIndex) return false;
        PlayerState p = players.get(idx);
        int limit = getHandLimit(p);
        if (p.hand.size() > limit) {
            phase = Phase.DISCARD;
            statusMessage = p.name + " must discard to " + limit + " cards.";
            return true;
        }
        phase = Phase.NEXT_TURN_DELAY;
        statusMessage = "Next player...";
        return true;
    }

    /** Discard selected cards. Works in PLAYING or DISCARD when hand > limit. */
    public boolean discardCards(String playerName, int[] handIndices) {
        if (phase != Phase.PLAYING && phase != Phase.DISCARD) return false;
        int idx = indexOf(playerName);
        if (idx < 0 || idx != currentPlayerIndex) return false;
        PlayerState p = players.get(idx);
        int limit = getHandLimit(p);
        if (handIndices == null || handIndices.length == 0) return false;
        Arrays.sort(handIndices);
        for (int i = handIndices.length - 1; i >= 0; i--) {
            int hi = handIndices[i];
            if (hi >= 0 && hi < p.hand.size()) {
                BangCard c = p.hand.remove(hi);
                discardPile.add(c);
            }
        }
        statusMessage = p.name + " discarded.";
        checkSuzyLafayette(p.name);
        if (p.hand.size() <= limit) {
            phase = Phase.PLAYING;
            statusMessage = p.name + " can end turn.";
        } else {
            statusMessage = p.name + " must discard to " + limit + " cards.";
        }
        return true;
    }

    /** Process jail check (call after delay so client can show the card). Returns true if done. */
    public boolean processJailCheck() {
        if (phase != Phase.JAIL_CHECK || currentPlayerIndex < 0) return false;
        PlayerState p = players.get(currentPlayerIndex);
        if (jailPlayerIndex != currentPlayerIndex) return false;
        BangCard drawn = pendingJailDrawnCard;
        pendingJailDrawnCard = null;
        jailPlayerIndex = -1; // Remove jail
        if (drawn != null && drawn.isHearts()) {
            p.hand.add(drawn);
            drawCards(p.name, 1); // +1 more = 2 total for turn start
            phase = Phase.PLAYING;
            statusMessage = p.name + " escaped Jail! (♥)";
        } else {
            if (drawn != null) discardPile.add(drawn);
            statusMessage = p.name + " failed Jail, skipped turn.";
            // Advance to next player immediately; JAIL_SKIP_DELAY then starts their turn (no jumping)
            int n = players.size();
            for (int i = 1; i <= n; i++) {
                int next = (currentPlayerIndex + i) % n;
                if (players.get(next).isAlive) {
                    currentPlayerIndex = next;
                    break;
                }
            }
            phase = Phase.JAIL_SKIP_DELAY;
        }
        return true;
    }

    public BangCard getPendingJailDrawnCard() { return pendingJailDrawnCard; }
    public BangCard getPendingDynamiteDrawnCard() { return pendingDynamiteDrawnCard; }

    /** Process dynamite check (call after delay so client can show the card). Returns true if done. */
    public boolean processDynamiteCheck() {
        if (phase != Phase.DYNAMITE_CHECK || currentPlayerIndex < 0) return false;
        PlayerState p = players.get(currentPlayerIndex);
        if (dynamitePlayerIndex != currentPlayerIndex || dynamiteCard == null) {
            phase = Phase.PLAYING;
            drawCards(p.name, 2);
            statusMessage = p.name + "'s turn.";
            pendingDynamiteDrawnCard = null;
            return true;
        }
        BangCard drawn = pendingDynamiteDrawnCard;
        pendingDynamiteDrawnCard = null;
        if (drawn != null) discardPile.add(drawn);
        BangCard dynamite = dynamiteCard;
        dynamiteCard = null;
        dynamitePlayerIndex = -1;
        if (drawn != null && drawn.isSpades2to9()) {
            damagePlayer(currentPlayerIndex, 3, -1);
            discardPile.add(dynamite);
            statusMessage = p.name + " exploded! Dynamite! -3 HP.";
            if (!p.isAlive) {
                nextTurn();
                return true;
            }
        } else {
            int n = players.size();
            int next = (currentPlayerIndex + 1) % n;
            int count = 0;
            while (count < n) {
                if (players.get(next).isAlive) {
                    dynamiteCard = dynamite;
                    dynamitePlayerIndex = next;
                    statusMessage = p.name + " passed Dynamite to " + players.get(next).name + ".";
                    break;
                }
                next = (next + 1) % n;
                count++;
            }
            if (dynamiteCard == null) discardPile.add(dynamite); // all dead
        }
        phase = Phase.PLAYING;
        p = players.get(currentPlayerIndex);
        if (p.isAlive) {
            drawCards(p.name, 2);
            statusMessage = p.name + "'s turn.";
        }
        return true;
    }

    /** Panic: victim chooses from hand. Cat Balou: attacker chooses from victim's hand/equip. data = "hand:random" or "equip:1". */
    public boolean chooseCardToGive(String playerName, String data) {
        if (phase != Phase.CHOOSE_CARD || pendingChooseTarget < 0) return false;
        PlayerState victim = players.get(pendingChooseTarget);
        PlayerState chooser = pendingChooseIsPanic ? victim : players.get(pendingChooseSource);
        if (!chooser.name.equals(playerName)) return false;
        if (pendingChooseIsPanic && data != null && data.startsWith("equip:")) return false; // Panic: hand only
        BangCard chosen = null;
        if (data != null && data.startsWith("hand:")) {
            String sub = data.substring(5);
            int hi;
            if ("random".equals(sub) && !victim.hand.isEmpty()) {
                hi = new Random().nextInt(victim.hand.size());
            } else {
                try { hi = Integer.parseInt(sub); } catch (NumberFormatException e) { return false; }
            }
            if (hi >= 0 && hi < victim.hand.size()) {
                chosen = victim.hand.remove(hi);
            }
        } else if (data != null && data.startsWith("equip:") && !pendingChooseIsPanic) {
            int ei;
            try { ei = Integer.parseInt(data.substring(6)); } catch (NumberFormatException e) { return false; }
            if (ei >= 0 && ei < victim.equipment.size()) {
                BangCard eq = victim.equipment.get(ei);
                if (eq != null && !isRoleOrCharacter(eq)) {
                    chosen = victim.equipment.remove(ei);
                    recalcEquipmentStats(victim);
                }
            }
        }
        if (chosen == null) return false;
        if (pendingChooseIsPanic) {
            players.get(pendingChooseSource).hand.add(chosen);
            statusMessage = victim.name + " gave " + chosen.typeId() + " to " + players.get(pendingChooseSource).name;
        } else {
            discardPile.add(chosen);
            statusMessage = victim.name + " discarded " + chosen.typeId();
        }
        checkSuzyLafayette(victim.name);
        phase = Phase.PLAYING;
        pendingChooseTarget = -1;
        pendingChooseSource = -1;
        return true;
    }

    public boolean isChoosingCard(String playerName) {
        if (phase != Phase.CHOOSE_CARD || pendingChooseTarget < 0) return false;
        String chooser = pendingChooseIsPanic ? players.get(pendingChooseTarget).name : players.get(pendingChooseSource).name;
        return chooser.equals(playerName);
    }

    /** Chooser: Panic=victim, Cat Balou=attacker. */
    public String getChoosingTargetName() {
        if (phase != Phase.CHOOSE_CARD || pendingChooseTarget < 0) return null;
        return pendingChooseIsPanic ? players.get(pendingChooseTarget).name : players.get(pendingChooseSource).name;
    }

    /** Victim (whose card is taken). For Cat Balou, client shows victim's panel to attacker. */
    public String getChooseVictimName() {
        if (phase != Phase.CHOOSE_CARD || pendingChooseTarget < 0) return null;
        return players.get(pendingChooseTarget).name;
    }

    public boolean isChoosePanic() { return pendingChooseIsPanic; }

    private String gameOverWinner = ""; // "Outlaws", "Law", "Renegade", etc.

    private void endGame() {
        phase = Phase.GAME_OVER;
        if (sheriffIndex >= 0 && !players.get(sheriffIndex).isAlive) {
            gameOverWinner = "Outlaws";
            statusMessage = "Outlaws win! Sheriff is dead.";
        } else {
            long alive = players.stream().filter(p -> p.isAlive).count();
            if (alive <= 1) {
                PlayerState last = players.stream().filter(p -> p.isAlive).findFirst().orElse(null);
                if (last != null) {
                    gameOverWinner = last.role == Role.RENEGADE ? "Renegade" : "Law";
                    statusMessage = last.role == Role.RENEGADE ? "Renegade wins!" : "Law wins!";
                } else {
                    gameOverWinner = "";
                    statusMessage = "Game over!";
                }
            } else {
                gameOverWinner = "";
                statusMessage = "Game over!";
            }
        }
    }

    public void checkGameOver() {
        if (sheriffIndex < 0) return;
        PlayerState sheriff = players.get(sheriffIndex);
        if (!sheriff.isAlive) {
            endGame();
            return;
        }
        long alive = players.stream().filter(p -> p.isAlive).count();
        if (alive <= 1) endGame();
    }

    public String getGameOverWinner() { return gameOverWinner; }

    /** Prepare for new game: reset state but keep players. Call startGame() after. */
    public boolean prepareNewGame() {
        if (phase != Phase.GAME_OVER) return false;
        phase = Phase.WAITING;
        drawPile.clear();
        discardPile.clear();
        currentPlayerIndex = -1;
        sheriffIndex = -1;
        dynamiteCard = null;
        dynamitePlayerIndex = -1;
        jailPlayerIndex = -1;
        gameOverWinner = "";
        pendingBangTarget = -1;
        pendingBangSource = -1;
        pendingBarrelFailed = false;
        pendingJailDrawnCard = null;
        pendingDynamiteDrawnCard = null;
        pendingChooseTarget = -1;
        pendingChooseSource = -1;
        gatlingCurrentTarget = -1;
        indiansCurrentTarget = -1;
        indiansSource = -1;
        duelAttacker = duelDefender = -1;
        usedGatlingThisTurn = false;
        dealCardIndex = 0;
        dealFirstCount = 0;
        dealPauseTicks = 0;
        characterSelectPlayerIndex = -1;
        characterSelectOptions = new int[0];
        for (PlayerState p : players) {
            p.hand.clear();
            p.equipment.clear();
            p.characterId = 0;
            p.isAlive = true;
        }
        statusMessage = "New game starting...";
        return true;
    }

    public void resetToNewGame() {
        if (phase == Phase.GAME_OVER) {
            resetGame();
        }
    }

    public void updateCanHeal() {
        long alive = players.stream().filter(p -> p.isAlive).count();
        boolean canHeal = alive > 2;
        for (PlayerState p : players) p.canHeal = canHeal;
    }

    /** Beer: không hồi máu khi còn 2 người hoặc khi full máu. */
    public boolean canUseBeer(PlayerState p) {
        return p.canHeal && p.hp < p.maxHp;
    }

    /** Kit Carlson (8): pick 2 of 3 to keep, 1 goes on top of draw pile. */
    public boolean kitCarlsonPick(String playerName, int idx1, int idx2) {
        if (phase != Phase.KIT_DISCARD || currentPlayerIndex < 0) return false;
        PlayerState p = players.get(currentPlayerIndex);
        if (!p.name.equals(playerName)) return false;
        if (pendingKitCarlsonCards.size() != 3) return false;
        if (idx1 == idx2 || idx1 < 0 || idx2 < 0 || idx1 > 2 || idx2 > 2) return false;
        int dropIdx = 3 - idx1 - idx2;
        if (dropIdx < 0 || dropIdx > 2) return false;
        for (int i = 0; i < 3; i++) {
            BangCard c = pendingKitCarlsonCards.get(i);
            if (i == dropIdx) drawPile.add(c);
            else p.hand.add(c);
        }
        pendingKitCarlsonCards.clear();
        phase = Phase.PLAYING;
        statusMessage = p.name + "'s turn.";
        return true;
    }

    public List<BangCard> getPendingKitCarlsonCards() { return new ArrayList<>(pendingKitCarlsonCards); }

    /** Sid Ketchum (14): discard 2 cards to heal 1 HP. */
    public boolean sidKetchumHeal(String playerName, int handIdx1, int handIdx2) {
        if (phase != Phase.PLAYING && phase != Phase.DISCARD) return false;
        int idx = indexOf(playerName);
        if (idx < 0 || idx != currentPlayerIndex) return false;
        PlayerState p = players.get(idx);
        if (p.characterId != 14 || !canUseBeer(p)) return false;
        if (p.hand.size() < 2) return false;
        if (handIdx1 == handIdx2 || handIdx1 < 0 || handIdx2 < 0 || handIdx1 >= p.hand.size() || handIdx2 >= p.hand.size()) return false;
        int[] sorted = new int[]{ Math.min(handIdx1, handIdx2), Math.max(handIdx1, handIdx2) };
        BangCard c1 = p.hand.remove(sorted[1]);
        BangCard c2 = p.hand.remove(sorted[0]);
        discardPile.add(c1);
        discardPile.add(c2);
        p.hp = Math.min(p.maxHp, p.hp + 1);
        statusMessage = p.name + " (Sid Ketchum) discarded 2 to heal +1 HP!";
        return true;
    }

    /** Bang: target may UseMissed, UseBarrel (if barreling), UseBeer (when lethal), or TakeHit. Slab (13) needs 2 Miss. */
    public boolean reactToBang(String reactorName, int handIndex) {
        if (phase != Phase.REACTING && phase != Phase.GATLING_REACT || pendingBangTarget < 0) return false;
        PlayerState target = players.get(pendingBangTarget);
        if (!target.name.equals(reactorName)) return false;
        int srcIdx = pendingBangSource;
        PlayerState shooter = players.get(srcIdx);
        int missNeeded = (shooter.characterId == 13) ? 2 : 1; // Slab The Killer: need 2 Miss
        if (handIndex >= 0 && handIndex < target.hand.size()) {
            BangCard c = target.hand.get(handIndex);
            boolean countsAsMiss = BangCard.MISSED.equals(c.typeId())
                    || (target.characterId == 4 && BangCard.BANG.equals(c.typeId())); // Calamity Janet: BANG as MISS
            if (countsAsMiss) {
                target.hand.remove(handIndex);
                discardPile.add(c);
                pendingSlabMissCount++;
                if (pendingSlabMissCount >= missNeeded) {
                    pendingSlabMissCount = 0;
                    statusMessage = target.name + " used Miss! Dodged.";
                    if (phase == Phase.GATLING_REACT) {
                        advanceGatling(srcIdx);
                    } else {
                        phase = Phase.PLAYING;
                        pendingBangTarget = -1;
                        pendingBangSource = -1;
                        pendingBarrelFailed = false;
                    }
                    return true;
                }
                statusMessage = target.name + " used Miss (" + pendingSlabMissCount + "/" + missNeeded + "). Need " + (missNeeded - pendingSlabMissCount) + " more.";
                return true;
            }
            if (BangCard.BEER.equals(c.typeId()) && target.hp == 1) {
                target.hand.remove(handIndex);
                discardPile.add(c);
                target.hp = Math.min(target.maxHp, target.hp + 1);
                pendingSlabMissCount = 0;
                statusMessage = target.name + " used Beer! +1 HP, dodged.";
                if (phase == Phase.GATLING_REACT) {
                    advanceGatling(srcIdx);
                } else {
                    phase = Phase.PLAYING;
                    pendingBangTarget = -1;
                    pendingBangSource = -1;
                    pendingBarrelFailed = false;
                }
                return true;
            }
        }
        pendingSlabMissCount = 0;
        statusMessage = target.name + " took the hit! Lost 1 HP.";
        damagePlayer(pendingBangTarget, 1, pendingBangSource);
        if (phase == Phase.GATLING_REACT) {
            advanceGatling(srcIdx);
        } else {
            phase = Phase.PLAYING;
            pendingBangTarget = -1;
            pendingBangSource = -1;
            pendingBarrelFailed = false;
        }
        return true;
    }

    /** Use Barrel when shot: draw 1 (or 2 for Lucky Duke). If Hearts, nullify Bang. */
    public boolean reactToBangWithBarrel(String reactorName) {
        if (phase != Phase.REACTING && phase != Phase.GATLING_REACT || pendingBangTarget < 0) return false;
        PlayerState target = players.get(pendingBangTarget);
        if (!target.name.equals(reactorName) || !target.barreling || pendingBarrelFailed) return false;
        int srcIdx = pendingBangSource;
        BangCard drawn;
        if (target.characterId == 7) {
            BangCard c1 = drawOne();
            BangCard c2 = drawOne();
            drawn = (c1 != null && c1.isHearts()) ? c1 : (c2 != null && c2.isHearts()) ? c2 : c1;
            if (c1 != null && c1 != drawn) discardPile.add(c1);
            if (c2 != null && c2 != drawn) discardPile.add(c2);
        } else {
            drawn = drawOne();
        }
        if (drawn != null) discardPile.add(drawn);
        if (drawn != null && drawn.isHearts()) {
            pendingSlabMissCount = 0;
            statusMessage = target.name + " used Barrel! (♥) Dodged.";
            pendingBarrelFailed = false;
            if (phase == Phase.GATLING_REACT) {
                advanceGatling(srcIdx);
            } else {
                phase = Phase.PLAYING;
                pendingBangTarget = -1;
                pendingBangSource = -1;
            }
            return true;
        }
        pendingBarrelFailed = true;
        statusMessage = target.name + " used Barrel (no ♥). Use Miss/Beer or take hit.";
        return true;
    }

    public boolean isReactingToBang(String playerName) {
        return (phase == Phase.REACTING || phase == Phase.GATLING_REACT) && pendingBangTarget >= 0
                && players.get(pendingBangTarget).name.equals(playerName);
    }

    public String getReactingTargetName() {
        if ((phase == Phase.REACTING || phase == Phase.GATLING_REACT || phase == Phase.INDIANS_REACT) && pendingBangTarget >= 0)
            return players.get(pendingBangTarget).name;
        return null;
    }

    /** True if the reacting target can use Barrel (has Barrel and hasn't failed yet). */
    public boolean canTargetUseBarrel(String targetName) {
        if (pendingBangTarget < 0) return false;
        PlayerState t = players.get(pendingBangTarget);
        return t.name.equals(targetName) && t.barreling && !pendingBarrelFailed;
    }

    /** Jourdonnais (15): when Bang target, draw 1; if Hearts → dodge. */
    public boolean reactToBangWithJourdonnais(String reactorName) {
        if (phase != Phase.REACTING && phase != Phase.GATLING_REACT || pendingBangTarget < 0) return false;
        PlayerState target = players.get(pendingBangTarget);
        if (!target.name.equals(reactorName) || target.characterId != 15) return false;
        int srcIdx = pendingBangSource;
        BangCard drawn = drawOne();
        if (drawn != null) discardPile.add(drawn);
        if (drawn != null && drawn.isHearts()) {
            pendingSlabMissCount = 0;
            statusMessage = target.name + " used Jourdonnais! (♥) Dodged.";
            if (phase == Phase.GATLING_REACT) advanceGatling(srcIdx);
            else { phase = Phase.PLAYING; pendingBangTarget = -1; pendingBangSource = -1; pendingBarrelFailed = false; }
            return true;
        }
        statusMessage = target.name + " used Jourdonnais (no ♥). Use Miss/Beer or take hit.";
        return true;
    }

    public boolean canTargetUseJourdonnais(String targetName) {
        if (pendingBangTarget < 0) return false;
        PlayerState t = players.get(pendingBangTarget);
        return t.name.equals(targetName) && t.characterId == 15;
    }

    /** Indians: each target must play Bang (not Missed). No Barrel. No Bang = lose 1 HP. */
    private void advanceIndians(int sourceIdx) {
        int n = players.size();
        int start = indiansCurrentTarget < 0 ? 0 : indiansCurrentTarget + 1;
        for (int i = 0; i < n; i++) {
            int idx = (start + i) % n;
            if (idx == sourceIdx) continue;
            PlayerState t = players.get(idx);
            if (!t.isAlive) continue;
            indiansCurrentTarget = idx;
            indiansSource = sourceIdx;
            pendingBangSource = sourceIdx;
            pendingBangTarget = idx;
            phase = Phase.INDIANS_REACT;
            statusMessage = t.name + " hit by Indians! Play Bang or lose 1 HP.";
            return;
        }
        phase = Phase.PLAYING;
        indiansCurrentTarget = -1;
        indiansSource = -1;
        pendingBangTarget = -1;
        pendingBangSource = -1;
        statusMessage = "Indians ended.";
    }

    /** Calamity Janet (4): BANG and MISSED interchangeable. */
    private boolean countsAsBang(BangCard c, PlayerState p) {
        if (c == null) return false;
        return BangCard.BANG.equals(c.typeId())
                || (p.characterId == 4 && BangCard.MISSED.equals(c.typeId()));
    }

    /** React to Indians: play Bang or Miss (Calamity Janet) or take 1 damage. No Barrel. */
    public boolean reactToIndians(String reactorName, int handIndex) {
        if (phase != Phase.INDIANS_REACT || pendingBangTarget < 0) return false;
        PlayerState target = players.get(pendingBangTarget);
        if (!target.name.equals(reactorName)) return false;
        int srcIdx = indiansSource;
        if (handIndex >= 0 && handIndex < target.hand.size()) {
            BangCard c = target.hand.get(handIndex);
            if (countsAsBang(c, target)) {
                target.hand.remove(handIndex);
                discardPile.add(c);
                statusMessage = target.name + " played Bang! Dodged Indians.";
                advanceIndians(srcIdx);
                return true;
            }
        }
        statusMessage = target.name + " has no Bang! Lost 1 HP.";
        damagePlayer(pendingBangTarget, 1, indiansSource);
        advanceIndians(srcIdx);
        return true;
    }

    public boolean isReactingToIndians(String playerName) {
        return phase == Phase.INDIANS_REACT && pendingBangTarget >= 0
                && players.get(pendingBangTarget).name.equals(playerName);
    }

    /** Gatling: bắn lần lượt từng người (trừ người đánh). Mỗi người dùng Miss hoặc mất máu. */
    private void advanceGatling(int sourceIdx) {
        int n = players.size();
        int start = gatlingCurrentTarget < 0 ? 0 : gatlingCurrentTarget + 1;
        for (int i = 0; i < n; i++) {
            int idx = (start + i) % n;
            if (idx == sourceIdx) continue;
            PlayerState t = players.get(idx);
            if (!t.isAlive) continue;
            gatlingCurrentTarget = idx;
            pendingBangSource = sourceIdx;
            pendingBangTarget = idx;
            pendingBarrelFailed = false;
            phase = Phase.GATLING_REACT;
            statusMessage = t.name + " hit by Gatling! Use Miss, Barrel, or lose HP.";
            return;
        }
        phase = Phase.PLAYING;
        gatlingCurrentTarget = -1;
        pendingBangTarget = -1;
        pendingBangSource = -1;
        statusMessage = "Gatling ended.";
    }

    /** Duel: 2 người lần lượt đánh Bang. Attacker đánh trước. Calamity Janet: Miss counts as Bang. */
    private void advanceDuel() {
        int current = duelAttackerTurn ? duelAttacker : duelDefender;
        PlayerState p = players.get(current);
        boolean hasBang = p.hand.stream().anyMatch(c -> countsAsBang(c, p));
        if (!hasBang) {
            int other = duelAttackerTurn ? duelDefender : duelAttacker;
            damagePlayer(current, 1, other);
            statusMessage = p.name + " lost Duel! Lost 1 HP.";
            phase = Phase.PLAYING;
            duelAttacker = duelDefender = -1;
        }
    }

    /** Đánh Bang trong Duel. handIndex = vị trí lá Bang/Miss (Calamity Janet) trong hand. */
    public boolean playDuelBang(String playerName, int handIndex) {
        if (phase != Phase.DUEL_PLAY) return false;
        int idx = indexOf(playerName);
        if (idx < 0) return false;
        int current = duelAttackerTurn ? duelAttacker : duelDefender;
        if (idx != current) return false;
        PlayerState p = players.get(idx);
        if (handIndex < 0 || handIndex >= p.hand.size()) return false;
        BangCard card = p.hand.get(handIndex);
        if (!countsAsBang(card, p)) return false;
        p.hand.remove(handIndex);
        discardPile.add(card);
        duelAttackerTurn = !duelAttackerTurn;
        statusMessage = p.name + " played Bang in Duel!";
        advanceDuel();
        return true;
    }

    /** Bỏ qua Duel (không có Bang). */
    public boolean passDuel(String playerName) {
        if (phase != Phase.DUEL_PLAY) return false;
        int idx = indexOf(playerName);
        if (idx < 0) return false;
        int current = duelAttackerTurn ? duelAttacker : duelDefender;
        if (idx != current) return false;
        int other = duelAttackerTurn ? duelDefender : duelAttacker;
        damagePlayer(idx, 1, other);
        statusMessage = players.get(idx).name + " has no Bang, lost Duel! Lost 1 HP.";
        phase = Phase.PLAYING;
        duelAttacker = duelDefender = -1;
        return true;
    }

    public boolean isInDuel(String playerName) {
        return phase == Phase.DUEL_PLAY && (duelAttackerTurn ? duelAttacker : duelDefender) == indexOf(playerName);
    }

    public boolean isDuelPhase() { return phase == Phase.DUEL_PLAY; }
    public String getDuelAttackerName() { return duelAttacker >= 0 && duelAttacker < players.size() ? players.get(duelAttacker).name : ""; }
    public String getDuelDefenderName() { return duelDefender >= 0 && duelDefender < players.size() ? players.get(duelDefender).name : ""; }
    public boolean isDuelAttackerTurn() { return duelAttackerTurn; }

    // ── Card effects (stub - full logic in playCard) ─────────────────────────────────────

    public boolean playCard(String playerName, int handIndex, String targetName) {
        if (phase != Phase.PLAYING && phase != Phase.DISCARD) return false;
        int idx = indexOf(playerName);
        if (idx < 0 || idx != currentPlayerIndex) return false;
        PlayerState p = players.get(idx);
        if (handIndex < 0 || handIndex >= p.hand.size()) return false;
        BangCard card = p.hand.remove(handIndex);
        discardPile.add(card);

        String cardType = card.typeId();
        if (p.characterId == 4 && BangCard.MISSED.equals(cardType)) cardType = BangCard.BANG; // Calamity Janet: Miss as Bang
        switch (cardType) {
            case BangCard.BANG -> {
                if (targetName == null || targetName.isEmpty()) { restoreCard(p, handIndex, card); return false; }
                int toIdx = indexOf(targetName);
                if (toIdx < 0 || !canShoot(idx, toIdx)) { restoreCard(p, handIndex, card); return false; }
                boolean hasVolcanic = p.equipment.stream().anyMatch(c -> BangCard.VOLCANIC.equals(c.typeId()));
                if (usedGatlingThisTurn || (p.useBang <= 0 && !hasVolcanic)) { restoreCard(p, handIndex, card); return false; }
                if (p.useBang > 0) p.useBang--;
                statusMessage = p.name + " shot " + players.get(toIdx).name + " with Bang!";
                pendingBangSource = idx;
                pendingBangTarget = toIdx;
                phase = Phase.REACTING;
                return true;
            }
            case BangCard.CAT_BALOU -> {
                if (targetName == null || targetName.isEmpty()) return false;
                int toIdx = indexOf(targetName);
                if (toIdx < 0) return false;
                PlayerState target = players.get(toIdx);
                if (target.hand.isEmpty() && target.equipment.isEmpty()) return false;
                phase = Phase.CHOOSE_CARD;
                pendingChooseTarget = toIdx;
                pendingChooseSource = idx;
                pendingChooseIsPanic = false;
                statusMessage = target.name + ": click equipment to discard, or frame for random hand";
                return true;
            }
            case BangCard.PANIC -> {
                if (targetName == null || targetName.isEmpty()) { restoreCard(p, handIndex, card); return false; }
                int toIdx = indexOf(targetName);
                if (toIdx < 0 || !canUseOn(idx, toIdx, false)) { restoreCard(p, handIndex, card); return false; }
                PlayerState target = players.get(toIdx);
                if (target.hand.isEmpty()) { restoreCard(p, handIndex, card); return false; }
                int hi = new Random().nextInt(target.hand.size());
                BangCard chosen = target.hand.remove(hi);
                players.get(idx).hand.add(chosen);
                statusMessage = p.name + " stole " + chosen.typeId() + " from " + target.name + "!";
                checkSuzyLafayette(target.name);
                return true;
            }
            case BangCard.BEER -> {
                if (!canUseBeer(p)) return false;
                p.hp = Math.min(p.maxHp, p.hp + 1);
                statusMessage = p.name + " drank Beer! +1 HP";
                return true;
            }
            case BangCard.STAGECOACH -> {
                drawCards(p.name, 2);
                statusMessage = p.name + " drew 2 cards.";
                return true;
            }
            case BangCard.WELLS_FARGO -> {
                drawCards(p.name, 3);
                statusMessage = p.name + " drew 3 cards.";
                return true;
            }
            case BangCard.JAIL -> {
                if (targetName == null || targetName.isEmpty()) return false;
                int toIdx = indexOf(targetName);
                if (toIdx < 0 || toIdx == idx) return false;
                PlayerState target = players.get(toIdx);
                if (!target.jail || jailPlayerIndex >= 0) return false;
                jailPlayerIndex = toIdx;
                statusMessage = p.name + " jailed " + target.name + "!";
                return true;
            }
            case BangCard.GATLING -> {
                // Gatling = shoot each person 1 Bang. Miss/Barrel to dodge, else take 1 damage. Mutually exclusive with Bang this turn.
                if (usedGatlingThisTurn) { restoreCard(p, handIndex, card); return false; }
                usedGatlingThisTurn = true;
                statusMessage = p.name + " used Gatling! Shooting everyone.";
                phase = Phase.GATLING_REACT;
                gatlingCurrentTarget = -1;
                advanceGatling(idx);
                return true;
            }
            case BangCard.DUEL -> {
                if (targetName == null || targetName.isEmpty()) return false;
                int toIdx = indexOf(targetName);
                if (toIdx < 0 || toIdx == idx) return false;
                PlayerState target = players.get(toIdx);
                if (!target.isAlive) return false;
                statusMessage = p.name + " challenged " + target.name + "! (Duel)";
                phase = Phase.DUEL_PLAY;
                duelAttacker = idx;
                duelDefender = toIdx;
                duelAttackerTurn = false;
                advanceDuel();
                return true;
            }
            case BangCard.INDIANS -> {
                statusMessage = p.name + " played Indians! Everyone must play Bang or lose 1 HP.";
                phase = Phase.INDIANS_REACT;
                indiansCurrentTarget = -1;
                indiansSource = idx;
                advanceIndians(idx);
                return true;
            }
            case BangCard.GENERAL_STORE, BangCard.SALOON -> {
                statusMessage = p.name + " played " + card.typeId() + " (TODO)";
                return true;
            }
            default -> {
                discardPile.remove(discardPile.size() - 1);
                p.hand.add(handIndex, card);
                return false;
            }
        }
    }

    /** Restore a card to hand when play is rejected (undo discard). */
    private void restoreCard(PlayerState p, int handIndex, BangCard card) {
        discardPile.remove(discardPile.size() - 1);
        p.hand.add(Math.min(handIndex, p.hand.size()), card);
    }

    /** Equip blue card. replaceGun: if true and already has gun, discard old and equip new. Dynamite goes to shared slot. */
    public boolean equipCard(String playerName, int handIndex, boolean replaceGun) {
        if (phase != Phase.PLAYING && phase != Phase.DISCARD) return false;
        int idx = indexOf(playerName);
        if (idx < 0 || idx != currentPlayerIndex) return false;
        PlayerState p = players.get(idx);
        if (handIndex < 0 || handIndex >= p.hand.size()) return false;
        BangCard card = p.hand.get(handIndex);
        if (!card.isBlue()) return false;

        if (BangCard.DYNAMITE.equals(card.typeId())) {
            if (dynamiteCard != null) return false; // Someone already has dynamite
            p.hand.remove(handIndex);
            dynamiteCard = card;
            dynamitePlayerIndex = idx;
            statusMessage = p.name + " placed Dynamite!";
            return true;
        }

        String eqType = card.getEquipmentType();
        int existingSlot = findEquipmentSlot(p, eqType);
        if (existingSlot >= 0) {
            if (card.isGun() && replaceGun) {
                BangCard old = p.equipment.remove(existingSlot);
                discardPile.add(old);
                p.hand.remove(handIndex);
                p.equipment.add(existingSlot, card);
                applyEquipment(p, card);
                statusMessage = p.name + " replaced gun with " + card.typeId();
                return true;
            }
            return false; // already have same type, no replace
        }
        p.hand.remove(handIndex);
        p.equipment.add(card);
        applyEquipment(p, card);
        statusMessage = p.name + " equipped " + card.typeId();
        return true;
    }

    private int findEquipmentSlot(PlayerState p, String eqType) {
        for (int i = 0; i < p.equipment.size(); i++) {
            if (eqType.equals(p.equipment.get(i).getEquipmentType())) return i;
        }
        return -1;
    }

    /** Role/character not in equipment; exclude for future. */
    private boolean isRoleOrCharacter(BangCard c) {
        return c != null && ("role".equals(c.typeId()) || "character".equals(c.typeId()));
    }

    private void applyEquipment(PlayerState p, BangCard c) {
        recalcEquipmentStats(p);
    }

    private void recalcEquipmentStats(PlayerState p) {
        p.gapShoot = 1;
        p.useBang = 1;
        p.gap = 1;
        p.gapUse = 1;
        p.barreling = false;
        if (p.characterId == 6) p.useBang = 0; // Willy The Kid: unlimited Bang
        for (BangCard c : p.equipment) {
            switch (c.typeId()) {
                case BangCard.VOLCANIC -> p.useBang = 0;
                case BangCard.SCHOFIELD -> p.gapShoot = Math.max(p.gapShoot, 2);
                case BangCard.REMINGTON -> p.gapShoot = Math.max(p.gapShoot, 3);
                case BangCard.REV_CARBINE -> p.gapShoot = Math.max(p.gapShoot, 4);
                case BangCard.WINCHESTER -> p.gapShoot = Math.max(p.gapShoot, 5);
                case BangCard.MUSTANG -> p.gap++;
                case BangCard.APPALOOSA -> {} // -1 distance handled in getDistanceTo (shooter sees targets closer)
                case BangCard.BARREL -> p.barreling = true;
                default -> {}
            }
        }
    }

    public boolean hasGun(PlayerState p) {
        return findEquipmentSlot(p, "gun") >= 0;
    }

    public boolean canEquip(PlayerState p, BangCard card) {
        if (!card.isBlue()) return false;
        if (BangCard.DYNAMITE.equals(card.typeId())) return dynamiteCard == null;
        String eqType = card.getEquipmentType();
        return findEquipmentSlot(p, eqType) < 0;
    }

    private int pendingSlabMissCount = 0; // Slab The Killer (13): need 2 Miss to dodge

    private boolean damagePlayer(int toIdx, int amount, int damageSourceIdx) {
        PlayerState target = players.get(toIdx);
        int prevHp = target.hp;
        target.hp = Math.max(0, target.hp - amount);
        int lost = prevHp - target.hp;
        if (target.hp <= 0) {
            target.isAlive = false;
            giveDeadPlayerCardsToVultureSam(toIdx);
        }
        if (target.characterId == 11 && lost > 0) {
            for (int i = 0; i < lost; i++) drawCards(target.name, 1); // Bart Cassidy: lose HP → draw
        }
        if (target.characterId == 2 && lost > 0 && damageSourceIdx >= 0) {
            PlayerState attacker = players.get(damageSourceIdx);
            if (!attacker.hand.isEmpty()) {
                BangCard stolen = attacker.hand.remove(new Random().nextInt(attacker.hand.size()));
                target.hand.add(stolen); // El Gringo: when damaged, draw 1 from attacker
                checkSuzyLafayette(attacker.name);
            }
        }
        updateCanHeal();
        checkGameOver();
        return true;
    }

    /** Vulture Sam (3): when someone dies, give their hand+equipment to nearest alive Vulture Sam. */
    private void giveDeadPlayerCardsToVultureSam(int deadIdx) {
        PlayerState dead = players.get(deadIdx);
        List<BangCard> toGive = new ArrayList<>();
        toGive.addAll(dead.hand);
        dead.hand.clear();
        for (BangCard c : dead.equipment) {
            if (c != null && !BangCard.DYNAMITE.equals(c.typeId())) toGive.add(c);
        }
        dead.equipment.removeIf(c -> c != null && !BangCard.DYNAMITE.equals(c.typeId()));
        if (toGive.isEmpty()) return;
        int n = players.size();
        for (int i = 1; i <= n; i++) {
            int idx = (deadIdx + i) % n;
            PlayerState p = players.get(idx);
            if (p.isAlive && p.characterId == 3) {
                for (BangCard c : toGive) {
                    if (p.hand.size() < MAX_HAND_SIZE) p.hand.add(c);
                    else discardPile.add(c);
                }
                statusMessage = p.name + " (Vulture Sam) took " + dead.name + "'s cards!";
                return;
            }
        }
        for (BangCard c : toGive) discardPile.add(c);
    }

    public static final int MAX_HAND_SIZE = 15;
    public static final int HAND_LIMIT_END_TURN = 5; // Must have <= 5 cards to end turn (or use maxHp)

    public void drawCards(String playerName, int count) {
        int idx = indexOf(playerName);
        if (idx < 0) return;
        PlayerState p = players.get(idx);
        while (count > 0 && p.hand.size() < MAX_HAND_SIZE) {
            BangCard c = drawOne();
            if (c == null) break;
            p.hand.add(c);
            count--;
        }
    }

    /** Suzy Lafayette (10): when hand empty, draw 1. Call after actions that might empty hand. */
    public void checkSuzyLafayette(String playerName) {
        int idx = indexOf(playerName);
        if (idx < 0) return;
        PlayerState p = players.get(idx);
        if (p.isAlive && p.characterId == 10 && p.hand.isEmpty()) {
            drawCards(playerName, 1);
            statusMessage = p.name + " (Suzy Lafayette) drew 1 - hand was empty!";
        }
    }

    /** Rút 1 lá từ chồng bài, xáo discard nếu hết. */
    private BangCard drawOne() {
        if (!drawPile.isEmpty()) return drawPile.remove(drawPile.size() - 1);
        if (discardPile.isEmpty()) return null;
        drawPile.addAll(discardPile);
        discardPile.clear();
        Collections.shuffle(drawPile);
        return drawPile.remove(drawPile.size() - 1);
    }

    public int getDeckCount() {
        return drawPile.size();
    }

    // ── Getters ───────────────────────────────────────────────────────────────────────────

    public Phase getPhase() { return phase; }
    public List<PlayerState> getPlayers() { return players; }
    public List<String> getPendingPlayers() { return Collections.unmodifiableList(pendingPlayers); }
    public String getStatusMessage() { return statusMessage; }
    public void setStatusMessage(String s) { statusMessage = s; }
    public int getCurrentPlayerIndex() { return currentPlayerIndex; }
    public String getCurrentPlayerName() {
        if (currentPlayerIndex < 0 || currentPlayerIndex >= players.size()) return "";
        return players.get(currentPlayerIndex).name;
    }
    public PlayerState currentPlayer() {
        if (currentPlayerIndex < 0 || currentPlayerIndex >= players.size()) return null;
        return players.get(currentPlayerIndex);
    }
    public int getSheriffIndex() { return sheriffIndex; }
    public int getDealCardIndex() { return dealCardIndex; }
    public boolean isEmpty() { return players.isEmpty() && pendingPlayers.isEmpty(); }
    public BangCard getDynamiteCard() { return dynamiteCard; }
    public int getDynamitePlayerIndex() { return dynamitePlayerIndex; }
    public int getJailPlayerIndex() { return jailPlayerIndex; }
}
