package com.pokermc.stock.block;

import com.mojang.serialization.MapCodec;
import com.pokermc.PokerMod;
import com.pokermc.stock.blockentity.StockExchangeBlockEntity;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Stock Exchange Block - opens the stock trading GUI when right-clicked.
 */
public class StockExchangeBlock extends BlockWithEntity {

    public StockExchangeBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.NORTH));
    }

    @Override
    public MapCodec<? extends BlockWithEntity> getCodec() {
        return MapCodec.unit(this);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(Properties.HORIZONTAL_FACING);
    }

    @Nullable
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        Direction direction = ctx.getPlayerLookDirection();
        // Only use horizontal directions (not UP or DOWN)
        if (direction.getAxis() == Direction.Axis.Y) {
            direction = Direction.NORTH;
        }
        return getDefaultState().with(Properties.HORIZONTAL_FACING, direction.getOpposite());
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new StockExchangeBlockEntity(pos, state);
    }

    /**
     * Called first when a player right-clicks with an item in hand.
     * We always pass through so that onUse() gets called regardless of held item.
     */
    @Override
    protected ActionResult onUseWithItem(ItemStack stack, BlockState state, World world,
                                             BlockPos pos, PlayerEntity player, Hand hand,
                                             BlockHitResult hit) {
        if (hand != Hand.MAIN_HAND) return ActionResult.PASS_TO_DEFAULT_BLOCK_ACTION;
        return ActionResult.PASS_TO_DEFAULT_BLOCK_ACTION;
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player,
                                 BlockHitResult hit) {
        if (world.isClient()) {
            return ActionResult.SUCCESS;
        }

        player.sendMessage(Text.literal("§6[Stock Exchange] §fOpening stock market..."), false);

        BlockEntity rawBe = world.getBlockEntity(pos);
        if (rawBe instanceof StockExchangeBlockEntity be) {
            be.openFor((ServerPlayerEntity) player);
        } else {
            player.sendMessage(Text.literal("§c[Stock Exchange] Error: block entity is "
                    + (rawBe == null ? "null" : rawBe.getClass().getSimpleName())), false);
            System.err.println("[Stock Exchange] onUse: no StockExchangeBlockEntity at " + pos + ", got " + rawBe);
        }
        return ActionResult.SUCCESS;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState blockState, BlockEntityType<T> type) {
        if (world.isClient()) return null;
        return validateTicker(type,
                PokerMod.STOCK_EXCHANGE_BLOCK_ENTITY,
                StockExchangeBlockEntity::tick);
    }
}
