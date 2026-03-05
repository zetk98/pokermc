package com.pokermc.game.bang;

import java.util.*;

/**
 * Bang! game logic. Circular distance, player stats, card effects.
 */
public class BangGame {

    public enum Phase { WAITING, DEALING, PLAYING, REACTING, GAME_OVER }
    public enum Role { SHERIFF, DEPUTY, OUTLAW, RENEGADE }

    public static class PlayerState {
        public final String name;
        public int seatIndex;
        public Role role;
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
    }

    private final List<PlayerState> players = new ArrayList<>();
    private final List<String> pendingPlayers = new ArrayList<>();
    private final List<BangCard> drawPile = new ArrayList<>();
    private final List<BangCard> discardPile = new ArrayList<>();
    private Phase phase = Phase.WAITING;
    private int currentPlayerIndex = -1;
    private int sheriffIndex = -1;
    private int maxPlayers = 4;
    private String statusMessage = "Waiting for players...";
    private int dealCardIndex = 0;
    public static final int DEAL_TICKS_PER_CARD = 8;

    // ── Distance (circular): min steps going clockwise or counter-clockwise ─────────────────
    /** Distance from player at fromIdx to player at toIdx (1-based). */
    public int getDistance(int fromIdx, int toIdx) {
        if (players.isEmpty()) return 0;
        int n = players.size();
        int clockwise = (toIdx - fromIdx + n) % n;
        int counter = (fromIdx - toIdx + n) % n;
        return Math.min(clockwise, counter);
    }

    /** Distance from playerIdx to targetIdx, accounting for Mustang on target (+1 to others). */
    public int getDistanceTo(int fromIdx, int toIdx) {
        int base = getDistance(fromIdx, toIdx);
        if (base <= 0) return base;
        PlayerState target = players.get(toIdx);
        for (BangCard c : target.equipment) {
            if (BangCard.MUSTANG.equals(c.typeId())) base++;
        }
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

    public boolean hasPlayer(String name) {
        return players.stream().anyMatch(p -> p.name.equals(name)) || pendingPlayers.contains(name);
    }

    public int indexOf(String name) {
        for (int i = 0; i < players.size(); i++)
            if (players.get(i).name.equals(name)) return i;
        return -1;
    }

    public void setMaxPlayers(int n) { maxPlayers = Math.max(4, Math.min(6, n)); }
    public int getMaxPlayers() { return maxPlayers; }

    // ── Game start ───────────────────────────────────────────────────────────────────────

    public boolean startGame() {
        if (phase != Phase.WAITING || players.size() < 4) return false;
        if (players.size() > maxPlayers) return false;

        drawPile.clear();
        discardPile.clear();
        drawPile.addAll(BangCard.buildDeck());

        // Assign roles: 1 Sheriff, 1 Renegade, rest Deputies/Outlaws
        List<Role> roles = new ArrayList<>();
        roles.add(Role.SHERIFF);
        roles.add(Role.RENEGADE);
        int outlaws = players.size() - 2;
        for (int i = 0; i < outlaws; i++) roles.add(i % 2 == 0 ? Role.OUTLAW : Role.DEPUTY);
        Collections.shuffle(roles);

        for (int i = 0; i < players.size(); i++) {
            PlayerState p = players.get(i);
            p.hand.clear();
            p.equipment.clear();
            p.isAlive = true;
            p.applyRole(roles.get(i));
            if (p.role == Role.SHERIFF) sheriffIndex = i;
        }

        phase = Phase.DEALING;
        dealCardIndex = 0;
        currentPlayerIndex = -1;
        statusMessage = "Dealing cards...";
        return true;
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
            int maxCards = Math.min(players.get(idx).hp, 5);
            if (players.get(idx).hand.size() < maxCards) {
                if (count == dealCardIndex) {
                    targetIdx = idx;
                    break;
                }
                count++;
            }
        }
        if (targetIdx >= 0 && !drawPile.isEmpty()) {
            players.get(targetIdx).hand.add(drawPile.remove(drawPile.size() - 1));
        }
        dealCardIndex++;
        if (dealCardIndex >= totalCards) finishDealing();
        return true;
    }

    private void finishDealing() {
        phase = Phase.PLAYING;
        currentPlayerIndex = sheriffIndex;
        statusMessage = "Your turn, " + currentPlayer().name + "!";
    }

    public void nextTurn() {
        int n = players.size();
        for (int i = 1; i <= n; i++) {
            int next = (currentPlayerIndex + i) % n;
            if (players.get(next).isAlive) {
                currentPlayerIndex = next;
                PlayerState p = players.get(currentPlayerIndex);
                p.useBang = 1; // reset per turn
                for (BangCard c : p.equipment) {
                    if (BangCard.VOLCANIC.equals(c.typeId())) p.useBang = 0;
                }
                statusMessage = p.name + "'s turn.";
                return;
            }
        }
        endGame();
    }

    private void endGame() {
        phase = Phase.GAME_OVER;
        statusMessage = "Game over!";
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

    public void updateCanHeal() {
        long alive = players.stream().filter(p -> p.isAlive).count();
        boolean canHeal = alive > 2;
        for (PlayerState p : players) p.canHeal = canHeal;
    }

    // ── Card effects (stub - full logic in playCard) ─────────────────────────────────────

    public boolean playCard(String playerName, int handIndex, String targetName) {
        int idx = indexOf(playerName);
        if (idx < 0) return false;
        PlayerState p = players.get(idx);
        if (handIndex < 0 || handIndex >= p.hand.size()) return false;
        BangCard card = p.hand.remove(handIndex);
        discardPile.add(card);

        switch (card.typeId()) {
            case BangCard.BANG -> {
                if (targetName == null || targetName.isEmpty()) return false;
                int toIdx = indexOf(targetName);
                if (toIdx < 0 || !canShoot(idx, toIdx)) return false;
                if (p.useBang == 0 || p.useBang > 0) {
                    if (p.useBang > 0) p.useBang--;
                    statusMessage = p.name + " shot " + players.get(toIdx).name + "!";
                    return damagePlayer(toIdx, 1);
                }
                return false;
            }
            case BangCard.CAT_BALOU -> {
                if (targetName == null || targetName.isEmpty()) return false;
                int toIdx = indexOf(targetName);
                if (toIdx < 0) return false;
                // Force discard - simplified: remove random from hand
                PlayerState target = players.get(toIdx);
                if (!target.hand.isEmpty()) {
                    target.hand.remove(target.hand.size() - 1);
                }
                statusMessage = p.name + " used Cat Balou on " + target.name;
                return true;
            }
            case BangCard.PANIC -> {
                if (targetName == null || targetName.isEmpty()) return false;
                int toIdx = indexOf(targetName);
                if (toIdx < 0 || !canUseOn(idx, toIdx, false)) return false;
                PlayerState target = players.get(toIdx);
                if (!target.hand.isEmpty()) {
                    BangCard stolen = target.hand.remove(target.hand.size() - 1);
                    p.hand.add(stolen);
                }
                statusMessage = p.name + " used Panic! on " + target.name;
                return true;
            }
            case BangCard.BEER -> {
                if (!p.canHeal) return false;
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
            case BangCard.GATLING, BangCard.INDIANS, BangCard.DUEL, BangCard.GENERAL_STORE,
                 BangCard.SALOON -> {
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

    private boolean damagePlayer(int toIdx, int amount) {
        PlayerState target = players.get(toIdx);
        target.hp = Math.max(0, target.hp - amount);
        if (target.hp <= 0) target.isAlive = false;
        updateCanHeal();
        checkGameOver();
        return true;
    }

    public void drawCards(String playerName, int count) {
        int idx = indexOf(playerName);
        if (idx < 0) return;
        PlayerState p = players.get(idx);
        while (count > 0 && !drawPile.isEmpty() && p.hand.size() < 5) {
            p.hand.add(drawPile.remove(drawPile.size() - 1));
            count--;
        }
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
}
