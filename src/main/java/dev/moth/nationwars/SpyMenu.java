package dev.moth.nationwars;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

public final class SpyMenu extends AbstractContainerMenu {
    private final Inventory playerInventory;
    private final SimpleContainer spyContainer;

    public SpyMenu(int containerId, Inventory playerInventory) {
        super(MenuType.GENERIC_9x6, containerId);
        this.playerInventory = playerInventory;
        this.spyContainer = new SimpleContainer(54);
        this.spyContainer.startOpen(playerInventory.player);
        this.refresh();
        for (int row = 0; row < 6; ++row) {
            for (int column = 0; column < 9; ++column) {
                this.addSlot(new DisplaySlot(this.spyContainer, column + row * 9, 8 + column * 18, 18 + row * 18));
            }
        }
        for (int row = 0; row < 3; ++row) {
            for (int column = 0; column < 9; ++column) {
                this.addSlot(new Slot(playerInventory, column + row * 9 + 9, 8 + column * 18, 139 + row * 18));
            }
        }
        for (int column = 0; column < 9; ++column) {
            this.addSlot(new Slot(playerInventory, column, 8 + column * 18, 197));
        }
    }

    private void refresh() {
        NationStore store = NationStore.get();
        MinecraftServer server = this.playerInventory.player.getServer();
        NationStore.Nation nation = store.nationOf(this.playerInventory.player.getUUID()).orElse(null);
        if (nation == null || nation.spyAgency == null) {
            ItemStack empty = Items.BARRIER.getDefaultInstance();
            empty.set(DataComponents.CUSTOM_NAME, Component.literal("No spy agency"));
            empty.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("Create one with /spy create."))));
            this.spyContainer.setItem(22, empty);
            return;
        }
        List<NationStore.SpyUnit> spies = nation.spyAgency.spies.stream().sorted(Comparator.comparingInt(spy -> spy.id)).toList();
        int slot = 0;
        for (NationStore.SpyUnit spy : spies) {
            if (slot >= 45) {
                break;
            }
            this.spyContainer.setItem(slot++, displaySpy(store, server, spy));
        }
        ItemStack summary = Items.WRITABLE_BOOK.getDefaultInstance();
        summary.set(DataComponents.CUSTOM_NAME, Component.literal("Spy agency"));
        summary.set(DataComponents.LORE, new ItemLore(List.of(
            Component.literal("Spies: " + spies.size() + "/" + SpyCommands.maxSpies(nation)),
            Component.literal("Use /spy mission to open the mission UI.")
        )));
        this.spyContainer.setItem(49, summary);
    }

    private static ItemStack displaySpy(NationStore store, MinecraftServer server, NationStore.SpyUnit spy) {
        ItemStack stack = Items.BOOK.getDefaultInstance();
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("spy" + spy.id));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.literal("country: " + countryName(store, spy.country)));
        lore.add(Component.literal("status: " + spy.status));
        lore.add(Component.literal("mission: " + (spy.mission.isBlank() ? "none" : spy.mission)));
        long seconds = Math.max(0L, (spy.availableTick - NationStore.persistentNow() + 19L) / 20L);
        lore.add(Component.literal("time left: " + seconds + "s"));
        if (!spy.targetChunk.isBlank()) {
            lore.add(Component.literal("chunk: " + shortChunk(spy.targetChunk)));
        }
        stack.set(DataComponents.LORE, new ItemLore(lore));
        return stack;
    }

    private static String countryName(NationStore store, String id) {
        if (id == null || id.isBlank()) {
            return "none";
        }
        return store.nationById(id).map(nation -> nation.name).orElse(id);
    }

    private static String shortChunk(String id) {
        try {
            ClaimKey claim = ClaimKey.parse(id);
            return claim.x() + ";" + claim.z();
        } catch (RuntimeException ignored) {
            return id;
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        return ItemStack.EMPTY;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.spyContainer.stopOpen(player);
    }

    private static final class DisplaySlot extends Slot {
        private DisplaySlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }
    }
}
