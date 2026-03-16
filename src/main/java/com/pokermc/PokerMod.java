package com.pokermc;

import com.pokermc.bang.block.BangTableBlock;
import com.pokermc.blackjack.block.BlackjackTableBlock;
import com.pokermc.common.network.CloseGamePayload;
import com.pokermc.market.block.MarketBlock;
import com.pokermc.stock.block.StockExchangeBlock;
import com.pokermc.stock.blockentity.StockExchangeBlockEntity;
import com.pokermc.stock.item.StockCertificateItem;
import com.pokermc.stock.network.StockNetworking;
import com.pokermc.market.blockentity.MarketBlockEntity;
import com.pokermc.goldenticket.block.GoldenTicketBlock;
import com.pokermc.goldenticket.blockentity.GoldenTicketBlockEntity;
import com.pokermc.goldenticket.item.GoldenTicketItem;
import com.pokermc.poker.block.PokerTableBlock;
import com.pokermc.xoso.block.XosoBlock;
import com.pokermc.bang.blockentity.BangTableBlockEntity;
import com.pokermc.blackjack.blockentity.BlackjackTableBlockEntity;
import com.pokermc.poker.blockentity.PokerTableBlockEntity;
import com.pokermc.xoso.blockentity.XosoBlockEntity;
import com.pokermc.bang.network.BangNetworking;
import com.pokermc.blackjack.network.BlackjackNetworking;
import com.pokermc.market.game.MarketTicker;
import com.pokermc.xoso.game.LotteryPrizeScheduler;
import com.pokermc.market.network.MarketNetworking;
import com.pokermc.goldenticket.network.GoldenTicketNetworking;
import com.pokermc.xoso.network.XosoNetworking;
import com.pokermc.common.component.PokerComponents;
import com.pokermc.common.config.PokerConfig;
import com.pokermc.common.item.ZCoinBagItem;
import com.pokermc.common.item.ZCoinItem;
import com.pokermc.xoso.item.LotteryTicketItem;
import com.pokermc.poker.network.PokerNetworking;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.command.CommandManager;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class PokerMod implements ModInitializer {

    public static final String MOD_ID = "casinocraft";

    // Blocks, block items, block entity types — initialized in onInitialize (1.21+ requires register-then-use)
    public static PokerTableBlock POKER_TABLE_BLOCK;
    public static BlackjackTableBlock BLACKJACK_TABLE_BLOCK;
    public static BangTableBlock BANG_TABLE_BLOCK;
    public static XosoBlock XOSO_BLOCK;
    public static MarketBlock MARKET_BLOCK;
    public static GoldenTicketBlock GOLDEN_TICKET_BLOCK;
    public static StockExchangeBlock STOCK_EXCHANGE_BLOCK;
    public static BlockItem POKER_TABLE_ITEM;
    public static BlockItem BLACKJACK_TABLE_ITEM;
    public static BlockItem BANG_TABLE_ITEM;
    public static BlockItem XOSO_TABLE_ITEM;
    public static BlockItem MARKET_TABLE_ITEM;
    public static BlockItem GOLDEN_TICKET_TABLE_ITEM;
    public static BlockItem STOCK_EXCHANGE_ITEM;
    public static ZCoinItem ZCOIN_ITEM;
    public static ZCoinBagItem ZCOIN_BAG_ITEM;
    public static LotteryTicketItem LOTTERY_TICKET_ITEM;
    public static GoldenTicketItem GOLDEN_TICKET_ITEM;
    public static StockCertificateItem STOCK_CERTIFICATE_ITEM;
    public static BlockEntityType<PokerTableBlockEntity> POKER_TABLE_BLOCK_ENTITY;
    public static BlockEntityType<BlackjackTableBlockEntity> BLACKJACK_TABLE_BLOCK_ENTITY;
    public static BlockEntityType<BangTableBlockEntity> BANG_TABLE_BLOCK_ENTITY;
    public static BlockEntityType<XosoBlockEntity> XOSO_BLOCK_ENTITY;
    public static BlockEntityType<MarketBlockEntity> MARKET_BLOCK_ENTITY;
    public static BlockEntityType<GoldenTicketBlockEntity> GOLDEN_TICKET_BLOCK_ENTITY;
    public static BlockEntityType<StockExchangeBlockEntity> STOCK_EXCHANGE_BLOCK_ENTITY;

    private static <T extends Block> T registerBlock(String name, T block, boolean withItem) {
        RegistryKey<Block> blockKey = RegistryKey.of(RegistryKeys.BLOCK, Identifier.of(MOD_ID, name));
        T registered = (T) Registry.register(Registries.BLOCK, blockKey, block);
        if (withItem) {
            BlockItem item = new BlockItem(registered, new Item.Settings()
                    .registryKey(RegistryKey.of(RegistryKeys.ITEM, Identifier.of(MOD_ID, name)))
                    .useBlockPrefixedTranslationKey()
                    .component(DataComponentTypes.ITEM_MODEL, Identifier.of(MOD_ID, "item/" + name)));
            Registry.register(Registries.ITEM, Identifier.of(MOD_ID, name), item);
        }
        return registered;
    }

    @Override
    public void onInitialize() {
        PokerConfig.get();
        LotteryPrizeScheduler.register();
        MarketTicker.register();
        PokerComponents.ZCOIN_BAG_BALANCE.toString(); // ensure components are registered
        PokerComponents.LOTTERY_TICKET_NUMBER.toString();
        PokerComponents.LOTTERY_TICKET_DAY.toString();
        PokerComponents.GOLDEN_TICKET_TIER.toString();

        // Create and register blocks (must register immediately in 1.21+)
        POKER_TABLE_BLOCK = registerBlock("poker_table", new PokerTableBlock(
                AbstractBlock.Settings.create()
                        .mapColor(MapColor.DARK_GREEN)
                        .strength(2.5f)
                        .nonOpaque()
                        .registryKey(RegistryKey.of(RegistryKeys.BLOCK, Identifier.of(MOD_ID, "poker_table")))),
                true);
        POKER_TABLE_ITEM = (BlockItem) Registries.ITEM.get(Identifier.of(MOD_ID, "poker_table"));

        BLACKJACK_TABLE_BLOCK = registerBlock("blackjack_table", new BlackjackTableBlock(
                AbstractBlock.Settings.create()
                        .mapColor(MapColor.MAGENTA)
                        .strength(2.5f)
                        .nonOpaque()
                        .registryKey(RegistryKey.of(RegistryKeys.BLOCK, Identifier.of(MOD_ID, "blackjack_table")))),
                true);
        BLACKJACK_TABLE_ITEM = (BlockItem) Registries.ITEM.get(Identifier.of(MOD_ID, "blackjack_table"));

        BANG_TABLE_BLOCK = registerBlock("bang_table", new BangTableBlock(
                AbstractBlock.Settings.create()
                        .mapColor(MapColor.TERRACOTTA_ORANGE)
                        .strength(2.5f)
                        .nonOpaque()
                        .registryKey(RegistryKey.of(RegistryKeys.BLOCK, Identifier.of(MOD_ID, "bang_table")))),
                true);
        BANG_TABLE_ITEM = (BlockItem) Registries.ITEM.get(Identifier.of(MOD_ID, "bang_table"));

        XOSO_BLOCK = registerBlock("xoso_table", new XosoBlock(
                AbstractBlock.Settings.create()
                        .mapColor(MapColor.YELLOW)
                        .strength(2.5f)
                        .nonOpaque()
                        .registryKey(RegistryKey.of(RegistryKeys.BLOCK, Identifier.of(MOD_ID, "xoso_table")))),
                true);
        XOSO_TABLE_ITEM = (BlockItem) Registries.ITEM.get(Identifier.of(MOD_ID, "xoso_table"));

        MARKET_BLOCK = registerBlock("market", new MarketBlock(
                AbstractBlock.Settings.create()
                        .mapColor(MapColor.CYAN)
                        .strength(2.5f)
                        .nonOpaque()
                        .registryKey(RegistryKey.of(RegistryKeys.BLOCK, Identifier.of(MOD_ID, "market")))),
                true);
        MARKET_TABLE_ITEM = (BlockItem) Registries.ITEM.get(Identifier.of(MOD_ID, "market"));

        GOLDEN_TICKET_BLOCK = registerBlock("golden_ticket_table", new GoldenTicketBlock(
                AbstractBlock.Settings.create()
                        .mapColor(MapColor.GOLD)
                        .strength(2.5f)
                        .nonOpaque()
                        .registryKey(RegistryKey.of(RegistryKeys.BLOCK, Identifier.of(MOD_ID, "golden_ticket_table")))),
                true);
        GOLDEN_TICKET_TABLE_ITEM = (BlockItem) Registries.ITEM.get(Identifier.of(MOD_ID, "golden_ticket_table"));

        STOCK_EXCHANGE_BLOCK = registerBlock("stock_exchange", new StockExchangeBlock(
                AbstractBlock.Settings.create()
                        .mapColor(MapColor.DIAMOND_BLUE)
                        .strength(2.5f)
                        .nonOpaque()
                        .registryKey(RegistryKey.of(RegistryKeys.BLOCK, Identifier.of(MOD_ID, "stock_exchange")))),
                true);
        STOCK_EXCHANGE_ITEM = (BlockItem) Registries.ITEM.get(Identifier.of(MOD_ID, "stock_exchange"));

        ZCOIN_ITEM = Registry.register(Registries.ITEM, Identifier.of(MOD_ID, "zcoin"),
                new ZCoinItem(new Item.Settings().maxCount(64)
                        .registryKey(RegistryKey.of(RegistryKeys.ITEM, Identifier.of(MOD_ID, "zcoin")))
                        .component(DataComponentTypes.ITEM_MODEL, Identifier.of(MOD_ID, "item/zcoin"))));
        ZCOIN_BAG_ITEM = Registry.register(Registries.ITEM, Identifier.of(MOD_ID, "zcoin_bag"),
                new ZCoinBagItem(new Item.Settings().maxCount(1)
                        .registryKey(RegistryKey.of(RegistryKeys.ITEM, Identifier.of(MOD_ID, "zcoin_bag")))
                        .component(DataComponentTypes.ITEM_MODEL, Identifier.of(MOD_ID, "item/zcoin_bag"))));
        LOTTERY_TICKET_ITEM = Registry.register(Registries.ITEM, Identifier.of(MOD_ID, "lottery_ticket"),
                new LotteryTicketItem(new Item.Settings().maxCount(16)
                        .registryKey(RegistryKey.of(RegistryKeys.ITEM, Identifier.of(MOD_ID, "lottery_ticket")))
                        .component(DataComponentTypes.ITEM_MODEL, Identifier.of(MOD_ID, "item/lottery_ticket"))));
        GOLDEN_TICKET_ITEM = Registry.register(Registries.ITEM, Identifier.of(MOD_ID, "golden_ticket"),
                new GoldenTicketItem(new Item.Settings().maxCount(64)
                        .registryKey(RegistryKey.of(RegistryKeys.ITEM, Identifier.of(MOD_ID, "golden_ticket")))
                        .component(DataComponentTypes.ITEM_MODEL, Identifier.of(MOD_ID, "item/golden_ticket"))));
        STOCK_CERTIFICATE_ITEM = Registry.register(Registries.ITEM, Identifier.of(MOD_ID, "stock_certificate"),
                new StockCertificateItem(new Item.Settings().maxCount(64)
                        .registryKey(RegistryKey.of(RegistryKeys.ITEM, Identifier.of(MOD_ID, "stock_certificate")))
                        .component(DataComponentTypes.ITEM_MODEL, Identifier.of(MOD_ID, "item/stock_certificate"))));

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
        XOSO_BLOCK_ENTITY = Registry.register(
                Registries.BLOCK_ENTITY_TYPE,
                Identifier.of(MOD_ID, "xoso_table"),
                net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder.create(XosoBlockEntity::new, XOSO_BLOCK).build()
        );
        MARKET_BLOCK_ENTITY = Registry.register(
                Registries.BLOCK_ENTITY_TYPE,
                Identifier.of(MOD_ID, "market"),
                net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder.create(MarketBlockEntity::new, MARKET_BLOCK).build()
        );
        GOLDEN_TICKET_BLOCK_ENTITY = Registry.register(
                Registries.BLOCK_ENTITY_TYPE,
                Identifier.of(MOD_ID, "golden_ticket_table"),
                net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder.create(GoldenTicketBlockEntity::new, GOLDEN_TICKET_BLOCK).build()
        );
        STOCK_EXCHANGE_BLOCK_ENTITY = Registry.register(
                Registries.BLOCK_ENTITY_TYPE,
                Identifier.of(MOD_ID, "stock_exchange"),
                net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder.create(StockExchangeBlockEntity::new, STOCK_EXCHANGE_BLOCK).build()
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
        PayloadTypeRegistry.playS2C().register(
                XosoNetworking.OpenXosoPayload.ID,
                XosoNetworking.OpenXosoPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(
                XosoNetworking.XosoStatePayload.ID,
                XosoNetworking.XosoStatePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(
                GoldenTicketNetworking.OpenGoldenTicketPayload.ID,
                GoldenTicketNetworking.OpenGoldenTicketPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(
                MarketNetworking.OpenMarketPayload.ID,
                MarketNetworking.OpenMarketPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(
                MarketNetworking.MarketStatePayload.ID,
                MarketNetworking.MarketStatePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(
                CloseGamePayload.ID,
                CloseGamePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(
                com.pokermc.common.network.CloseScreenPayload.ID,
                com.pokermc.common.network.CloseScreenPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(
                StockNetworking.OpenStockExchangePayload.ID,
                StockNetworking.OpenStockExchangePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(
                StockNetworking.StockStatePayload.ID,
                StockNetworking.StockStatePayload.CODEC);

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
        PayloadTypeRegistry.playC2S().register(
                XosoNetworking.XosoActionPayload.ID,
                XosoNetworking.XosoActionPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(
                GoldenTicketNetworking.GoldenTicketActionPayload.ID,
                GoldenTicketNetworking.GoldenTicketActionPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(
                MarketNetworking.MarketActionPayload.ID,
                MarketNetworking.MarketActionPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(
                MarketNetworking.RequestMarketRefreshPayload.ID,
                MarketNetworking.RequestMarketRefreshPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(
                StockNetworking.StockActionPayload.ID,
                StockNetworking.StockActionPayload.CODEC);

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
        ServerPlayNetworking.registerGlobalReceiver(
                XosoNetworking.XosoActionPayload.ID,
                (payload, context) -> context.server().execute(
                        () -> XosoNetworking.handleAction(context.player(), payload)
                )
        );
        ServerPlayNetworking.registerGlobalReceiver(
                GoldenTicketNetworking.GoldenTicketActionPayload.ID,
                (payload, context) -> context.server().execute(
                        () -> GoldenTicketNetworking.handleAction(context.player(), payload)
                )
        );
        ServerPlayNetworking.registerGlobalReceiver(
                MarketNetworking.MarketActionPayload.ID,
                (payload, context) -> context.server().execute(
                        () -> MarketNetworking.handleAction(context.player(), payload)
                )
        );
        ServerPlayNetworking.registerGlobalReceiver(
                MarketNetworking.RequestMarketRefreshPayload.ID,
                (payload, context) -> context.server().execute(
                        () -> MarketNetworking.handleRefreshRequest(context.player(), payload)
                )
        );
        ServerPlayNetworking.registerGlobalReceiver(
                StockNetworking.StockActionPayload.ID,
                (payload, context) -> context.server().execute(
                        () -> StockNetworking.handleAction(context.player(), payload)
                )
        );

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("day").executes(context -> {
                var source = context.getSource();
                var world = source.getWorld();
                long day = world.getTime() / 24000;
                source.sendFeedback(() -> Text.literal("§6[Day] §fCurrent day: §e" + day + " §7(lottery: check table for period)"), false);
                return 1;
            }));
        });

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
                            entries.add(XOSO_TABLE_ITEM);
                            entries.add(LOTTERY_TICKET_ITEM);
                            entries.add(MARKET_TABLE_ITEM);
                            entries.add(GOLDEN_TICKET_TABLE_ITEM);
                            entries.add(GOLDEN_TICKET_ITEM);
                            entries.add(STOCK_EXCHANGE_ITEM);
                            entries.add(STOCK_CERTIFICATE_ITEM);
                        })
                        .build());

        System.out.println("[CasinoCraft] Initialized. Bet item: " + PokerConfig.get().betItemId
                + " | BE type: " + POKER_TABLE_BLOCK_ENTITY);
    }
}
