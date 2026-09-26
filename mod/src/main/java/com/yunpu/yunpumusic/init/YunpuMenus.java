package com.yunpu.yunpumusic.init;

import com.yunpu.yunpumusic.YunpuMusic;
import com.yunpu.yunpumusic.menu.YunpuMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class YunpuMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, YunpuMusic.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<YunpuMenu>> YUNPU_TERMINAL =
            MENUS.register("yunpu_terminal", () -> IMenuTypeExtension.create(YunpuMenu::new));

    private YunpuMenus() {
    }
}
