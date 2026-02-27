package com.pokermc.config;

import com.pokermc.PokerMod;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * ZCoin balance from player inventory: ZCoin items + ZCoinBag contents.
 * Bộ đếm xu đếm cả xu rời (ZCoin item) và xu trong túi (ZCoinBag).
 * Replaces file-based wallet.
 */
public class ZCoinStorage {

    /** Total ZCoin: loose coins + all coins inside ZCoin bags in inventory. */
    public static int getBalance(PlayerEntity player) {
        if (player == null) return 0;
        int total = 0;
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack stack = inv.getStack(i);
            if (stack.getItem() == PokerMod.ZCOIN_ITEM) {
                total += stack.getCount();
            } else             if (stack.getItem() == PokerMod.ZCOIN_BAG_ITEM) {
                total += com.pokermc.item.ZCoinBagItem.getBalance(stack); // sums ZCoin in slots
            }
        }
        return total;
    }

    /** Add ZCoin to player. Prefers bags, then gives loose coins. */
    public static boolean add(ServerPlayerEntity player, int amount) {
        if (amount <= 0) return true;
        int remaining = amount;

        for (int i = 0; i < player.getInventory().size() && remaining > 0; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.getItem() == PokerMod.ZCOIN_BAG_ITEM) {
                int space = com.pokermc.item.ZCoinBagItem.getMaxCapacity() - com.pokermc.item.ZCoinBagItem.getBalance(stack);
                int add = Math.min(remaining, space);
                if (add > 0) {
                    com.pokermc.item.ZCoinBagItem.addBalance(stack, add);
                    remaining -= add;
                }
            }
        }

        while (remaining > 0) {
            int give = Math.min(remaining, 64);
            ItemStack coins = new ItemStack(PokerMod.ZCOIN_ITEM, give);
            if (!player.getInventory().insertStack(coins)) {
                player.dropItem(coins, false);
            }
            remaining -= give;
        }
        return true;
    }

    /** Deduct ZCoin from player. Returns true if successful. */
    public static boolean deduct(ServerPlayerEntity player, int amount) {
        if (amount <= 0) return true;
        if (getBalance(player) < amount) return false;

        int remaining = amount;

        for (int i = 0; i < player.getInventory().size() && remaining > 0; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.getItem() == PokerMod.ZCOIN_BAG_ITEM) {
                int took = com.pokermc.item.ZCoinBagItem.takeBalance(stack, remaining);
                remaining -= took;
            }
        }

        for (int i = 0; i < player.getInventory().size() && remaining > 0; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.getItem() == PokerMod.ZCOIN_ITEM) {
                int take = Math.min(stack.getCount(), remaining);
                stack.decrement(take);
                remaining -= take;
            }
        }

        return remaining <= 0;
    }

    /** Take all ZCoin for table. Returns amount taken. */
    public static int takeAll(ServerPlayerEntity player) {
        int total = getBalance(player);
        if (total <= 0) return 0;

        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.getItem() == PokerMod.ZCOIN_BAG_ITEM) {
                com.pokermc.item.ZCoinBagItem.setBalance(stack, 0);
            } else if (stack.getItem() == PokerMod.ZCOIN_ITEM) {
                stack.setCount(0);
            }
        }
        return total;
    }

    /** Give ZCoin back (e.g. when leaving table). */
    public static void giveBack(ServerPlayerEntity player, int amount) {
        add(player, amount);
    }
}
