package com.yunpu.yunpumusic.world;

import com.mojang.serialization.MapCodec;
import com.yunpu.yunpumusic.YunpuMusic;
import com.yunpu.yunpumusic.init.YunpuBlocks;
import com.yunpu.yunpumusic.menu.YunpuMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.Containers;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * 云谱刻录台（下段）：主机柜、刻录盘与操作面。
 *
 * <p>占用两格高度：上方紧邻的一格由 {@link BlockYunpuBurnerTop} 承载，
 * 两格共享同一个方块实体。模型正面朝向由 {@code facing} 决定，
 * 终端屏幕位于 {@code facing} 方向的反面（与模型的后方支架一致）。
 */
public class BlockYunpuBurner extends HorizontalDirectionalBlock implements EntityBlock {
    private static final MapCodec<BlockYunpuBurner> CODEC = simpleCodec(properties -> new BlockYunpuBurner());

    /** 下段碰撞箱：机身本体。 */
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(0, 0, 0, 16, 2, 16),
            Block.box(1, 2, 1, 15, 14, 15));
    /** 上段（屏幕 / 支架）碰撞箱，用于上段方块的轮廓。 */
    static final VoxelShape TOP_SHAPE = Shapes.or(
            Block.box(1, 0, 12, 15, 13, 16),
            Block.box(0, 0, 11, 2, 14, 16),
            Block.box(14, 0, 11, 16, 14, 16),
            Block.box(0, 14, 10, 16, 16, 16),
            Block.box(2, 2, 10.3, 14, 12.5, 12.5));

    public BlockYunpuBurner() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOD)
                .sound(SoundType.WOOD)
                .strength(1.5F, 6.0F)
                .noOcclusion()
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
        return SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    // ------------------------------------------------------------------
    // 放置与拆除（两格联动）
    // ------------------------------------------------------------------

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (!level.getBlockState(pos.above()).canBeReplaced(context)) {
            return null;
        }
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable net.minecraft.world.entity.LivingEntity placer,
                            net.minecraft.world.item.ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        BlockPos above = pos.above();
        if (level.getBlockState(above).canBeReplaced()) {
            level.setBlock(above, YunpuBlocks.YUNPU_BURNER_TOP.get().defaultBlockState()
                    .setValue(FACING, state.getValue(FACING)), Block.UPDATE_ALL);
        }
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide) {
            BlockPos above = pos.above();
            if (level.getBlockState(above).is(YunpuBlocks.YUNPU_BURNER_TOP.get())) {
                level.destroyBlock(above, false);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide) {
            if (level.getBlockEntity(pos) instanceof BlockEntityYunpuBurner burner) {
                for (int slot = 0; slot < burner.inventory().getSlots(); slot++) {
                    Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(),
                            burner.inventory().getStackInSlot(slot));
                }
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)
               || !level.getBlockState(pos.below()).isAir();
    }

    // ------------------------------------------------------------------
    // 交互
    // ------------------------------------------------------------------

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof BlockEntityYunpuBurner burner && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(new net.minecraft.world.SimpleMenuProvider(
                    (containerId, inventory, ignored) -> new YunpuMenu(containerId, inventory, pos),
                    Component.translatable("gui.yunpumusic.title")), buffer -> buffer.writeBlockPos(pos));
            YunpuMusic.LOGGER.debug("Opened Yunpu terminal for {} at {}", player.getName().getString(), pos);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BlockEntityYunpuBurner(pos, state);
    }

}
