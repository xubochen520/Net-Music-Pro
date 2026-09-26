package com.yunpu.yunpumusic.init;

import com.yunpu.yunpumusic.YunpuMusic;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class YunpuItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(YunpuMusic.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, YunpuMusic.MOD_ID);

    public static final DeferredItem<Item> YUNPU_BURNER = ITEMS.register("yunpu_burner",
            () -> new BlockItem(YunpuBlocks.YUNPU_BURNER.get(), new Item.Properties().stacksTo(1)));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> YUNPU_TAB = TABS.register("yunpu",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.yunpumusic"))
                    .icon(() -> new ItemStack(YunpuBlocks.YUNPU_BURNER.get()))
                    .displayItems((parameters, output) -> output.accept(new ItemStack(YUNPU_BURNER.get())))
                    .build());

    private YunpuItems() {
    }
}
