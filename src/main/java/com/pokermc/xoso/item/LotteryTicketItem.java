package com.pokermc.xoso.item;

import com.pokermc.common.component.PokerComponents;
import com.pokermc.common.config.CasinoCraftConfig;
import com.pokermc.PokerMod;
import com.pokermc.xoso.game.XosoGame;
import com.pokermc.xoso.game.LotteryDrawStorage;
import net.minecraft.component.type.TooltipDisplayComponent;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

import java.util.function.Consumer;

/**
 * Lottery ticket - stores 4-digit number and purchase period.
 * Draw every 10 min. Right-click to claim within 7 periods of draw.
 * E.g. buy period 163 → valid until period 170 → period 171 cannot claim.
 */
public class LotteryTicketItem extends Item {

    /** Valid for 7 periods after purchase period (not 7 days). E.g. period 163 → expires after period 170. */
    private static final long CLAIM_PERIODS = 7L;

    public LotteryTicketItem(Settings settings) {
        super(settings);
    }

    public static long getPurchasePeriod(ItemStack stack) {
        Long p = stack.get(PokerComponents.LOTTERY_TICKET_DAY);
        return p != null ? p : -1;
    }

    public static void setPurchasePeriod(ItemStack stack, long period) {
        if (stack.isEmpty() || !(stack.getItem() instanceof LotteryTicketItem)) return;
        stack.set(PokerComponents.LOTTERY_TICKET_DAY, Math.max(0, period));
    }

    public static long getExpiryPeriod(ItemStack stack) {
        long p = getPurchasePeriod(stack);
        return p >= 0 ? p + CLAIM_PERIODS : -1;
    }

    /** @deprecated Use getPurchasePeriod. Kept for compatibility. */
    @Deprecated
    public static long getPurchaseDay(ItemStack stack) { return getPurchasePeriod(stack); }

    /** @deprecated Use setPurchasePeriod. Kept for compatibility. */
    @Deprecated
    public static void setPurchaseDay(ItemStack stack, long period) { setPurchasePeriod(stack, period); }

    public static int getNumber(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof LotteryTicketItem)) return -1;
        Integer n = stack.get(PokerComponents.LOTTERY_TICKET_NUMBER);
        return n != null ? Math.max(0, Math.min(9999, n)) : -1;
    }

    public static void setNumber(ItemStack stack, int number) {
        if (stack.isEmpty() || !(stack.getItem() instanceof LotteryTicketItem)) return;
        number = Math.max(0, Math.min(9999, number));
        stack.set(PokerComponents.LOTTERY_TICKET_NUMBER, number);
    }

    public static String getNumberString(ItemStack stack) {
        int n = getNumber(stack);
        return n >= 0 ? String.format("%04d", n) : "????";
    }

    private static long getOverworldTime(World world) {
        if (!(world instanceof ServerWorld sw)) return world.getTime();
        var server = sw.getServer();
        if (server == null) return world.getTime();
        var overworld = server.getWorld(World.OVERWORLD);
        return overworld != null ? overworld.getTime() : world.getTime();
    }

    @Override
    public Text getName(ItemStack stack) {
        String num = getNumberString(stack);
        return Text.literal("Lottery Ticket #" + num).formatted(Formatting.GOLD);
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, TooltipDisplayComponent displayComponent, Consumer<Text> textConsumer, TooltipType type) {
        long period = getPurchasePeriod(stack);
        String num = getNumberString(stack);
        textConsumer.accept(Text.literal("Number: " + num).formatted(Formatting.GRAY));
        textConsumer.accept(Text.literal("Period #" + (period >= 0 ? period : "?")).formatted(Formatting.GRAY));
        textConsumer.accept(Text.literal("Valid for 7 periods after draw").formatted(Formatting.YELLOW));
    }

    @Override
    public net.minecraft.util.ActionResult use(World world, net.minecraft.entity.player.PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient()) return net.minecraft.util.ActionResult.PASS;
        if (!(user instanceof net.minecraft.server.network.ServerPlayerEntity player)) return net.minecraft.util.ActionResult.PASS;

        long purchasePeriod = getPurchasePeriod(stack);
        if (purchasePeriod < 0) return net.minecraft.util.ActionResult.PASS;

        long worldTime = getOverworldTime(world);
        long currentPeriod = worldTime / XosoGame.getTicksPerDraw();
        long expiryPeriod = purchasePeriod + CLAIM_PERIODS;

        if (currentPeriod < purchasePeriod) {
            player.sendMessage(Text.literal("§cToo early! Period #" + purchasePeriod + " hasn't happened yet. Current: #" + currentPeriod + "."), true);
            return net.minecraft.util.ActionResult.FAIL;
        }
        if (currentPeriod > expiryPeriod) {
            player.sendMessage(Text.literal("§cThis ticket has expired."), true);
            return net.minecraft.util.ActionResult.FAIL;
        }

        if (!(world instanceof net.minecraft.server.world.ServerWorld sw)) return net.minecraft.util.ActionResult.PASS;
        LotteryDrawStorage.DrawResult result = LotteryDrawStorage.get(sw, purchasePeriod);
        if (result == null) {
            player.sendMessage(Text.literal("§cNo draw results for period #" + purchasePeriod + "."), true);
            return net.minecraft.util.ActionResult.FAIL;
        }

        int ticketNum = getNumber(stack);
        if (ticketNum < 0) return net.minecraft.util.ActionResult.PASS;
        String ticketStr = String.format("%04d", ticketNum);

        int prize = 0;
        String prizeName = "";
        if (ticketStr.equals(result.special())) {
            prize = Math.max(0, CasinoCraftConfig.get().xosoPrizeDacBiet);
            prizeName = "Special";
        } else if (ticketStr.endsWith(result.first())) {
            prize = Math.max(0, CasinoCraftConfig.get().xosoPrizeNhat);
            prizeName = "1st";
        } else if (ticketStr.endsWith(result.second())) {
            prize = Math.max(0, CasinoCraftConfig.get().xosoPrizeNhi);
            prizeName = "2nd";
        } else if (ticketStr.endsWith(result.third())) {
            prize = Math.max(0, CasinoCraftConfig.get().xosoPrizeBa);
            prizeName = "3rd";
        }

        stack.decrement(1);
        if (prize > 0) {
            // Schedule give in 2 seconds - avoids conflict with item use handling
            com.pokermc.xoso.game.LotteryPrizeScheduler.schedule(player, prize, prizeName, purchasePeriod);
            player.sendMessage(Text.literal("§e[Lottery] §fPrize " + prize + " ZC will be added to your inventory in a few seconds..."), true);
        } else {
            player.sendMessage(Text.literal("§7Ticket did not win. Ticket destroyed."), true);
        }
        return net.minecraft.util.ActionResult.SUCCESS;
    }
}
