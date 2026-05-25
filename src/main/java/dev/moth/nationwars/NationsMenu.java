package dev.moth.nationwars;

import java.util.ArrayList;
import java.util.List;
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

public final class NationsMenu extends AbstractContainerMenu {
   private static final int ROWS = 3;
   private static final int NATION_SLOTS = 27;
   private final Inventory playerInventory;
   private final SimpleContainer nationsContainer;
   private final String[] nationIds = new String[27];

   public NationsMenu(int containerId, Inventory playerInventory) {
      super(MenuType.GENERIC_9x3, containerId);
      this.playerInventory = playerInventory;
      this.nationsContainer = new SimpleContainer(27);
      this.nationsContainer.startOpen(playerInventory.player);
      this.refreshNations();

      for (int row = 0; row < 3; row++) {
         for (int column = 0; column < 9; column++) {
            this.addSlot(new NationsMenu.DisplaySlot(this.nationsContainer, column + row * 9, 8 + column * 18, 18 + row * 18));
         }
      }

      int inventoryOffset = -18;

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
      if (slotIndex >= 0 && slotIndex < 27) {
         if (player instanceof ServerPlayer serverPlayer && (clickType == ClickType.PICKUP || clickType == ClickType.QUICK_MOVE)) {
            this.sendNationDetails(serverPlayer, slotIndex);
         }
      } else {
         super.clicked(slotIndex, button, clickType, player);
      }
   }

   public void removed(Player player) {
      super.removed(player);
      this.nationsContainer.stopOpen(player);
   }

   private void refreshNations() {
      this.nationsContainer.clearContent();

      for (int i = 0; i < this.nationIds.length; i++) {
         this.nationIds[i] = null;
      }

      List<NationStore.Nation> nations = NationStore.get().nationsSorted();
      if (nations.isEmpty()) {
         ItemStack empty = new ItemStack(Items.BARRIER);
         empty.set(DataComponents.CUSTOM_NAME, Component.literal("No nations yet"));
         empty.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("Use /nation create to start one."))));
         this.nationsContainer.setItem(13, empty);
      } else {
         int slot = 10;

         for (NationStore.Nation nation : nations) {
            if (slot >= 27) {
               break;
            }

            this.nationIds[slot] = nation.id;
            this.nationsContainer.setItem(slot, this.displayStack(nation));
            slot += 2;
            if (slot == 18) {
               slot = 19;
            }
         }
      }
   }

   private ItemStack displayStack(NationStore.Nation nation) {
      NationStore store = NationStore.get();
      ItemStack stack = new ItemStack(NationIcons.countryBlock(nation.doctrine()));
      stack.set(DataComponents.CUSTOM_NAME, Component.literal(nation.name));
      List<Component> lore = new ArrayList<>();
      lore.add(Component.literal("Leader: " + nation.ownerName));
      lore.add(Component.literal("Claims: " + store.claimCount(nation)));
      lore.add(Component.literal("Members: " + nation.members.size()));
      lore.add(Component.literal("Cities: " + nation.cityClaims.size()));
      lore.add(Component.literal("Treasury: $" + NationStore.roundMoney(nation.balance)));
      store.allianceOf(nation).ifPresent(alliance -> lore.add(Component.literal("Alliance: " + alliance.name)));
      if (nation.capitalClaim != null && !nation.capitalClaim.isBlank()) {
         lore.add(Component.literal("Capital: " + ClaimKey.parse(nation.capitalClaim).shortName()));
      }

      int activeWars = store.activeWarsOf(nation).size();
      if (activeWars > 0) {
         lore.add(Component.literal("Active wars: " + activeWars));
      }

      lore.add(Component.literal("Click for details"));
      stack.set(DataComponents.LORE, new ItemLore(lore));
      return stack;
   }

   private void sendNationDetails(ServerPlayer player, int slotIndex) {
      String nationId = this.nationIds[slotIndex];
      if (nationId != null) {
         NationStore store = NationStore.get();
         NationStore.Nation nation = store.nationById(nationId).orElse(null);
         if (nation == null) {
            this.refreshAndSync();
            player.sendSystemMessage(Component.literal("[NationWars] That nation no longer exists."));
         } else {
            player.sendSystemMessage(Component.literal("[NationWars] " + nation.name));
            player.sendSystemMessage(
               Component.literal(
                  "Leader: "
                     + nation.ownerName
                     + " | claims: "
                     + store.claimCount(nation)
                     + " | members: "
                     + nation.members.size()
                     + " | treasury: $"
                     + NationStore.roundMoney(nation.balance)
               )
            );
            if (nation.capitalClaim != null && !nation.capitalClaim.isBlank()) {
               player.sendSystemMessage(Component.literal("Capital: " + ClaimKey.parse(nation.capitalClaim).shortName()));
            }
         }
      }
   }

   private void refreshAndSync() {
      this.refreshNations();
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
