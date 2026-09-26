package com.yunpu.yunpumusic.init;

import com.yunpu.yunpumusic.YunpuMusic;
import com.yunpu.yunpumusic.world.BlockYunpuBurner;
import com.yunpu.yunpumusic.world.BlockYunpuBurnerTop;
import com.yunpu.yunpumusic.world.BlockEntityYunpuBurner;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class YunpuBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(YunpuMusic.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, YunpuMusic.MOD_ID);

    /** 云谱刻录台 · 下段（主机、唱盘、操作面） */
    public static final DeferredBlock<Block> YUNPU_BURNER = BLOCKS.register("yunpu_burner", BlockYunpuBurner::new);
    /** 云谱刻录台 · 上段（支架、登录终端屏幕、顶盖） */
    public static final DeferredBlock<Block> YUNPU_BURNER_TOP = BLOCKS.register("yunpu_burner_top", BlockYunpuBurnerTop::new);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BlockEntityYunpuBurner>> YUNPU_BURNER_ENTITY =
            BLOCK_ENTITIES.register("yunpu_burner", () -> BlockEntityType.Builder
                    .of(BlockEntityYunpuBurner::new, YUNPU_BURNER.get(), YUNPU_BURNER_TOP.get())
                    .build(null));

    private YunpuBlocks() {
    }
}
