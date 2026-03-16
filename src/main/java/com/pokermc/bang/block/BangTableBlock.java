package com.pokermc.bang.block;

import com.mojang.serialization.MapCodec;
import com.pokermc.bang.blockentity.BangTableBlockEntity;
import com.pokermc.common.network.CloseGamePayload;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class BangTableBlock extends BlockWithEntity {

    public BangTableBlock(Settings settings) {
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
        return new BangTableBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onStateReplaced(BlockState state, net.minecraft.server.world.ServerWorld world, BlockPos pos, boolean moved) {
        if (!moved) {
            var be = world.getBlockEntity(pos);
            if (be instanceof BangTableBlockEntity table) {
                var viewers = table.getViewers();
                for (var p : viewers) BangTableBlockEntity.clearPlayerTable(p.getUuid());
                CloseGamePayload.sendToAll(viewers, pos);
            }
        }
        super.onStateReplaced(state, world, pos, moved);
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player,
                                 BlockHitResult hit) {
        if (world.isClient()) return ActionResult.SUCCESS;

        BlockEntity rawBe = world.getBlockEntity(pos);
        if (rawBe instanceof BangTableBlockEntity be) {
            be.openFor((ServerPlayerEntity) player);
        }
        return ActionResult.SUCCESS;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state,
                                                                   BlockEntityType<T> type) {
        if (world.isClient()) return null;
        return validateTicker(type,
                com.pokermc.PokerMod.BANG_TABLE_BLOCK_ENTITY,
                BangTableBlockEntity::tick);
    }
}
