package com.pokermc.block;

import com.mojang.serialization.MapCodec;
import com.pokermc.blockentity.BlackjackTableBlockEntity;
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

public class BlackjackTableBlock extends BlockWithEntity {

    public BlackjackTableBlock(Settings settings) {
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
        return new BlackjackTableBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        super.onStateReplaced(state, world, pos, newState, moved);
        if (world.isClient || state.isOf(newState.getBlock()) || moved) return;
        BlockEntity be = world.getBlockEntity(pos);
        if (be instanceof BlackjackTableBlockEntity table) table.refundAllPlayers();
    }

    @Override
    protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world,
                                             BlockPos pos, PlayerEntity player, Hand hand,
                                             BlockHitResult hit) {
        if (hand != Hand.MAIN_HAND) return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player,
                                 BlockHitResult hit) {
        if (world.isClient) return ActionResult.SUCCESS;

        player.sendMessage(Text.literal("§d[CasinoCraft] §fMở bàn Blackjack..."), false);

        BlockEntity rawBe = world.getBlockEntity(pos);
        if (rawBe instanceof BlackjackTableBlockEntity be) {
            be.openFor((ServerPlayerEntity) player);
        } else {
            player.sendMessage(Text.literal("§c[CasinoCraft] Lỗi: block entity không hợp lệ"), false);
        }
        return ActionResult.SUCCESS;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state,
                                                                   BlockEntityType<T> type) {
        if (world.isClient()) return null;
        return validateTicker(type,
                com.pokermc.PokerMod.BLACKJACK_TABLE_BLOCK_ENTITY,
                BlackjackTableBlockEntity::tick);
    }
}
