package com.yunpu.yunpumusic.menu;

import com.github.tartaricacid.netmusic.item.ItemMusicCD;
import com.github.tartaricacid.netmusic.init.InitItems;
import com.yunpu.yunpumusic.api.SongRef;
import com.yunpu.yunpumusic.api.YunpuUrl;
import com.yunpu.yunpumusic.credentials.Role;
import com.yunpu.yunpumusic.init.YunpuBlocks;
import com.yunpu.yunpumusic.init.YunpuMenus;
import com.yunpu.yunpumusic.network.YunpuNetwork;
import com.yunpu.yunpumusic.world.BlockEntityYunpuBurner;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/** The two persistent disc slots and the player's inventory. */
public class YunpuMenu extends AbstractContainerMenu {
    public static final int INPUT = 0;
    public static final int OUTPUT = 1;
    private static final int PLAYER_START = 2;
    private static final int PLAYER_END = 38;

    private final BlockPos pos;
    private final ContainerData data;
    private final ItemStackHandler discs;
    private boolean burnPage;

    public YunpuMenu(int containerId, Inventory inventory, BlockPos pos) {
        this(containerId, inventory, pos, new SimpleContainerData(1));
        if (inventory.player instanceof ServerPlayer serverPlayer) {
            data.set(0, Role.resolve(serverPlayer).ordinal());
            YunpuNetwork.sendInit(serverPlayer, pos);
        }
    }

    public YunpuMenu(int containerId, Inventory inventory, FriendlyByteBuf buffer) {
        this(containerId, inventory, buffer.readBlockPos(), new SimpleContainerData(1));
    }

    private YunpuMenu(int containerId, Inventory inventory, BlockPos pos, ContainerData data) {
        super(YunpuMenus.YUNPU_TERMINAL.get(), containerId);
        this.pos = pos;
        this.data = data;
        this.discs = inventory.player.level().getBlockEntity(pos) instanceof BlockEntityYunpuBurner burner
                ? burner.inventory() : new ItemStackHandler(2);
        addDataSlots(data);

        addSlot(new SlotItemHandler(discs, BlockEntityYunpuBurner.INPUT_SLOT, 42, 101) {
            @Override public boolean isActive() { return burnPage; }
        });
        addSlot(new SlotItemHandler(discs, BlockEntityYunpuBurner.OUTPUT_SLOT, 133, 101) {
            @Override public boolean isActive() { return burnPage; }
            @Override public boolean mayPlace(ItemStack stack) { return false; }
        });
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9,
                        14 + column * 18, 137 + row * 18) {
                    @Override public boolean isActive() { return burnPage; }
                });
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 14 + column * 18, 194) {
                @Override public boolean isActive() { return burnPage; }
            });
        }
    }

    public BlockPos pos() { return pos; }
    public Role role() { return Role.byOrdinal(data.get(0)); }
    public void setBurnPage(boolean burnPage) { this.burnPage = burnPage; }
    public boolean burnPage() { return burnPage; }

    public boolean canBurn() {
        ItemStack input = discs.getStackInSlot(INPUT);
        return !input.isEmpty() && discs.isItemValid(INPUT, input)
                && discs.getStackInSlot(OUTPUT).isEmpty();
    }

    /** Runs only on the server after the packet handler verifies the open menu. */
    public boolean burn(SongRef song) {
        if (song == null || song.id().isBlank() || song.title().isBlank()
                || song.id().length() > 256 || song.title().length() > 256 || !canBurn()) {
            return false;
        }
        // Start with a fresh CD so old song metadata and custom names cannot survive an overwrite.
        ItemStack result = new ItemStack(InitItems.MUSIC_CD.get());
        ItemMusicCD.SongInfo info = new ItemMusicCD.SongInfo(
                YunpuUrl.build(song), song.title(), song.durationSeconds(), "",
                song.vip(), false, song.artists());
        ItemMusicCD.setSongInfo(info, result);
        if (ItemMusicCD.getSongInfo(result) == null) {
            return false;
        }
        Component name = Component.literal(song.title());
        if (song.vip()) {
            name = name.copy().append(Component.literal("【vip】").withStyle(ChatFormatting.RED));
        }
        String platformName = switch (song.platform()) {
            case KUGOU -> "酷狗";
            case NETEASE -> "网易";
            case QQ -> "QQ";
        };
        ChatFormatting platformColor = switch (song.platform()) {
            case KUGOU -> ChatFormatting.BLUE;
            case NETEASE -> ChatFormatting.RED;
            case QQ -> ChatFormatting.YELLOW;
        };
        result.set(DataComponents.ITEM_NAME,
                name.copy().append(Component.literal("【" + platformName + "】").withStyle(platformColor)));
        discs.setStackInSlot(INPUT, ItemStack.EMPTY);
        discs.setStackInSlot(OUTPUT, result);
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < PLAYER_START) {
            if (!moveItemStackTo(stack, PLAYER_START, PLAYER_END, true)) return ItemStack.EMPTY;
        } else if (discs.isItemValid(INPUT, stack)) {
            if (!moveItemStackTo(stack, INPUT, INPUT + 1, false)) return ItemStack.EMPTY;
        } else if (index < 29) {
            if (!moveItemStackTo(stack, 29, PLAYER_END, false)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, PLAYER_START, 29, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        slot.onTake(player, stack);
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockEntity(pos) instanceof BlockEntityYunpuBurner
                && player.level().getBlockState(pos).is(YunpuBlocks.YUNPU_BURNER.get())
                && player.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) <= 64;
    }
}
