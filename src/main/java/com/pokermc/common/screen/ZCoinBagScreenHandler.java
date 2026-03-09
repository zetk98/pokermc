package com.pokermc.common.screen;

import com.pokermc.PokerMod;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.Generic3x3ContainerScreenHandler;
import net.minecraft.screen.slot.Slot;

/**
 * ZCoin Bag - extends vanilla Generic3x3ContainerScreenHandler, dùng ScreenHandlerTypes.GENERIC_3X3.
 * Thay 9 slot đầu bằng ZCoinOnlySlot để chỉ cho phép ZCoin.
 */
public class ZCoinBagScreenHandler extends Generic3x3ContainerScreenHandler {

    private final Inventory bagInventory;
    private final int bagSlotIndex;

    public ZCoinBagScreenHandler(int syncId, PlayerInventory playerInventory, Inventory bagInventory, int bagSlotIndex) {
        super(syncId, playerInventory, bagInventory);
        this.bagInventory = bagInventory;
        this.bagSlotIndex = bagSlotIndex;

        // Thay 9 slot đầu bằng ZCoinOnlySlot (layout giống dispenser)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int i = col + row * 3;
                this.slots.set(i, new ZCoinOnlySlot(bagInventory, i, 62 + col * 18, 18 + row * 18));
            }
        }
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        ItemStack copy = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasStack()) {
            ItemStack stack = slot.getStack();
            copy = stack.copy();
            if (index < 9) {
                // Từ bag -> player
                if (!this.insertItem(stack, 9, 45, true)) return ItemStack.EMPTY;
            } else {
                // Từ player -> bag: chỉ ZCoin
                if (stack.getItem() != PokerMod.ZCOIN_ITEM) return ItemStack.EMPTY;
                if (!this.insertItem(stack, 0, 9, false)) return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) slot.setStack(ItemStack.EMPTY);
            else slot.markDirty();
        }
        return copy;
    }

    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        if (!player.getEntityWorld().isClient() && bagSlotIndex >= 0 && bagSlotIndex <= 40) {
            ItemStack bag = bagSlotIndex == 40 ? player.getOffHandStack() : player.getInventory().getStack(bagSlotIndex);
            if (bag.getItem() == PokerMod.ZCOIN_BAG_ITEM) {
                saveToBag(bag, bagInventory);
            }
        }
    }

    /** Load bag contents into inventory. Chỉ load ZCoin. Hỗ trợ BALANCE cũ và CONTAINER mới. */
    public static void loadFromBag(ItemStack bagStack, Inventory inv) {
        if (bagStack.isEmpty() || !(bagStack.getItem() instanceof com.pokermc.common.item.ZCoinBagItem)) return;
        var container = bagStack.get(net.minecraft.component.DataComponentTypes.CONTAINER);
        if (container != null) {
            var list = net.minecraft.util.collection.DefaultedList.ofSize(9, ItemStack.EMPTY);
            container.copyTo(list);
            for (int i = 0; i < Math.min(9, list.size()); i++) {
                ItemStack s = list.get(i);
                if (!s.isEmpty() && s.getItem() == PokerMod.ZCOIN_ITEM) {
                    inv.setStack(i, s.copy());
                }
            }
        } else {
            int balance = com.pokermc.common.item.ZCoinBagItem.getBalance(bagStack);
            if (balance > 0) {
                inv.clear();
                int remaining = balance;
                for (int i = 0; i < 9 && remaining > 0; i++) {
                    int put = Math.min(remaining, 64);
                    inv.setStack(i, new ItemStack(PokerMod.ZCOIN_ITEM, put));
                    remaining -= put;
                }
            }
        }
    }

    /** Save inventory to bag. */
    public static void saveToBag(ItemStack bagStack, Inventory inv) {
        if (bagStack.isEmpty() || !(bagStack.getItem() instanceof com.pokermc.common.item.ZCoinBagItem)) return;
        var list = net.minecraft.util.collection.DefaultedList.ofSize(9, ItemStack.EMPTY);
        for (int i = 0; i < 9; i++) {
            ItemStack s = inv.getStack(i);
            if (!s.isEmpty() && s.getItem() == PokerMod.ZCOIN_ITEM) {
                list.set(i, s.copy());
            }
        }
        bagStack.set(net.minecraft.component.DataComponentTypes.CONTAINER,
                net.minecraft.component.type.ContainerComponent.fromStacks(list));
        bagStack.remove(com.pokermc.common.component.PokerComponents.ZCOIN_BAG_BALANCE);
    }
}
