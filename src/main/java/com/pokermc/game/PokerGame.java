package com.pokermc.game;

import com.pokermc.config.PokerConfig;

import java.util.*;
import java.util.Collections;

/**
 * Server-side Texas Hold'em game logic.
 * Stored inside the PokerTableBlockEntity.
 */
public class PokerGame {

    public enum Phase { WAITING, PRE_FLOP, FLOP, TURN, RIVER, SHOWDOWN }
    public enum Action { FOLD, CHECK, CALL, RAISE, ALLIN }

    public static class PlayerState {
        public final String name;
        public int chips;
        public final List<Card> holeCards = new ArrayList<>();
        public int currentBet = 0;
        public boolean folded = false;
        public boolean allIn = false;
        public boolean hasActed = false;

        public PlayerState(String name, int chips) {
            this.name = name;
            this.chips = chips;
        }
    }

    private final List<PlayerState> players = new ArrayList<>();
    /** Players who tried to join while a game was in progress; they enter at the next round. */
    private final List<String> pendingPlayers = new ArrayList<>();
    private final List<Card> communityCards = new ArrayList<>();
    private final Deck deck = new Deck();
    private Phase phase = Phase.WAITING;
    private int pot = 0;
    private int currentBet = 0;
    private int dealerIndex = 0;
    private int currentPlayerIndex = -1;
    private int lastRaiserIndex = -1;
    private int firstCheckerIndex = -1;
    private String lastWinner = "";
    private String lastWinningHand = "";
    private int lastPotWon = 0;
    private String statusMessage = "Waiting for players...";
    private long turnStartTick = 0;

    // ── Per-table bet level (overrides global config) ─────────────────────────
    private int betLevel      = 10;
    private int smallBlind    = 5;
    private int bigBlind      = 10;
    private int minRaise      = 10;
    private int startingChips = 1000; // 100 BB default

    public void setBetLevel(int level) {
        this.betLevel      = level;
        this.smallBlind    = Math.max(1, level / 2);
        this.bigBlind      = level;
        this.minRaise      = level;
        this.startingChips = level * 100;
        statusMessage = "Room created. Blinds: " + smallBlind + "/" + bigBlind
                      + ".  Stack: " + startingChips;
    }

    public int getBetLevel()      { return betLevel; }
    public int getStartingChips() { return startingChips; }

    // ── Player management ──────────────────────────────────────────────────────

    /** Join with custom chip count (0 = use table's startingChips). */
    public boolean addPlayer(String name, int customChips) {
        if (players.stream().anyMatch(p -> p.name.equals(name))) return false;
        if (pendingPlayers.contains(name)) return false;

        if (phase != Phase.WAITING) {
            pendingPlayers.add(name);
            statusMessage = name + " will join next round.";
            return true;
        }

        PokerConfig cfg = PokerConfig.get();
        if (players.size() >= cfg.maxPlayers) return false;
        int chips = customChips > 0 ? customChips : startingChips;
        players.add(new PlayerState(name, chips));
        statusMessage = name + " joined the table.";
        return true;
    }

    public boolean addPlayer(String name) { return addPlayer(name, 0); }

    public boolean removePlayer(String name) {
        pendingPlayers.remove(name);
        if (phase == Phase.WAITING || phase == Phase.SHOWDOWN) {
            return players.removeIf(p -> p.name.equals(name));
        }
        int idx = -1;
        for (int i = 0; i < players.size(); i++) {
            if (players.get(i).name.equals(name)) { idx = i; break; }
        }
        if (idx < 0) return false;
        players.get(idx).folded = true;
        players.get(idx).hasActed = true;
        players.remove(idx);
        if (idx <= currentPlayerIndex) currentPlayerIndex = Math.max(-1, currentPlayerIndex - 1);
        if (currentPlayerIndex >= players.size()) currentPlayerIndex = -1;
        checkRoundEnd();
        return true;
    }

    public boolean hasPlayer(String name) {
        return players.stream().anyMatch(p -> p.name.equals(name))
                || pendingPlayers.contains(name);
    }

    public boolean isActivePlayer(String name) {
        return players.stream().anyMatch(p -> p.name.equals(name));
    }

    public boolean isPendingPlayer(String name) {
        return pendingPlayers.contains(name);
    }

    public List<String> getPendingPlayers() { return Collections.unmodifiableList(pendingPlayers); }

    /** Add chips directly to a player already seated (e.g. from deposit). */
    public boolean addChips(String name, int amount) {
        PlayerState p = players.stream().filter(ps -> ps.name.equals(name)).findFirst().orElse(null);
        if (p == null) return false;
        p.chips += amount;
        statusMessage = name + " deposited " + amount + " ZC.";
        return true;
    }

    // ── Game start ─────────────────────────────────────────────────────────────

    public boolean startGame() {
        if (phase != Phase.WAITING) return false;
        if (players.size() < PokerConfig.get().minPlayers) return false;

        deck.reset();
        deck.shuffle();
        communityCards.clear();
        pot = 0;
        currentBet = 0;
        lastWinner = "";
        lastWinningHand = "";
        lastPotWon = 0;

        for (PlayerState p : players) {
            p.holeCards.clear();
            p.currentBet = 0;
            p.folded = false;
            p.allIn = false;
            p.hasActed = false;
        }

        // Deal 2 cards each
        for (int round = 0; round < 2; round++) {
            for (PlayerState p : players) {
                p.holeCards.add(deck.deal());
            }
        }

        phase = Phase.PRE_FLOP;

        int sbIdx = (dealerIndex + 1) % players.size();
        int bbIdx = (dealerIndex + 2) % players.size();
        postBlind(sbIdx, smallBlind);
        postBlind(bbIdx, bigBlind);
        currentBet = bigBlind;

        // UTG acts first pre-flop
        currentPlayerIndex = (dealerIndex + 3) % players.size();
        // Handle heads-up (2 players): dealer is SB, other is BB; dealer acts first
        if (players.size() == 2) {
            currentPlayerIndex = dealerIndex;
        }

        statusMessage = "Game started! Pre-flop. " + currentPlayer().name + "'s turn.";
        return true;
    }

    private void postBlind(int idx, int amount) {
        PlayerState p = players.get(idx);
        int actual = Math.min(amount, p.chips);
        p.chips -= actual;
        p.currentBet = actual;
        pot += actual;
    }

    // ── Player action ──────────────────────────────────────────────────────────

    public boolean performAction(String playerName, Action action, int amount) {
        if (phase == Phase.WAITING || phase == Phase.SHOWDOWN) return false;
        PlayerState cur = currentPlayer();
        if (cur == null || !cur.name.equals(playerName)) return false;

        switch (action) {
            case FOLD -> {
                cur.folded = true;
                cur.hasActed = true;
                statusMessage = playerName + " folded.";
            }
            case CHECK -> {
                if (currentBet > cur.currentBet) return false;
                if (firstCheckerIndex < 0) firstCheckerIndex = players.indexOf(cur);
                cur.hasActed = true;
                statusMessage = playerName + " checked.";
            }
            case CALL -> {
                int toCall = Math.min(currentBet - cur.currentBet, cur.chips);
                cur.chips -= toCall;
                cur.currentBet += toCall;
                pot += toCall;
                if (cur.chips == 0) cur.allIn = true;
                cur.hasActed = true;
                statusMessage = playerName + " called " + toCall + ".";
            }
            case RAISE -> {
                if (amount < minRaise) return false;
                int toCall = currentBet - cur.currentBet;
                int total = Math.min(toCall + amount, cur.chips);
                if (total <= 0) return false;
                cur.chips -= total;
                cur.currentBet += total;
                pot += total;
                currentBet = cur.currentBet;
                lastRaiserIndex = players.indexOf(cur);
                firstCheckerIndex = -1;
                if (cur.chips == 0) cur.allIn = true;
                cur.hasActed = true;
                for (PlayerState p : players)
                    if (!p.folded && !p.allIn && !p.name.equals(playerName))
                        p.hasActed = false;
                statusMessage = playerName + " raised to " + currentBet + ".";
            }
            case ALLIN -> {
                int allInAmt = cur.chips;
                if (allInAmt <= 0) return false;
                cur.chips = 0;
                cur.currentBet += allInAmt;
                pot += allInAmt;
                if (cur.currentBet > currentBet) {
                    currentBet = cur.currentBet;
                    lastRaiserIndex = players.indexOf(cur);
                    firstCheckerIndex = -1;
                    for (PlayerState p : players)
                        if (!p.folded && !p.allIn && !p.name.equals(playerName))
                            p.hasActed = false;
                }
                cur.allIn = true;
                cur.hasActed = true;
                statusMessage = playerName + " is ALL IN for " + cur.currentBet + "!";
            }
        }

        checkRoundEnd();
        return true;
    }

    private void checkRoundEnd() {
        // Count non-folded players
        List<PlayerState> active = new ArrayList<>();
        for (PlayerState p : players) if (!p.folded) active.add(p);

        if (active.size() <= 1) { endHand(); return; }

        // Players who can still act (not folded, not all-in)
        List<PlayerState> canAct = new ArrayList<>();
        for (PlayerState p : active) if (!p.allIn) canAct.add(p);

        // If nobody can act (all remaining are all-in), deal all 5 and showdown
        if (canAct.isEmpty()) { dealAllAndShowdown(); return; }

        // Betting round done: every actionable player has acted AND matched the current bet
        boolean roundDone = true;
        for (PlayerState p : canAct) {
            if (!p.hasActed || p.currentBet != currentBet) { roundDone = false; break; }
        }
        if (roundDone) { nextPhase(); return; }

        advancePlayer();
    }

    private void advancePlayer() {
        int sz = players.size();
        for (int i = 1; i <= sz; i++) {
            int next = (currentPlayerIndex + i) % sz;
            PlayerState p = players.get(next);
            if (!p.folded && !p.allIn) {
                currentPlayerIndex = next;
                statusMessage = p.name + "'s turn.";
                return;
            }
        }
        // No one can act — move to next phase
        nextPhase();
    }

    private void dealAllAndShowdown() {
        while (communityCards.size() < 5) communityCards.add(deck.deal());
        endHand();
    }

    private void nextPhase() {
        for (PlayerState p : players) { p.currentBet = 0; p.hasActed = false; }
        currentBet = 0;

        // First to act: last raiser if any, else first checker, else first after dealer
        int firstIdx = lastRaiserIndex >= 0 ? lastRaiserIndex
                : firstCheckerIndex >= 0 ? firstCheckerIndex
                : dealerIndex;
        lastRaiserIndex = -1;
        firstCheckerIndex = -1;

        currentPlayerIndex = firstIdx;
        int sz = players.size();
        boolean found = false;
        for (int i = 0; i < sz; i++) {
            int next = (firstIdx + i) % sz;
            if (!players.get(next).folded && !players.get(next).allIn) {
                currentPlayerIndex = next;
                found = true;
                break;
            }
        }
        if (!found) { endHand(); return; }

        switch (phase) {
            case PRE_FLOP -> {
                communityCards.add(deck.deal());
                communityCards.add(deck.deal());
                communityCards.add(deck.deal());
                phase = Phase.FLOP;
                statusMessage = "Flop dealt. " + currentPlayer().name + "'s turn.";
            }
            case FLOP -> {
                communityCards.add(deck.deal());
                phase = Phase.TURN;
                statusMessage = "Turn dealt. " + currentPlayer().name + "'s turn.";
            }
            case TURN -> {
                communityCards.add(deck.deal());
                phase = Phase.RIVER;
                statusMessage = "River dealt. " + currentPlayer().name + "'s turn.";
            }
            case RIVER -> {
                phase = Phase.SHOWDOWN;
                endHand();
            }
            default -> endHand();
        }
    }

    private void endHand() {
        while (communityCards.size() < 5) communityCards.add(deck.deal());

        long activePlayers = players.stream().filter(p -> !p.folded).count();

        if (activePlayers == 1) {
            PlayerState winner = players.stream().filter(p -> !p.folded).findFirst().orElse(null);
            if (winner != null) {
                lastPotWon = pot;
                winner.chips += pot;
                lastWinner = winner.name;
                lastWinningHand = "Last player standing";
                statusMessage = winner.name + " wins " + pot + " ZC! (everyone else folded)";
            }
        } else {
            // Showdown - evaluate hands
            List<Card> board = new ArrayList<>(communityCards);
            PlayerState winner = null;
            HandEvaluator.HandResult bestHand = null;

            for (PlayerState p : players) {
                if (p.folded) continue;
                List<Card> allCards = new ArrayList<>(p.holeCards);
                allCards.addAll(board);
                if (allCards.size() >= 5) {
                    HandEvaluator.HandResult hand = HandEvaluator.evaluate(allCards);
                    if (bestHand == null || hand.compareTo(bestHand) > 0) {
                        bestHand = hand;
                        winner = p;
                    }
                }
            }

            if (winner != null) {
                lastPotWon = pot;
                winner.chips += pot;
                lastWinner = winner.name;
                lastWinningHand = bestHand != null ? bestHand.getDisplayName() : "";
                statusMessage = winner.name + " wins " + pot + " ZC with " + lastWinningHand + "!";
            }
        }

        pot = 0;
        phase = Phase.SHOWDOWN;
        currentPlayerIndex = -1;
        dealerIndex = (dealerIndex + 1) % players.size();
    }

    public void resetToWaiting(net.minecraft.server.MinecraftServer server) {
        if (phase != Phase.SHOWDOWN) return;
        PokerConfig cfg = PokerConfig.get();
        for (PlayerState p : players) {
            int chips = resolveStartChipsForReset(p.name, server);
            p.chips = chips;
            p.holeCards.clear();
            p.currentBet = 0;
            p.folded = false;
            p.allIn = false;
            p.hasActed = false;
        }
        for (String name : pendingPlayers)
            if (players.size() < cfg.maxPlayers)
                players.add(new PlayerState(name, resolveStartChipsForReset(name, server)));
        pendingPlayers.clear();
        phase = Phase.WAITING;
        statusMessage = "Ready for next game.";
    }

    /** Take ZCoin from inventory for new game; 0 = use table default. */
    private int resolveStartChipsForReset(String name, net.minecraft.server.MinecraftServer server) {
        if (server == null) return startingChips;
        var sp = server.getPlayerManager().getPlayer(name);
        if (sp == null) return startingChips;
        int taken = com.pokermc.config.ZCoinStorage.takeAll(sp);
        return taken > 0 ? taken : startingChips;
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private PlayerState currentPlayer() {
        if (currentPlayerIndex < 0 || currentPlayerIndex >= players.size()) return null;
        return players.get(currentPlayerIndex);
    }

    // ── Getters ────────────────────────────────────────────────────────────────

    public Phase getPhase() { return phase; }
    public List<PlayerState> getPlayers() { return players; }
    public List<Card> getCommunityCards() { return communityCards; }
    public int getPot() { return pot; }
    public int getCurrentBet() { return currentBet; }
    public String getCurrentPlayerName() {
        PlayerState p = currentPlayer();
        return p != null ? p.name : "";
    }
    public String getLastWinner() { return lastWinner; }
    public String getLastWinningHand() { return lastWinningHand; }
    public int getLastPotWon() { return lastPotWon; }
    public String getStatusMessage() { return statusMessage; }
    public void setStatusMessage(String msg) { this.statusMessage = msg; }
    public PlayerState getPlayer(String name) {
        return players.stream().filter(p -> p.name.equals(name)).findFirst().orElse(null);
    }
    public void setTurnStartTick(long tick) { this.turnStartTick = tick; }
    public long getTurnStartTick() { return turnStartTick; }
}
