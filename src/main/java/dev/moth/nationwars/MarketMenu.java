/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.core.HolderLookup$Provider
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

import dev.moth.nationwars.NationStore;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
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

public final class MarketMenu
extends AbstractContainerMenu {
    private static final int ROWS = 6;
    private static final int MARKET_SLOTS = 54;
    private final Inventory playerInventory;
    private final SimpleContainer marketContainer;
    private final int[] listingIds = new int[54];

    public MarketMenu(int containerId, Inventory playerInventory) {
        super(MenuType.GENERIC_9x6, containerId);
        int column;
        this.playerInventory = playerInventory;
        this.marketContainer = new SimpleContainer(54);
        this.marketContainer.startOpen(playerInventory.player);
        this.refreshListings();
        for (int row = 0; row < 6; ++row) {
            for (column = 0; column < 9; ++column) {
                this.addSlot(new DisplaySlot((Container)this.marketContainer, column + row * 9, 8 + column * 18, 18 + row * 18));
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
                    this.buyListing(serverPlayer, slotIndex);
                }
            }
            return;
        }
        super.clicked(slotIndex, button, clickType, player);
    }

    public void removed(Player player) {
        super.removed(player);
        this.marketContainer.stopOpen(player);
    }

    private void refreshListings() {
        this.marketContainer.clearContent();
        for (int i = 0; i < this.listingIds.length; ++i) {
            this.listingIds[i] = 0;
        }
        NationStore store = NationStore.get();
        List<NationStore.MarketListing> listings = store.marketListings();
        int slot = 0;
        for (NationStore.MarketListing listing : listings) {
            if (slot >= 54) break;
            ItemStack stack = store.listingStack(listing, (HolderLookup.Provider)this.playerInventory.player.registryAccess());
            if (stack.isEmpty()) continue;
            this.marketContainer.setItem(slot, this.displayStack(stack, listing));
            this.listingIds[slot] = listing.id;
            ++slot;
        }
        if (slot == 0) {
            ItemStack empty = new ItemStack((ItemLike)Items.BARRIER);
            empty.set(DataComponents.CUSTOM_NAME, Component.literal((String)"No market listings"));
            empty.set(DataComponents.LORE, new ItemLore(List.of(Component.literal((String)"Use /market sellhand <price> to list your held stack."))));
            this.marketContainer.setItem(22, empty);
        }
    }

    private ItemStack displayStack(ItemStack stack, NationStore.MarketListing listing) {
        ItemStack display = stack.copy();
        List<Component> lore = new ArrayList<>();
        lore.add(Component.literal((String)("Price: $" + NationStore.roundMoney(listing.price))));
        double buyerPrice = this.adjustedBuyPrice(listing.price, this.playerInventory.player.getUUID());
        if (Math.abs(buyerPrice - listing.price) > 0.001) {
            lore.add(Component.literal((String)("Your price: $" + NationStore.roundMoney(buyerPrice))));
        }
        lore.add(Component.literal((String)("Seller: " + listing.sellerName)));
        lore.add(Component.literal((String)("Listing #" + listing.id)));
        lore.add(Component.literal((String)"Click to buy"));
        display.set(DataComponents.LORE, new ItemLore(lore));
        return display;
    }

    private void buyListing(ServerPlayer buyer, int slotIndex) {
        UUID sellerId;
        int listingId = this.listingIds[slotIndex];
        if (listingId <= 0) {
            return;
        }
        NationStore store = NationStore.get();
        NationStore.MarketListing listing = store.marketListing(listingId).orElse(null);
        if (listing == null) {
            this.refreshAndSync();
            buyer.sendSystemMessage((Component)Component.literal((String)"[NationWars] That listing is gone."));
            return;
        }
        if (listing.seller.equals(buyer.getUUID().toString())) {
            buyer.sendSystemMessage((Component)Component.literal((String)"[NationWars] You cannot buy your own listing."));
            return;
        }
        try {
            sellerId = UUID.fromString(listing.seller);
        }
        catch (IllegalArgumentException exception) {
            store.removeMarketListing(listing.id);
            this.refreshAndSync();
            buyer.sendSystemMessage((Component)Component.literal((String)"[NationWars] That listing had an invalid seller and has been removed."));
            return;
        }
        ItemStack purchased = store.listingStack(listing, (HolderLookup.Provider)buyer.registryAccess());
        if (purchased.isEmpty()) {
            store.removeMarketListing(listing.id);
            this.refreshAndSync();
            buyer.sendSystemMessage((Component)Component.literal((String)"[NationWars] That listing was invalid and has been removed."));
            return;
        }
        double buyerPrice = this.adjustedBuyPrice(listing.price, buyer.getUUID());
        if (!store.withdrawPlayerMoney(buyer.getUUID(), buyerPrice)) {
            buyer.sendSystemMessage((Component)Component.literal((String)("[NationWars] You need $" + NationStore.roundMoney(buyerPrice) + " to buy this.")));
            return;
        }
        if (!store.removeMarketListing(listing.id)) {
            store.addPlayerMoney(buyer.getUUID(), buyerPrice);
            this.refreshAndSync();
            buyer.sendSystemMessage((Component)Component.literal((String)"[NationWars] Someone bought that listing first."));
            return;
        }
        double sellerPayout = this.adjustedSellerPayout(listing.price, sellerId);
        store.addPlayerMoney(sellerId, sellerPayout);
        buyer.getInventory().placeItemBackInInventory(purchased);
        buyer.sendSystemMessage((Component)Component.literal((String)("[NationWars] Bought " + purchased.getCount() + "x " + purchased.getHoverName().getString() + " for $" + NationStore.roundMoney(buyerPrice) + ".")));
        this.refreshAndSync();
    }

    private double adjustedBuyPrice(double price, UUID buyer) {
        return NationStore.roundMoney(price * NationStore.get().nationOf(buyer).map(nation -> nation.doctrine().marketBuyMultiplier).orElse(1.0));
    }

    private double adjustedSellerPayout(double price, UUID seller) {
        return NationStore.roundMoney(price * NationStore.get().nationOf(seller).map(nation -> nation.doctrine().marketSellMultiplier).orElse(1.0));
    }

    private void refreshAndSync() {
        this.refreshListings();
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
