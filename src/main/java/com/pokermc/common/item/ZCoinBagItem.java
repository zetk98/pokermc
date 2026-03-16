package com.pokermc.common.item;

import com.pokermc.PokerMod;
import com.pokermc.common.component.PokerComponents;
import com.pokermc.common.screen.ZCoinBagScreenHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

/**
 * Bag that stores ZCoin. Right-click to open 9-slot GUI (coin purse style).
 * Only ZCoin can be placed. Supports old BALANCE and new CONTAINER.
 */
public class ZCoinBagItem extends Item {

    public ZCoinBagItem(Settings settings) {
        super(settings);
    }

    @Override
    public net.minecraft.util.ActionResult use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!(stack.getItem() instanceof ZCoinBagItem)) return net.minecraft.util.ActionResult.PASS;
        if (world.isClient()) return net.minecraft.util.ActionResult.SUCCESS;

        int slotIndex = hand == Hand.MAIN_HAND ? user.getInventory().getSelectedSlot() : 40; // 40 = offhand

        SimpleInventory inv = new SimpleInventory(9);
        ZCoinBagScreenHandler.loadFromBag(stack, inv);
        user.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, pinv, p) -> new ZCoinBagScreenHandler(syncId, pinv, inv, slotIndex),
                Text.translatable("item.casinocraft.zcoin_bag")));
        return net.minecraft.util.ActionResult.SUCCESS;
    }

    /** Config max. Legacy bag uses this. Container bag is limited by 9×64=576. */
    public static int getMaxCapacity() {
        int cap = com.pokermc.common.config.PokerConfig.get().zcoinBagMaxCapacity;
        return cap <= 0 ? 999999 : cap;
    }

    /** Actual space available. Container = 9×64 max; legacy = config max. Tránh mất ZC khi config > 576. */
    public static int getAvailableSpace(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof ZCoinBagItem)) return 0;
        int balance = getBalance(stack);
        var container = stack.get(net.minecraft.component.DataComponentTypes.CONTAINER);
        int actualMax = (container != null) ? 9 * 64 : getMaxCapacity();
        return Math.max(0, actualMax - balance);
    }

    public static int getBalance(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof ZCoinBagItem)) return 0;
        int containerTotal = 0;
        var container = stack.get(net.minecraft.component.DataComponentTypes.CONTAINER);
        if (container != null) {
            for (var s : container.iterateNonEmpty()) {
                if (s.getItem() == PokerMod.ZCOIN_ITEM) containerTotal += s.getCount();
            }
        }
        Integer balanceVal = stack.get(PokerComponents.ZCOIN_BAG_BALANCE);
        int legacyTotal = balanceVal != null ? Math.max(0, balanceVal) : 0;
        return Math.max(containerTotal, legacyTotal);
    }

    public static void setBalance(ItemStack stack, int amount) {
        if (stack.isEmpty() || !(stack.getItem() instanceof ZCoinBagItem)) return;
        amount = Math.max(0, Math.min(amount, getMaxCapacity()));
        var container = stack.get(net.minecraft.component.DataComponentTypes.CONTAINER);
        if (container != null) {
            var list = net.minecraft.util.collection.DefaultedList.ofSize(9, ItemStack.EMPTY);
            int remaining = amount;
            for (int i = 0; i < 9 && remaining > 0; i++) {
                int put = Math.min(remaining, 64);
                list.set(i, new ItemStack(PokerMod.ZCOIN_ITEM, put));
                remaining -= put;
            }
            stack.set(net.minecraft.component.DataComponentTypes.CONTAINER,
                    net.minecraft.component.type.ContainerComponent.fromStacks(list));
        } else {
            stack.set(PokerComponents.ZCOIN_BAG_BALANCE, amount);
        }
        if (amount == 0) stack.remove(PokerComponents.ZCOIN_BAG_BALANCE);
    }

    public static void addBalance(ItemStack stack, int amount) {
        if (amount <= 0) return;
        var container = stack.get(net.minecraft.component.DataComponentTypes.CONTAINER);
        if (container != null) {
            var list = net.minecraft.util.collection.DefaultedList.ofSize(9, ItemStack.EMPTY);
            container.copyTo(list);
            int remaining = amount;
            for (int i = 0; i < 9 && remaining > 0; i++) {
                ItemStack s = list.get(i);
                int space = s.isEmpty() ? 64 : (s.getItem() == PokerMod.ZCOIN_ITEM ? 64 - s.getCount() : 0);
                int add = Math.min(remaining, space);
                if (add > 0) {
                    if (s.isEmpty()) list.set(i, new ItemStack(PokerMod.ZCOIN_ITEM, add));
                    else s.increment(add);
                    remaining -= add;
                }
            }
            stack.set(net.minecraft.component.DataComponentTypes.CONTAINER,
                    net.minecraft.component.type.ContainerComponent.fromStacks(list));
        } else {
            setBalance(stack, getBalance(stack) + amount);
        }
    }

    public static int takeBalance(ItemStack stack, int maxTake) {
        int cur = getBalance(stack);
        int take = Math.min(cur, maxTake);
        if (take <= 0) return 0;
        var container = stack.get(net.minecraft.component.DataComponentTypes.CONTAINER);
        if (container != null) {
            var list = net.minecraft.util.collection.DefaultedList.ofSize(9, ItemStack.EMPTY);
            container.copyTo(list);
            int remaining = take;
            for (int i = 0; i < 9 && remaining > 0; i++) {
                ItemStack s = list.get(i);
                if (!s.isEmpty() && s.getItem() == PokerMod.ZCOIN_ITEM) {
                    int takeFrom = Math.min(s.getCount(), remaining);
                    s.decrement(takeFrom);
                    if (s.isEmpty()) list.set(i, ItemStack.EMPTY);
                    remaining -= takeFrom;
                }
            }
            stack.set(net.minecraft.component.DataComponentTypes.CONTAINER,
                    net.minecraft.component.type.ContainerComponent.fromStacks(list));
        } else {
            setBalance(stack, cur - take);
        }
        return take;
    }

    @Override
    public Text getName(ItemStack stack) {
        int bal = getBalance(stack);
        return Text.translatable(getTranslationKey()).append(" (" + bal + " ZC)");
    }

    @Override
    public boolean hasGlint(ItemStack stack) {
        return getBalance(stack) > 0;
    }
}
