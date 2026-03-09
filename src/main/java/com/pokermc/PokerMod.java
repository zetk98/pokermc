package com.pokermc;

import com.pokermc.bang.block.BangTableBlock;
import com.pokermc.blackjack.block.BlackjackTableBlock;
import com.pokermc.poker.block.PokerTableBlock;
import com.pokermc.bang.blockentity.BangTableBlockEntity;
import com.pokermc.blackjack.blockentity.BlackjackTableBlockEntity;
import com.pokermc.poker.blockentity.PokerTableBlockEntity;
import com.pokermc.bang.network.BangNetworking;
import com.pokermc.blackjack.network.BlackjackNetworking;
import com.pokermc.common.component.PokerComponents;
import com.pokermc.common.config.PokerConfig;
import com.pokermc.common.item.ZCoinBagItem;
import com.pokermc.common.item.ZCoinItem;
import com.pokermc.poker.network.PokerNetworking;
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
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
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
                    .registryKey(RegistryKey.of(RegistryKeys.BLOCK, Identifier.of(MOD_ID, "poker_table")))
    );

    // Items
    public static final ZCoinItem ZCOIN_ITEM = new ZCoinItem(new Item.Settings()
            .maxCount(64)
            .registryKey(RegistryKey.of(RegistryKeys.ITEM, Identifier.of(MOD_ID, "zcoin"))));
    public static final ZCoinBagItem ZCOIN_BAG_ITEM = new ZCoinBagItem(new Item.Settings()
            .maxCount(1)
            .registryKey(RegistryKey.of(RegistryKeys.ITEM, Identifier.of(MOD_ID, "zcoin_bag"))));

    public static final BlackjackTableBlock BLACKJACK_TABLE_BLOCK = new BlackjackTableBlock(
            AbstractBlock.Settings.create()
                    .mapColor(MapColor.MAGENTA)
                    .strength(2.5f)
                    .nonOpaque()
                    .registryKey(RegistryKey.of(RegistryKeys.BLOCK, Identifier.of(MOD_ID, "blackjack_table")))
    );

    public static final BangTableBlock BANG_TABLE_BLOCK = new BangTableBlock(
            AbstractBlock.Settings.create()
                    .mapColor(MapColor.TERRACOTTA_ORANGE)
                    .strength(2.5f)
                    .nonOpaque()
                    .registryKey(RegistryKey.of(RegistryKeys.BLOCK, Identifier.of(MOD_ID, "bang_table")))
    );

    // Block items
    public static final BlockItem POKER_TABLE_ITEM = new BlockItem(
            POKER_TABLE_BLOCK,
            new Item.Settings().registryKey(RegistryKey.of(RegistryKeys.ITEM, Identifier.of(MOD_ID, "poker_table")))
    );
    public static final BlockItem BLACKJACK_TABLE_ITEM = new BlockItem(
            BLACKJACK_TABLE_BLOCK,
            new Item.Settings().registryKey(RegistryKey.of(RegistryKeys.ITEM, Identifier.of(MOD_ID, "blackjack_table")))
    );
    public static final BlockItem BANG_TABLE_ITEM = new BlockItem(
            BANG_TABLE_BLOCK,
            new Item.Settings().registryKey(RegistryKey.of(RegistryKeys.ITEM, Identifier.of(MOD_ID, "bang_table")))
    );

    // Block entity types — initialized in onInitialize
    public static BlockEntityType<PokerTableBlockEntity> POKER_TABLE_BLOCK_ENTITY;
    public static BlockEntityType<BlackjackTableBlockEntity> BLACKJACK_TABLE_BLOCK_ENTITY;
    public static BlockEntityType<BangTableBlockEntity> BANG_TABLE_BLOCK_ENTITY;

    @Override
    public void onInitialize() {
        PokerConfig.get();
        PokerComponents.ZCOIN_BAG_BALANCE.toString(); // ensure component is registered
        // Register block & item
        Registry.register(Registries.BLOCK, Identifier.of(MOD_ID, "poker_table"), POKER_TABLE_BLOCK);
        Registry.register(Registries.ITEM, Identifier.of(MOD_ID, "poker_table"), POKER_TABLE_ITEM);
        Registry.register(Registries.BLOCK, Identifier.of(MOD_ID, "blackjack_table"), BLACKJACK_TABLE_BLOCK);
        Registry.register(Registries.ITEM, Identifier.of(MOD_ID, "blackjack_table"), BLACKJACK_TABLE_ITEM);
        Registry.register(Registries.BLOCK, Identifier.of(MOD_ID, "bang_table"), BANG_TABLE_BLOCK);
        Registry.register(Registries.ITEM, Identifier.of(MOD_ID, "bang_table"), BANG_TABLE_ITEM);
        Registry.register(Registries.ITEM, Identifier.of(MOD_ID, "zcoin"), ZCOIN_ITEM);
        Registry.register(Registries.ITEM, Identifier.of(MOD_ID, "zcoin_bag"), ZCOIN_BAG_ITEM);

        // Register block entity type using vanilla builder (no deprecated Fabric wrapper)
        POKER_TABLE_BLOCK_ENTITY = Registry.register(
                Registries.BLOCK_ENTITY_TYPE,
                Identifier.of(MOD_ID, "poker_table"),
                net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder.create(PokerTableBlockEntity::new, POKER_TABLE_BLOCK).build()
        );
        BLACKJACK_TABLE_BLOCK_ENTITY = Registry.register(
                Registries.BLOCK_ENTITY_TYPE,
                Identifier.of(MOD_ID, "blackjack_table"),
                net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder.create(BlackjackTableBlockEntity::new, BLACKJACK_TABLE_BLOCK).build()
        );
        BANG_TABLE_BLOCK_ENTITY = Registry.register(
                Registries.BLOCK_ENTITY_TYPE,
                Identifier.of(MOD_ID, "bang_table"),
                net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder.create(BangTableBlockEntity::new, BANG_TABLE_BLOCK).build()
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
        PayloadTypeRegistry.playS2C().register(
                BangNetworking.OpenBangPayload.ID,
                BangNetworking.OpenBangPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(
                BangNetworking.BangStatePayload.ID,
                BangNetworking.BangStatePayload.CODEC);

        // Register C2S custom payloads
        PayloadTypeRegistry.playC2S().register(
                PokerNetworking.PlayerActionPayload.ID,
                PokerNetworking.PlayerActionPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(
                BlackjackNetworking.BlackjackActionPayload.ID,
                BlackjackNetworking.BlackjackActionPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(
                BangNetworking.BangActionPayload.ID,
                BangNetworking.BangActionPayload.CODEC);

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
        ServerPlayNetworking.registerGlobalReceiver(
                BangNetworking.BangActionPayload.ID,
                (payload, context) -> context.server().execute(
                        () -> BangNetworking.handleAction(context.player(), payload)
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
                            entries.add(BANG_TABLE_ITEM);
                        })
                        .build());

        System.out.println("[CasinoCraft] Initialized. Bet item: " + PokerConfig.get().betItemId
                + " | BE type: " + POKER_TABLE_BLOCK_ENTITY);
    }
}
