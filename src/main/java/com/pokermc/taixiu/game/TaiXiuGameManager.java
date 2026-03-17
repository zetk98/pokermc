package com.pokermc.taixiu.game;

import com.pokermc.PokerMod;
import com.pokermc.common.config.ZCoinStorage;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.*;

/**
 * Global Tai Xiu game manager - server-wide, all blocks share the same game.
 * The game runs continuously regardless of whether players are watching.
 * Uses ServerTickEvents for global server-wide timing.
 */
public class TaiXiuGameManager {

    private static TaiXiuGameManager INSTANCE;

    /** 20 seconds per full cycle: 10s betting + 10s rolling+result (400 ticks at 20 TPS) */
    private static final long TICKS_PER_CYCLE = 400L;
    private static final int BETTING_TICKS = 200; // 10 seconds for betting
    private static final int ROLLING_TICKS = 60; // 3 seconds for rolling animation
    private static final int RESULT_TICKS = 140; // 7 seconds for showing result (total 10s for rolling+result)

    /** Maximum history size */
    private static final int MAX_HISTORY = 50;

    /** Maximum player bet history per player */
    private static final int MAX_PLAYER_BET_HISTORY = 20;

    /** Game states */
    public enum GameState {
        BETTING,    // Players can place bets
        ROLLING,    // Dice animation, no bets
        RESULT      // Showing result, settling bets
    }

    /** Bet types */
    public enum BetType {
        TAI("Big", "11-17", 1, 1),
        XIU("Small", "4-10", 1, 1),
        ODD("Odd", "5,7,9,11,13,15,17", 1, 1),
        EVEN("Even", "4,6,8,10,12,14,16", 1, 1),
        PAIR("Pair", "Any pair", 8, 1),
        TRIPLE("Triple", "Three of a kind", 150, 1);

        private final String displayName;
        private final String description;
        private final int payoutMultiplier;
        private final int baseBet;

        BetType(String displayName, String description, int payoutMultiplier, int baseBet) {
            this.displayName = displayName;
            this.description = description;
            this.payoutMultiplier = payoutMultiplier;
            this.baseBet = baseBet;
        }

        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
        public int getPayoutMultiplier() { return payoutMultiplier; }
    }

    /** Number bet (1-6) */
    public static class NumberBet {
        public final int number; // 1-6
        public final int amount;

        public NumberBet(int number, int amount) {
            this.number = number;
            this.amount = amount;
        }
    }

    /** Player bet */
    public static class Bet {
        public final java.util.UUID playerId;
        public final String playerName;
        public final BetType betType;
        public final int amount;
        public final NumberBet numberBet; // For single number bets
        public final String betDisplay; // "50 ZC for TAI"

        public Bet(ServerPlayerEntity player, BetType betType, int amount) {
            this.playerId = player.getUuid();
            this.playerName = player.getName().getString();
            this.betType = betType;
            this.amount = amount;
            this.numberBet = null;
            this.betDisplay = amount + " ZC for " + betType.getDisplayName();
        }

        public Bet(ServerPlayerEntity player, int number, int amount) {
            this.playerId = player.getUuid();
            this.playerName = player.getName().getString();
            this.betType = null;
            this.amount = amount;
            this.numberBet = new NumberBet(number, amount);
            this.betDisplay = amount + " ZC for " + number;
        }
    }

    /** Player bet result - tracks win/loss for player history */
    public static class PlayerBetResult {
        public final String betDisplay;
        public final int amount;
        public final boolean won;
        public final int winAmount;
        public final long timestamp;

        public PlayerBetResult(String betDisplay, int amount, boolean won, int winAmount) {
            this.betDisplay = betDisplay;
            this.amount = amount;
            this.won = won;
            this.winAmount = winAmount;
            this.timestamp = System.currentTimeMillis();
        }
    }

    /** Dice roll result */
    public static class DiceResult {
        public final int die1, die2, die3;
        public final int total;
        public final boolean isTriple;
        public final long timestamp;
        public final long worldTime;

        public DiceResult(int d1, int d2, int d3, long time, long worldTime) {
            this.die1 = d1;
            this.die2 = d2;
            this.die3 = d3;
            this.total = d1 + d2 + d3;
            this.isTriple = (d1 == d2 && d2 == d3);
            this.timestamp = time;
            this.worldTime = worldTime;
        }

        public boolean isTai() { return total >= 11 && total <= 17 && !isTriple; }
        public boolean isXiu() { return total >= 4 && total <= 10 && !isTriple; }
        public boolean isOdd() { return total % 2 == 1; }
        public boolean isEven() { return total % 2 == 0; }
        public boolean isPair() { return die1 == die2 || die2 == die3 || die1 == die3; }

        public int countNumber(int num) {
            int count = 0;
            if (die1 == num) count++;
            if (die2 == num) count++;
            if (die3 == num) count++;
            return count;
        }

        public String getResultString() {
            StringBuilder sb = new StringBuilder();
            if (isTriple) {
                sb.append("TRIPLE ").append(die1);
            } else {
                // TAI/XIU
                if (isTai()) sb.append("TAI");
                else sb.append("XIU");
                // ODD/EVEN
                sb.append(",").append(isOdd() ? "ODD" : "EVEN");
                // PAIR (if applicable, but not triple)
                if (isPair()) sb.append(",PAIR");
            }
            return sb.toString();
        }
    }

    // Game state (global, server-wide)
    private GameState state = GameState.BETTING;
    private long cycleStartTick = 0;
    private long currentWorldTime = 0;
    private long internalTickCount = 0; // Internal tick counter for reliable timing
    private Random random = new Random();

    // Current dice values (during rolling animation)
    private int animDie1 = 1, animDie2 = 1, animDie3 = 1;
    private DiceResult lastResult = null;

    // Active bets for current round
    private final List<Bet> activeBets = new ArrayList<>();

    // History
    private final List<DiceResult> history = new ArrayList<>();

    // Player bet history - tracks win/loss for each player
    private final Map<java.util.UUID, List<PlayerBetResult>> playerBetHistory = new java.util.HashMap<>();

    private TaiXiuGameManager() {
        // Initialize with a dummy result
        lastResult = new DiceResult(1, 1, 1, System.currentTimeMillis(), 0);
    }

    public static TaiXiuGameManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new TaiXiuGameManager();
        }
        return INSTANCE;
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(TaiXiuGameManager::serverTick);
    }

    /**
     * Global server tick - runs continuously every server tick.
     * This ensures the game cycle runs regardless of players.
     */
    private static void serverTick(MinecraftServer server) {
        try {
            getInstance().tick(server);
        } catch (Exception e) {
            System.err.println("[Tai Xiu] Server tick error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Main tick method.
     * @return true if state changed
     */
    public boolean tick(MinecraftServer server) {
        // Increment internal tick counter (reliable timing)
        internalTickCount++;

        // Debug: Log every 100 ticks to verify it's running
        if (internalTickCount % 100 == 0) {
            System.out.println("[Tai Xiu] Game tick at internal count " + internalTickCount + ", state: " + state);
        }

        // Use overworld time for consistency (for display purposes)
        long worldTime = 0;
        var overworld = server.getWorld(net.minecraft.world.World.OVERWORLD);
        if (overworld != null) {
            worldTime = overworld.getTime();
        } else {
            // Fallback to first world
            for (var w : server.getWorlds()) {
                worldTime = w.getTime();
                break;
            }
        }
        this.currentWorldTime = worldTime;

        // Calculate position in current cycle using INTERNAL tick counter (reliable)
        long cyclePosition = internalTickCount % TICKS_PER_CYCLE;

        GameState newState = GameState.BETTING;
        if (cyclePosition < BETTING_TICKS) {
            newState = GameState.BETTING;
        } else if (cyclePosition < BETTING_TICKS + ROLLING_TICKS) {
            newState = GameState.ROLLING;
        } else {
            newState = GameState.RESULT;
        }

        // State transition handling
        if (newState != state) {
            state = newState;
            if (state == GameState.ROLLING) {
                // Starting rolling phase - finalize bets
                cycleStartTick = worldTime - BETTING_TICKS;
            } else if (state == GameState.RESULT) {
                // Rolling complete - generate result
                generateResult(worldTime);
                // Settle bets and pay winners
                settleBets(lastResult, server);
                // Broadcast result to all players
                broadcastResult(lastResult, server);
                // Prepare for next round (bets cleared immediately after this)
                activeBets.clear();
            }
            return true; // State changed
        }

        // Update dice animation based on state
        if (state == GameState.ROLLING) {
            // Fast spinning during rolling
            if (internalTickCount % 2 == 0) {
                animDie1 = random.nextInt(6) + 1;
                animDie2 = random.nextInt(6) + 1;
                animDie3 = random.nextInt(6) + 1;
            }
        } else if (state == GameState.BETTING) {
            // Slower animation during betting
            if (internalTickCount % 20 == 0) {
                animDie1 = random.nextInt(6) + 1;
                animDie2 = random.nextInt(6) + 1;
                animDie3 = random.nextInt(6) + 1;
            }
        }

        return false; // No state change
    }

    /**
     * Generate the dice result for this round.
     */
    private void generateResult(long worldTime) {
        int d1 = random.nextInt(6) + 1;
        int d2 = random.nextInt(6) + 1;
        int d3 = random.nextInt(6) + 1;
        lastResult = new DiceResult(d1, d2, d3, System.currentTimeMillis(), worldTime);

        // Add to history
        history.add(lastResult);
        if (history.size() > MAX_HISTORY) {
            history.remove(0);
        }
    }

    /**
     * Settle all bets based on the dice result.
     */
    private void settleBets(DiceResult result, MinecraftServer server) {
        for (Bet bet : activeBets) {
            int winAmount = 0;
            boolean won = false;

            if (bet.numberBet != null) {
                // Number bet: count occurrences
                int count = result.countNumber(bet.numberBet.number);
                if (count > 0) {
                    winAmount = bet.amount * count; // 1:1 per die
                    won = true;
                }
            } else {
                // Regular bets
                won = switch (bet.betType) {
                    case TAI -> result.isTai();
                    case XIU -> result.isXiu();
                    case ODD -> result.isOdd();
                    case EVEN -> result.isEven();
                    case PAIR -> result.isPair();
                    case TRIPLE -> result.isTriple;
                    default -> false;
                };

                if (won) {
                    winAmount = bet.amount * bet.betType.getPayoutMultiplier();
                }
            }

            // Record bet result to player history
            addPlayerBetResult(bet.playerId, bet.betDisplay, bet.amount, won, winAmount);

            // Find player if online
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(bet.playerId);
            if (player != null) {
                if (won) {
                    ZCoinStorage.add(player, winAmount);
                    player.sendMessage(Text.literal("§a[Tai Xiu] You won " + winAmount + " ZC! (" + bet.betDisplay + ")").formatted(Formatting.GREEN), false);
                } else {
                    player.sendMessage(Text.literal("§c[Tai Xiu] You lost " + bet.amount + " ZC. (" + bet.betDisplay + ")").formatted(Formatting.RED), false);
                }
            }
        }
    }

    /**
     * Add a bet result to player's history.
     */
    private void addPlayerBetResult(java.util.UUID playerId, String betDisplay, int amount, boolean won, int winAmount) {
        playerBetHistory.computeIfAbsent(playerId, k -> new ArrayList<>());
        List<PlayerBetResult> history = playerBetHistory.get(playerId);
        history.add(new PlayerBetResult(betDisplay, amount, won, winAmount));
        // Keep only the last MAX_PLAYER_BET_HISTORY results
        if (history.size() > MAX_PLAYER_BET_HISTORY) {
            history.remove(0);
        }
    }

    /**
     * Broadcast the dice result to all online players on the server.
     */
    private void broadcastResult(DiceResult result, MinecraftServer server) {
        String resultMsg = "§6[Tai Xiu] §fResult: " + result.die1 + "-" + result.die2 + "-" + result.die3 +
                          " (Total: " + result.total + ") - " + result.getResultString();

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            player.sendMessage(Text.literal(resultMsg), false);
        }
    }

    /**
     * Place a bet.
     * @return Result message
     */
    public String placeBet(ServerPlayerEntity player, BetType betType, int amount) {
        if (state != GameState.BETTING) {
            return "Betting is closed!";
        }

        if (amount <= 0) {
            return "Invalid bet amount!";
        }

        if (ZCoinStorage.getBalance(player) < amount) {
            return "Insufficient ZC! Need " + amount + " ZC.";
        }

        if (!ZCoinStorage.deduct(player, amount)) {
            return "Failed to deduct ZCoin!";
        }

        activeBets.add(new Bet(player, betType, amount));

        return "Bet " + amount + " ZC on " + betType.getDisplayName() + "!";
    }

    /**
     * Place a number bet (1-6).
     * @return Result message
     */
    public String placeNumberBet(ServerPlayerEntity player, int number, int amount) {
        if (number < 1 || number > 6) {
            return "Invalid number! Choose 1-6.";
        }

        if (state != GameState.BETTING) {
            return "Betting is closed!";
        }

        if (amount <= 0) {
            return "Invalid bet amount!";
        }

        if (ZCoinStorage.getBalance(player) < amount) {
            return "Insufficient ZC! Need " + amount + " ZC.";
        }

        if (!ZCoinStorage.deduct(player, amount)) {
            return "Failed to deduct ZCoin!";
        }

        activeBets.add(new Bet(player, number, amount));

        return "Bet " + amount + " ZC on number " + number + "!";
    }

    /**
     * Get formatted bet string for a player (e.g., "50 ZC for TAI")
     */
    public String getPlayerBetString(java.util.UUID playerId) {
        StringBuilder sb = new StringBuilder();
        int totalBet = 0;
        for (Bet bet : activeBets) {
            if (bet.playerId.equals(playerId)) {
                if (!sb.isEmpty()) sb.append(", ");
                sb.append(bet.betDisplay);
                totalBet += bet.amount;
            }
        }
        return sb.isEmpty() ? "" : sb.toString() + " (Total: " + totalBet + " ZC)";
    }

    /**
     * Get total bet amount for a player.
     */
    public int getPlayerBetTotal(java.util.UUID playerId) {
        int total = 0;
        for (Bet bet : activeBets) {
            if (bet.playerId.equals(playerId)) {
                total += bet.amount;
            }
        }
        return total;
    }

    // ===== Getters =====

    public GameState getState() { return state; }
    public long getCurrentWorldTime() { return currentWorldTime; }
    public long getInternalTickCount() { return internalTickCount; }

    public int getAnimDie1() { return animDie1; }
    public int getAnimDie2() { return animDie2; }
    public int getAnimDie3() { return animDie3; }

    public DiceResult getLastResult() { return lastResult; }
    public List<DiceResult> getHistory() { return new ArrayList<>(history); }

    /**
     * Get seconds until roll (during betting phase).
     */
    public int getSecondsUntilRoll() {
        if (state != GameState.BETTING) return 0;
        long cyclePosition = internalTickCount % TICKS_PER_CYCLE;
        long remaining = BETTING_TICKS - cyclePosition;
        return (int) Math.max(0, remaining / 20);
    }

    /**
     * Get seconds until next betting phase (during result phase).
     */
    public int getSecondsUntilNextBet() {
        if (state != GameState.RESULT) return 0;
        long cyclePosition = internalTickCount % TICKS_PER_CYCLE;
        long remaining = TICKS_PER_CYCLE - cyclePosition;
        return (int) Math.max(0, remaining / 20);
    }

    /**
     * Get state for serialization - includes all player bets
     */
    public List<Bet> getAllActiveBets() {
        return new ArrayList<>(activeBets);
    }

    /**
     * Get bets for a specific player
     */
    public List<Bet> getPlayerBets(java.util.UUID playerId) {
        List<Bet> playerBets = new ArrayList<>();
        for (Bet bet : activeBets) {
            if (bet.playerId.equals(playerId)) {
                playerBets.add(bet);
            }
        }
        return playerBets;
    }

    /**
     * Get bet history for a specific player (win/loss records)
     */
    public List<PlayerBetResult> getPlayerBetHistory(java.util.UUID playerId) {
        List<PlayerBetResult> history = playerBetHistory.get(playerId);
        return history != null ? new ArrayList<>(history) : new ArrayList<>();
    }
}
