package dev.moth.nationwars;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

public final class DoctrineMenu extends AbstractContainerMenu {
   private static final int ROWS = 3;
   private static final int DOCTRINE_SLOTS = 27;
   private final Inventory playerInventory;
   private final SimpleContainer doctrinesContainer;

   public DoctrineMenu(int containerId, Inventory playerInventory) {
      super(MenuType.GENERIC_9x3, containerId);
      this.playerInventory = playerInventory;
      this.doctrinesContainer = new SimpleContainer(27);
      this.doctrinesContainer.startOpen(playerInventory.player);
      this.refresh();

      for (int row = 0; row < 3; row++) {
         for (int column = 0; column < 9; column++) {
            this.addSlot(new DoctrineMenu.DisplaySlot(this.doctrinesContainer, column + row * 9, 8 + column * 18, 18 + row * 18));
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

   public void removed(Player player) {
      super.removed(player);
      this.doctrinesContainer.stopOpen(player);
   }

   private void refresh() {
      this.doctrinesContainer.clearContent();
      int slot = 10;

      for (Doctrine doctrine : Doctrine.values()) {
         this.doctrinesContainer.setItem(slot, this.displayStack(doctrine));
         slot += 2;
         if (slot == 18) {
            slot = 19;
         }
      }
   }

   private ItemStack displayStack(Doctrine doctrine) {
      ItemStack stack = new ItemStack(iconFor(doctrine));
      stack.set(DataComponents.CUSTOM_NAME, Component.literal(doctrine.displayName));
      List<Component> lore = new ArrayList<>();
      lore.add(Component.literal("ID: " + doctrine.id));
      lore.add(Component.literal("Ideology: " + doctrine.ideology.displayName));
      lore.add(Component.literal(NationStore.get().isDoctrineTaken(doctrine) ? "Status: taken" : "Status: available"));
      lore.add(Component.literal("Free claims: " + doctrine.freeClaims));
      lore.add(Component.literal("Claim cost x" + doctrine.claimCostMultiplier));
      lore.add(Component.literal("Maintenance x" + doctrine.maintenanceMultiplier));
      lore.add(Component.literal("Capture: " + doctrine.captureSeconds + "s"));
      lore.add(Component.literal("Justification: " + doctrine.justificationSeconds / 60 + "m base"));
      addTraits(lore, doctrine);
      stack.set(DataComponents.LORE, new ItemLore(lore));
      return stack;
   }

   private static void addTraits(List<Component> lore, Doctrine doctrine) {
      switch (doctrine) {
         case GERMAN:
            lore.add(Component.literal("+ Faster enemy capture"));
            lore.add(Component.literal("+ Shorter war justification"));
            lore.add(Component.literal("- Higher maintenance"));
            lore.add(Component.literal("- Loses more land on surrender"));
            break;
         case SOVIET:
            lore.add(Component.literal("+ Cheaper claims and maintenance"));
            lore.add(Component.literal("+ Capital harder to conquer"));
            lore.add(Component.literal("- Capital has no passive income"));
            lore.add(Component.literal("- Higher market buy prices"));
            break;
         case AMERICAN:
            lore.add(Component.literal("+ Can buy city income claims"));
            lore.add(Component.literal("+ Better market prices"));
            lore.add(Component.literal("- Cannot justify wars"));
            lore.add(Component.literal("- Distance raises claim cost"));
            break;
         case FRENCH:
            lore.add(Component.literal("+ Lower surrender loss"));
            lore.add(Component.literal("+ Land is harder to conquer"));
            lore.add(Component.literal("- Ally defense decline costs treasury"));
            lore.add(Component.literal("- Fails maintenance harder"));
            break;
         case BRITISH:
            lore.add(Component.literal("+ Captures fascist land faster"));
            lore.add(Component.literal("+ More starter claims"));
            lore.add(Component.literal("- Coast claims fall faster"));
            lore.add(Component.literal("- Peace offers cost more"));
            break;
         case ITALIAN:
            lore.add(Component.literal("+ Speed on owned land"));
            lore.add(Component.literal("+ Land is harder to conquer"));
            lore.add(Component.literal("- War declarations can be rejected"));
            lore.add(Component.literal("- Recaptures take longer"));
            break;
         case ROMANIAN:
            lore.add(Component.literal("+ Can leave wars on cooldown"));
            lore.add(Component.literal("+ Enemies justify slower"));
            lore.add(Component.literal("- Lost core raises maintenance"));
            lore.add(Component.literal("- Treasury randomly drains"));
      }
   }

   private static Item iconFor(Doctrine doctrine) {
      return switch (doctrine) {
         case GERMAN -> Items.IRON_SWORD;
         case SOVIET -> Items.REDSTONE_BLOCK;
         case AMERICAN -> Items.DIAMOND;
         case FRENCH -> Items.BLUE_BANNER;
         case BRITISH -> Items.SHIELD;
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
