/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.core.component.DataComponents
 *  net.minecraft.network.chat.Component
 *  net.minecraft.network.chat.MutableComponent
 *  net.minecraft.server.MinecraftServer
 *  net.minecraft.server.level.ServerPlayer
 *  net.minecraft.world.Container
 *  net.minecraft.world.SimpleContainer
 *  net.minecraft.world.entity.player.Inventory
 *  net.minecraft.world.entity.player.Player
 *  net.minecraft.world.inventory.AbstractContainerMenu
 *  net.minecraft.world.inventory.ClickType
 *  net.minecraft.world.inventory.MenuType
 *  net.minecraft.world.inventory.Slot
 *  net.minecraft.world.item.ItemStack
 *  net.minecraft.world.item.Items
 *  net.minecraft.world.item.component.ItemLore
 *  net.minecraft.world.level.ItemLike
 */
package dev.moth.nationwars;

import dev.moth.nationwars.NationStore;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.ItemLike;

public final class WarMenu
extends AbstractContainerMenu {
    private static final int ROWS = 6;
    private static final int WAR_SLOTS = 54;
    private static final int PAGE_SIZE = 36;
    private final Inventory playerInventory;
    private final SimpleContainer warContainer;
    private final String[] warIds = new String[54];
    private final String[] hints = new String[54];
    private int page;

    public WarMenu(int containerId, Inventory playerInventory) {
        super(MenuType.GENERIC_9x6, containerId);
        int column;
        this.playerInventory = playerInventory;
        this.warContainer = new SimpleContainer(54);
        this.warContainer.startOpen(playerInventory.player);
        this.refreshWars();
        for (int row = 0; row < 6; ++row) {
            for (column = 0; column < 9; ++column) {
                this.addSlot(new DisplaySlot((Container)this.warContainer, column + row * 9, 8 + column * 18, 18 + row * 18));
            }
        }
        int inventoryOffset = 36;
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
        if (slotIndex >= 0 && slotIndex < 54) {
            if (player instanceof ServerPlayer) {
                ServerPlayer serverPlayer = (ServerPlayer)player;
                if (clickType == ClickType.PICKUP || clickType == ClickType.QUICK_MOVE) {
                    if (slotIndex == 50 && this.page > 0) {
                        --this.page;
                        this.refreshAndSync();
                        return;
                    }
                    if (slotIndex == 53 && this.page + 1 < this.pageCount()) {
                        ++this.page;
                        this.refreshAndSync();
                        return;
                    }
                    this.sendDetailsOrHint(serverPlayer, slotIndex);
                }
            }
            return;
        }
        super.clicked(slotIndex, button, clickType, player);
    }

    public void removed(Player player) {
        super.removed(player);
        this.warContainer.stopOpen(player);
    }

    private void refreshWars() {
        this.warContainer.clearContent();
        for (int i = 0; i < 54; ++i) {
            this.warIds[i] = null;
            this.hints[i] = null;
        }
        NationStore store = NationStore.get();
        MinecraftServer server = this.playerInventory.player.getServer();
        List<NationStore.War> wars = store.wars().stream().sorted(Comparator.comparing(war -> war.id)).toList();
        int pageCount = this.pageCount();
        this.page = Math.min(this.page, pageCount - 1);
        int slot = 0;
        for (NationStore.War war2 : wars.stream().skip((long)this.page * PAGE_SIZE).limit(PAGE_SIZE).toList()) {
            this.warIds[slot] = war2.id;
            this.warContainer.setItem(slot, this.displayWar(store, server, war2));
            ++slot;
        }
        if (slot == 0) {
            ItemStack empty = new ItemStack((ItemLike)Items.BARRIER);
            empty.set(DataComponents.CUSTOM_NAME, NationText.tr("nationwars.gui.wars.empty"));
            empty.set(DataComponents.LORE, new ItemLore(List.of(NationText.tr("nationwars.gui.wars.empty_lore"))));
            this.warContainer.setItem(13, empty);
        }
        this.setHint(45, Items.WRITABLE_BOOK.getDefaultInstance(), NationText.tr("nationwars.gui.wars.justify"), "/war justify <nation>");
        this.setHint(46, Items.IRON_SWORD.getDefaultInstance(), NationText.tr("nationwars.gui.wars.declare"), "/war declare <nation>");
        this.setHint(47, Items.SHIELD.getDefaultInstance(), NationText.tr("nationwars.gui.wars.join_defend"), "/war join <nation> | /war defend <nation>");
        this.setHint(48, Items.WHITE_BANNER.getDefaultInstance(), NationText.tr("nationwars.gui.wars.peace"), "/peace <nation>");
        this.setHint(49, Items.RED_BANNER.getDefaultInstance(), NationText.tr("nationwars.gui.wars.surrender"), "/surrender <nation>");
        if (this.page > 0) {
            this.warContainer.setItem(50, control(Items.ARROW.getDefaultInstance(), NationText.tr("nationwars.gui.wars.previous")));
        }
        this.warContainer.setItem(51, control(Items.WRITABLE_BOOK.getDefaultInstance(), NationText.tr("nationwars.gui.wars.page", this.page + 1, pageCount)));
        if (this.page + 1 < pageCount) {
            this.warContainer.setItem(53, control(Items.ARROW.getDefaultInstance(), NationText.tr("nationwars.gui.wars.next")));
        }
    }

    private int pageCount() {
        int count = NationStore.get().wars().size();
        return Math.max(1, (count + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    private static ItemStack control(ItemStack stack, Component name) {
        stack.set(DataComponents.CUSTOM_NAME, name);
        return stack;
    }

    private ItemStack displayWar(NationStore store, MinecraftServer server, NationStore.War war) {
        String attacker = store.nationById(war.attacker).map(nation -> nation.name).orElse(war.attacker);
        String defender = store.nationById(war.defender).map(nation -> nation.name).orElse(war.defender);
        ItemStack stack = new ItemStack((ItemLike)(war.active ? Items.IRON_SWORD : Items.CLOCK));
        stack.set(DataComponents.CUSTOM_NAME, NationText.tr("nationwars.war.versus", attacker, defender));
        List<Component> lore = new ArrayList<>();
        if (war.active) {
            lore.add(NationText.tr("nationwars.gui.wars.status.active"));
            lore.add(NationText.tr("nationwars.gui.wars.attackers", WarMenu.nationNames(store, war.attackerSide)));
            lore.add(NationText.tr("nationwars.gui.wars.defenders", WarMenu.nationNames(store, war.defenderSide)));
            lore.add(NationText.tr("nationwars.gui.wars.captured", WarMenu.capturedCount(war)));
            if (!war.joinRequests.isEmpty()) {
                lore.add(NationText.tr("nationwars.gui.wars.pending_joins", WarMenu.nationNames(store, war.joinRequests.keySet())));
            }
            if (war.peaceDeal != null) {
                lore.add(NationText.tr("nationwars.gui.wars.peace_pending"));
            }
        } else if (war.pendingDefenderResponse) {
            lore.add(NationText.tr("nationwars.gui.wars.status.waiting"));
        } else {
            long secondsLeft = Math.max(0L, (war.justificationCompleteTick - NationStore.persistentNow()) / 20L);
            lore.add(NationText.tr("nationwars.gui.wars.status.justifying"));
            lore.add(NationText.tr("nationwars.gui.wars.ready_in", secondsLeft));
        }
        lore.add(NationText.tr("nationwars.gui.common.click_details"));
        stack.set(DataComponents.LORE, new ItemLore(lore));
        return stack;
    }

    private void setHint(int slot, ItemStack stack, Component title, String command) {
        stack.set(DataComponents.CUSTOM_NAME, title);
        stack.set(DataComponents.LORE, new ItemLore(List.of(Component.literal(command), NationText.tr("nationwars.gui.wars.click_command"))));
        this.hints[slot] = command;
        this.warContainer.setItem(slot, stack);
    }

    private void sendDetailsOrHint(ServerPlayer player, int slotIndex) {
        if (this.hints[slotIndex] != null) {
            player.sendSystemMessage(NationText.message("nationwars.gui.wars.command_hint", this.hints[slotIndex]));
            return;
        }
        if (this.warIds[slotIndex] == null) {
            return;
        }
        NationStore store = NationStore.get();
        NationStore.War war = store.wars().stream().filter(candidate -> this.warIds[slotIndex].equals(candidate.id)).findFirst().orElse(null);
        if (war == null) {
            this.refreshAndSync();
            player.sendSystemMessage(NationText.message("nationwars.gui.wars.error.gone"));
            return;
        }
        String attacker = store.nationById(war.attacker).map(nation -> nation.name).orElse(war.attacker);
        String defender = store.nationById(war.defender).map(nation -> nation.name).orElse(war.defender);
        player.sendSystemMessage(NationText.message("nationwars.war.versus", attacker, defender));
        player.sendSystemMessage(NationText.tr("nationwars.gui.wars.attackers", WarMenu.nationNames(store, war.attackerSide)));
        player.sendSystemMessage(NationText.tr("nationwars.gui.wars.defenders", WarMenu.nationNames(store, war.defenderSide)));
        player.sendSystemMessage(NationText.tr("nationwars.gui.wars.captured", WarMenu.capturedCount(war)));
    }

    private void refreshAndSync() {
        this.refreshWars();
        this.broadcastChanges();
    }

    private static Component nationNames(NationStore store, Set<String> ids) {
        if (ids.isEmpty()) {
            return NationText.tr("nationwars.common.none");
        }
        return Component.literal(ids.stream().map(id -> store.nationById((String)id).map(nation -> nation.name).orElse((String)id)).sorted().collect(Collectors.joining(", ")));
    }

    private static int capturedCount(NationStore.War war) {
        return war.capturedClaimsByNation.values().stream().mapToInt(Set::size).sum();
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
