package com.pokermc;

import com.pokermc.block.PokerTableBlock;
import com.pokermc.blockentity.PokerTableBlockEntity;
import com.pokermc.config.PokerConfig;
import com.pokermc.network.PokerNetworking;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.MapColor;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class PokerMod implements ModInitializer {

    public static final String MOD_ID = "pokermc";

    // Block
    public static final PokerTableBlock POKER_TABLE_BLOCK = new PokerTableBlock(
            AbstractBlock.Settings.create()
                    .mapColor(MapColor.DARK_GREEN)
                    .strength(2.5f)
                    .nonOpaque()
    );

    // Block item
    public static final BlockItem POKER_TABLE_ITEM = new BlockItem(
            POKER_TABLE_BLOCK,
            new Item.Settings()
    );

    // Block entity type — initialized in onInitialize
    public static BlockEntityType<PokerTableBlockEntity> POKER_TABLE_BLOCK_ENTITY;

    @Override
    public void onInitialize() {
        PokerConfig.get();

        // Register block & item
        Registry.register(Registries.BLOCK, Identifier.of(MOD_ID, "poker_table"), POKER_TABLE_BLOCK);
        Registry.register(Registries.ITEM, Identifier.of(MOD_ID, "poker_table"), POKER_TABLE_ITEM);

        // Register block entity type using vanilla builder (no deprecated Fabric wrapper)
        POKER_TABLE_BLOCK_ENTITY = Registry.register(
                Registries.BLOCK_ENTITY_TYPE,
                Identifier.of(MOD_ID, "poker_table"),
                BlockEntityType.Builder.create(PokerTableBlockEntity::new, POKER_TABLE_BLOCK).build()
        );

        // Register S2C custom payloads
        PayloadTypeRegistry.playS2C().register(
                PokerNetworking.OpenTablePayload.ID,
                PokerNetworking.OpenTablePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(
                PokerNetworking.GameStatePayload.ID,
                PokerNetworking.GameStatePayload.CODEC);

        // Register C2S custom payload
        PayloadTypeRegistry.playC2S().register(
                PokerNetworking.PlayerActionPayload.ID,
                PokerNetworking.PlayerActionPayload.CODEC);

        // Handle incoming C2S player actions on the server
        ServerPlayNetworking.registerGlobalReceiver(
                PokerNetworking.PlayerActionPayload.ID,
                (payload, context) -> context.server().execute(
                        () -> PokerNetworking.handlePlayerAction(context.player(), payload)
                )
        );

        // Add poker table to the Functional Blocks creative tab
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(entries ->
                entries.add(POKER_TABLE_ITEM));

        System.out.println("[PokerMC] Initialized. Bet item: " + PokerConfig.get().betItemId
                + " | BE type: " + POKER_TABLE_BLOCK_ENTITY);
    }
}
