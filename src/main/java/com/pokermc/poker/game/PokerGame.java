package com.pokermc.poker.game;

import com.pokermc.common.config.PokerConfig;
import com.pokermc.common.config.ZCoinStorage;
import com.pokermc.common.game.Card;
import com.pokermc.common.game.Deck;

import java.util.*;
import java.util.Collections;

/**
 * Server-side Texas Hold'em game logic.
 * Stored inside the PokerTableBlockEntity.
 */
public class PokerGame {

    public enum Phase { WAITING, DEALING, PRE_FLOP, FLOP, TURN, RIVER, SHOWDOWN }
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

    /** DEALING phase: deal one card per tick. dealCardIndex 0..2*n-1. */
    private int dealCardIndex = 0;
    public static final int DEAL_TICKS_PER_CARD = 12;

    // Blinds from config - no room level, free bet
    private int smallBlind() { return Math.max(1, PokerConfig.get().smallBlindAmount); }
    private int bigBlind()   { return Math.max(1, PokerConfig.get().bigBlindAmount); }
    private int minRaise()   { return Math.max(1, PokerConfig.get().minRaiseAmount); }
    public int getBetLevel() { return bigBlind(); }

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

        var cfg = PokerConfig.get();
        if (players.size() >= cfg.maxPlayers) return false;
        if (customChips <= 0) return false;
        players.add(new PlayerState(name, customChips));
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

    /** Reset to empty room when everyone has left. Pot is lost. */
    public void resetWhenEmpty() {
        if (!players.isEmpty() || !pendingPlayers.isEmpty()) return;
        players.clear();
        pendingPlayers.clear();
        communityCards.clear();
        deck.reset();
        phase = Phase.WAITING;
        pot = 0;
        currentBet = 0;
        dealerIndex = 0;
        currentPlayerIndex = -1;
        lastRaiserIndex = -1;
        firstCheckerIndex = -1;
        lastWinner = "";
        lastWinningHand = "";
        lastPotWon = 0;
        statusMessage = "Waiting for players...";
        turnStartTick = 0;
    }

    /** Add chips directly to a player already seated (e.g. from deposit). */
    public boolean addChips(String name, int amount) {
        PlayerState p = players.stream().filter(ps -> ps.name.equals(name)).findFirst().orElse(null);
        if (p == null) return false;
        p.chips += amount;
        statusMessage = name + " deposited " + amount + " ZC.";
        return true;
    }

    // ── Game start ─────────────────────────────────────────────────────────────

    /** Kick players with chips < minZcToJoin before new game. Returns kicked names. */
    public List<String> kickPlayersBelowMin(net.minecraft.server.MinecraftServer server) {
        int min = PokerConfig.get().minZcToJoin;
        List<String> kicked = new ArrayList<>();
        for (int i = players.size() - 1; i >= 0; i--) {
            PlayerState p = players.get(i);
            if (p.chips < min) {
                kicked.add(p.name);
                if (server != null) {
                    var sp = server.getPlayerManager().getPlayer(p.name);
                    if (sp != null) {
                        ZCoinStorage.giveBack(sp, p.chips);
                        sp.sendMessage(net.minecraft.text.Text.literal("§e[Poker] §fBị kick - cần tối thiểu " + min + " ZC để chơi."), true);
                    } else {
                        addToPot(p.chips);
                    }
                } else {
                    addToPot(p.chips);
                }
                players.remove(i);
            }
        }
        if (!kicked.isEmpty()) statusMessage = String.join(", ", kicked) + " bị kick (< " + min + " ZC).";
        return kicked;
    }

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

        dealCardIndex = 0;
        dealerIndex = 0; // Ván đầu: dealer = chủ phòng (room owner)
        phase = Phase.DEALING;
        statusMessage = "Dealing cards...";
        return true;
    }

    /**
     * Deal one card at a time. Dealer nhận bài đầu tiên, rồi theo chiều kim đồng hồ.
     * Called from BlockEntity tick when phase == DEALING.
     * @return true if a card was dealt, false if dealing is complete
     */
    public boolean dealOneCard() {
        if (phase != Phase.DEALING || players.isEmpty()) return false;
        int n = players.size();
        int totalCards = 2 * n;
        if (dealCardIndex >= totalCards) {
            finishDealing();
            return false;
        }
        // Dealer nhận bài đầu tiên, rồi (dealer+1), (dealer+2)... theo chiều kim đồng hồ
        int targetIdx = (dealerIndex + (dealCardIndex % n)) % n;
        players.get(targetIdx).holeCards.add(deck.deal());
        dealCardIndex++;
        if (dealCardIndex >= totalCards) {
            finishDealing();
        } else {
            statusMessage = "Dealing cards...";
        }
        return true;
    }

    private void finishDealing() {
        phase = Phase.PRE_FLOP;

        int sbIdx = (dealerIndex + 1) % players.size();
        int bbIdx = (dealerIndex + 2) % players.size();
        postBlind(sbIdx, smallBlind());
        postBlind(bbIdx, bigBlind());
        currentBet = bigBlind();

        // UTG acts first pre-flop
        currentPlayerIndex = (dealerIndex + 3) % players.size();
        // Handle heads-up (2 players): dealer is SB, other is BB; dealer acts first
        if (players.size() == 2) {
            currentPlayerIndex = dealerIndex;
        }

        statusMessage = "Pre-flop. " + currentPlayer().name + "'s turn.";
    }

    public int getDealCardIndex() { return dealCardIndex; }

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
                if (players.stream().anyMatch(p -> p.allIn)) return false; // All-in: no raise
                if (amount < minRaise()) return false;
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
                if (players.stream().anyMatch(p -> p.allIn)) return false; // All-in: no all-in
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
                dealerIndex = players.indexOf(winner); // Ván sau: dealer = người thắng, sang phải
            }
        } else {
            // Showdown - evaluate hands, handle ties (split pot evenly)
            List<Card> board = new ArrayList<>(communityCards);
            List<PlayerState> winners = new ArrayList<>();
            HandEvaluator.HandResult bestHand = null;

            for (PlayerState p : players) {
                if (p.folded) continue;
                List<Card> allCards = new ArrayList<>(p.holeCards);
                allCards.addAll(board);
                if (allCards.size() >= 5) {
                    HandEvaluator.HandResult hand = HandEvaluator.evaluate(allCards);
                    if (bestHand == null || hand.compareTo(bestHand) > 0) {
                        bestHand = hand;
                        winners.clear();
                        winners.add(p);
                    } else if (hand.compareTo(bestHand) == 0) {
                        winners.add(p);
                    }
                }
            }

            if (!winners.isEmpty()) {
                lastPotWon = pot;
                int each = pot / winners.size();
                int remainder = pot % winners.size();
                for (int i = 0; i < winners.size(); i++) {
                    winners.get(i).chips += each + (i < remainder ? 1 : 0);
                }
                lastWinner = String.join(" & ", winners.stream().map(p -> p.name).toList());
                lastWinningHand = bestHand != null ? bestHand.getDisplayName() : "";
                statusMessage = lastWinner + (winners.size() > 1 ? " split " : " wins ") + pot + " ZC with " + lastWinningHand + "!";
                // Huề: random 1 người trong số người thắng làm dealer
                PlayerState nextDealer = winners.size() == 1 ? winners.get(0)
                        : winners.get(new Random().nextInt(winners.size()));
                dealerIndex = players.indexOf(nextDealer);
            }
        }

        pot = 0;
        phase = Phase.SHOWDOWN;
        currentPlayerIndex = -1;
    }

    public void resetToWaiting(net.minecraft.server.MinecraftServer server) {
        if (phase != Phase.SHOWDOWN) return;
        var cfg = PokerConfig.get();
        for (PlayerState p : players) {
            p.holeCards.clear();
            p.currentBet = 0;
            p.folded = false;
            p.allIn = false;
            p.hasActed = false;
        }
        for (String name : pendingPlayers) {
            if (players.size() >= cfg.maxPlayers) break;
            if (server == null) continue;
            var sp = server.getPlayerManager().getPlayer(name);
            if (sp == null) continue;
            int chips = ZCoinStorage.takeAll(sp);
            if (chips > 0) players.add(new PlayerState(name, chips));
        }
        pendingPlayers.clear();
        phase = Phase.WAITING;
        statusMessage = "Ready for next game.";
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

    /** Add chips to pot (e.g. when player leaves mid-game and can't receive chips back). */
    public void addToPot(int amount) {
        if (amount > 0) pot += amount;
    }
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
    public int getDealerIndex() { return dealerIndex; }
    public void setTurnStartTick(long tick) { this.turnStartTick = tick; }
    public long getTurnStartTick() { return turnStartTick; }
}
