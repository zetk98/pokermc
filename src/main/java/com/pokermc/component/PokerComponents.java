package com.pokermc.component;

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
}
