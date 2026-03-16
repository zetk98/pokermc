package com.pokermc.stock.game;

import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Stock types available in the Minecraft Stock Exchange.
 * 15 stocks across 3 risk tiers with different volatility characteristics.
 */
public enum StockType {
    // ===== BLUE CHIP (Low Risk, Stable) =====
    ZCOR("ZCOR", "ZCoin Corp", 100, Tier.LOW_RISK, Formatting.GREEN),
    GOLD("GOLD", "Gold Mine Ltd", 80, Tier.LOW_RISK, Formatting.YELLOW),
    DIAM("DIAM", "Diamond Trading", 500, Tier.LOW_RISK, Formatting.AQUA),
    IRON("IRON", "IronWorks Inc", 40, Tier.LOW_RISK, Formatting.GRAY),
    EMRL("EMRL", "Emerald Empire", 200, Tier.LOW_RISK, Formatting.DARK_GREEN),

    // ===== GROWTH (Medium Risk, Moderate Volatility) =====
    REDST("REDST", "Redstone Systems", 150, Tier.MEDIUM_RISK, Formatting.RED),
    LAPIS("LAPIS", "Lapis Technologies", 60, Tier.MEDIUM_RISK, Formatting.BLUE),
    NETR("NETR", "Nether Energy", 120, Tier.MEDIUM_RISK, Formatting.DARK_RED),
    QUART("QUART", "Quartz Corp", 90, Tier.MEDIUM_RISK, Formatting.WHITE),
    COPR("COPR", "Copper Electronics", 50, Tier.MEDIUM_RISK, Formatting.GOLD),

    // ===== HIGH RISK (High Volatility, Big Swings) =====
    NETH("NETH", "Nether Industries", 45, Tier.HIGH_RISK, Formatting.DARK_RED),
    ENDR("ENDR", "Ender Investments", 250, Tier.HIGH_RISK, Formatting.DARK_PURPLE),
    BLAZE("BLAZE", "Blaze Power Plant", 180, Tier.HIGH_RISK, Formatting.GOLD),
    GHAST("GHAST", "Ghast Transport", 75, Tier.HIGH_RISK, Formatting.LIGHT_PURPLE),
    WITHE("WITHE", "Wither Skull Co", 300, Tier.HIGH_RISK, Formatting.DARK_GRAY);

    private final String ticker;
    private final String displayName;
    private final int basePrice;
    private final Tier tier;
    private final Formatting color;

    StockType(String ticker, String displayName, int basePrice, Tier tier, Formatting color) {
        this.ticker = ticker;
        this.displayName = displayName;
        this.basePrice = basePrice;
        this.tier = tier;
        this.color = color;
    }

    public String getTicker() { return ticker; }
    public String getDisplayName() { return displayName; }
    public int getBasePrice() { return basePrice; }
    public Tier getTier() { return tier; }
    public Formatting getColor() { return color; }

    /**
     * Get formatted display name with color.
     */
    public Text getFormattedName() {
        return Text.literal(ticker + " - " + displayName).formatted(color);
    }

    /**
     * Risk tier with volatility characteristics.
     */
    public enum Tier {
        LOW_RISK("Blue Chip", 2, 5, 0.98f, 1.02f),
        MEDIUM_RISK("Growth", 5, 15, 0.95f, 1.05f),
        HIGH_RISK("High Risk", 10, 30, 0.90f, 1.10f);

        private final String displayName;
        private final int minChangePercent;      // Minimum daily change
        private final int maxChangePercent;      // Maximum daily change
        private final float minMultiplier;       // For random price changes
        private final float maxMultiplier;

        Tier(String displayName, int minChange, int maxChange, float minMult, float maxMult) {
            this.displayName = displayName;
            this.minChangePercent = minChange;
            this.maxChangePercent = maxChange;
            this.minMultiplier = minMult;
            this.maxMultiplier = maxMult;
        }

        public String getDisplayName() { return displayName; }
        public int getMinChangePercent() { return minChangePercent; }
        public int getMaxChangePercent() { return maxChangePercent; }
        public float getMinMultiplier() { return minMultiplier; }
        public float getMaxMultiplier() { return maxMultiplier; }

        /**
         * Calculate price change based on tier volatility.
         * @param currentPrice Current stock price
         * @param randomSeed Random seed for variation
         * @return New price after change
         */
        public int calculatePriceChange(int currentPrice, long randomSeed) {
            // Use seeded random for deterministic but varied results
            java.util.Random rand = new java.util.Random(randomSeed);
            float changePercent = minChangePercent + rand.nextFloat() * (maxChangePercent - minChangePercent);
            boolean positive = rand.nextBoolean();

            float multiplier = positive ? (1.0f + changePercent / 100.0f) : (1.0f - changePercent / 100.0f);

            // Clamp to tier limits
            multiplier = Math.max(minMultiplier, Math.min(maxMultiplier, multiplier));

            return Math.max(1, (int)(currentPrice * multiplier));
        }
    }

    /**
     * Find stock type by ticker symbol (case-insensitive).
     */
    public static StockType byTicker(String ticker) {
        for (StockType type : values()) {
            if (type.ticker.equalsIgnoreCase(ticker)) {
                return type;
            }
        }
        return null;
    }

    /**
     * Get all stocks of a specific tier.
     */
    public static StockType[] getByTier(Tier tier) {
        return java.util.Arrays.stream(values())
                .filter(s -> s.tier == tier)
                .toArray(StockType[]::new);
    }
}
