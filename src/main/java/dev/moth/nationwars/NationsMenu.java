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
 *  net.minecraft.world.item.ItemStack
 *  net.minecraft.world.item.Items
 *  net.minecraft.world.item.component.ItemLore
 *  net.minecraft.world.level.ItemLike
 */
package dev.moth.nationwars;

import dev.moth.nationwars.ClaimKey;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.ItemLike;

public final class NationsMenu
extends AbstractContainerMenu {
    private static final int ROWS = 6;
    private static final int NATION_SLOTS = 54;
    private static final int PAGE_SIZE = 45;
    private final Inventory playerInventory;
    private final SimpleContainer nationsContainer;
    private final String[] nationIds = new String[NATION_SLOTS];
    private int page;

    public NationsMenu(int containerId, Inventory playerInventory) {
        super(MenuType.GENERIC_9x6, containerId);
        int column;
        this.playerInventory = playerInventory;
        this.nationsContainer = new SimpleContainer(NATION_SLOTS);
        this.nationsContainer.startOpen(playerInventory.player);
        this.refreshNations();
        for (int row = 0; row < ROWS; ++row) {
            for (column = 0; column < 9; ++column) {
                this.addSlot(new DisplaySlot((Container)this.nationsContainer, column + row * 9, 8 + column * 18, 18 + row * 18));
            }
        }
        for (int row = 0; row < 3; ++row) {
            for (int column2 = 0; column2 < 9; ++column2) {
                this.addSlot(new Slot((Container)playerInventory, column2 + row * 9 + 9, 8 + column2 * 18, 139 + row * 18));
            }
        }
        for (column = 0; column < 9; ++column) {
            this.addSlot(new Slot((Container)playerInventory, column, 8 + column * 18, 197));
        }
    }

    public boolean stillValid(Player player) {
        return true;
    }

    public ItemStack quickMoveStack(Player player, int slotIndex) {
        return ItemStack.EMPTY;
    }

    public void clicked(int slotIndex, int button, ClickType clickType, Player player) {
        if (slotIndex >= 0 && slotIndex < NATION_SLOTS) {
            if (player instanceof ServerPlayer) {
                ServerPlayer serverPlayer = (ServerPlayer)player;
                if (clickType == ClickType.PICKUP || clickType == ClickType.QUICK_MOVE) {
                    if (slotIndex == 45 && this.page > 0) {
                        --this.page;
                        this.refreshAndSync();
                        return;
                    }
                    if (slotIndex == 53 && this.page + 1 < this.pageCount()) {
                        ++this.page;
                        this.refreshAndSync();
                        return;
                    }
                    this.sendNationDetails(serverPlayer, slotIndex);
                }
            }
            return;
        }
        super.clicked(slotIndex, button, clickType, player);
    }

    public void removed(Player player) {
        super.removed(player);
        this.nationsContainer.stopOpen(player);
    }

    private void refreshNations() {
        this.nationsContainer.clearContent();
        for (int i = 0; i < this.nationIds.length; ++i) {
            this.nationIds[i] = null;
        }
        List<NationStore.Nation> nations = NationStore.get().nationsSorted();
        if (nations.isEmpty()) {
            ItemStack empty = new ItemStack((ItemLike)Items.BARRIER);
            empty.set(DataComponents.CUSTOM_NAME, NationText.tr("nationwars.gui.nations.empty"));
            empty.set(DataComponents.LORE, new ItemLore(List.of(NationText.tr("nationwars.gui.nations.empty_lore"))));
            this.nationsContainer.setItem(22, empty);
            return;
        }
        int pageCount = this.pageCount();
        this.page = Math.min(this.page, pageCount - 1);
        int slot = 0;
        for (NationStore.Nation nation : nations.stream().skip((long)this.page * PAGE_SIZE).limit(PAGE_SIZE).toList()) {
            this.nationIds[slot] = nation.id;
            this.nationsContainer.setItem(slot, this.displayStack(nation));
            ++slot;
        }
        if (this.page > 0) {
            this.nationsContainer.setItem(45, control(Items.ARROW.getDefaultInstance(), NationText.tr("nationwars.gui.common.previous_page")));
        }
        this.nationsContainer.setItem(49, control(Items.WRITABLE_BOOK.getDefaultInstance(), NationText.tr("nationwars.gui.nations.page", this.page + 1, pageCount)));
        if (this.page + 1 < pageCount) {
            this.nationsContainer.setItem(53, control(Items.ARROW.getDefaultInstance(), NationText.tr("nationwars.gui.common.next_page")));
        }
    }

    private int pageCount() {
        int count = NationStore.get().nationsSorted().size();
        return Math.max(1, (count + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    private static ItemStack control(ItemStack stack, Component name) {
        stack.set(DataComponents.CUSTOM_NAME, name);
        return stack;
    }

    private ItemStack displayStack(NationStore.Nation nation) {
        int activeWars;
        NationStore store = NationStore.get();
        ItemStack stack = new ItemStack((ItemLike)NationIcons.countryBlock(nation.doctrine()));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal((String)nation.name));
        List<Component> lore = new ArrayList<>();
        lore.add(NationText.tr("nationwars.gui.nations.command_id", nation.id));
        lore.add(NationText.tr("nationwars.gui.nations.leader", nation.ownerName));
        lore.add(NationText.tr("nationwars.gui.nations.claims", store.claimCount(nation)));
        lore.add(NationText.tr("nationwars.gui.nations.members", nation.members.size()));
        lore.add(NationText.tr("nationwars.gui.nations.cities", nation.cityClaims.size()));
        lore.add(NationText.tr("nationwars.gui.nations.treasury", NationStore.roundMoney(nation.balance)));
        store.allianceOf(nation).ifPresent(alliance -> lore.add(NationText.tr("nationwars.gui.nations.alliance", alliance.name)));
        if (nation.capitalClaim != null && !nation.capitalClaim.isBlank()) {
            lore.add(NationText.tr("nationwars.gui.nations.capital", ClaimKey.parse(nation.capitalClaim).shortName()));
        }
        if ((activeWars = store.activeWarsOf(nation).size()) > 0) {
            lore.add(NationText.tr("nationwars.gui.nations.active_wars", activeWars));
        }
        lore.add(NationText.tr("nationwars.gui.common.click_details"));
        stack.set(DataComponents.LORE, new ItemLore(lore));
        return stack;
    }

    private void sendNationDetails(ServerPlayer player, int slotIndex) {
        String nationId = this.nationIds[slotIndex];
        if (nationId == null) {
            return;
        }
        NationStore store = NationStore.get();
        NationStore.Nation nation = store.nationById(nationId).orElse(null);
        if (nation == null) {
            this.refreshAndSync();
            player.sendSystemMessage(NationText.message("nationwars.gui.nations.error.gone"));
            return;
        }
        player.sendSystemMessage(NationText.message("nationwars.gui.nations.details_header", nation.name));
        player.sendSystemMessage(NationText.tr("nationwars.gui.nations.command_id", nation.id));
        player.sendSystemMessage(NationText.tr("nationwars.gui.nations.details", nation.ownerName, store.claimCount(nation), nation.members.size(), NationStore.roundMoney(nation.balance)));
        if (nation.capitalClaim != null && !nation.capitalClaim.isBlank()) {
            player.sendSystemMessage(NationText.tr("nationwars.gui.nations.capital", ClaimKey.parse(nation.capitalClaim).shortName()));
        }
    }

    private void refreshAndSync() {
        this.refreshNations();
        this.broadcastChanges();
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
