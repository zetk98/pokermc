package com.pokermc.game;

import com.pokermc.config.TradeConfig;
import com.pokermc.config.ZCoinStorage;

import java.util.*;

/**
 * Blackjack (xì dách) — custom rules.
 * Max 21. Player 16–27, Dealer 15–28. Xì lác, xì bàn. Dealer solo phase.
 */
public class BlackjackGame {

    public enum Phase { WAITING, BETTING, PLAYING, DEALER_SOLO, SETTLEMENT }
    public enum Action { HIT, STAND }
    public enum Result { WIN, LOSE, PUSH }

    public static class PlayerState {
        public final String name;
        public int chips;
        public int currentBet = 0;
        public final List<Card> hand = new ArrayList<>();
        public boolean stood = false;
        public boolean busted = false;
        public boolean xilac = false;   // A + 10/J/Q/K
        public boolean xiban = false;  // 2 Aces
        public boolean resolved = false;  // instant resolve (xì lác/xì bàn) or penalty
        public Result result = null;   // WIN, LOSE, PUSH after solo
        public boolean soloDone = false;  // dealer has compared with this player

        public PlayerState(String name, int chips) {
            this.name = name;
            this.chips = chips;
        }

        /** Hand value: 2-10 face, JQK=10, A=1 or 10 or 11. Sum = best value (A as 11, if >21 then 1). */
        public int getHandValue() {
            int total = 0;
            int aces = 0;
            for (Card c : hand) {
                int v = c.getBlackjackValue();
                if (v == 11) aces++;
                total += v;
            }
            while (total > 21 && aces > 0) {
                total -= 10;
                aces--;
            }
            return total;
        }

        public boolean isXilac() {
            if (hand.size() != 2) return false;
            boolean hasAce = hand.stream().anyMatch(c -> c.rank() == Card.Rank.ACE);
            boolean hasTen = hand.stream().anyMatch(c ->
                    c.rank() == Card.Rank.TEN || c.rank() == Card.Rank.JACK
                            || c.rank() == Card.Rank.QUEEN || c.rank() == Card.Rank.KING);
            return hasAce && hasTen;
        }

        public boolean isXiban() {
            return hand.size() == 2 && hand.stream().filter(c -> c.rank() == Card.Rank.ACE).count() == 2;
        }
    }

    private static final int PLAYER_MIN = 16;
    private static final int PLAYER_MAX = 27;
    private static final int DEALER_MIN = 15;
    private static final int DEALER_MAX = 28;

    private final List<PlayerState> players = new ArrayList<>();
    private final List<String> pendingPlayers = new ArrayList<>();
    private final List<Card> dealerHand = new ArrayList<>();
    private final Deck deck = new Deck();
    private Phase phase = Phase.WAITING;
    private int currentPlayerIndex = -1;
    private String dealerName = "";
    private String statusMessage = "Waiting for players...";
    private boolean dealerRevealed = false;
    private int betTimeRemaining = 0;
    private int soloTargetIndex = -1;  // player index dealer is comparing with

    private int maxBet() { return Math.max(1, TradeConfig.get().blackjackMaxBet); }
    public int getMaxBet() { return maxBet(); }
    public int getBetTimeRemaining() { return betTimeRemaining; }

    public boolean tickBetTimer() {
        if (phase != Phase.BETTING || betTimeRemaining <= 0) return false;
        betTimeRemaining--;
        if (betTimeRemaining <= 0) return true;
        return false;
    }

    public boolean addPlayer(String name, int chips) {
        if (players.stream().anyMatch(p -> p.name.equals(name))) return false;
        if (pendingPlayers.contains(name)) return false;
        if (phase != Phase.WAITING && phase != Phase.SETTLEMENT) {
            pendingPlayers.add(name);
            return true;
        }
        if (players.size() >= 8) return false;
        players.add(new PlayerState(name, chips));
        if (dealerName.isEmpty()) dealerName = name;
        statusMessage = name + " joined.";
        return true;
    }

    public boolean removePlayer(String name) {
        pendingPlayers.remove(name);
        int idx = -1;
        for (int i = 0; i < players.size(); i++) {
            if (players.get(i).name.equals(name)) { idx = i; break; }
        }
        if (idx < 0) return false;
        players.remove(idx);
        if (dealerName.equals(name)) {
            dealerName = players.isEmpty() ? "" : players.get(0).name;
        }
        if (currentPlayerIndex >= players.size()) currentPlayerIndex = -1;
        else if (idx <= currentPlayerIndex) currentPlayerIndex = Math.max(-1, currentPlayerIndex - 1);
        return true;
    }

    public boolean hasPlayer(String name) {
        return players.stream().anyMatch(p -> p.name.equals(name)) || pendingPlayers.contains(name);
    }

    public String getDealerName() { return dealerName; }
    public boolean isDealer(String name) { return dealerName.equals(name); }

    public void setBet(String name, int amount) {
        if (phase != Phase.BETTING) return;
        if (name.equals(dealerName)) return;
        PlayerState p = players.stream().filter(ps -> ps.name.equals(name)).findFirst().orElse(null);
        if (p == null) return;
        amount = Math.max(0, Math.min(amount, Math.min(maxBet(), p.chips)));
        p.currentBet = amount;
    }

    public boolean startRound(net.minecraft.server.MinecraftServer server) {
        if (phase != Phase.WAITING && phase != Phase.SETTLEMENT) return false;
        for (String name : new ArrayList<>(pendingPlayers)) {
            if (players.size() >= 8) break;
            if (server != null) {
                var sp = server.getPlayerManager().getPlayer(name);
                if (sp != null) {
                    int chips = ZCoinStorage.takeAll(sp);
                    if (chips > 0) players.add(new PlayerState(name, chips));
                }
            }
            pendingPlayers.remove(name);
        }
        long playerCount = players.stream().filter(p -> !p.name.equals(dealerName)).count();
        if (playerCount < 1) return false;

        deck.reset();
        deck.shuffle();
        dealerHand.clear();
        soloTargetIndex = -1;
        for (PlayerState p : players) {
            p.hand.clear();
            p.stood = false;
            p.busted = false;
            p.xilac = false;
            p.xiban = false;
            p.resolved = false;
            p.result = null;
            p.soloDone = false;
            p.currentBet = 0;
        }
        phase = Phase.BETTING;
        betTimeRemaining = 100;
        statusMessage = "Place bet (max " + maxBet() + " ZC) — 5s";
        return true;
    }

    public boolean confirmBets() {
        if (phase != Phase.BETTING) return false;
        int activeBets = 0;
        for (PlayerState p : players) {
            if (p.name.equals(dealerName)) continue;
            if (p.currentBet > 0) activeBets++;
        }
        if (activeBets < 1) {
            phase = Phase.WAITING;
            statusMessage = "No bets placed. Press Start to try again.";
            return true;
        }

        phase = Phase.PLAYING;
        dealerHand.add(deck.deal());
        dealerHand.add(deck.deal());
        dealerRevealed = false;

        boolean dealerXilac = isDealerXilac();
        boolean dealerXiban = isDealerXiban();

        for (PlayerState p : players) {
            if (p.name.equals(dealerName)) continue;
            if (p.currentBet > 0) {
                p.chips -= p.currentBet;
                p.hand.add(deck.deal());
                p.hand.add(deck.deal());
                if (p.isXiban()) {
                    p.xiban = true;
                    p.resolved = true;
                    if (dealerXiban) {
                        p.result = Result.PUSH;
                        p.chips += p.currentBet;
                    } else {
                        p.chips += takeFromDealer(p.currentBet * 2);
                        p.result = Result.WIN;
                    }
                    p.soloDone = true;
                } else if (p.isXilac()) {
                    p.xilac = true;
                    p.resolved = true;
                    if (dealerXilac) {
                        p.result = Result.PUSH;
                        p.chips += p.currentBet;
                    } else {
                        p.chips += takeFromDealer(p.currentBet * 2);
                        p.result = Result.WIN;
                    }
                    p.soloDone = true;
                }
            }
        }

        currentPlayerIndex = nextActivePlayerIndex(-1);
        if (currentPlayerIndex >= 0) {
            statusMessage = players.get(currentPlayerIndex).name + " — Hit or Stand?";
        } else {
            finishPlayingPhase();
        }
        return true;
    }

    private boolean isDealerXilac() {
        if (dealerHand.size() != 2) return false;
        boolean hasAce = dealerHand.stream().anyMatch(c -> c.rank() == Card.Rank.ACE);
        boolean hasTen = dealerHand.stream().anyMatch(c ->
                c.rank() == Card.Rank.TEN || c.rank() == Card.Rank.JACK
                        || c.rank() == Card.Rank.QUEEN || c.rank() == Card.Rank.KING);
        return hasAce && hasTen;
    }

    private boolean isDealerXiban() {
        return dealerHand.size() == 2 && dealerHand.stream().filter(c -> c.rank() == Card.Rank.ACE).count() == 2;
    }

    private int nextActivePlayerIndex(int from) {
        for (int i = from + 1; i < players.size(); i++) {
            PlayerState p = players.get(i);
            if (p.name.equals(dealerName)) continue;
            if (p.currentBet > 0 && !p.resolved && !p.stood && !p.busted) return i;
        }
        return -1;
    }

    public boolean performAction(String name, Action action) {
        if (phase != Phase.PLAYING) return false;
        if (currentPlayerIndex < 0) return false;
        PlayerState p = players.get(currentPlayerIndex);
        if (!p.name.equals(name)) return false;

        if (action == Action.HIT) {
            p.hand.add(deck.deal());
            int v = p.getHandValue();
            if (v >= 28) {
                p.busted = true;
                applyPlayerPenalty(p);
                return true;
            }
            statusMessage = p.name + " hit.";
        } else {
            p.stood = true;
            int v = p.getHandValue();
            if (v < PLAYER_MIN || v >= 28) {
                applyPlayerPenalty(p);
                return true;
            }
            statusMessage = p.name + " stood.";
        }

        currentPlayerIndex = nextActivePlayerIndex(currentPlayerIndex);
        if (currentPlayerIndex < 0) {
            finishPlayingPhase();
        } else {
            statusMessage = players.get(currentPlayerIndex).name + " — Hit or Stand?";
        }
        return true;
    }

    private void applyPlayerPenalty(PlayerState offender) {
        int bet = offender.currentBet;
        int others = 0;
        for (PlayerState x : players) {
            if (x.name.equals(offender.name)) continue;
            others++;
        }
        offender.chips -= bet * (others - 1);
        for (PlayerState x : players) {
            if (x.name.equals(offender.name)) continue;
            x.chips += bet;
        }
        offender.resolved = true;
        offender.result = Result.LOSE;
        offender.soloDone = true;
        phase = Phase.SETTLEMENT;
        statusMessage = offender.name + " out of range — round over.";
    }

    private void finishPlayingPhase() {
        int dealerVal = getDealerHandValue();
        if (dealerVal < DEALER_MIN || dealerVal >= 29) {
            dealerRevealed = true;
            applyDealerOutOfRangePenalty();
            return;
        }
        boolean allResolved = true;
        for (PlayerState p : players) {
            if (p.name.equals(dealerName)) continue;
            if (p.currentBet > 0 && !p.resolved) {
                if (p.busted) {
                    p.resolved = true;
                    p.result = Result.PUSH;
                    p.soloDone = true;
                    p.chips += p.currentBet;
                } else {
                    allResolved = false;
                }
            }
        }
        if (allResolved) {
            dealerRevealed = true;
            phase = Phase.SETTLEMENT;
            statusMessage = "Round over. Press Start for next round.";
        } else {
            phase = Phase.DEALER_SOLO;
            soloTargetIndex = -1;
            statusMessage = "Dealer — choose Solo or Hit";
        }
    }

    private void applyDealerOutOfRangePenalty() {
        dealerRevealed = true;
        for (PlayerState p : players) {
            if (p.name.equals(dealerName)) continue;
            if (p.currentBet > 0 && !p.resolved) {
                p.chips += p.currentBet;
                p.result = Result.PUSH;
            }
            p.soloDone = true;
        }
        phase = Phase.SETTLEMENT;
        statusMessage = "Dealer out of range — round over.";
    }

    private void applyDealerPenalty() {
        dealerRevealed = true;
        for (PlayerState p : players) {
            if (p.name.equals(dealerName)) continue;
            if (p.currentBet > 0 && !p.busted) {
                p.chips += takeFromDealer(p.currentBet * 2);
                p.result = Result.WIN;
            } else if (p.busted) {
                p.result = Result.PUSH;
                p.chips += p.currentBet;
            }
            p.soloDone = true;
        }
        phase = Phase.SETTLEMENT;
        statusMessage = "Dealer bust — round over.";
    }

    /** Dealer hits during solo phase */
    public boolean dealerHit() {
        if (phase != Phase.DEALER_SOLO) return false;
        dealerHand.add(deck.deal());
        int v = getDealerHandValue();
        if (v >= 29) {
            applyDealerPenalty();
            return true;
        }
        statusMessage = "Dealer hit.";
        return true;
    }

    /** Dealer solos (compares) with player at index. Only when that player has finished (stood or busted). */
    public boolean dealerSolo(int playerIndex) {
        if (phase != Phase.DEALER_SOLO) return false;
        if (playerIndex < 0 || playerIndex >= players.size()) return false;
        PlayerState p = players.get(playerIndex);
        if (p.name.equals(dealerName)) return false;
        if (p.currentBet <= 0 || p.soloDone) return false;
        if (!p.stood && !p.busted) return false;  // Player must have finished drawing
        int dealerVal = getDealerHandValue();
        if (dealerVal < DEALER_MIN) return false;

        int playerVal = p.getHandValue();
        if (p.busted) {
            p.result = Result.PUSH;
            p.chips += p.currentBet;
        } else if (dealerVal > playerVal) {
            p.result = Result.LOSE;
            giveToDealer(p.currentBet);
        } else if (dealerVal < playerVal) {
            p.chips += takeFromDealer(p.currentBet * 2);
            p.result = Result.WIN;
        } else {
            p.result = Result.PUSH;
            p.chips += p.currentBet;
        }
        p.soloDone = true;
        soloTargetIndex = playerIndex;
        dealerRevealed = true;

        boolean allDone = true;
        for (PlayerState x : players) {
            if (x.name.equals(dealerName)) continue;
            if (x.currentBet > 0 && !x.soloDone) allDone = false;
        }
        if (allDone) {
            phase = Phase.SETTLEMENT;
            statusMessage = "Round over. Press Start for next round.";
        } else {
            statusMessage = "Dealer — choose Solo or Hit";
        }
        return true;
    }

    /** Dealer solos all players at once. If dealer < 15 or >= 29, dealer busts and pays everyone. */
    public boolean dealerSoloAll() {
        if (phase != Phase.DEALER_SOLO) return false;
        int dealerVal = getDealerHandValue();
        if (dealerVal < DEALER_MIN || dealerVal >= 29) {
            applyDealerPenalty();
            statusMessage = "Dealer out of range — pays everyone.";
            return true;
        }
        for (PlayerState p : players) {
            if (p.name.equals(dealerName) || p.currentBet <= 0 || p.soloDone) continue;
            int playerVal = p.getHandValue();
            if (p.busted) {
                p.result = Result.PUSH;
                p.chips += p.currentBet;
            } else if (dealerVal > playerVal) {
                p.result = Result.LOSE;
                giveToDealer(p.currentBet);
            } else if (dealerVal < playerVal) {
                p.chips += takeFromDealer(p.currentBet * 2);
                p.result = Result.WIN;
            } else {
                p.result = Result.PUSH;
                p.chips += p.currentBet;
            }
            p.soloDone = true;
        }
        dealerRevealed = true;
        phase = Phase.SETTLEMENT;
        statusMessage = "Round over. Press Start for next round.";
        return true;
    }

    private int getDealerHandValue() {
        int total = 0;
        int aces = 0;
        for (Card c : dealerHand) {
            int v = c.getBlackjackValue();
            if (v == 11) aces++;
            total += v;
        }
        while (total > 21 && aces > 0) {
            total -= 10;
            aces--;
        }
        return total;
    }

    public List<PlayerState> getPlayers() { return players; }
    public List<String> getPendingPlayers() { return pendingPlayers; }
    public List<Card> getDealerHand() { return dealerHand; }
    public Phase getPhase() { return phase; }
    public int getCurrentPlayerIndex() { return currentPlayerIndex; }
    public String getCurrentPlayerName() {
        return currentPlayerIndex >= 0 && currentPlayerIndex < players.size()
                ? players.get(currentPlayerIndex).name : "";
    }
    public String getStatusMessage() { return statusMessage; }
    public void setStatusMessage(String s) { statusMessage = s; }
    public boolean isDealerRevealed() { return dealerRevealed; }
    public int getSoloTargetIndex() { return soloTargetIndex; }

    private void giveToDealer(int amount) {
        for (PlayerState x : players) {
            if (x.name.equals(dealerName)) { x.chips += amount; return; }
        }
    }

    /** Chips từ người chơi văng game → đẩy vào dealer (người khác có thể ăn). */
    public void addDisconnectChipsToDealer(int amount) {
        if (amount > 0) giveToDealer(amount);
    }

    private int takeFromDealer(int amount) {
        for (PlayerState x : players) {
            if (x.name.equals(dealerName)) {
                int taken = Math.min(amount, x.chips);
                x.chips -= taken;
                return taken;
            }
        }
        return 0;
    }

    /** Reset to empty room when everyone has left. */
    public void resetWhenEmpty() {
        if (!players.isEmpty() || !pendingPlayers.isEmpty()) return;
        players.clear();
        pendingPlayers.clear();
        dealerHand.clear();
        deck.reset();
        phase = Phase.WAITING;
        currentPlayerIndex = -1;
        dealerName = "";
        dealerRevealed = false;
        betTimeRemaining = 0;
        soloTargetIndex = -1;
        statusMessage = "Waiting for players...";
    }

    /** Kick players with chips < minZcToJoin before new round. Returns kicked names. */
    public List<String> kickPlayersBelowMin(net.minecraft.server.MinecraftServer server) {
        int min = TradeConfig.get().minZcToJoin;
        List<String> kicked = new ArrayList<>();
        for (int i = players.size() - 1; i >= 0; i--) {
            PlayerState p = players.get(i);
            if (p.chips < min) {
                kicked.add(p.name);
                if (server != null) {
                    var sp = server.getPlayerManager().getPlayer(p.name);
                    if (sp != null) {
                        ZCoinStorage.giveBack(sp, p.chips);
                        sp.sendMessage(net.minecraft.text.Text.literal("§d[Blackjack] §fBị kick - cần tối thiểu " + min + " ZC để chơi."), true);
                    } else {
                        addDisconnectChipsToDealer(p.chips);
                    }
                } else {
                    addDisconnectChipsToDealer(p.chips);
                }
                players.remove(i);
            }
        }
        if (!kicked.isEmpty()) statusMessage = String.join(", ", kicked) + " bị kick (< " + min + " ZC).";
        if (kicked.contains(dealerName)) dealerName = players.isEmpty() ? "" : players.get(0).name;
        return kicked;
    }
}
