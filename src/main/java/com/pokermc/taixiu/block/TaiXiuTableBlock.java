package com.pokermc.taixiu.block;

import com.pokermc.PokerMod;
import com.pokermc.taixiu.blockentity.TaiXiuTableBlockEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Tai Xiu (Sic Bo) table block.
 */
public class TaiXiuTableBlock extends Block implements BlockEntityProvider {

    public TaiXiuTableBlock(Settings settings) {
        super(settings);
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new TaiXiuTableBlockEntity(pos, state);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos,
                               PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient()) {
            BlockEntity be = world.getBlockEntity(pos);
            if (be instanceof TaiXiuTableBlockEntity taixiuBe) {
                taixiuBe.openFor((net.minecraft.server.network.ServerPlayerEntity) player);
            }
        }
        return ActionResult.SUCCESS;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return (world1, pos, state1, be) -> {
            if (be instanceof TaiXiuTableBlockEntity taixiuBe) {
                TaiXiuTableBlockEntity.tick(world1, pos, state1, taixiuBe);
            }
        };
    }
}
