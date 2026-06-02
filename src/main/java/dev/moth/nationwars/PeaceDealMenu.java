package dev.moth.nationwars;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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

public final class PeaceDealMenu extends AbstractContainerMenu {
   private static final int ROWS = 6;
   private static final int TOP_SLOTS = 54;
   private static final double MONEY_STEP = 100.0;
   private static final double CLAIM_SCORE = 100.0;
   private static final double BASE_PEACE_OFFER_FEE = 10.0;
   private static final int[] DEMAND_SLOTS = new int[]{9, 10, 11, 12, 18, 19, 20, 21, 27, 28, 29, 30, 36, 37, 38, 39};
   private static final int[] OFFER_SLOTS = new int[]{14, 15, 16, 17, 23, 24, 25, 26, 32, 33, 34, 35, 41, 42, 43, 44};
   private final Inventory playerInventory;
   private final SimpleContainer dealContainer;
   private final NationStore.Nation ownNation;
   private final NationStore.Nation otherNation;
   private final NationStore.War war;
   private final String[] demandedClaimSlots = new String[54];
   private final String[] offeredClaimSlots = new String[54];
   private final PeaceDealMenu.Action[] actions = new PeaceDealMenu.Action[54];
   private NationStore.PeaceDeal draft;
   private boolean viewingIncoming;

   public PeaceDealMenu(int containerId, Inventory playerInventory, NationStore.Nation ownNation, NationStore.Nation otherNation, NationStore.War war) {
      super(MenuType.GENERIC_9x6, containerId);
      this.playerInventory = playerInventory;
      this.ownNation = ownNation;
      this.otherNation = otherNation;
      this.war = war;
      this.dealContainer = new SimpleContainer(54);
      this.dealContainer.startOpen(playerInventory.player);
      NationStore.PeaceDeal pending = war.peaceDeal;
      if (pending != null && ownNation.id.equals(pending.receiver)) {
         this.viewingIncoming = true;
         this.draft = copyDeal(pending);
      } else if (pending != null && ownNation.id.equals(pending.proposer)) {
         this.viewingIncoming = false;
         this.draft = copyDeal(pending);
      } else {
         this.viewingIncoming = false;
         this.draft = emptyDeal(ownNation, otherNation);
      }

      this.refresh();

      for (int row = 0; row < 6; row++) {
         for (int column = 0; column < 9; column++) {
            this.addSlot(new PeaceDealMenu.DisplaySlot(this.dealContainer, column + row * 9, 8 + column * 18, 18 + row * 18));
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
            this.handleTopClick(serverPlayer, slotIndex);
         }
      } else {
         super.clicked(slotIndex, button, clickType, player);
      }
   }

   public void removed(Player player) {
      super.removed(player);
      this.dealContainer.stopOpen(player);
   }

   private void refresh() {
      this.dealContainer.clearContent();

      for (int i = 0; i < 54; i++) {
         this.demandedClaimSlots[i] = null;
         this.offeredClaimSlots[i] = null;
         this.actions[i] = null;
      }

      this.dealContainer
         .setItem(0, item(Items.PAPER, "Make a demand", List.of(Component.literal("Claims and money you want from " + this.otherNation.name + "."))));
      this.dealContainer.setItem(4, item(Items.BOOK, "Peace Deal", this.dealLore()));
      this.dealContainer
         .setItem(8, item(Items.CHEST, "Make an offer", List.of(Component.literal("Claims and money you give to " + this.otherNation.name + "."))));

      for (int slot : List.of(13, 22, 31, 40)) {
         this.dealContainer.setItem(slot, item(Items.BLACK_STAINED_GLASS_PANE, " ", List.of()));
      }

      this.drawClaimColumn(true);
      this.drawClaimColumn(false);
      this.drawControls();
   }

   private void drawClaimColumn(boolean demands) {
      NationStore store = NationStore.get();
      NationStore.Nation source = demands ? this.otherNation : this.ownNation;
      int[] slots = demands ? DEMAND_SLOTS : OFFER_SLOTS;
      List<String> claims = store.borderClaimsOf(source).stream().filter(claimIdx -> !claimIdx.equals(source.capitalClaim)).limit((long)slots.length).toList();
      if (claims.isEmpty()) {
         int slot = slots[5];
         String name = demands ? "No demandable border claims" : "No offerable border claims";
         this.dealContainer.setItem(slot, item(Items.BARRIER, name, List.of(Component.literal("Capital claims cannot be selected."))));
      } else {
         Set<String> selected = demands ? this.draft.demandedClaims : this.draft.offeredClaims;

         for (int i = 0; i < claims.size(); i++) {
            int slot = slots[i];
            String claimId = claims.get(i);
            boolean active = selected.contains(claimId);
            Item icon = active ? Items.FILLED_MAP : Items.MAP;
            String label = (active ? "Selected: " : "") + (demands ? "Demand " : "Offer ") + ClaimKey.parse(claimId).shortName();
            List<Component> lore = new ArrayList<>();
            lore.add(Component.literal("Owner: " + source.name));
            lore.add(Component.literal("Value: 100 score"));
            lore.add(Component.literal(this.viewingIncoming ? "Incoming deal is read-only." : "Click to toggle."));
            this.dealContainer.setItem(slot, item(icon, label, lore));
            if (demands) {
               this.demandedClaimSlots[slot] = claimId;
            } else {
               this.offeredClaimSlots[slot] = claimId;
            }
         }
      }
   }

   private void drawControls() {
      this.dealContainer
         .setItem(
            45,
            item(Items.GOLD_NUGGET, "-$100 Demand", List.of(Component.literal("Demanded reparations: $" + NationStore.roundMoney(this.draft.demandedMoney))))
         );
      this.actions[45] = PeaceDealMenu.Action.DEMAND_MONEY_DOWN;
      this.dealContainer
         .setItem(
            46,
            item(Items.GOLD_INGOT, "+$100 Demand", List.of(Component.literal("Demanded reparations: $" + NationStore.roundMoney(this.draft.demandedMoney))))
         );
      this.actions[46] = PeaceDealMenu.Action.DEMAND_MONEY_UP;
      int returnable = this.returnableCapturedClaims();
      this.dealContainer
         .setItem(
            47,
            item(
               this.draft.returnCapturedClaims ? Items.LIME_BANNER : Items.WHITE_BANNER,
               "Return captured claims",
               List.of(
                  Component.literal("Captured claims: " + returnable),
                  Component.literal(this.draft.returnCapturedClaims ? "Included in this deal." : "Click to toggle.")
               )
            )
         );
      this.actions[47] = PeaceDealMenu.Action.RETURN_CAPTURED;
      if (this.viewingIncoming) {
         this.dealContainer.setItem(48, item(Items.WRITABLE_BOOK, "Make counteroffer", List.of(Component.literal("Start a new deal from your side."))));
         this.actions[48] = PeaceDealMenu.Action.COUNTER_OR_CLEAR;
         this.dealContainer
            .setItem(49, item(Items.EMERALD_BLOCK, "Accept peace deal", List.of(Component.literal("Applies the shown terms and ends the war."))));
         this.actions[49] = PeaceDealMenu.Action.PRIMARY;
      } else {
         this.dealContainer.setItem(48, item(Items.REDSTONE, "Clear draft", List.of(Component.literal("Remove all selected terms."))));
         this.actions[48] = PeaceDealMenu.Action.COUNTER_OR_CLEAR;
         this.dealContainer
            .setItem(
               49,
               item(
                  Items.EMERALD,
                  this.primaryLabel(),
                  List.of(
                     Component.literal("Sends this deal to " + this.otherNation.name + "."),
                     Component.literal("Offer fee: $" + NationStore.roundMoney(this.peaceOfferFee())),
                     Component.literal(isEmptyDeal(this.draft) ? "No terms means white peace." : summary(this.draft))
                  )
               )
            );
         this.actions[49] = PeaceDealMenu.Action.PRIMARY;
      }

      this.dealContainer
         .setItem(
            51, item(Items.GOLD_INGOT, "+$100 Offer", List.of(Component.literal("Offered reparations: $" + NationStore.roundMoney(this.draft.offeredMoney))))
         );
      this.actions[51] = PeaceDealMenu.Action.OFFER_MONEY_UP;
      this.dealContainer
         .setItem(
            52, item(Items.GOLD_NUGGET, "-$100 Offer", List.of(Component.literal("Offered reparations: $" + NationStore.roundMoney(this.draft.offeredMoney))))
         );
      this.actions[52] = PeaceDealMenu.Action.OFFER_MONEY_DOWN;
      this.dealContainer.setItem(53, item(Items.BARRIER, "Close", List.of()));
      this.actions[53] = PeaceDealMenu.Action.CLOSE;
   }

   private void handleTopClick(ServerPlayer player, int slotIndex) {
      PeaceDealMenu.Action action = this.actions[slotIndex];
      if (action != null) {
         this.handleAction(player, action);
      } else if (this.viewingIncoming) {
         player.sendSystemMessage(Component.literal("[NationWars] Click Make counteroffer before editing terms."));
      } else {
         String demandedClaim = this.demandedClaimSlots[slotIndex];
         if (demandedClaim != null) {
            toggle(this.draft.demandedClaims, demandedClaim);
            this.refreshAndSync();
         } else {
            String offeredClaim = this.offeredClaimSlots[slotIndex];
            if (offeredClaim != null) {
               toggle(this.draft.offeredClaims, offeredClaim);
               this.refreshAndSync();
            }
         }
      }
   }

   private void handleAction(ServerPlayer player, PeaceDealMenu.Action action) {
      if (this.viewingIncoming
         && action != PeaceDealMenu.Action.PRIMARY
         && action != PeaceDealMenu.Action.COUNTER_OR_CLEAR
         && action != PeaceDealMenu.Action.CLOSE) {
         player.sendSystemMessage(Component.literal("[NationWars] Click Make counteroffer before editing terms."));
      } else {
         switch (action) {
            case DEMAND_MONEY_UP:
               this.draft.demandedMoney = NationStore.roundMoney(this.draft.demandedMoney + 100.0);
               break;
            case DEMAND_MONEY_DOWN:
               this.draft.demandedMoney = NationStore.roundMoney(Math.max(0.0, this.draft.demandedMoney - 100.0));
               break;
            case OFFER_MONEY_UP:
               this.draft.offeredMoney = NationStore.roundMoney(this.draft.offeredMoney + 100.0);
               break;
            case OFFER_MONEY_DOWN:
               this.draft.offeredMoney = NationStore.roundMoney(Math.max(0.0, this.draft.offeredMoney - 100.0));
               break;
            case RETURN_CAPTURED:
               if (this.returnableCapturedClaims() > 0) {
                  this.draft.returnCapturedClaims = !this.draft.returnCapturedClaims;
               }
               break;
            case COUNTER_OR_CLEAR:
               this.viewingIncoming = false;
               this.draft = emptyDeal(this.ownNation, this.otherNation);
               break;
            case PRIMARY:
               if (this.viewingIncoming) {
                  this.acceptDeal(player);
               } else {
                  this.sendDeal(player);
               }

               return;
            case CLOSE:
               player.closeContainer();
               return;
         }

         this.refreshAndSync();
      }
   }

   private void sendDeal(ServerPlayer player) {
      NationStore store = NationStore.get();
      if (this.war.active && store.isWarParticipant(this.war, this.ownNation) && store.isWarParticipant(this.war, this.otherNation)) {
         long cooldownUntil = store.peaceCooldownUntil(this.ownNation, this.otherNation);
         if (cooldownUntil > (long)player.getServer().getTickCount()) {
            long secondsLeft = Math.max(1L, (cooldownUntil - (long)player.getServer().getTickCount()) / 20L);
            player.sendSystemMessage(Component.literal("[NationWars] Your last peace offer was rejected. Try again in " + secondsLeft + " seconds."));
         } else {
            NationStore.PeaceDeal sent = copyDeal(this.draft);
            sent.proposer = this.ownNation.id;
            sent.receiver = this.otherNation.id;
            double fee = this.peaceOfferFee();
            if (this.ownNation.balance + 1.0E-4 < fee) {
               player.sendSystemMessage(Component.literal("[NationWars] Nation treasury needs $" + NationStore.roundMoney(fee) + " to send this peace deal."));
            } else {
               this.ownNation.balance = NationStore.roundMoney(this.ownNation.balance - fee);
               store.setPeaceDeal(this.war, sent);
               store.notifyNation(
                  player.getServer(),
                  this.otherNation,
                  Component.literal(
                     "[NationWars] " + this.ownNation.name + " sent a peace deal: " + summary(sent) + " Use /peace " + this.ownNation.id + " to review it."
                  )
               );
               store.notifyNation(player.getServer(), this.ownNation, Component.literal("[NationWars] Peace deal sent to " + this.otherNation.name + "."));
               player.closeContainer();
            }
         }
      } else {
         player.sendSystemMessage(Component.literal("[NationWars] That war is no longer active."));
         player.closeContainer();
      }
   }

   private void acceptDeal(ServerPlayer player) {
      NationStore store = NationStore.get();
      NationStore.PeaceDeal pending = this.war.peaceDeal;
      if (pending != null && this.ownNation.id.equals(pending.receiver)) {
         NationStore.Nation proposer = store.nationById(pending.proposer).orElse(this.otherNation);
         NationStore.Nation receiver = store.nationById(pending.receiver).orElse(this.ownNation);
         if (!store.applyPeaceDeal(this.war, pending)) {
            player.sendSystemMessage(Component.literal("[NationWars] Could not apply that deal. Check claim ownership and treasuries."));
            this.refreshAndSync();
         } else {
            store.notifyNation(player.getServer(), proposer, Component.literal("[NationWars] " + receiver.name + " accepted the peace deal."));
            store.notifyNation(player.getServer(), receiver, Component.literal("[NationWars] Peace deal accepted with " + proposer.name + "."));
            player.closeContainer();
         }
      } else {
         player.sendSystemMessage(Component.literal("[NationWars] That peace deal is no longer available."));
         player.closeContainer();
      }
   }

   private List<Component> dealLore() {
      List<Component> lore = new ArrayList<>();
      lore.add(Component.literal(this.ownNation.name + " <-> " + this.otherNation.name));
      lore.add(Component.literal(this.viewingIncoming ? "Incoming proposal from " + this.otherNation.name : "Draft from " + this.ownNation.name));
      lore.add(Component.literal("Demand score: " + (int)this.demandScore()));
      lore.add(Component.literal("Offer score: " + (int)this.offerScore()));
      lore.add(Component.literal("Net score: " + (int)(this.demandScore() - this.offerScore())));
      lore.add(Component.literal(summary(this.draft)));
      return lore;
   }

   private String primaryLabel() {
      return this.war.peaceDeal != null && this.ownNation.id.equals(this.war.peaceDeal.proposer) ? "Replace sent deal" : "Send peace deal";
   }

   private double demandScore() {
      return (double)this.draft.demandedClaims.size() * 100.0
         + this.draft.demandedMoney
         + (this.draft.returnCapturedClaims ? (double)this.returnableCapturedClaims() * 100.0 : 0.0);
   }

   private double offerScore() {
      return (double)this.draft.offeredClaims.size() * 100.0 + this.draft.offeredMoney;
   }

   private double peaceOfferFee() {
      return NationStore.roundMoney(10.0 * this.ownNation.doctrine().peaceOfferCostMultiplier);
   }

   private int returnableCapturedClaims() {
      return NationStore.get().returnableCapturedClaimsForDeal(this.war, this.ownNation, this.otherNation);
   }

   private void refreshAndSync() {
      this.refresh();
      this.broadcastChanges();
   }

   private static void toggle(Set<String> selected, String claimId) {
      if (!selected.remove(claimId)) {
         selected.add(claimId);
      }
   }

   private static boolean isEmptyDeal(NationStore.PeaceDeal deal) {
      return deal.demandedClaims.isEmpty()
         && deal.offeredClaims.isEmpty()
         && deal.demandedMoney <= 0.0
         && deal.offeredMoney <= 0.0
         && !deal.returnCapturedClaims;
   }

   private static String summary(NationStore.PeaceDeal deal) {
      List<String> parts = new ArrayList<>();
      if (!deal.demandedClaims.isEmpty()) {
         parts.add("demands " + deal.demandedClaims.size() + " claims");
      }

      if (deal.demandedMoney > 0.0) {
         parts.add("demands $" + NationStore.roundMoney(deal.demandedMoney));
      }

      if (!deal.offeredClaims.isEmpty()) {
         parts.add("offers " + deal.offeredClaims.size() + " claims");
      }

      if (deal.offeredMoney > 0.0) {
         parts.add("offers $" + NationStore.roundMoney(deal.offeredMoney));
      }

      if (deal.returnCapturedClaims) {
         parts.add("returns captured claims");
      }

      return parts.isEmpty() ? "white peace" : String.join(", ", parts);
   }

   private static NationStore.PeaceDeal emptyDeal(NationStore.Nation proposer, NationStore.Nation receiver) {
      NationStore.PeaceDeal deal = new NationStore.PeaceDeal();
      deal.proposer = proposer.id;
      deal.receiver = receiver.id;
      return deal;
   }

   private static NationStore.PeaceDeal copyDeal(NationStore.PeaceDeal source) {
      NationStore.PeaceDeal copy = new NationStore.PeaceDeal();
      copy.proposer = source.proposer;
      copy.receiver = source.receiver;
      copy.demandedClaims.addAll(source.demandedClaims);
      copy.offeredClaims.addAll(source.offeredClaims);
      copy.demandedMoney = NationStore.roundMoney(source.demandedMoney);
      copy.offeredMoney = NationStore.roundMoney(source.offeredMoney);
      copy.returnCapturedClaims = source.returnCapturedClaims;
      return copy;
   }

   private static ItemStack item(Item item, String name, List<Component> lore) {
      ItemStack stack = new ItemStack(item);
      stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
      if (!lore.isEmpty()) {
         stack.set(DataComponents.LORE, new ItemLore(lore));
      }

      return stack;
   }

   private static enum Action {
      DEMAND_MONEY_UP,
      DEMAND_MONEY_DOWN,
      OFFER_MONEY_UP,
      OFFER_MONEY_DOWN,
      RETURN_CAPTURED,
      COUNTER_OR_CLEAR,
      PRIMARY,
      CLOSE;
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
