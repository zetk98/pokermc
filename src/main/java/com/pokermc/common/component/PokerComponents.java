package com.pokermc.common.component;

import com.mojang.serialization.Codec;
import com.pokermc.PokerMod;
import net.minecraft.component.ComponentType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/**
 * Custom data components for PokerMC items.
 */
public class PokerComponents {

    /** ZCoin balance stored in ZCoinBagItem (0–99999). */
    public static final ComponentType<Integer> ZCOIN_BAG_BALANCE = Registry.register(
            Registries.DATA_COMPONENT_TYPE,
            Identifier.of(PokerMod.MOD_ID, "zcoin_bag_balance"),
            ComponentType.<Integer>builder().codec(Codec.INT).build()
    );

    /** Lottery ticket number (0000-9999) stored in LotteryTicketItem. */
    public static final ComponentType<Integer> LOTTERY_TICKET_NUMBER = Registry.register(
            Registries.DATA_COMPONENT_TYPE,
            Identifier.of(PokerMod.MOD_ID, "lottery_ticket_number"),
            ComponentType.<Integer>builder().codec(Codec.INT).build()
    );

    /** Lottery ticket purchase day (game day when bought). */
    public static final ComponentType<Long> LOTTERY_TICKET_DAY = Registry.register(
            Registries.DATA_COMPONENT_TYPE,
            Identifier.of(PokerMod.MOD_ID, "lottery_ticket_day"),
            ComponentType.<Long>builder().codec(Codec.LONG).build()
    );
}
