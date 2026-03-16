package com.pokermc.goldenticket.item;

import com.pokermc.common.component.PokerComponents;
import com.pokermc.common.config.CasinoCraftConfig;
import com.pokermc.common.config.ZCoinStorage;
import com.pokermc.goldenticket.game.GoldenTicketTierConfig;
import com.pokermc.goldenticket.game.GoldenTicketRewardEngine;
import com.pokermc.PokerMod;
import net.minecraft.component.type.TooltipDisplayComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

import java.util.function.Consumer;

/**
 * Golden Ticket - gacha item. Right-click to redeem for ZCoin reward.
 * Stackable 64. Tier stored in component (0=5zc, 1=10zc, 2=20zc).
 */
public class GoldenTicketItem extends Item {

    public GoldenTicketItem(Settings settings) {
        super(settings);
    }

    public static int getTier(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof GoldenTicketItem)) return 0;
        Integer t = stack.get(PokerComponents.GOLDEN_TICKET_TIER);
        return t != null ? Math.max(0, t) : 0;
    }

    public static void setTier(ItemStack stack, int tier) {
        if (stack.isEmpty() || !(stack.getItem() instanceof GoldenTicketItem)) return;
        stack.set(PokerComponents.GOLDEN_TICKET_TIER, Math.max(0, tier));
    }

    public static ItemStack create(int tier) {
        ItemStack stack = new ItemStack(PokerMod.GOLDEN_TICKET_ITEM, 1);
        setTier(stack, tier);
        return stack;
    }

    @Override
    public Text getName(ItemStack stack) {
        int tier = getTier(stack);
        var tiers = CasinoCraftConfig.get().goldenTicketTiers;
        int price = tier < tiers.size() ? tiers.get(tier).price : 5;
        return Text.literal("Golden Ticket (" + price + " ZC)").formatted(Formatting.GOLD);
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, TooltipDisplayComponent displayComponent,
                              Consumer<Text> textConsumer, TooltipType type) {
        int tier = getTier(stack);
        var tiers = CasinoCraftConfig.get().goldenTicketTiers;
        if (tier >= tiers.size()) {
            textConsumer.accept(Text.literal("Unknown tier").formatted(Formatting.RED));
            return;
        }
        GoldenTicketTierConfig cfg = tiers.get(tier);
        textConsumer.accept(Text.literal("Type: " + cfg.price + " ZC ticket").formatted(Formatting.GRAY));
        textConsumer.accept(Text.literal("Reward: " + cfg.rewardMin + " - " + cfg.rewardMax + " ZC").formatted(Formatting.GRAY));
        textConsumer.accept(Text.literal("Right-click to redeem").formatted(Formatting.YELLOW));
        textConsumer.accept(Text.literal("Reward odds: low values common, high values rare").formatted(Formatting.DARK_GRAY));
        textConsumer.accept(Text.literal("Jackpot: " + cfg.jackpotThreshold + "+ ZC → server broadcast").formatted(Formatting.DARK_GRAY));
        if (cfg.upgradeChance > 0 && tier + 1 < tiers.size()) {
            int nextPrice = tiers.get(tier + 1).price;
            textConsumer.accept(Text.literal(String.format("%.0f%% chance → %d ZC ticket", cfg.upgradeChance * 100, nextPrice))
                    .formatted(Formatting.AQUA));
        }
    }

    @Override
    public net.minecraft.util.ActionResult use(World world, net.minecraft.entity.player.PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient()) return net.minecraft.util.ActionResult.PASS;
        if (!(user instanceof ServerPlayerEntity player)) return net.minecraft.util.ActionResult.PASS;

        int tier = getTier(stack);
        var tiers = CasinoCraftConfig.get().goldenTicketTiers;
        if (tier >= tiers.size()) {
            player.sendMessage(Text.literal("§cInvalid ticket."), true);
            return net.minecraft.util.ActionResult.FAIL;
        }

        GoldenTicketTierConfig cfg = tiers.get(tier);
        Random rng = player.getRandom();

        // Check upgrade chance first
        if (cfg.upgradeChance > 0 && tier + 1 < tiers.size() && rng.nextDouble() < cfg.upgradeChance) {
            stack.decrement(1);
            ItemStack upgraded = create(tier + 1);
            if (!player.getInventory().insertStack(upgraded)) {
                player.dropItem(upgraded, false);
            }
            player.sendMessage(Text.literal("§6[Golden Ticket] §fLucky! You got an upgraded ticket (" +
                    tiers.get(tier + 1).price + " ZC)!"), true);
            return net.minecraft.util.ActionResult.SUCCESS;
        }

        // Roll ZCoin reward (weighted: low values more likely)
        int reward = GoldenTicketRewardEngine.rollReward(cfg, rng);
        stack.decrement(1);

        ZCoinStorage.add(player, reward);
        player.sendMessage(Text.literal("§6[Golden Ticket] §fYou won §e" + reward + " §fZC!"), true);

        // Jackpot broadcast: reward >= jackpotThreshold
        if (reward >= cfg.jackpotThreshold && player.getEntityWorld() instanceof net.minecraft.server.world.ServerWorld sw) {
            var server = sw.getServer();
            if (server != null) {
                Text broadcast = Text.literal("§6§l[JACKPOT!] §f" + player.getName().getString() +
                        " §ewon §f" + reward + " §eZC §ffrom a Golden Ticket (" + cfg.price + " ZC)!");
                server.getPlayerManager().getPlayerList().forEach(p -> p.sendMessage(broadcast, false));
            }
        }

        return net.minecraft.util.ActionResult.SUCCESS;
    }
}
