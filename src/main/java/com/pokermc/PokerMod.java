package com.pokermc;

import com.pokermc.block.BlackjackTableBlock;
import com.pokermc.block.PokerTableBlock;
import com.pokermc.blockentity.BlackjackTableBlockEntity;
import com.pokermc.blockentity.PokerTableBlockEntity;
import com.pokermc.network.BlackjackNetworking;
import com.pokermc.component.PokerComponents;
import com.pokermc.config.PokerConfig;
import com.pokermc.item.ZCoinBagItem;
import com.pokermc.item.ZCoinItem;
import com.pokermc.network.PokerNetworking;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.MapColor;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class PokerMod implements ModInitializer {

    public static final String MOD_ID = "casinocraft";

    // Block
    public static final PokerTableBlock POKER_TABLE_BLOCK = new PokerTableBlock(
            AbstractBlock.Settings.create()
                    .mapColor(MapColor.DARK_GREEN)
                    .strength(2.5f)
                    .nonOpaque()
    );

    // Items
    public static final ZCoinItem ZCOIN_ITEM = new ZCoinItem(new Item.Settings().maxCount(64));
    public static final ZCoinBagItem ZCOIN_BAG_ITEM = new ZCoinBagItem(new Item.Settings().maxCount(1));

    public static final BlackjackTableBlock BLACKJACK_TABLE_BLOCK = new BlackjackTableBlock(
            AbstractBlock.Settings.create()
                    .mapColor(MapColor.MAGENTA)
                    .strength(2.5f)
                    .nonOpaque()
    );

    // Block items
    public static final BlockItem POKER_TABLE_ITEM = new BlockItem(
            POKER_TABLE_BLOCK,
            new Item.Settings()
    );
    public static final BlockItem BLACKJACK_TABLE_ITEM = new BlockItem(
            BLACKJACK_TABLE_BLOCK,
            new Item.Settings()
    );

    // Block entity types — initialized in onInitialize
    public static BlockEntityType<PokerTableBlockEntity> POKER_TABLE_BLOCK_ENTITY;
    public static BlockEntityType<BlackjackTableBlockEntity> BLACKJACK_TABLE_BLOCK_ENTITY;

    @Override
    public void onInitialize() {
        PokerConfig.get();
        PokerComponents.ZCOIN_BAG_BALANCE.toString(); // ensure component is registered
        // Register block & item
        Registry.register(Registries.BLOCK, Identifier.of(MOD_ID, "poker_table"), POKER_TABLE_BLOCK);
        Registry.register(Registries.ITEM, Identifier.of(MOD_ID, "poker_table"), POKER_TABLE_ITEM);
        Registry.register(Registries.BLOCK, Identifier.of(MOD_ID, "blackjack_table"), BLACKJACK_TABLE_BLOCK);
        Registry.register(Registries.ITEM, Identifier.of(MOD_ID, "blackjack_table"), BLACKJACK_TABLE_ITEM);
        Registry.register(Registries.ITEM, Identifier.of(MOD_ID, "zcoin"), ZCOIN_ITEM);
        Registry.register(Registries.ITEM, Identifier.of(MOD_ID, "zcoin_bag"), ZCOIN_BAG_ITEM);

        // Register block entity type using vanilla builder (no deprecated Fabric wrapper)
        POKER_TABLE_BLOCK_ENTITY = Registry.register(
                Registries.BLOCK_ENTITY_TYPE,
                Identifier.of(MOD_ID, "poker_table"),
                BlockEntityType.Builder.create(PokerTableBlockEntity::new, POKER_TABLE_BLOCK).build()
        );
        BLACKJACK_TABLE_BLOCK_ENTITY = Registry.register(
                Registries.BLOCK_ENTITY_TYPE,
                Identifier.of(MOD_ID, "blackjack_table"),
                BlockEntityType.Builder.create(BlackjackTableBlockEntity::new, BLACKJACK_TABLE_BLOCK).build()
        );

        // Register S2C custom payloads
        PayloadTypeRegistry.playS2C().register(
                PokerNetworking.OpenTablePayload.ID,
                PokerNetworking.OpenTablePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(
                PokerNetworking.GameStatePayload.ID,
                PokerNetworking.GameStatePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(
                BlackjackNetworking.OpenBlackjackPayload.ID,
                BlackjackNetworking.OpenBlackjackPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(
                BlackjackNetworking.BlackjackStatePayload.ID,
                BlackjackNetworking.BlackjackStatePayload.CODEC);

        // Register C2S custom payloads
        PayloadTypeRegistry.playC2S().register(
                PokerNetworking.PlayerActionPayload.ID,
                PokerNetworking.PlayerActionPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(
                BlackjackNetworking.BlackjackActionPayload.ID,
                BlackjackNetworking.BlackjackActionPayload.CODEC);

        // Handle incoming C2S player actions on the server
        ServerPlayNetworking.registerGlobalReceiver(
                PokerNetworking.PlayerActionPayload.ID,
                (payload, context) -> context.server().execute(
                        () -> PokerNetworking.handlePlayerAction(context.player(), payload)
                )
        );
        ServerPlayNetworking.registerGlobalReceiver(
                BlackjackNetworking.BlackjackActionPayload.ID,
                (payload, context) -> context.server().execute(
                        () -> BlackjackNetworking.handleAction(context.player(), payload)
                )
        );

        // Create CasinoCraft creative tab
        Registry.register(Registries.ITEM_GROUP,
                Identifier.of(MOD_ID, "main"),
                FabricItemGroup.builder()
                        .icon(() -> new ItemStack(POKER_TABLE_ITEM))
                        .displayName(Text.translatable("itemGroup.casinocraft"))
                        .entries((context, entries) -> {
                            entries.add(POKER_TABLE_ITEM);
                            entries.add(BLACKJACK_TABLE_ITEM);
                            entries.add(ZCOIN_ITEM);
                            entries.add(ZCOIN_BAG_ITEM);
                        })
                        .build());

        System.out.println("[CasinoCraft] Initialized. Bet item: " + PokerConfig.get().betItemId
                + " | BE type: " + POKER_TABLE_BLOCK_ENTITY);
    }
}
