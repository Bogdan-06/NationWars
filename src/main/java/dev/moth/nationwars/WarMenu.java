package dev.moth.nationwars;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
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

public final class WarMenu extends AbstractContainerMenu {
   private static final int ROWS = 6;
   private static final int WAR_SLOTS = 54;
   private final Inventory playerInventory;
   private final SimpleContainer warContainer;
   private final String[] warIds = new String[54];
   private final String[] hints = new String[54];

   public WarMenu(int containerId, Inventory playerInventory) {
      super(MenuType.GENERIC_9x6, containerId);
      this.playerInventory = playerInventory;
      this.warContainer = new SimpleContainer(54);
      this.warContainer.startOpen(playerInventory.player);
      this.refreshWars();

      for (int row = 0; row < 6; row++) {
         for (int column = 0; column < 9; column++) {
            this.addSlot(new WarMenu.DisplaySlot(this.warContainer, column + row * 9, 8 + column * 18, 18 + row * 18));
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
            this.sendDetailsOrHint(serverPlayer, slotIndex);
         }
      } else {
         super.clicked(slotIndex, button, clickType, player);
      }
   }

   public void removed(Player player) {
      super.removed(player);
      this.warContainer.stopOpen(player);
   }

   private void refreshWars() {
      this.warContainer.clearContent();

      for (int i = 0; i < 54; i++) {
         this.warIds[i] = null;
         this.hints[i] = null;
      }

      NationStore store = NationStore.get();
      MinecraftServer server = this.playerInventory.player.getServer();
      List<NationStore.War> wars = store.wars().stream().sorted(Comparator.comparing(warx -> warx.id)).toList();
      int slot = 0;

      for (NationStore.War war : wars) {
         if (slot >= 36) {
            break;
         }

         this.warIds[slot] = war.id;
         this.warContainer.setItem(slot, this.displayWar(store, server, war));
         slot++;
      }

      if (slot == 0) {
         ItemStack empty = new ItemStack(Items.BARRIER);
         empty.set(DataComponents.CUSTOM_NAME, Component.literal("No wars or justifications"));
         empty.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("Start with /war justify <nation>."))));
         this.warContainer.setItem(13, empty);
      }

      this.setHint(45, Items.WRITABLE_BOOK.getDefaultInstance(), "Justify war", "/war justify <nation>");
      this.setHint(46, Items.IRON_SWORD.getDefaultInstance(), "Declare war", "/war declare <nation>");
      this.setHint(47, Items.SHIELD.getDefaultInstance(), "Join or defend", "/war join <nation> | /war defend <nation>");
      this.setHint(48, Items.WHITE_BANNER.getDefaultInstance(), "Peace deal", "/peace <nation>");
      this.setHint(49, Items.RED_BANNER.getDefaultInstance(), "Surrender", "/surrender <nation>");
   }

   private ItemStack displayWar(NationStore store, MinecraftServer server, NationStore.War war) {
      String attacker = store.nationById(war.attacker).map(nation -> nation.name).orElse(war.attacker);
      String defender = store.nationById(war.defender).map(nation -> nation.name).orElse(war.defender);
      ItemStack stack = new ItemStack(war.active ? Items.IRON_SWORD : Items.CLOCK);
      stack.set(DataComponents.CUSTOM_NAME, Component.literal(attacker + " vs " + defender));
      List<Component> lore = new ArrayList<>();
      if (war.active) {
         lore.add(Component.literal("Status: active"));
         lore.add(Component.literal("Attackers: " + nationNames(store, war.attackerSide)));
         lore.add(Component.literal("Defenders: " + nationNames(store, war.defenderSide)));
         lore.add(Component.literal("Captured claims: " + capturedCount(war)));
         if (!war.joinRequests.isEmpty()) {
            lore.add(Component.literal("Pending joins: " + nationNames(store, war.joinRequests.keySet())));
         }

         if (war.peaceDeal != null) {
            lore.add(Component.literal("Peace offer pending"));
         }
      } else if (war.pendingDefenderResponse) {
         lore.add(Component.literal("Status: waiting for defender response"));
      } else {
         long secondsLeft = Math.max(0L, (war.justificationCompleteTick - (long)server.getTickCount()) / 20L);
         lore.add(Component.literal("Status: justifying"));
         lore.add(Component.literal("Ready in: " + secondsLeft + "s"));
      }

      lore.add(Component.literal("Click for details"));
      stack.set(DataComponents.LORE, new ItemLore(lore));
      return stack;
   }

   private void setHint(int slot, ItemStack stack, String title, String command) {
      stack.set(DataComponents.CUSTOM_NAME, Component.literal(title));
      stack.set(DataComponents.LORE, new ItemLore(List.of(Component.literal(command), Component.literal("Click to print command"))));
      this.hints[slot] = command;
      this.warContainer.setItem(slot, stack);
   }

   private void sendDetailsOrHint(ServerPlayer player, int slotIndex) {
      if (this.hints[slotIndex] != null) {
         player.sendSystemMessage(Component.literal("[NationWars] " + this.hints[slotIndex]));
      } else if (this.warIds[slotIndex] != null) {
         NationStore store = NationStore.get();
         NationStore.War war = store.wars().stream().filter(candidate -> this.warIds[slotIndex].equals(candidate.id)).findFirst().orElse(null);
         if (war == null) {
            this.refreshAndSync();
            player.sendSystemMessage(Component.literal("[NationWars] That war no longer exists."));
         } else {
            String attacker = store.nationById(war.attacker).map(nation -> nation.name).orElse(war.attacker);
            String defender = store.nationById(war.defender).map(nation -> nation.name).orElse(war.defender);
            player.sendSystemMessage(Component.literal("[NationWars] " + attacker + " vs " + defender));
            player.sendSystemMessage(Component.literal("Attackers: " + nationNames(store, war.attackerSide)));
            player.sendSystemMessage(Component.literal("Defenders: " + nationNames(store, war.defenderSide)));
            player.sendSystemMessage(Component.literal("Captured claims: " + capturedCount(war)));
         }
      }
   }

   private void refreshAndSync() {
      this.refreshWars();
      this.broadcastChanges();
   }

   private static String nationNames(NationStore store, Set<String> ids) {
      return ids.isEmpty()
         ? "none"
         : ids.stream().map(id -> store.nationById(id).map(nation -> nation.name).orElse(id)).sorted().collect(Collectors.joining(", "));
   }

   private static int capturedCount(NationStore.War war) {
      return war.capturedClaimsByNation.values().stream().mapToInt(Set::size).sum();
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
