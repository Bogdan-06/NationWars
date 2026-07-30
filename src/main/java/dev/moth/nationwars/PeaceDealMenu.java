/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.core.component.DataComponents
 *  net.minecraft.network.chat.Component
 *  net.minecraft.server.level.ServerPlayer
 *  net.minecraft.world.Container
 *  net.minecraft.world.SimpleContainer
 *  net.minecraft.world.entity.player.Inventory
 *  net.minecraft.world.entity.player.Player
 *  net.minecraft.world.inventory.AbstractContainerMenu
 *  net.minecraft.world.inventory.ClickType
 *  net.minecraft.world.inventory.MenuType
 *  net.minecraft.world.inventory.Slot
 *  net.minecraft.world.item.Item
 *  net.minecraft.world.item.ItemStack
 *  net.minecraft.world.item.Items
 *  net.minecraft.world.item.component.ItemLore
 *  net.minecraft.world.level.ItemLike
 */
package dev.moth.nationwars;

import dev.moth.nationwars.ClaimKey;
import dev.moth.nationwars.NationStore;
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
import net.minecraft.world.level.ItemLike;

public final class PeaceDealMenu
extends AbstractContainerMenu {
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
    private final NationStore.PeaceDeal incomingSnapshot;
    private final String[] demandedClaimSlots = new String[54];
    private final String[] offeredClaimSlots = new String[54];
    private final Action[] actions = new Action[54];
    private NationStore.PeaceDeal draft;
    private boolean viewingIncoming;
    private int demandPage;
    private int offerPage;

    public PeaceDealMenu(int containerId, Inventory playerInventory, NationStore.Nation ownNation, NationStore.Nation otherNation, NationStore.War war) {
        super(MenuType.GENERIC_9x6, containerId);
        int column;
        this.playerInventory = playerInventory;
        this.ownNation = ownNation;
        this.otherNation = otherNation;
        this.war = war;
        this.dealContainer = new SimpleContainer(54);
        this.dealContainer.startOpen(playerInventory.player);
        NationStore.PeaceDeal pending = war.peaceDeal;
        if (pending != null && ownNation.id.equals(pending.receiver)) {
            this.viewingIncoming = true;
            this.incomingSnapshot = PeaceDealMenu.copyDeal(pending);
            this.draft = PeaceDealMenu.copyDeal(pending);
        } else if (pending != null && ownNation.id.equals(pending.proposer)) {
            this.viewingIncoming = false;
            this.incomingSnapshot = null;
            this.draft = PeaceDealMenu.copyDeal(pending);
        } else {
            this.viewingIncoming = false;
            this.incomingSnapshot = null;
            this.draft = PeaceDealMenu.emptyDeal(ownNation, otherNation);
        }
        this.refresh();
        for (int row = 0; row < 6; ++row) {
            for (column = 0; column < 9; ++column) {
                this.addSlot(new DisplaySlot((Container)this.dealContainer, column + row * 9, 8 + column * 18, 18 + row * 18));
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
                    this.handleTopClick(serverPlayer, slotIndex);
                }
            }
            return;
        }
        super.clicked(slotIndex, button, clickType, player);
    }

    public void removed(Player player) {
        super.removed(player);
        this.dealContainer.stopOpen(player);
    }

    private void refresh() {
        this.dealContainer.clearContent();
        for (int i = 0; i < 54; ++i) {
            this.demandedClaimSlots[i] = null;
            this.offeredClaimSlots[i] = null;
            this.actions[i] = null;
        }
        this.dealContainer.setItem(0, PeaceDealMenu.item(Items.PAPER,
            NationText.tr(this.viewingIncoming ? "nationwars.gui.peace.they_demand" : "nationwars.gui.peace.make_demand"),
            List.of(NationText.tr(this.viewingIncoming ? "nationwars.gui.peace.they_demand_lore" : "nationwars.gui.peace.make_demand_lore", this.otherNation.name))));
        this.dealContainer.setItem(4, PeaceDealMenu.item(Items.BOOK, NationText.tr("nationwars.gui.peace.title"), this.dealLore()));
        this.dealContainer.setItem(8, PeaceDealMenu.item(Items.CHEST,
            NationText.tr(this.viewingIncoming ? "nationwars.gui.peace.they_offer" : "nationwars.gui.peace.make_offer"),
            List.of(NationText.tr(this.viewingIncoming ? "nationwars.gui.peace.they_offer_lore" : "nationwars.gui.peace.make_offer_lore", this.otherNation.name))));
        for (int slot : List.of(Integer.valueOf(13), Integer.valueOf(22), Integer.valueOf(31), Integer.valueOf(40))) {
            this.dealContainer.setItem(slot, PeaceDealMenu.item(Items.BLACK_STAINED_GLASS_PANE, Component.literal(" "), List.of()));
        }
        this.drawPageControls();
        this.drawClaimColumn(true);
        this.drawClaimColumn(false);
        this.drawControls();
    }

    private void drawClaimColumn(boolean demands) {
        NationStore store = NationStore.get();
        NationStore.Nation source = demands ? this.demandSource() : this.offerSource();
        int[] slots = demands ? DEMAND_SLOTS : OFFER_SLOTS;
        Set<String> selected = demands ? this.draft.demandedClaims : this.draft.offeredClaims;
        ArrayList<String> allClaims = new ArrayList<>(store.borderClaimsOf(source).stream()
            .filter(claimId -> !claimId.equals(source.capitalClaim))
            .filter(claimId -> !store.isClaimCapturedInOtherActiveWar(this.war, claimId)).toList());
        selected.stream().filter(claimId -> source.id.equals(store.nationOwning(ClaimKey.parse(claimId)).map(owner -> owner.id).orElse("")))
            .filter(claimId -> !claimId.equals(source.capitalClaim) && !store.isClaimCapturedInOtherActiveWar(this.war, claimId)
                && !allClaims.contains(claimId)).forEach(allClaims::add);
        allClaims.sort(String::compareTo);
        int pageCount = Math.max(1, (allClaims.size() + slots.length - 1) / slots.length);
        int selectedPage = demands ? Math.min(this.demandPage, pageCount - 1) : Math.min(this.offerPage, pageCount - 1);
        if (demands) {
            this.demandPage = selectedPage;
        } else {
            this.offerPage = selectedPage;
        }
        List<String> claims = allClaims.stream().skip((long)selectedPage * slots.length).limit(slots.length).toList();
        if (claims.isEmpty()) {
            int slot = slots[5];
            Component name = NationText.tr(demands ? "nationwars.gui.peace.no_demandable" : "nationwars.gui.peace.no_offerable");
            this.dealContainer.setItem(slot, PeaceDealMenu.item(Items.BARRIER, name, List.of(NationText.tr("nationwars.gui.peace.capital_excluded"))));
            return;
        }
        for (int i = 0; i < claims.size(); ++i) {
            int slot = slots[i];
            String claimId2 = claims.get(i);
            boolean active = selected.contains(claimId2);
            Item icon = active ? Items.FILLED_MAP : Items.MAP;
            String direction = this.viewingIncoming ? (demands ? "they_demand_claim" : "they_offer_claim") : (demands ? "demand_claim" : "offer_claim");
            String labelKey = "nationwars.gui.peace." + (active ? "selected_" : "") + direction;
            Component label = NationText.tr(labelKey, ClaimKey.parse(claimId2).shortName());
            ArrayList<Component> lore = new ArrayList<Component>();
            lore.add(NationText.tr("nationwars.gui.common.owner", source.name));
            lore.add(NationText.tr("nationwars.gui.peace.claim_score"));
            lore.add(NationText.tr(this.viewingIncoming ? "nationwars.gui.peace.read_only" : "nationwars.gui.common.click_toggle"));
            this.dealContainer.setItem(slot, PeaceDealMenu.item(icon, label, lore));
            if (demands) {
                this.demandedClaimSlots[slot] = claimId2;
                continue;
            }
            this.offeredClaimSlots[slot] = claimId2;
        }
    }

    private void drawPageControls() {
        int demandPages = this.claimPageCount(this.demandSource(), this.draft.demandedClaims);
        int offerPages = this.claimPageCount(this.offerSource(), this.draft.offeredClaims);
        this.dealContainer.setItem(1, PeaceDealMenu.item(Items.ARROW, NationText.tr("nationwars.gui.peace.previous_demands"), List.of(NationText.tr("nationwars.gui.common.page", this.demandPage + 1, demandPages))));
        this.actions[1] = Action.DEMAND_PREV;
        this.dealContainer.setItem(2, PeaceDealMenu.item(Items.ARROW, NationText.tr("nationwars.gui.peace.next_demands"), List.of(NationText.tr("nationwars.gui.common.page", this.demandPage + 1, demandPages))));
        this.actions[2] = Action.DEMAND_NEXT;
        this.dealContainer.setItem(6, PeaceDealMenu.item(Items.ARROW, NationText.tr("nationwars.gui.peace.previous_offers"), List.of(NationText.tr("nationwars.gui.common.page", this.offerPage + 1, offerPages))));
        this.actions[6] = Action.OFFER_PREV;
        this.dealContainer.setItem(7, PeaceDealMenu.item(Items.ARROW, NationText.tr("nationwars.gui.peace.next_offers"), List.of(NationText.tr("nationwars.gui.common.page", this.offerPage + 1, offerPages))));
        this.actions[7] = Action.OFFER_NEXT;
    }

    private int claimPageCount(NationStore.Nation nation, Set<String> selected) {
        NationStore store = NationStore.get();
        Set<String> visible = new java.util.LinkedHashSet<>(store.borderClaimsOf(nation).stream()
            .filter(claimId -> !claimId.equals(nation.capitalClaim))
            .filter(claimId -> !store.isClaimCapturedInOtherActiveWar(this.war, claimId)).toList());
        selected.stream().filter(claimId -> nation.id.equals(store.nationOwning(ClaimKey.parse(claimId)).map(owner -> owner.id).orElse("")))
            .filter(claimId -> !claimId.equals(nation.capitalClaim) && !store.isClaimCapturedInOtherActiveWar(this.war, claimId)).forEach(visible::add);
        int claims = visible.size();
        return Math.max(1, (claims + DEMAND_SLOTS.length - 1) / DEMAND_SLOTS.length);
    }

    private NationStore.Nation demandSource() {
        return NationStore.peaceDemandSource(this.viewingIncoming, this.ownNation, this.otherNation);
    }

    private NationStore.Nation offerSource() {
        return NationStore.peaceOfferSource(this.viewingIncoming, this.ownNation, this.otherNation);
    }

    private void drawControls() {
        if (this.viewingIncoming) {
            this.dealContainer.setItem(45, PeaceDealMenu.item(Items.GOLD_INGOT, NationText.tr("nationwars.gui.peace.they_demand_money", NationStore.roundMoney(this.draft.demandedMoney)),
                List.of(NationText.tr("nationwars.gui.peace.demand_taken"))));
            this.dealContainer.setItem(46, PeaceDealMenu.item(Items.GRAY_STAINED_GLASS_PANE, NationText.tr("nationwars.gui.peace.terms_read_only"), List.of()));
        } else {
            this.dealContainer.setItem(45, PeaceDealMenu.item(Items.GOLD_NUGGET, NationText.tr("nationwars.gui.peace.demand_down"), List.of(NationText.tr("nationwars.gui.peace.demanded_reparations", NationStore.roundMoney(this.draft.demandedMoney)))));
            this.actions[45] = Action.DEMAND_MONEY_DOWN;
            this.dealContainer.setItem(46, PeaceDealMenu.item(Items.GOLD_INGOT, NationText.tr("nationwars.gui.peace.demand_up"), List.of(NationText.tr("nationwars.gui.peace.demanded_reparations", NationStore.roundMoney(this.draft.demandedMoney)))));
            this.actions[46] = Action.DEMAND_MONEY_UP;
        }
        int returnable = this.returnableCapturedClaims();
        this.dealContainer.setItem(47, PeaceDealMenu.item(this.draft.returnCapturedClaims ? Items.LIME_BANNER : Items.WHITE_BANNER,
            NationText.tr("nationwars.gui.peace.return_captured"), List.of(NationText.tr("nationwars.gui.wars.captured", returnable),
                NationText.tr(this.draft.returnCapturedClaims ? "nationwars.gui.peace.included" : "nationwars.gui.common.click_toggle"))));
        this.actions[47] = Action.RETURN_CAPTURED;
        if (this.viewingIncoming) {
            this.dealContainer.setItem(48, PeaceDealMenu.item(Items.WRITABLE_BOOK, NationText.tr("nationwars.gui.peace.counteroffer"), List.of(NationText.tr("nationwars.gui.peace.counteroffer_lore"))));
            this.actions[48] = Action.COUNTER_OR_CLEAR;
            this.dealContainer.setItem(49, PeaceDealMenu.item(Items.EMERALD_BLOCK, NationText.tr("nationwars.gui.peace.accept"), List.of(NationText.tr("nationwars.gui.peace.accept_lore"))));
            this.actions[49] = Action.PRIMARY;
        } else {
            this.dealContainer.setItem(48, PeaceDealMenu.item(Items.REDSTONE, NationText.tr("nationwars.gui.trade.clear"), List.of(NationText.tr("nationwars.gui.trade.clear_lore"))));
            this.actions[48] = Action.COUNTER_OR_CLEAR;
            this.dealContainer.setItem(49, PeaceDealMenu.item(Items.EMERALD, this.primaryLabel(), List.of(
                NationText.tr("nationwars.gui.peace.sends_to", this.otherNation.name),
                NationText.tr("nationwars.gui.peace.offer_fee", NationStore.roundMoney(this.peaceOfferFee())),
                PeaceDealMenu.isEmptyDeal(this.draft) ? NationText.tr("nationwars.gui.peace.white_peace_lore") : PeaceDealMenu.summary(this.draft))));
            this.actions[49] = Action.PRIMARY;
        }
        this.drawPuppetTerm();
        if (this.viewingIncoming) {
            this.dealContainer.setItem(51, PeaceDealMenu.item(Items.GOLD_INGOT, NationText.tr("nationwars.gui.peace.they_offer_money", NationStore.roundMoney(this.draft.offeredMoney)),
                List.of(NationText.tr("nationwars.gui.peace.offer_paid"))));
            this.dealContainer.setItem(52, PeaceDealMenu.item(Items.GRAY_STAINED_GLASS_PANE, NationText.tr("nationwars.gui.peace.terms_read_only"), List.of()));
        } else {
            this.dealContainer.setItem(51, PeaceDealMenu.item(Items.GOLD_INGOT, NationText.tr("nationwars.gui.peace.offer_up"), List.of(NationText.tr("nationwars.gui.peace.offered_reparations", NationStore.roundMoney(this.draft.offeredMoney)))));
            this.actions[51] = Action.OFFER_MONEY_UP;
            this.dealContainer.setItem(52, PeaceDealMenu.item(Items.GOLD_NUGGET, NationText.tr("nationwars.gui.peace.offer_down"), List.of(NationText.tr("nationwars.gui.peace.offered_reparations", NationStore.roundMoney(this.draft.offeredMoney)))));
            this.actions[52] = Action.OFFER_MONEY_DOWN;
        }
        this.dealContainer.setItem(53, PeaceDealMenu.item(Items.BARRIER, NationText.tr("nationwars.gui.common.close"), List.of()));
        this.actions[53] = Action.CLOSE;
    }

    private void drawPuppetTerm() {
        if (!NationWarsConfig.get().puppets || this.war.independenceWar) {
            return;
        }
        boolean available = this.viewingIncoming || NationStore.get().canEstablishPuppet(this.ownNation, this.otherNation);
        Component name = NationText.tr(this.draft.puppetReceiver
            ? "nationwars.gui.peace.puppet.enabled"
            : "nationwars.gui.peace.puppet.disabled");
        ArrayList<Component> lore = new ArrayList<>();
        lore.add(NationText.tr("nationwars.gui.peace.puppet.term", this.viewingIncoming ? this.ownNation.name : this.otherNation.name));
        if (this.viewingIncoming) {
            lore.add(NationText.tr("nationwars.gui.peace.puppet.read_only"));
        } else if (available || this.draft.puppetReceiver) {
            lore.add(NationText.tr("nationwars.gui.peace.puppet.click_toggle"));
            this.actions[50] = Action.PUPPET_RECEIVER;
        } else {
            lore.add(NationText.tr("nationwars.gui.peace.puppet.unavailable"));
        }
        this.dealContainer.setItem(50, PeaceDealMenu.item(
            this.draft.puppetReceiver ? Items.LIME_BANNER : Items.WHITE_BANNER, name, lore));
    }

    private void handleTopClick(ServerPlayer player, int slotIndex) {
        Action action = this.actions[slotIndex];
        if (action != null) {
            this.handleAction(player, action);
            return;
        }
        if (this.viewingIncoming) {
            player.sendSystemMessage(NationText.message("nationwars.peace.error.counteroffer_first"));
            return;
        }
        String demandedClaim = this.demandedClaimSlots[slotIndex];
        if (demandedClaim != null) {
            PeaceDealMenu.toggle(this.draft.demandedClaims, demandedClaim);
            this.refreshAndSync();
            return;
        }
        String offeredClaim = this.offeredClaimSlots[slotIndex];
        if (offeredClaim != null) {
            PeaceDealMenu.toggle(this.draft.offeredClaims, offeredClaim);
            this.refreshAndSync();
        }
    }

    private void handleAction(ServerPlayer player, Action action) {
        if (this.viewingIncoming && action != Action.PRIMARY && action != Action.COUNTER_OR_CLEAR && action != Action.CLOSE
            && action != Action.DEMAND_PREV && action != Action.DEMAND_NEXT && action != Action.OFFER_PREV && action != Action.OFFER_NEXT) {
            player.sendSystemMessage(NationText.message("nationwars.peace.error.counteroffer_first"));
            return;
        }
        switch (action.ordinal()) {
            case 0: {
                this.draft.demandedMoney = NationStore.roundMoney(this.draft.demandedMoney + 100.0);
                break;
            }
            case 1: {
                this.draft.demandedMoney = NationStore.roundMoney(Math.max(0.0, this.draft.demandedMoney - 100.0));
                break;
            }
            case 2: {
                this.draft.offeredMoney = NationStore.roundMoney(this.draft.offeredMoney + 100.0);
                break;
            }
            case 3: {
                this.draft.offeredMoney = NationStore.roundMoney(Math.max(0.0, this.draft.offeredMoney - 100.0));
                break;
            }
            case 4: {
                if (this.returnableCapturedClaims() <= 0) break;
                this.draft.returnCapturedClaims = !this.draft.returnCapturedClaims;
                break;
            }
            case 5: {
                if (this.viewingIncoming && !this.currentIncomingMatches()) {
                    this.refuseStaleIncoming(player);
                    return;
                }
                this.viewingIncoming = false;
                this.draft = PeaceDealMenu.emptyDeal(this.ownNation, this.otherNation);
                break;
            }
            case 6: {
                if (this.viewingIncoming) {
                    this.acceptDeal(player);
                } else {
                    this.sendDeal(player);
                }
                return;
            }
            case 7: {
                player.closeContainer();
                return;
            }
            case 8: {
                this.demandPage = Math.max(0, this.demandPage - 1);
                break;
            }
            case 9: {
                this.demandPage = Math.min(this.claimPageCount(this.demandSource(), this.draft.demandedClaims) - 1, this.demandPage + 1);
                break;
            }
            case 10: {
                this.offerPage = Math.max(0, this.offerPage - 1);
                break;
            }
            case 11: {
                this.offerPage = Math.min(this.claimPageCount(this.offerSource(), this.draft.offeredClaims) - 1, this.offerPage + 1);
                break;
            }
            case 12: {
                if (!this.draft.puppetReceiver && !NationStore.get().canEstablishPuppet(this.ownNation, this.otherNation)) {
                    player.sendSystemMessage(NationText.message("nationwars.gui.peace.puppet.error_unavailable"));
                    return;
                }
                this.draft.puppetReceiver = !this.draft.puppetReceiver;
                break;
            }
        }
        this.refreshAndSync();
    }

    private void sendDeal(ServerPlayer player) {
        if (NationWarsConfig.get().noMercy) {
            player.sendSystemMessage(NationText.message("nationwars.peace.error.disabled"));
            player.closeContainer();
            return;
        }
        if (this.incomingSnapshot != null && !this.currentIncomingMatches()) {
            this.refuseStaleIncoming(player);
            return;
        }
        NationStore store = NationStore.get();
        if (!store.isCurrentNation(this.ownNation) || !store.isCurrentNation(this.otherNation)
            || !store.isOwner(player.getUUID(), this.ownNation)) {
            player.sendSystemMessage(NationText.message("nationwars.peace.error.owner_send"));
            player.closeContainer();
            return;
        }
        if (!(store.isCurrentActiveWar(this.war) && store.isWarParticipant(this.war, this.ownNation) && store.isWarParticipant(this.war, this.otherNation))) {
            player.sendSystemMessage(NationText.message("nationwars.peace.error.war_inactive"));
            player.closeContainer();
            return;
        }
        if (this.draft.puppetReceiver && (!NationWarsConfig.get().puppets || this.war.independenceWar
            || !store.canEstablishPuppet(this.ownNation, this.otherNation))) {
            player.sendSystemMessage(NationText.message("nationwars.gui.peace.puppet.error_unavailable"));
            this.refreshAndSync();
            return;
        }
        NationStore.PeaceDeal existing = this.war.peaceDeal;
        if (existing != null && !(this.ownNation.id.equals(existing.proposer) && this.otherNation.id.equals(existing.receiver)
            || this.ownNation.id.equals(existing.receiver) && this.otherNation.id.equals(existing.proposer))) {
            player.sendSystemMessage(NationText.message("nationwars.peace.error.other_pair"));
            return;
        }
        long cooldownUntil = store.peaceCooldownUntil(this.ownNation, this.otherNation);
        if (cooldownUntil > NationStore.persistentNow()) {
            long secondsLeft = Math.max(1L, (cooldownUntil - NationStore.persistentNow()) / 20L);
            player.sendSystemMessage(NationText.message("nationwars.peace.error.cooldown", secondsLeft));
            return;
        }
        NationStore.PeaceDeal sent = PeaceDealMenu.copyDeal(this.draft);
        sent.proposer = this.ownNation.id;
        sent.receiver = this.otherNation.id;
        double fee = this.peaceOfferFee();
        if (store.isSpendingBlocked(this.ownNation, NationStore.persistentNow())) {
            player.sendSystemMessage(NationText.message("nationwars.peace.error.spending_blocked"));
            return;
        }
        if (this.ownNation.balance + 1.0E-4 < fee) {
            player.sendSystemMessage(NationText.message("nationwars.peace.error.fee", NationStore.roundMoney(fee)));
            return;
        }
        this.ownNation.balance = NationStore.roundMoney(this.ownNation.balance - fee);
        if (!store.setPeaceDeal(this.war, sent)) {
            this.ownNation.balance = NationStore.roundMoney(this.ownNation.balance + fee);
            player.sendSystemMessage(NationText.message("nationwars.peace.error.changed"));
            player.closeContainer();
            return;
        }
        store.notifyNation(player.getServer(), this.otherNation, NationText.message("nationwars.peace.sent_received", this.ownNation.name, PeaceDealMenu.summary(sent), this.ownNation.id));
        store.notifyNation(player.getServer(), this.ownNation, NationText.message("nationwars.peace.sent", this.otherNation.name));
        player.closeContainer();
    }

    private void acceptDeal(ServerPlayer player) {
        if (NationWarsConfig.get().noMercy) {
            player.sendSystemMessage(NationText.message("nationwars.peace.error.disabled"));
            player.closeContainer();
            return;
        }
        NationStore store = NationStore.get();
        if (!store.isCurrentNation(this.ownNation) || !store.isCurrentNation(this.otherNation)
            || !store.isOwner(player.getUUID(), this.ownNation)) {
            player.sendSystemMessage(NationText.message("nationwars.peace.error.owner_accept"));
            player.closeContainer();
            return;
        }
        NationStore.PeaceDeal pending = this.war.peaceDeal;
        if (pending == null || !this.ownNation.id.equals(pending.receiver)) {
            player.sendSystemMessage(NationText.message("nationwars.peace.error.unavailable"));
            player.closeContainer();
            return;
        }
        if (!PeaceDealMenu.sameDeal(this.incomingSnapshot, pending)) {
            this.refuseStaleIncoming(player);
            return;
        }
        NationStore.Nation proposer = store.nationById(pending.proposer).orElse(this.otherNation);
        NationStore.Nation receiver = store.nationById(pending.receiver).orElse(this.ownNation);
        if (!store.applyPeaceDeal(this.war, pending)) {
            player.sendSystemMessage(NationText.message("nationwars.peace.error.apply"));
            this.refreshAndSync();
            return;
        }
        store.notifyNation(player.getServer(), proposer, NationText.message("nationwars.peace.accepted_by", receiver.name));
        store.notifyNation(player.getServer(), receiver, NationText.message("nationwars.peace.accepted_with", proposer.name));
        player.closeContainer();
    }

    private List<Component> dealLore() {
        ArrayList<Component> lore = new ArrayList<Component>();
        lore.add(NationText.tr("nationwars.gui.common.between", this.ownNation.name, this.otherNation.name));
        lore.add(NationText.tr(this.viewingIncoming ? "nationwars.gui.common.incoming_from" : "nationwars.gui.common.draft_from",
            this.viewingIncoming ? this.otherNation.name : this.ownNation.name));
        lore.add(NationText.tr(this.viewingIncoming ? "nationwars.gui.peace.they_demand_score" : "nationwars.gui.peace.demand_score", (int)this.demandScore()));
        lore.add(NationText.tr(this.viewingIncoming ? "nationwars.gui.peace.they_offer_score" : "nationwars.gui.peace.offer_score", (int)this.offerScore()));
        lore.add(NationText.tr(this.viewingIncoming ? "nationwars.gui.peace.net_cost" : "nationwars.gui.peace.net_score", (int)(this.demandScore() - this.offerScore())));
        lore.add(PeaceDealMenu.summary(this.draft));
        return lore;
    }

    private Component primaryLabel() {
        return NationText.tr(this.war.peaceDeal != null && this.ownNation.id.equals(this.war.peaceDeal.proposer)
            ? "nationwars.gui.peace.replace" : "nationwars.gui.peace.send");
    }

    private double demandScore() {
        return (double)this.draft.demandedClaims.size() * 100.0 + this.draft.demandedMoney + (this.draft.returnCapturedClaims ? (double)this.returnableCapturedClaims() * 100.0 : 0.0);
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

    private boolean currentIncomingMatches() {
        return PeaceDealMenu.sameDeal(this.incomingSnapshot, this.war.peaceDeal);
    }

    private void refuseStaleIncoming(ServerPlayer player) {
        player.sendSystemMessage(NationText.message("nationwars.peace.error.stale"));
        player.closeContainer();
    }

    private static void toggle(Set<String> selected, String claimId) {
        if (!selected.remove(claimId)) {
            selected.add(claimId);
        }
    }

    private static boolean isEmptyDeal(NationStore.PeaceDeal deal) {
        return deal.demandedClaims.isEmpty() && deal.offeredClaims.isEmpty() && deal.demandedMoney <= 0.0 && deal.offeredMoney <= 0.0
            && !deal.returnCapturedClaims && !deal.puppetReceiver;
    }

    private static Component summary(NationStore.PeaceDeal deal) {
        List<Component> parts = new ArrayList<>();
        if (!deal.demandedClaims.isEmpty()) {
            parts.add(NationText.tr("nationwars.peace.summary.demands_claims", deal.demandedClaims.size()));
        }
        if (deal.demandedMoney > 0.0) {
            parts.add(NationText.tr("nationwars.peace.summary.demands_money", NationStore.roundMoney(deal.demandedMoney)));
        }
        if (!deal.offeredClaims.isEmpty()) {
            parts.add(NationText.tr("nationwars.peace.summary.offers_claims", deal.offeredClaims.size()));
        }
        if (deal.offeredMoney > 0.0) {
            parts.add(NationText.tr("nationwars.peace.summary.offers_money", NationStore.roundMoney(deal.offeredMoney)));
        }
        if (deal.returnCapturedClaims) {
            parts.add(NationText.tr("nationwars.peace.summary.returns_captured"));
        }
        if (deal.puppetReceiver) {
            parts.add(NationText.tr("nationwars.gui.peace.puppet.summary"));
        }
        return parts.isEmpty() ? NationText.tr("nationwars.peace.summary.white") : NationText.join(parts);
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
        copy.puppetReceiver = source.puppetReceiver;
        return copy;
    }

    private static boolean sameDeal(NationStore.PeaceDeal expected, NationStore.PeaceDeal current) {
        return expected != null && current != null
            && expected.proposer.equals(current.proposer)
            && expected.receiver.equals(current.receiver)
            && expected.demandedClaims.equals(current.demandedClaims)
            && expected.offeredClaims.equals(current.offeredClaims)
            && Double.compare(expected.demandedMoney, current.demandedMoney) == 0
            && Double.compare(expected.offeredMoney, current.offeredMoney) == 0
            && expected.returnCapturedClaims == current.returnCapturedClaims
            && expected.puppetReceiver == current.puppetReceiver;
    }

    private static ItemStack item(Item item, Component name, List<Component> lore) {
        ItemStack stack = new ItemStack((ItemLike)item);
        stack.set(DataComponents.CUSTOM_NAME, name);
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
        CLOSE,
        DEMAND_PREV,
        DEMAND_NEXT,
        OFFER_PREV,
        OFFER_NEXT,
        PUPPET_RECEIVER;

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
