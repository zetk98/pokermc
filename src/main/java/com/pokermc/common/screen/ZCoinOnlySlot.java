package com.pokermc.common.screen;

import com.pokermc.PokerMod;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;

/** Slot that only accepts ZCoin items. */
public class ZCoinOnlySlot extends Slot {

    public ZCoinOnlySlot(Inventory inventory, int index, int x, int y) {
        super(inventory, index, x, y);
    }

    @Override
    public boolean canInsert(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == PokerMod.ZCOIN_ITEM;
    }
}
