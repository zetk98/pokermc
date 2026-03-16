package com.pokermc.xoso.game;

import com.pokermc.PokerMod;
import com.pokermc.common.config.CasinoCraftConfig;
import com.pokermc.common.config.ZCoinStorage;
import com.pokermc.xoso.item.LotteryTicketItem;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;

import java.util.*;

/**
 * Vietnam's Lottery - 4 digits (0000-9999).
 * Draw every 10 real minutes. Prizes: match from last digit up (3rd=1, 2nd=2, 1st=3, special=4).
 * Player must claim within 7 periods of draw.
 */
public class XosoGame {

    /** Dedicated lock for lottery draw synchronization */
    private static final Object DRAW_LOCK = new Object();

    /** 10 minutes = 12000 ticks (20 TPS) */
    private static final long TICKS_PER_DRAW = 12000L;

    /** Last draw period (-1 = not yet drawn). Period = worldTime / TICKS_PER_DRAW */
    private long lastDrawPeriod = -1;
    /** Special prize = 4 digits. 1st = last 3, 2nd = last 2, 3rd = last 1. */
    private String resultSpecial = "";
    private String result1st = "";
    private String result2nd = "";
    private String result3rd = "";
    private String statusMessage = "4 digits (0000-9999). Draw every 10 min. Claim within 7 periods.";

    private static long getOverworldTime(ServerPlayerEntity player) {
        var world = player.getEntityWorld();
        if (!(world instanceof ServerWorld sw)) return world.getTime();
        var server = sw.getServer();
        if (server == null) return world.getTime();
        var overworld = server.getWorld(World.OVERWORLD);
        return overworld != null ? overworld.getTime() : world.getTime();
    }

    private static int ticketPrice() {
        return Math.max(1, CasinoCraftConfig.get().xosoTicketPrice);
    }
    private static int prizeSpecial() { return Math.max(0, CasinoCraftConfig.get().xosoPrizeDacBiet); }
    private static int prize1st() { return Math.max(0, CasinoCraftConfig.get().xosoPrizeNhat); }
    private static int prize2nd() { return Math.max(0, CasinoCraftConfig.get().xosoPrizeNhi); }
    private static int prize3rd() { return Math.max(0, CasinoCraftConfig.get().xosoPrizeBa); }

    public static boolean isValidNumber(String num) {
        if (num == null || num.length() != 4) return false;
        for (char c : num.toCharArray()) {
            if (c < '0' || c > '9') return false;
        }
        return true;
    }

    /** Buy ticket: give player LotteryTicketItem. Buys for next 10-min draw. Uses overworld time. */
    public String buyTicket(ServerPlayerEntity player, String number) {
        if (!isValidNumber(number)) return "Invalid number. Enter 4 digits (0000-9999).";
        int price = ticketPrice();
        if (ZCoinStorage.getBalance(player) < price) return "Not enough ZC. Need " + price + " ZC.";
        if (!ZCoinStorage.deduct(player, price)) return "Could not deduct ZC.";
        int num = Integer.parseInt(number);
        long worldTime = getOverworldTime(player);
        long purchasePeriod = (worldTime / TICKS_PER_DRAW) + 1; // next draw
        ItemStack ticket = new ItemStack(PokerMod.LOTTERY_TICKET_ITEM, 1);
        LotteryTicketItem.setNumber(ticket, num);
        LotteryTicketItem.setPurchasePeriod(ticket, purchasePeriod);
        if (!player.getInventory().insertStack(ticket)) {
            player.dropItem(ticket, false);
        }
        return "Bought ticket #" + number + " for next draw (10 min)! (-" + price + " ZC)";
    }

    /** Buy random ticket */
    public String buyRandomTicket(ServerPlayerEntity player) {
        Random r = new Random();
        String num = String.format("%04d", r.nextInt(10000));
        return buyTicket(player, num);
    }

    /** Tick: draw every 10 real minutes. Catches up if server was offline. Returns true if state changed. */
    public boolean tick(long worldTime, MinecraftServer server, ServerWorld world) {
        if (server == null) return false;
        ServerWorld overworld = server.getWorld(World.OVERWORLD);
        if (overworld == null) return false;
        long currentPeriod = overworld.getTime() / TICKS_PER_DRAW;
        if (currentPeriod <= lastDrawPeriod) return false;
        boolean changed = false;
        while (lastDrawPeriod < currentPeriod) {
            long drawPeriod = lastDrawPeriod + 1;
            synchronized (DRAW_LOCK) {
                var existing = LotteryDrawStorage.get(server, drawPeriod);
                if (existing != null) {
                    resultSpecial = existing.special();
                    result1st = existing.first();
                    result2nd = existing.second();
                    result3rd = existing.third();
                } else {
                    performDraw();
                    storeDrawForClaim(server, drawPeriod);
                    if (drawPeriod == currentPeriod) broadcastDrawResults(server); // only broadcast latest
                }
                lastDrawPeriod = drawPeriod;
                changed = true;
            }
        }
        return changed;
    }

    private void performDraw() {
        Random r = new Random();
        resultSpecial = String.format("%04d", r.nextInt(10000));
        result1st = resultSpecial.substring(1); // last 3
        result2nd = resultSpecial.substring(2); // last 2
        result3rd = resultSpecial.substring(3); // last 1
        statusMessage = "Results: 3rd " + result3rd + ", 2nd " + result2nd + ", 1st " + result1st + ", Sp " + resultSpecial + ". Right-click ticket to claim!";
    }

    /** Store draw results for claim-by-right-click. */
    public void storeDrawForClaim(MinecraftServer server, long drawPeriod) {
        if (!resultSpecial.isEmpty() && server != null) {
            LotteryDrawStorage.store(server, drawPeriod, new LotteryDrawStorage.DrawResult(
                    resultSpecial, result1st, result2nd, result3rd));
        }
    }

    private void broadcastDrawResults(net.minecraft.server.MinecraftServer server) {
        Text header = Text.literal("§6[Lottery] §fPeriod results:").formatted(Formatting.GOLD);
        Text line3 = Text.literal("§73rd: §e" + result3rd);
        Text line2 = Text.literal("§72nd: §e" + result2nd);
        Text line1 = Text.literal("§71st: §e" + result1st);
        Text lineSp = Text.literal("§7Sp:  §e" + resultSpecial);
        Text footer = Text.literal("§fRight-click ticket to claim!");
        for (var p : server.getPlayerManager().getPlayerList()) {
            p.sendMessage(header, false);
            p.sendMessage(line3, false);
            p.sendMessage(line2, false);
            p.sendMessage(line1, false);
            p.sendMessage(lineSp, false);
            p.sendMessage(footer, false);
        }
    }

    public long getLastDrawPeriod() { return lastDrawPeriod; }
    public static long getTicksPerDraw() { return TICKS_PER_DRAW; }
    public String getResultSpecial() { return resultSpecial; }
    public String getResult1st() { return result1st; }
    public String getResult2nd() { return result2nd; }
    public String getResult3rd() { return result3rd; }
    public String getStatusMessage() { return statusMessage; }
    public void setStatusMessage(String msg) { this.statusMessage = msg; }
    public int getTicketPrice() { return ticketPrice(); }
}
