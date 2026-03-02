package com.pokermc.block;

import com.mojang.serialization.MapCodec;
import com.pokermc.blockentity.PokerTableBlockEntity;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class PokerTableBlock extends BlockWithEntity {

    public PokerTableBlock(Settings settings) {
        super(settings);
    }

    @Override
    public MapCodec<? extends BlockWithEntity> getCodec() {
        return MapCodec.unit(this);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new PokerTableBlockEntity(pos, state);
    }

    /**
     * Called first when a player right-clicks with an item in hand.
     * We always pass through so that onUse() gets called regardless of held item.
     */
    @Override
    protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world,
                                             BlockPos pos, PlayerEntity player, Hand hand,
                                             BlockHitResult hit) {
        // Only handle main hand to avoid opening screen twice
        if (hand != Hand.MAIN_HAND) return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        // Route to onUse directly
        return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /**
     * Called after onUseWithItem returns PASS_TO_DEFAULT_BLOCK_INTERACTION.
     */
    @Override
    @SuppressWarnings("deprecation")
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        super.onStateReplaced(state, world, pos, newState, moved);
        if (world.isClient || state.isOf(newState.getBlock()) || moved) return;
        BlockEntity be = world.getBlockEntity(pos);
        if (be instanceof PokerTableBlockEntity table) {
            table.refundAllPlayers();
        }
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player,
                                 BlockHitResult hit) {
        if (world.isClient) {
            return ActionResult.SUCCESS;
        }

        player.sendMessage(Text.literal("§6[PokerMC] §fOpening poker table..."), false);

        BlockEntity rawBe = world.getBlockEntity(pos);
        if (rawBe instanceof PokerTableBlockEntity be) {
            be.openFor((ServerPlayerEntity) player);
        } else {
            player.sendMessage(Text.literal("§c[PokerMC] Error: block entity is "
                    + (rawBe == null ? "null" : rawBe.getClass().getSimpleName())), false);
            System.err.println("[PokerMC] onUse: no PokerTableBlockEntity at " + pos + ", got " + rawBe);
        }
        return ActionResult.SUCCESS;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state,
                                                                   BlockEntityType<T> type) {
        if (world.isClient()) return null;
        return validateTicker(type,
                com.pokermc.PokerMod.POKER_TABLE_BLOCK_ENTITY,
                PokerTableBlockEntity::tick);
    }
}
