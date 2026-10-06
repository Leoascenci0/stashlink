package io.github.leoascenci0.stashlink.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Bloco da "gaveta" de teste (Item 26): só existe nos testes. */
public final class ApiDrawerBlock extends BaseEntityBlock {
    public ApiDrawerBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ApiDrawerBlockEntity(pos, state);
    }
}
