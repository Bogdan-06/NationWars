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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

public final class NationCreateMenu extends AbstractContainerMenu {
   private static final int ROWS = 3;
   private static final int CHOICE_SLOTS = 27;
   private final String nationName;
   private final Inventory playerInventory;
   private final SimpleContainer choicesContainer;
   private final Doctrine[] choices = new Doctrine[27];

   public NationCreateMenu(int containerId, Inventory playerInventory, String nationName) {
      super(MenuType.GENERIC_9x3, containerId);
      this.playerInventory = playerInventory;
      this.nationName = nationName;
      this.choicesContainer = new SimpleContainer(27);
      this.choicesContainer.startOpen(playerInventory.player);
      this.refreshChoices();

      for (int row = 0; row < 3; row++) {
         for (int column = 0; column < 9; column++) {
            this.addSlot(new NationCreateMenu.DisplaySlot(this.choicesContainer, column + row * 9, 8 + column * 18, 18 + row * 18));
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
            this.chooseDoctrine(serverPlayer, slotIndex);
         }
      } else {
         super.clicked(slotIndex, button, clickType, player);
      }
   }

   public void removed(Player player) {
      super.removed(player);
      this.choicesContainer.stopOpen(player);
   }

   private void refreshChoices() {
      this.choicesContainer.clearContent();

      for (int i = 0; i < this.choices.length; i++) {
         this.choices[i] = null;
      }

      List<Doctrine> available = NationStore.get().availableDoctrines();
      if (available.isEmpty()) {
         ItemStack empty = new ItemStack(Items.BARRIER);
         empty.set(DataComponents.CUSTOM_NAME, Component.literal("No doctrines available"));
         empty.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("Every doctrine has already been picked."))));
         this.choicesContainer.setItem(13, empty);
      } else {
         int slot = 10;

         for (Doctrine doctrine : available) {
            if (slot >= 27) {
               break;
            }

            this.choices[slot] = doctrine;
            this.choicesContainer.setItem(slot, this.displayStack(doctrine));
            slot += 2;
            if (slot == 18) {
               slot = 19;
            }
         }
      }
   }

   private ItemStack displayStack(Doctrine doctrine) {
      ItemStack stack = new ItemStack(this.iconFor(doctrine));
      stack.set(DataComponents.CUSTOM_NAME, Component.literal(doctrine.displayName));
      List<Component> lore = new ArrayList<>();
      lore.add(Component.literal("Creates: " + this.nationName));
      lore.add(Component.literal("Ideology: " + doctrine.ideology.displayName));
      lore.add(Component.literal("Free claims: " + doctrine.freeClaims));
      lore.add(Component.literal("Income x" + doctrine.incomeMultiplier));
      lore.add(Component.literal("Claim cost x" + doctrine.claimCostMultiplier));
      lore.add(Component.literal("Maintenance x" + doctrine.maintenanceMultiplier));
      lore.add(Component.literal("Justification: " + doctrine.justificationSeconds / 60 + "m"));
      lore.add(Component.literal("Capture: " + doctrine.captureSeconds + "s"));
      lore.add(Component.literal("Click to create"));
      stack.set(DataComponents.LORE, new ItemLore(lore));
      return stack;
   }

   private void chooseDoctrine(ServerPlayer player, int slotIndex) {
      Doctrine doctrine = this.choices[slotIndex];
      if (doctrine != null) {
         if (NationCommands.createNationWithDoctrine(player, this.nationName, doctrine)) {
            player.closeContainer();
         } else {
            this.refreshChoices();
            this.broadcastChanges();
         }
      }
   }

   private Item iconFor(Doctrine doctrine) {
      return switch (doctrine) {
         case AMERICAN -> Items.DIAMOND;
         case SOVIET -> Items.REDSTONE_BLOCK;
         case FRENCH -> Items.BLUE_BANNER;
         case BRITISH -> Items.SHIELD;
         case GERMAN -> Items.IRON_SWORD;
         case ITALIAN -> Items.GREEN_BANNER;
         case ROMANIAN -> Items.EMERALD;
      };
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
