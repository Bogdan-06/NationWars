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
    private static final int ROWS = 3;
    private static final int NATION_SLOTS = 27;
    private final Inventory playerInventory;
    private final SimpleContainer nationsContainer;
    private final String[] nationIds = new String[27];

    public NationsMenu(int containerId, Inventory playerInventory) {
        super(MenuType.GENERIC_9x3, containerId);
        int column;
        this.playerInventory = playerInventory;
        this.nationsContainer = new SimpleContainer(27);
        this.nationsContainer.startOpen(playerInventory.player);
        this.refreshNations();
        for (int row = 0; row < 3; ++row) {
            for (column = 0; column < 9; ++column) {
                this.addSlot(new DisplaySlot((Container)this.nationsContainer, column + row * 9, 8 + column * 18, 18 + row * 18));
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
            empty.set(DataComponents.CUSTOM_NAME, Component.literal((String)"No nations yet"));
            empty.set(DataComponents.LORE, new ItemLore(List.of(Component.literal((String)"Use /nation create to start one."))));
            this.nationsContainer.setItem(13, empty);
            return;
        }
        int slot = 10;
        for (NationStore.Nation nation : nations) {
            if (slot >= 27) break;
            this.nationIds[slot] = nation.id;
            this.nationsContainer.setItem(slot, this.displayStack(nation));
            if ((slot += 2) != 18) continue;
            slot = 19;
        }
    }

    private ItemStack displayStack(NationStore.Nation nation) {
        int activeWars;
        NationStore store = NationStore.get();
        ItemStack stack = new ItemStack((ItemLike)NationIcons.countryBlock(nation.doctrine()));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal((String)nation.name));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.literal((String)("Command ID: " + nation.id)));
        lore.add(Component.literal((String)("Leader: " + nation.ownerName)));
        lore.add(Component.literal((String)("Claims: " + store.claimCount(nation))));
        lore.add(Component.literal((String)("Members: " + nation.members.size())));
        lore.add(Component.literal((String)("Cities: " + nation.cityClaims.size())));
        lore.add(Component.literal((String)("Treasury: $" + NationStore.roundMoney(nation.balance))));
        store.allianceOf(nation).ifPresent(alliance -> lore.add(Component.literal((String)("Alliance: " + alliance.name))));
        if (nation.capitalClaim != null && !nation.capitalClaim.isBlank()) {
            lore.add(Component.literal((String)("Capital: " + ClaimKey.parse(nation.capitalClaim).shortName())));
        }
        if ((activeWars = store.activeWarsOf(nation).size()) > 0) {
            lore.add(Component.literal((String)("Active wars: " + activeWars)));
        }
        lore.add(Component.literal((String)"Click for details"));
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
            player.sendSystemMessage((Component)Component.literal((String)"[NationWars] That nation no longer exists."));
            return;
        }
        player.sendSystemMessage((Component)Component.literal((String)("[NationWars] " + nation.name)));
        player.sendSystemMessage((Component)Component.literal((String)("Command ID: " + nation.id)));
        player.sendSystemMessage((Component)Component.literal((String)("Leader: " + nation.ownerName + " | claims: " + store.claimCount(nation) + " | members: " + nation.members.size() + " | treasury: $" + NationStore.roundMoney(nation.balance))));
        if (nation.capitalClaim != null && !nation.capitalClaim.isBlank()) {
            player.sendSystemMessage((Component)Component.literal((String)("Capital: " + ClaimKey.parse(nation.capitalClaim).shortName())));
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
