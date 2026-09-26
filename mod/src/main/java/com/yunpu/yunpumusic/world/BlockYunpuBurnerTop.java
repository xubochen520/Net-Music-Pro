package com.yunpu.yunpumusic.world;

import com.mojang.serialization.MapCodec;
import com.yunpu.yunpumusic.init.YunpuBlocks;
import com.yunpu.yunpumusic.menu.YunpuMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 云谱刻录台（上段）：登录终端屏幕与顶盖。
 *
 * <p>它不能独立存在于世界中——只能由下段方块放置，并在下段被破坏时一并移除。
 * 自身不持有方块实体，所有状态都与下段共享。
 */
public class BlockYunpuBurnerTop extends HorizontalDirectionalBlock {
    private static final MapCodec<BlockYunpuBurnerTop> CODEC = simpleCodec(properties -> new BlockYunpuBurnerTop());

    public BlockYunpuBurnerTop() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOD)
                .sound(SoundType.WOOD)
                .strength(1.5F, 6.0F)
                .noOcclusion()
                .noLootTable()
                .pushReaction(PushReaction.BLOCK)
                .isViewBlocking((state, level, pos) -> false));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return BlockYunpuBurner.TOP_SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return BlockYunpuBurner.TOP_SHAPE;
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, net.minecraft.world.level.storage.loot.LootParams.Builder builder) {
        // 掉落物由下段负责，上段不掉落任何东西
        return List.of();
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // 只能作为下段的附属出现
        return null;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        // 交互转发给下段，保证从屏幕一侧点击也能打开终端
        BlockPos below = pos.below();
        if (level.getBlockState(below).is(YunpuBlocks.YUNPU_BURNER.get())) {
            if (level.getBlockEntity(below) instanceof BlockEntityYunpuBurner && player instanceof ServerPlayer serverPlayer) {
                serverPlayer.openMenu(new net.minecraft.world.SimpleMenuProvider(
                        (containerId, inventory, ignored) -> new YunpuMenu(containerId, inventory, below),
                        Component.translatable("gui.yunpumusic.title")), buffer -> buffer.writeBlockPos(below));
                return InteractionResult.CONSUME;
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide) {
            BlockPos below = pos.below();
            if (level.getBlockState(below).is(YunpuBlocks.YUNPU_BURNER.get())) {
                level.destroyBlock(below, true);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public boolean canSurvive(BlockState state, net.minecraft.world.level.LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.below()).is(YunpuBlocks.YUNPU_BURNER.get());
    }
}
