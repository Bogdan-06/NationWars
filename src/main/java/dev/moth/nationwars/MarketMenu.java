package dev.moth.nationwars;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
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

public final class MarketMenu extends AbstractContainerMenu {
   private static final int ROWS = 6;
   private static final int MARKET_SLOTS = 54;
   private final Inventory playerInventory;
   private final SimpleContainer marketContainer;
   private final int[] listingIds = new int[54];

   public MarketMenu(int containerId, Inventory playerInventory) {
      super(MenuType.GENERIC_9x6, containerId);
      this.playerInventory = playerInventory;
      this.marketContainer = new SimpleContainer(54);
      this.marketContainer.startOpen(playerInventory.player);
      this.refreshListings();

      for (int row = 0; row < 6; row++) {
         for (int column = 0; column < 9; column++) {
            this.addSlot(new MarketMenu.DisplaySlot(this.marketContainer, column + row * 9, 8 + column * 18, 18 + row * 18));
         }
      }

      int inventoryOffset = 36;

      for (int row = 0; row < 3; row++) {
         for (int column = 0; column < 9; column++) {
            this.addSlot(new Slot(playerInventory, column + row * 9 + 9, 8 + column * 18, 103 + row * 18 + inventoryOffset));
         }
      }

      for (int column = 0; column < 9; column++) {
         this.addSlot(new Slot(playerInventory, column, 8 + column * 18, 161 + inventoryOffset));
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
         if (player instanceof ServerPlayer serverPlayer && (clickType == ClickType.PICKUP || clickType == ClickType.QUICK_MOVE)) {
            this.buyListing(serverPlayer, slotIndex);
         }
      } else {
         super.clicked(slotIndex, button, clickType, player);
      }
   }

   public void removed(Player player) {
      super.removed(player);
      this.marketContainer.stopOpen(player);
   }

   private void refreshListings() {
      this.marketContainer.clearContent();

      for (int i = 0; i < this.listingIds.length; i++) {
         this.listingIds[i] = 0;
      }

      NationStore store = NationStore.get();
      List<NationStore.MarketListing> listings = store.marketListings();
      int slot = 0;

      for (NationStore.MarketListing listing : listings) {
         if (slot >= 54) {
            break;
         }

         ItemStack stack = store.listingStack(listing, this.playerInventory.player.registryAccess());
         if (!stack.isEmpty()) {
            this.marketContainer.setItem(slot, this.displayStack(stack, listing));
            this.listingIds[slot] = listing.id;
            slot++;
         }
      }

      if (slot == 0) {
         ItemStack empty = new ItemStack(Items.BARRIER);
         empty.set(DataComponents.CUSTOM_NAME, Component.literal("No market listings"));
         empty.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("Use /market sellhand <price> to list your held stack."))));
         this.marketContainer.setItem(22, empty);
      }
   }

   private ItemStack displayStack(ItemStack stack, NationStore.MarketListing listing) {
      ItemStack display = stack.copy();
      List<Component> lore = new ArrayList<>();
      lore.add(Component.literal("Price: $" + NationStore.roundMoney(listing.price)));
      lore.add(Component.literal("Seller: " + listing.sellerName));
      lore.add(Component.literal("Listing #" + listing.id));
      lore.add(Component.literal("Click to buy"));
      display.set(DataComponents.LORE, new ItemLore(lore));
      return display;
   }

   private void buyListing(ServerPlayer buyer, int slotIndex) {
      int listingId = this.listingIds[slotIndex];
      if (listingId > 0) {
         NationStore store = NationStore.get();
         NationStore.MarketListing listing = store.marketListing(listingId).orElse(null);
         if (listing == null) {
            this.refreshAndSync();
            buyer.sendSystemMessage(Component.literal("[NationWars] That listing is gone."));
         } else if (listing.seller.equals(buyer.getUUID().toString())) {
            buyer.sendSystemMessage(Component.literal("[NationWars] You cannot buy your own listing."));
         } else {
            ItemStack purchased = store.listingStack(listing, buyer.registryAccess());
            if (purchased.isEmpty()) {
               store.removeMarketListing(listing.id);
               this.refreshAndSync();
               buyer.sendSystemMessage(Component.literal("[NationWars] That listing was invalid and has been removed."));
            } else if (!store.withdrawPlayerMoney(buyer.getUUID(), listing.price)) {
               buyer.sendSystemMessage(Component.literal("[NationWars] You need $" + NationStore.roundMoney(listing.price) + " to buy this."));
            } else if (!store.removeMarketListing(listing.id)) {
               store.addPlayerMoney(buyer.getUUID(), listing.price);
               this.refreshAndSync();
               buyer.sendSystemMessage(Component.literal("[NationWars] Someone bought that listing first."));
            } else {
               store.addPlayerMoney(UUID.fromString(listing.seller), listing.price);
               buyer.getInventory().placeItemBackInInventory(purchased);
               buyer.sendSystemMessage(
                  Component.literal(
                     "[NationWars] Bought "
                        + purchased.getCount()
                        + "x "
                        + purchased.getHoverName().getString()
                        + " for $"
                        + NationStore.roundMoney(listing.price)
                        + "."
                  )
               );
               this.refreshAndSync();
            }
         }
      }
   }

   private void refreshAndSync() {
      this.refreshListings();
      this.broadcastChanges();
   }

   private static final class DisplaySlot extends Slot {
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
