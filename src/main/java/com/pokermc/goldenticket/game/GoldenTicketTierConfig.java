package com.pokermc.goldenticket.game;

/**
 * Single tier config for Golden Ticket gacha.
 * Loaded from CasinoCraftConfig.goldenTicketTiers.
 */
public class GoldenTicketTierConfig {
    public int price;
    public int rewardMin;
    public int rewardMax;
    public int jackpotThreshold;
    public double upgradeChance;

    public GoldenTicketTierConfig() {}

    public GoldenTicketTierConfig(int price, int rewardMin, int rewardMax, int jackpotThreshold, double upgradeChance) {
        this.price = price;
        this.rewardMin = rewardMin;
        this.rewardMax = rewardMax;
        this.jackpotThreshold = jackpotThreshold;
        this.upgradeChance = upgradeChance;
    }

    public static GoldenTicketTierConfig tier5() {
        return new GoldenTicketTierConfig(5, 0, 20, 15, 0.01);
    }
    public static GoldenTicketTierConfig tier10() {
        return new GoldenTicketTierConfig(10, 5, 50, 35, 0.01);
    }
    public static GoldenTicketTierConfig tier20() {
        return new GoldenTicketTierConfig(20, 10, 100, 75, 0.0);
    }
}
