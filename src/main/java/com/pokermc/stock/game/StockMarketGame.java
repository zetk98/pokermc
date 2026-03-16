package com.pokermc.stock.game;

import com.pokermc.PokerMod;
import com.pokermc.common.config.ZCoinStorage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;

import java.util.*;

/**
 * Core game logic for the Minecraft Stock Exchange.
 * Handles price updates, buy/sell orders, and player portfolios.
 */
public class StockMarketGame {

    /** Update prices every 5 seconds (100 ticks at 20 TPS) for real-time trading */
    private static final long TICKS_PER_UPDATE = 100L;
    private static final int PRICE_HISTORY_SIZE = 10; // Keep 10 hours of history (10 bars)

    /** Current price for each stock */
    private final Map<StockType, StockPrice> currentPrices = new HashMap<>();

    /** Price history for each stock (hourly) */
    private final Map<StockType, List<Integer>> priceHistory = new HashMap<>();

    /** Player portfolios: player UUID -> list of holdings */
    private final Map<java.util.UUID, List<StockHolding>> portfolios = new HashMap<>();

    /** Pending orders: player UUID -> list of orders */
    private final Map<java.util.UUID, List<StockOrder>> pendingOrders = new HashMap<>();

    /** Market status message */
    private String statusMessage = "Market Open - Trading Active";
    private long lastUpdateTick = 0;

    /** Current market event (affects all stocks) */
    private MarketEvent currentEvent = MarketEvent.NONE;

    public StockMarketGame() {
        initializePrices();
        initializeHistory();
    }

    /**
     * Initialize all stock prices to base values.
     */
    private void initializePrices() {
        for (StockType type : StockType.values()) {
            currentPrices.put(type, new StockPrice(type.getBasePrice(), 0));
        }
    }

    /**
     * Initialize empty price history.
     */
    private void initializeHistory() {
        for (StockType type : StockType.values()) {
            List<Integer> history = new ArrayList<>();
            history.add(type.getBasePrice());
            priceHistory.put(type, history);
        }
    }

    /**
     * Main tick method - updates prices every 10 minutes.
     * @return true if prices changed
     */
    public boolean tick(long worldTime, MinecraftServer server, ServerWorld world) {
        if (worldTime - lastUpdateTick < TICKS_PER_UPDATE) {
            return false;
        }

        lastUpdateTick = worldTime;
        updatePrices(worldTime);
        processPendingOrders(server);
        generateNewEvent();

        return true;
    }

    /**
     * Update all stock prices based on their tier volatility.
     */
    private void updatePrices(long worldTime) {
        for (StockType type : StockType.values()) {
            StockPrice current = currentPrices.get(type);

            // Calculate new price
            int newPrice = type.getTier().calculatePriceChange(current.price, worldTime + type.ordinal());

            // Apply market event modifier
            newPrice = applyEventModifier(newPrice, type);

            int change = newPrice - current.price;
            int changePercent = current.price > 0 ? (change * 100 / current.price) : 0;

            currentPrices.put(type, new StockPrice(newPrice, changePercent));

            // Update history (keep last 24 entries)
            List<Integer> history = priceHistory.get(type);
            history.add(newPrice);
            if (history.size() > PRICE_HISTORY_SIZE) {
                history.remove(0);
            }
        }

        updateStatusMessage();
    }

    /**
     * Apply current market event modifier to price.
     */
    private int applyEventModifier(int price, StockType type) {
        return switch (currentEvent) {
            case BULL_MARKET -> (int)(price * 1.05f); // +5% to all
            case BEAR_MARKET -> (int)(price * 0.95f); // -5% to all
            case TECH_BOOM -> type.getTier() == StockType.Tier.MEDIUM_RISK ? (int)(price * 1.10f) : price;
            case RESOURCE_CRASH -> type.getTier() == StockType.Tier.LOW_RISK ? (int)(price * 0.90f) : price;
            case NETHER_CRISIS -> type == StockType.NETH || type == StockType.NETR ? (int)(price * 0.80f) : price;
            case END_DISCOVERY -> type == StockType.ENDR ? (int)(price * 1.15f) : price;
            default -> price;
        };
    }

    /**
     * Process all pending orders and execute matching orders.
     */
    private void processPendingOrders(MinecraftServer server) {
        List<StockOrder> toExecute = new ArrayList<>();

        for (Map.Entry<java.util.UUID, List<StockOrder>> entry : pendingOrders.entrySet()) {
            List<StockOrder> playerOrders = entry.getValue();
            Iterator<StockOrder> iter = playerOrders.iterator();

            while (iter.hasNext()) {
                StockOrder order = iter.next();

                // Check expiry (7 days = 100800 ticks)
                if (server.getOverworld().getTime() - order.createdAt > 100800) {
                    iter.remove();
                    continue;
                }

                // Check if order should execute
                StockPrice currentPrice = currentPrices.get(order.stockType);
                boolean shouldExecute = switch (order.orderType) {
                    case MARKET -> true; // Market orders always execute immediately
                    case LIMIT_BUY -> currentPrice.price <= order.limitPrice;
                    case LIMIT_SELL -> currentPrice.price >= order.limitPrice;
                    case STOP_LOSS -> currentPrice.price <= order.stopPrice;
                };

                if (shouldExecute) {
                    toExecute.add(order);
                    iter.remove();
                }
            }

            // Clean up empty lists
            if (playerOrders.isEmpty()) {
                pendingOrders.remove(entry.getKey());
            }
        }

        // Execute matched orders
        for (StockOrder order : toExecute) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(order.playerId);
            if (player != null) {
                executeOrder(player, order);
            }
        }
    }

    /**
     * Generate a new random market event.
     */
    private void generateNewEvent() {
        Random rand = new Random();
        int roll = rand.nextInt(100);

        // 10% chance of a market event
        if (roll < 10) {
            MarketEvent[] events = MarketEvent.values();
            currentEvent = events[rand.nextInt(events.length)];
            statusMessage = "EVENT: " + currentEvent.getDisplayName();
        } else {
            currentEvent = MarketEvent.NONE;
        }
    }

    /**
     * Buy stock at market price.
     * @return Result message
     */
    public String buyStock(ServerPlayerEntity player, StockType stockType, int quantity) {
        if (quantity <= 0) {
            return "Invalid quantity!";
        }

        StockPrice price = currentPrices.get(stockType);
        int totalCost = price.price * quantity;
        int fee = calculateFee(totalCost, 0.01f); // 1% fee for all orders
        int totalWithFee = totalCost + fee;

        if (ZCoinStorage.getBalance(player) < totalWithFee) {
            return "Insufficient ZC! Need " + totalWithFee + " ZC.";
        }

        if (!ZCoinStorage.deduct(player, totalWithFee)) {
            return "Failed to deduct ZCoin!";
        }

        addToPortfolio(player.getUuid(), stockType, quantity, price.price);
        statusMessage = "Bought " + quantity + " " + stockType.getTicker() + " @ " + price.price + " ZC";

        return "Bought " + quantity + " shares of " + stockType.getDisplayName() +
               " for " + totalCost + " ZC (fee: " + fee + " ZC)";
    }

    /**
     * Sell stock at market price.
     * @return Result message
     */
    public String sellStock(ServerPlayerEntity player, StockType stockType, int quantity) {
        if (quantity <= 0) {
            return "Invalid quantity!";
        }

        List<StockHolding> portfolio = portfolios.get(player.getUuid());
        if (portfolio == null) {
            return "You don't own any stocks!";
        }

        StockHolding holding = portfolio.stream()
                .filter(h -> h.stockType == stockType)
                .findFirst()
                .orElse(null);

        if (holding == null || holding.quantity < quantity) {
            return "You don't have enough shares! Owned: " +
                   (holding != null ? holding.quantity : 0);
        }

        StockPrice price = currentPrices.get(stockType);
        int totalValue = price.price * quantity;
        int fee = calculateFee(totalValue, 0.01f); // 1% fee
        int netValue = totalValue - fee;

        // Remove from portfolio
        holding.quantity -= quantity;
        if (holding.quantity <= 0) {
            portfolio.remove(holding);
        }
        if (portfolio.isEmpty()) {
            portfolios.remove(player.getUuid());
        }

        ZCoinStorage.add(player, netValue);

        int profit = (price.price - holding.avgCost) * quantity;
        String profitStr = profit >= 0 ? "+" + profit : String.valueOf(profit);

        statusMessage = "Sold " + quantity + " " + stockType.getTicker() + " @ " + price.price + " ZC";

        return "Sold " + quantity + " shares of " + stockType.getDisplayName() +
               " for " + netValue + " ZC (P/L: " + profitStr + " ZC)";
    }

    /**
     * Place a limit order.
     */
    public String placeLimitOrder(ServerPlayerEntity player, StockType stockType,
                                   OrderType orderType, int quantity, int limitPrice) {
        if (quantity <= 0) {
            return "Invalid quantity!";
        }

        StockPrice current = currentPrices.get(stockType);

        // Check if order can execute immediately
        boolean canExecuteNow = switch (orderType) {
            case LIMIT_BUY -> current.price <= limitPrice;
            case LIMIT_SELL -> current.price >= limitPrice;
            default -> false;
        };

        if (canExecuteNow) {
            // Execute immediately as market order
            if (orderType == OrderType.LIMIT_BUY) {
                return buyStock(player, stockType, quantity);
            } else {
                return sellStock(player, stockType, quantity);
            }
        }

        // For limit buys, check if player has enough ZC
        if (orderType == OrderType.LIMIT_BUY) {
            int maxCost = limitPrice * quantity;
            int fee = calculateFee(maxCost, 0.01f);
            if (ZCoinStorage.getBalance(player) < maxCost + fee) {
                return "Insufficient ZC for limit order!";
            }
            // Reserve funds
            ZCoinStorage.deduct(player, maxCost + fee);
        }

        // For limit sells, reserve shares
        if (orderType == OrderType.LIMIT_SELL) {
            List<StockHolding> portfolio = portfolios.get(player.getUuid());
            if (portfolio == null) {
                return "You don't own any stocks!";
            }
            StockHolding holding = portfolio.stream()
                    .filter(h -> h.stockType == stockType)
                    .findFirst()
                    .orElse(null);
            if (holding == null || holding.quantity < quantity) {
                return "Not enough shares for limit order!";
            }
            holding.quantity -= quantity; // Reserve shares
        }

        // Create order
        long createdAt = player.getEntityWorld().getTime();
        StockOrder order = new StockOrder(
                player.getUuid(),
                stockType,
                orderType,
                quantity,
                limitPrice,
                0,
                createdAt
        );

        pendingOrders.computeIfAbsent(player.getUuid(), k -> new ArrayList<>()).add(order);

        return "Limit order placed: " + orderType +
               " " + quantity + " " + stockType.getTicker() +
               " @ " + limitPrice + " ZC";
    }

    /**
     * Execute a pending order.
     */
    private void executeOrder(ServerPlayerEntity player, StockOrder order) {
        StockPrice price = currentPrices.get(order.stockType);

        if (order.orderType == OrderType.LIMIT_BUY) {
            // Return reserved funds and execute buy
            int reservedCost = order.limitPrice * order.quantity;
            int actualCost = price.price * order.quantity;
            int fee = calculateFee(actualCost, 0.01f);
            ZCoinStorage.add(player, reservedCost + fee); // Return reserved
            addToPortfolio(player.getUuid(), order.stockType, order.quantity, price.price);

            player.sendMessage(Text.literal("✓ Limit buy executed: " +
                    order.quantity + " " + order.stockType.getTicker() +
                    " @ " + price.price + " ZC").formatted(Formatting.GREEN), false);
        } else if (order.orderType == OrderType.LIMIT_SELL) {
            // Execute sell
            int value = price.price * order.quantity;
            int fee = calculateFee(value, 0.01f);
            ZCoinStorage.add(player, value - fee);

            player.sendMessage(Text.literal("✓ Limit sell executed: " +
                    order.quantity + " " + order.stockType.getTicker() +
                    " @ " + price.price + " ZC").formatted(Formatting.GREEN), false);
        }
    }

    /**
     * Cancel all pending orders for a player.
     */
    public String cancelOrders(ServerPlayerEntity player) {
        List<StockOrder> orders = pendingOrders.remove(player.getUuid());
        if (orders == null || orders.isEmpty()) {
            return "No pending orders to cancel.";
        }

        // Return reserved funds/shares
        for (StockOrder order : orders) {
            if (order.orderType == OrderType.LIMIT_BUY) {
                int reservedCost = order.limitPrice * order.quantity;
                int fee = calculateFee(reservedCost, 0.01f);
                ZCoinStorage.add(player, reservedCost + fee);
            } else if (order.orderType == OrderType.LIMIT_SELL) {
                addToPortfolio(player.getUuid(), order.stockType, order.quantity, 0);
            }
        }

        return "Cancelled " + orders.size() + " pending order(s).";
    }

    /**
     * Add stock to player's portfolio.
     */
    private void addToPortfolio(java.util.UUID playerId, StockType stockType, int quantity, int avgCost) {
        List<StockHolding> portfolio = portfolios.computeIfAbsent(playerId, k -> new ArrayList<>());

        StockHolding existing = portfolio.stream()
                .filter(h -> h.stockType == stockType)
                .findFirst()
                .orElse(null);

        if (existing != null) {
            // Update average cost
            int totalCost = existing.avgCost * existing.quantity + avgCost * quantity;
            existing.quantity += quantity;
            existing.avgCost = totalCost / existing.quantity;
        } else {
            portfolio.add(new StockHolding(stockType, quantity, avgCost));
        }
    }

    /**
     * Calculate trading fee.
     */
    private int calculateFee(int amount, float rate) {
        return Math.max(1, (int)(amount * rate));
    }

    /**
     * Update status message based on market state.
     */
    private void updateStatusMessage() {
        int upCount = 0;
        int downCount = 0;

        for (StockPrice price : currentPrices.values()) {
            if (price.changePercent > 0) upCount++;
            else if (price.changePercent < 0) downCount++;
        }

        if (upCount > downCount * 2) {
            statusMessage = "Market: BULLISH 🚀";
        } else if (downCount > upCount * 2) {
            statusMessage = "Market: BEARISH 📉";
        } else {
            statusMessage = "Market: MIXED 📊";
        }
    }

    // ===== Getters =====

    public Map<StockType, StockPrice> getCurrentPrices() {
        return new HashMap<>(currentPrices);
    }

    public StockPrice getPrice(StockType type) {
        return currentPrices.get(type);
    }

    public Map<StockType, List<Integer>> getPriceHistory() {
        return new HashMap<>(priceHistory);
    }

    public List<StockHolding> getPortfolio(java.util.UUID playerId) {
        List<StockHolding> portfolio = portfolios.get(playerId);
        return portfolio != null ? new ArrayList<>(portfolio) : Collections.emptyList();
    }

    public List<StockOrder> getPendingOrders(java.util.UUID playerId) {
        List<StockOrder> orders = pendingOrders.get(playerId);
        return orders != null ? new ArrayList<>(orders) : Collections.emptyList();
    }

    public String getStatusMessage() { return statusMessage; }
    public long getLastUpdateTick() { return lastUpdateTick; }
    public MarketEvent getCurrentEvent() { return currentEvent; }

    // ===== Inner Classes =====

    public static class StockPrice {
        public final int price;
        public final int changePercent;

        public StockPrice(int price, int changePercent) {
            this.price = price;
            this.changePercent = changePercent;
        }
    }

    public static class StockHolding {
        public StockType stockType;
        public int quantity;
        public int avgCost;

        public StockHolding(StockType stockType, int quantity, int avgCost) {
            this.stockType = stockType;
            this.quantity = quantity;
            this.avgCost = avgCost;
        }

        public int getCurrentValue(StockPrice currentPrice) {
            return quantity * currentPrice.price;
        }

        public int getProfitLoss(StockPrice currentPrice) {
            return (currentPrice.price - avgCost) * quantity;
        }
    }

    public static class StockOrder {
        public final java.util.UUID playerId;
        public final StockType stockType;
        public final OrderType orderType;
        public final int quantity;
        public final int limitPrice;
        public final int stopPrice;
        public final long createdAt;

        public StockOrder(java.util.UUID playerId, StockType stockType, OrderType orderType,
                          int quantity, int limitPrice, int stopPrice, long createdAt) {
            this.playerId = playerId;
            this.stockType = stockType;
            this.orderType = orderType;
            this.quantity = quantity;
            this.limitPrice = limitPrice;
            this.stopPrice = stopPrice;
            this.createdAt = createdAt;
        }
    }

    public enum OrderType {
        MARKET,
        LIMIT_BUY,
        LIMIT_SELL,
        STOP_LOSS
    }

    public enum MarketEvent {
        NONE("None"),
        BULL_MARKET("Bull Market - All stocks +5%"),
        BEAR_MARKET("Bear Market - All stocks -5%"),
        TECH_BOOM("Tech Boom - Growth stocks +10%"),
        RESOURCE_CRASH("Resource Crash - Blue chip -10%"),
        NETHER_CRISIS("Nether Crisis - Nether stocks -20%"),
        END_DISCOVERY("End Discovery - Ender stocks +15%");

        private final String displayName;

        MarketEvent(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() { return displayName; }
    }
}
