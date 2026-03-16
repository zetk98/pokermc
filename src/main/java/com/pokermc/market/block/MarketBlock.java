package com.pokermc.market.block;

import com.mojang.serialization.MapCodec;
import com.pokermc.common.network.CloseGamePayload;
import com.pokermc.market.blockentity.MarketBlockEntity;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class MarketBlock extends BlockWithEntity {

    public MarketBlock(Settings settings) {
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
        return new MarketBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onStateReplaced(BlockState state, net.minecraft.server.world.ServerWorld world, BlockPos pos, boolean moved) {
        if (!moved) {
            var center = pos.getX() + 0.5;
            var players = world.getPlayers().stream()
                    .filter(p -> p.squaredDistanceTo(center, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64 * 64)
                    .toList();
            CloseGamePayload.sendToAll(players, pos);
        }
        super.onStateReplaced(state, world, pos, moved);
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player,
                                 BlockHitResult hit) {
        if (world.isClient()) return ActionResult.SUCCESS;
        BlockEntity be = world.getBlockEntity(pos);
        if (be instanceof MarketBlockEntity market) {
            market.openFor((net.minecraft.server.network.ServerPlayerEntity) player);
        }
        return ActionResult.SUCCESS;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state,
                                                                    BlockEntityType<T> type) {
        return null;
    }
}
