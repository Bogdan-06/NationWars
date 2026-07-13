/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.core.component.DataComponents
 *  net.minecraft.network.chat.Component
 *  net.minecraft.network.chat.MutableComponent
 *  net.minecraft.server.level.ServerPlayer
 *  net.minecraft.world.Container
 *  net.minecraft.world.SimpleContainer
 *  net.minecraft.world.entity.player.Inventory
 *  net.minecraft.world.entity.player.Player
 *  net.minecraft.world.inventory.AbstractContainerMenu
 *  net.minecraft.world.inventory.ClickType
 *  net.minecraft.world.inventory.MenuType
 *  net.minecraft.world.inventory.Slot
 *  net.minecraft.world.item.Item
 *  net.minecraft.world.item.ItemStack
 *  net.minecraft.world.item.Items
 *  net.minecraft.world.item.component.ItemLore
 *  net.minecraft.world.level.ItemLike
 */
package dev.moth.nationwars;

import dev.moth.nationwars.Doctrine;
import dev.moth.nationwars.NationCommands;
import dev.moth.nationwars.NationIcons;
import dev.moth.nationwars.NationStore;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.ItemLike;

public final class NationCreateMenu
extends AbstractContainerMenu {
    private static final int ROWS = 3;
    private static final int CHOICE_SLOTS = 27;
    private final String nationName;
    private final Inventory playerInventory;
    private final SimpleContainer choicesContainer;
    private final Doctrine[] choices = new Doctrine[27];

    public NationCreateMenu(int containerId, Inventory playerInventory, String nationName) {
        super(MenuType.GENERIC_9x3, containerId);
        int column;
        this.playerInventory = playerInventory;
        this.nationName = nationName;
        this.choicesContainer = new SimpleContainer(27);
        this.choicesContainer.startOpen(playerInventory.player);
        this.refreshChoices();
        for (int row = 0; row < 3; ++row) {
            for (column = 0; column < 9; ++column) {
                this.addSlot(new DisplaySlot((Container)this.choicesContainer, column + row * 9, 8 + column * 18, 18 + row * 18));
            }
        }
        int inventoryOffset = -18;
        for (int row = 0; row < 3; ++row) {
            for (int column2 = 0; column2 < 9; ++column2) {
                this.addSlot(new Slot((Container)playerInventory, column2 + row * 9 + 9, 8 + column2 * 18, 103 + row * 18 + inventoryOffset));
            }
        }
        for (column = 0; column < 9; ++column) {
            this.addSlot(new Slot((Container)playerInventory, column, 8 + column * 18, 161 + inventoryOffset));
        }
    }

    public boolean stillValid(Player player) {
        return true;
    }

    public ItemStack quickMoveStack(Player player, int slotIndex) {
        return ItemStack.EMPTY;
    }

    public void clicked(int slotIndex, int button, ClickType clickType, Player player) {
        if (slotIndex >= 0 && slotIndex < 27) {
            if (player instanceof ServerPlayer) {
                ServerPlayer serverPlayer = (ServerPlayer)player;
                if (clickType == ClickType.PICKUP || clickType == ClickType.QUICK_MOVE) {
                    this.chooseDoctrine(serverPlayer, slotIndex);
                }
            }
            return;
        }
        super.clicked(slotIndex, button, clickType, player);
    }

    public void removed(Player player) {
        super.removed(player);
        this.choicesContainer.stopOpen(player);
    }

    private void refreshChoices() {
        this.choicesContainer.clearContent();
        for (int i = 0; i < this.choices.length; ++i) {
            this.choices[i] = null;
        }
        List<Doctrine> available = NationStore.get().availableDoctrines();
        if (available.isEmpty()) {
            ItemStack empty = new ItemStack((ItemLike)Items.BARRIER);
            empty.set(DataComponents.CUSTOM_NAME, NationText.tr("nationwars.gui.nation_create.no_doctrines"));
            empty.set(DataComponents.LORE, new ItemLore(List.of(NationText.tr("nationwars.gui.nation_create.no_doctrines_lore"))));
            this.choicesContainer.setItem(13, empty);
            return;
        }
        int slot = 10;
        for (Doctrine doctrine : available) {
            if (slot > 16) break;
            this.choices[slot] = doctrine;
            this.choicesContainer.setItem(slot, this.displayStack(doctrine));
            ++slot;
        }
    }

    private ItemStack displayStack(Doctrine doctrine) {
        ItemStack stack = new ItemStack((ItemLike)this.iconFor(doctrine));
        stack.set(DataComponents.CUSTOM_NAME, NationText.doctrineName(doctrine));
        List<Component> lore = new ArrayList<>();
        lore.add(this.nationName.isBlank()
            ? NationText.tr("nationwars.gui.nation_create.name_after_selection")
            : NationText.tr("nationwars.gui.nation_create.creates", this.nationName));
        lore.add(NationText.tr("nationwars.gui.doctrine.ideology", NationText.ideologyName(doctrine.ideology)));
        lore.add(NationText.tr("nationwars.gui.doctrine.free_claims", doctrine.freeClaims));
        lore.add(NationText.tr("nationwars.gui.doctrine.claim_cost", doctrine.claimCostMultiplier));
        lore.add(NationText.tr("nationwars.gui.doctrine.maintenance", doctrine.maintenanceMultiplier));
        lore.add(NationText.tr("nationwars.gui.doctrine.capture", doctrine.captureSeconds));
        lore.addAll(NationText.doctrinePerks(doctrine));
        lore.add(NationText.tr("nationwars.gui.nation_create.click"));
        stack.set(DataComponents.LORE, new ItemLore(lore));
        return stack;
    }

    private void chooseDoctrine(ServerPlayer player, int slotIndex) {
        Doctrine doctrine = this.choices[slotIndex];
        if (doctrine == null) {
            return;
        }
        if (this.nationName.isBlank()) {
            NationCommands.requestNationName(player, doctrine);
            return;
        }
        if (NationCommands.createNationWithDoctrine(player, this.nationName, doctrine)) {
            player.closeContainer();
            return;
        }
        this.refreshChoices();
        this.broadcastChanges();
    }

    private Item iconFor(Doctrine doctrine) {
        return NationIcons.countryBlock(doctrine);
    }

    private static final class DisplaySlot
    extends Slot {
        private DisplaySlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        public boolean mayPickup(Player player) {
            return false;
        }
    }
}
