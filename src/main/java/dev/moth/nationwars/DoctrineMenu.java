/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.core.component.DataComponents
 *  net.minecraft.network.chat.Component
 *  net.minecraft.network.chat.MutableComponent
 *  net.minecraft.world.Container
 *  net.minecraft.world.SimpleContainer
 *  net.minecraft.world.entity.player.Inventory
 *  net.minecraft.world.entity.player.Player
 *  net.minecraft.world.inventory.AbstractContainerMenu
 *  net.minecraft.world.inventory.MenuType
 *  net.minecraft.world.inventory.Slot
 *  net.minecraft.world.item.Item
 *  net.minecraft.world.item.ItemStack
 *  net.minecraft.world.item.component.ItemLore
 *  net.minecraft.world.level.ItemLike
 */
package dev.moth.nationwars;

import dev.moth.nationwars.Doctrine;
import dev.moth.nationwars.NationIcons;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.ItemLike;

public final class DoctrineMenu
extends AbstractContainerMenu {
    private static final int ROWS = 3;
    private static final int DOCTRINE_SLOTS = 27;
    private final Inventory playerInventory;
    private final SimpleContainer doctrinesContainer;

    public DoctrineMenu(int containerId, Inventory playerInventory) {
        super(MenuType.GENERIC_9x3, containerId);
        int column;
        this.playerInventory = playerInventory;
        this.doctrinesContainer = new SimpleContainer(27);
        this.doctrinesContainer.startOpen(playerInventory.player);
        this.refresh();
        for (int row = 0; row < 3; ++row) {
            for (column = 0; column < 9; ++column) {
                this.addSlot(new DisplaySlot((Container)this.doctrinesContainer, column + row * 9, 8 + column * 18, 18 + row * 18));
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

    public void removed(Player player) {
        super.removed(player);
        this.doctrinesContainer.stopOpen(player);
    }

    private void refresh() {
        this.doctrinesContainer.clearContent();
        int slot = 10;
        for (Doctrine doctrine : Doctrine.values()) {
            if (slot > 16) break;
            this.doctrinesContainer.setItem(slot, this.displayStack(doctrine));
            ++slot;
        }
    }

    private ItemStack displayStack(Doctrine doctrine) {
        ItemStack stack = new ItemStack((ItemLike)DoctrineMenu.iconFor(doctrine));
        stack.set(DataComponents.CUSTOM_NAME, NationText.doctrineName(doctrine));
        List<Component> lore = new ArrayList<>();
        lore.add(NationText.tr("nationwars.gui.doctrine.id", doctrine.id));
        lore.add(NationText.tr("nationwars.gui.doctrine.ideology", NationText.ideologyName(doctrine.ideology)));
        lore.add(NationText.tr("nationwars.gui.doctrine.free_claims", doctrine.freeClaims));
        lore.add(NationText.tr("nationwars.gui.doctrine.claim_cost", doctrine.claimCostMultiplier));
        lore.add(NationText.tr("nationwars.gui.doctrine.maintenance", doctrine.maintenanceMultiplier));
        lore.add(NationText.tr("nationwars.gui.doctrine.capture", doctrine.captureSeconds));
        lore.addAll(NationText.doctrinePerks(doctrine));
        stack.set(DataComponents.LORE, new ItemLore(lore));
        return stack;
    }

    private static Item iconFor(Doctrine doctrine) {
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
