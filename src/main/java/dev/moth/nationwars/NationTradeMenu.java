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
import net.minecraft.world.level.ItemLike;

public final class NationTradeMenu extends AbstractContainerMenu {
    private static final double MONEY_STEP = 100.0;
    private static final double INCOME_STEP = 1.0;
    private static final double SHIFT_INCOME_STEP = 10.0;
    private static final int[] REQUEST_SLOTS = new int[]{9, 10, 11, 12, 18, 19, 20, 21, 27, 28, 29, 30, 36, 37, 38, 39};
    private static final int[] OFFER_SLOTS = new int[]{14, 15, 16, 17, 23, 24, 25, 26, 32, 33, 34, 35, 41, 42, 43, 44};

    private final Inventory playerInventory;
    private final SimpleContainer tradeContainer;
    private final NationStore.Nation ownNation;
    private final NationStore.Nation otherNation;
    private final NationStore.TradeOffer incomingSnapshot;
    private final String[] requestedClaimSlots = new String[54];
    private final String[] offeredClaimSlots = new String[54];
    private final Action[] actions = new Action[54];
    private NationStore.TradeOffer draft;
    private boolean viewingIncoming;
    private int requestPage;
    private int offerPage;

    public NationTradeMenu(int containerId, Inventory playerInventory, NationStore.Nation ownNation, NationStore.Nation otherNation) {
        super(MenuType.GENERIC_9x6, containerId);
        int column;
        this.playerInventory = playerInventory;
        this.ownNation = ownNation;
        this.otherNation = otherNation;
        this.tradeContainer = new SimpleContainer(54);
        this.tradeContainer.startOpen(playerInventory.player);
        NationStore.TradeOffer pending = NationStore.get().tradeOfferBetween(ownNation, otherNation).orElse(null);
        if (pending != null && ownNation.id.equals(pending.receiver)) {
            this.viewingIncoming = true;
            this.incomingSnapshot = copyOffer(pending);
            this.draft = flipOfferForReceiver(pending);
        } else if (pending != null && ownNation.id.equals(pending.proposer)) {
            this.viewingIncoming = false;
            this.incomingSnapshot = null;
            this.draft = copyOffer(pending);
        } else {
            this.viewingIncoming = false;
            this.incomingSnapshot = null;
            this.draft = emptyOffer(ownNation, otherNation);
        }
        this.refresh();
        for (int row = 0; row < 6; ++row) {
            for (column = 0; column < 9; ++column) {
                this.addSlot(new DisplaySlot(this.tradeContainer, column + row * 9, 8 + column * 18, 18 + row * 18));
            }
        }
        int inventoryOffset = 36;
        for (int row = 0; row < 3; ++row) {
            for (int column2 = 0; column2 < 9; ++column2) {
                this.addSlot(new Slot(playerInventory, column2 + row * 9 + 9, 8 + column2 * 18, 103 + row * 18 + inventoryOffset));
            }
        }
        for (column = 0; column < 9; ++column) {
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
                this.handleTopClick(serverPlayer, slotIndex, button, clickType);
            }
            return;
        }
        super.clicked(slotIndex, button, clickType, player);
    }

    public void removed(Player player) {
        super.removed(player);
        this.tradeContainer.stopOpen(player);
    }

    private void refresh() {
        this.tradeContainer.clearContent();
        for (int i = 0; i < 54; ++i) {
            this.requestedClaimSlots[i] = null;
            this.offeredClaimSlots[i] = null;
            this.actions[i] = null;
        }
        this.tradeContainer.setItem(0, item(Items.PAPER, NationText.tr("nationwars.gui.trade.request_from", this.otherNation.name), List.of(NationText.tr("nationwars.gui.trade.request_lore"))));
        this.tradeContainer.setItem(4, item(Items.BOOK, NationText.tr("nationwars.gui.trade.title"), this.tradeLore()));
        this.tradeContainer.setItem(8, item(Items.CHEST, NationText.tr("nationwars.gui.trade.offer_to", this.otherNation.name), List.of(NationText.tr("nationwars.gui.trade.offer_lore"))));
        for (int slot : List.of(13, 22, 31, 40)) {
            this.tradeContainer.setItem(slot, item(Items.BLACK_STAINED_GLASS_PANE, Component.literal(" "), List.of()));
        }
        this.drawPageControls();
        this.drawClaimColumn(true);
        this.drawClaimColumn(false);
        this.drawControls();
    }

    private void drawClaimColumn(boolean requests) {
        NationStore store = NationStore.get();
        NationStore.Nation source = requests ? this.otherNation : this.ownNation;
        int[] slots = requests ? REQUEST_SLOTS : OFFER_SLOTS;
        List<String> allClaims = store.claimsOf(source).stream()
            .filter(claimId -> !claimId.equals(source.capitalClaim))
            .filter(claimId -> !store.isClaimCapturedInActiveWar(claimId))
            .toList();
        int pageCount = Math.max(1, (allClaims.size() + slots.length - 1) / slots.length);
        int selectedPage = requests ? Math.min(this.requestPage, pageCount - 1) : Math.min(this.offerPage, pageCount - 1);
        if (requests) {
            this.requestPage = selectedPage;
        } else {
            this.offerPage = selectedPage;
        }
        List<String> claims = allClaims.stream().skip((long)selectedPage * slots.length).limit(slots.length).toList();
        if (claims.isEmpty()) {
            int slot = slots[5];
            Component name = NationText.tr(requests ? "nationwars.gui.trade.no_requestable_claims" : "nationwars.gui.trade.no_offerable_claims");
            this.tradeContainer.setItem(slot, item(Items.BARRIER, name, List.of(NationText.tr("nationwars.gui.trade.no_claims_lore"))));
            return;
        }
        Set<String> selected = requests ? this.draft.requestedClaims : this.draft.offeredClaims;
        for (int i = 0; i < claims.size(); ++i) {
            int slot = slots[i];
            String claimId = claims.get(i);
            boolean active = selected.contains(claimId);
            Item icon = active ? Items.FILLED_MAP : Items.MAP;
            String key = active
                ? (requests ? "nationwars.gui.trade.selected_request" : "nationwars.gui.trade.selected_offer")
                : (requests ? "nationwars.gui.trade.request_claim" : "nationwars.gui.trade.offer_claim");
            Component label = NationText.tr(key, ClaimKey.parse(claimId).shortName());
            ArrayList<Component> lore = new ArrayList<Component>();
            lore.add(NationText.tr("nationwars.gui.common.owner", source.name));
            lore.add(NationText.tr(this.viewingIncoming ? "nationwars.gui.trade.read_only" : "nationwars.gui.common.click_toggle"));
            this.tradeContainer.setItem(slot, item(icon, label, lore));
            if (requests) {
                this.requestedClaimSlots[slot] = claimId;
            } else {
                this.offeredClaimSlots[slot] = claimId;
            }
        }
    }

    private void drawPageControls() {
        int requestPages = this.claimPageCount(this.otherNation);
        int offerPages = this.claimPageCount(this.ownNation);
        this.tradeContainer.setItem(1, item(Items.ARROW, NationText.tr("nationwars.gui.trade.previous_requests"), List.of(NationText.tr("nationwars.gui.common.page", this.requestPage + 1, requestPages))));
        this.actions[1] = Action.REQUEST_PREV;
        this.tradeContainer.setItem(2, item(Items.ARROW, NationText.tr("nationwars.gui.trade.next_requests"), List.of(NationText.tr("nationwars.gui.common.page", this.requestPage + 1, requestPages))));
        this.actions[2] = Action.REQUEST_NEXT;
        this.tradeContainer.setItem(6, item(Items.ARROW, NationText.tr("nationwars.gui.trade.previous_offers"), List.of(NationText.tr("nationwars.gui.common.page", this.offerPage + 1, offerPages))));
        this.actions[6] = Action.OFFER_PREV;
        this.tradeContainer.setItem(7, item(Items.ARROW, NationText.tr("nationwars.gui.trade.next_offers"), List.of(NationText.tr("nationwars.gui.common.page", this.offerPage + 1, offerPages))));
        this.actions[7] = Action.OFFER_NEXT;
    }

    private int claimPageCount(NationStore.Nation nation) {
        NationStore store = NationStore.get();
        int claims = (int)store.claimsOf(nation).stream()
            .filter(claimId -> !claimId.equals(nation.capitalClaim))
            .filter(claimId -> !store.isClaimCapturedInActiveWar(claimId))
            .count();
        return Math.max(1, (claims + REQUEST_SLOTS.length - 1) / REQUEST_SLOTS.length);
    }

    private void drawControls() {
        this.tradeContainer.setItem(45, item(Items.GOLD_NUGGET, NationText.tr("nationwars.gui.trade.request_down"), List.of(NationText.tr("nationwars.gui.trade.requested_money", NationStore.roundMoney(this.draft.requestedMoney)))));
        this.actions[45] = Action.REQUEST_MONEY_DOWN;
        this.tradeContainer.setItem(46, item(Items.GOLD_INGOT, NationText.tr("nationwars.gui.trade.request_up"), List.of(NationText.tr("nationwars.gui.trade.requested_money", NationStore.roundMoney(this.draft.requestedMoney)))));
        this.actions[46] = Action.REQUEST_MONEY_UP;
        this.tradeContainer.setItem(47, item(Items.CLOCK, NationText.tr("nationwars.gui.trade.request_income", NationStore.roundMoney(this.draft.requestedIncomePerMinute)), this.incomeControlLore(true)));
        this.actions[47] = Action.REQUEST_INCOME_CHANGE;
        this.tradeContainer.setItem(48, item(this.viewingIncoming ? Items.REDSTONE_BLOCK : Items.REDSTONE,
            NationText.tr(this.viewingIncoming ? "nationwars.gui.trade.reject" : "nationwars.gui.trade.clear"),
            List.of(NationText.tr(this.viewingIncoming ? "nationwars.gui.trade.reject_lore" : "nationwars.gui.trade.clear_lore"))));
        this.actions[48] = this.viewingIncoming ? Action.REJECT : Action.CLEAR;
        this.tradeContainer.setItem(49, item(this.viewingIncoming ? Items.EMERALD_BLOCK : Items.EMERALD, this.primaryLabel(), List.of(summary(this.draft))));
        this.actions[49] = Action.PRIMARY;
        this.tradeContainer.setItem(50, item(Items.CLOCK, NationText.tr("nationwars.gui.trade.offer_income", NationStore.roundMoney(this.draft.offeredIncomePerMinute)), this.incomeControlLore(false)));
        this.actions[50] = Action.OFFER_INCOME_CHANGE;
        this.tradeContainer.setItem(51, item(Items.GOLD_INGOT, NationText.tr("nationwars.gui.trade.offer_up"), List.of(NationText.tr("nationwars.gui.trade.offered_money", NationStore.roundMoney(this.draft.offeredMoney)))));
        this.actions[51] = Action.OFFER_MONEY_UP;
        this.tradeContainer.setItem(52, item(Items.GOLD_NUGGET, NationText.tr("nationwars.gui.trade.offer_down"), List.of(NationText.tr("nationwars.gui.trade.offered_money", NationStore.roundMoney(this.draft.offeredMoney)))));
        this.actions[52] = Action.OFFER_MONEY_DOWN;
        this.tradeContainer.setItem(53, item(Items.BARRIER, NationText.tr("nationwars.gui.common.close"), List.of()));
        this.actions[53] = Action.CLOSE;
    }

    private void handleTopClick(ServerPlayer player, int slotIndex, int button, ClickType clickType) {
        if (!tradeEnabled(player)) {
            return;
        }
        Action action = this.actions[slotIndex];
        if (action != null) {
            this.handleAction(player, action, button, clickType);
            return;
        }
        if (this.viewingIncoming) {
            player.sendSystemMessage(NationText.message("nationwars.trade.error.incoming_read_only_action"));
            return;
        }
        String requestedClaim = this.requestedClaimSlots[slotIndex];
        if (requestedClaim != null) {
            toggle(this.draft.requestedClaims, requestedClaim);
            this.refreshAndSync();
            return;
        }
        String offeredClaim = this.offeredClaimSlots[slotIndex];
        if (offeredClaim != null) {
            toggle(this.draft.offeredClaims, offeredClaim);
            this.refreshAndSync();
        }
    }

    private void handleAction(ServerPlayer player, Action action, int button, ClickType clickType) {
        if (this.viewingIncoming && action != Action.PRIMARY && action != Action.REJECT && action != Action.CLOSE
            && action != Action.REQUEST_PREV && action != Action.REQUEST_NEXT && action != Action.OFFER_PREV && action != Action.OFFER_NEXT) {
            player.sendSystemMessage(NationText.message("nationwars.trade.error.incoming_read_only"));
            return;
        }
        switch (action) {
            case REQUEST_MONEY_UP -> this.draft.requestedMoney = NationStore.roundMoney(this.draft.requestedMoney + MONEY_STEP);
            case REQUEST_MONEY_DOWN -> this.draft.requestedMoney = NationStore.roundMoney(Math.max(0.0, this.draft.requestedMoney - MONEY_STEP));
            case OFFER_MONEY_UP -> this.draft.offeredMoney = NationStore.roundMoney(this.draft.offeredMoney + MONEY_STEP);
            case OFFER_MONEY_DOWN -> this.draft.offeredMoney = NationStore.roundMoney(Math.max(0.0, this.draft.offeredMoney - MONEY_STEP));
            case REQUEST_INCOME_CHANGE -> this.changeIncomeTerm(true, button, clickType);
            case OFFER_INCOME_CHANGE -> this.changeIncomeTerm(false, button, clickType);
            case CLEAR -> this.draft = emptyOffer(this.ownNation, this.otherNation);
            case PRIMARY -> {
                if (this.viewingIncoming) {
                    this.acceptTrade(player);
                } else {
                    this.sendTrade(player);
                }
                return;
            }
            case REJECT -> {
                this.rejectTrade(player);
                return;
            }
            case CLOSE -> {
                player.closeContainer();
                return;
            }
            case REQUEST_PREV -> this.requestPage = Math.max(0, this.requestPage - 1);
            case REQUEST_NEXT -> this.requestPage = Math.min(this.claimPageCount(this.otherNation) - 1, this.requestPage + 1);
            case OFFER_PREV -> this.offerPage = Math.max(0, this.offerPage - 1);
            case OFFER_NEXT -> this.offerPage = Math.min(this.claimPageCount(this.ownNation) - 1, this.offerPage + 1);
        }
        this.refreshAndSync();
    }

    private void changeIncomeTerm(boolean requested, int button, ClickType clickType) {
        double step = clickType == ClickType.QUICK_MOVE ? SHIFT_INCOME_STEP : INCOME_STEP;
        double direction = button == 1 ? -1.0 : 1.0;
        if (requested) {
            this.draft.requestedIncomePerMinute = NationStore.roundMoney(Math.max(0.0, this.draft.requestedIncomePerMinute + direction * step));
        } else {
            this.draft.offeredIncomePerMinute = NationStore.roundMoney(Math.max(0.0, this.draft.offeredIncomePerMinute + direction * step));
        }
        this.draft.incomeTermsSpecified = true;
    }

    private void sendTrade(ServerPlayer player) {
        if (!tradeEnabled(player)) {
            return;
        }
        NationStore store = NationStore.get();
        if (!store.isCurrentNation(this.ownNation) || !store.isCurrentNation(this.otherNation)) {
            player.sendSystemMessage(NationText.message("nationwars.trade.error.nation_gone"));
            player.closeContainer();
            return;
        }
        if (!store.isOwner(player.getUUID(), this.ownNation)) {
            player.sendSystemMessage(NationText.message("nationwars.trade.error.leader_send"));
            player.closeContainer();
            return;
        }
        if (store.activeWarForCapture(this.ownNation, this.otherNation).isPresent() || store.activeWarForCapture(this.otherNation, this.ownNation).isPresent()) {
            player.sendSystemMessage(NationText.message("nationwars.trade.error.at_war"));
            return;
        }
        if (isEmptyOffer(this.draft)) {
            player.sendSystemMessage(NationText.message("nationwars.trade.error.empty"));
            return;
        }
        if (!this.validateDraft(player)) {
            return;
        }
        NationStore.TradeOffer sent = copyOffer(this.draft);
        sent.proposer = this.ownNation.id;
        sent.receiver = this.otherNation.id;
        sent.createdTick = NationStore.persistentNow();
        if (store.setTradeOffer(sent) == null) {
            player.sendSystemMessage(NationText.message("nationwars.trade.error.changed_nation"));
            player.closeContainer();
            return;
        }
        store.notifyNation(player.getServer(), this.otherNation, NationText.message("nationwars.trade.sent_received", this.ownNation.name, summary(sent), this.ownNation.id));
        store.notifyNation(player.getServer(), this.ownNation, NationText.message("nationwars.trade.sent", this.otherNation.name));
        player.closeContainer();
    }

    private boolean validateDraft(ServerPlayer player) {
        NationStore store = NationStore.get();
        if (this.ownNation.balance + 1.0E-4 < this.draft.offeredMoney) {
            player.sendSystemMessage(NationText.message("nationwars.trade.error.offer_money", NationStore.roundMoney(this.draft.offeredMoney)));
            return false;
        }
        if (this.otherNation.balance + 1.0E-4 < this.draft.requestedMoney) {
            player.sendSystemMessage(NationText.message("nationwars.trade.error.request_money", this.otherNation.name));
            return false;
        }
        if (store.isSpendingBlocked(this.ownNation, NationStore.persistentNow()) && this.draft.offeredMoney > 0.0) {
            player.sendSystemMessage(NationText.message("nationwars.command.error.spending_blocked"));
            return false;
        }
        for (String claimId : this.draft.offeredClaims) {
            if (!this.ownNation.id.equals(store.nationOwning(ClaimKey.parse(claimId)).map(nation -> nation.id).orElse(""))
                || claimId.equals(this.ownNation.capitalClaim) || store.isClaimCapturedInActiveWar(claimId)) {
                player.sendSystemMessage(NationText.message("nationwars.trade.error.offered_claim"));
                return false;
            }
        }
        for (String claimId : this.draft.requestedClaims) {
            if (!this.otherNation.id.equals(store.nationOwning(ClaimKey.parse(claimId)).map(nation -> nation.id).orElse(""))
                || claimId.equals(this.otherNation.capitalClaim) || store.isClaimCapturedInActiveWar(claimId)) {
                player.sendSystemMessage(NationText.message("nationwars.trade.error.requested_claim"));
                return false;
            }
        }
        return true;
    }

    private void acceptTrade(ServerPlayer player) {
        if (!tradeEnabled(player)) {
            return;
        }
        NationStore store = NationStore.get();
        if (!store.isCurrentNation(this.ownNation) || !store.isCurrentNation(this.otherNation)
            || !store.isOwner(player.getUUID(), this.ownNation)) {
            player.sendSystemMessage(NationText.message("nationwars.trade.error.leader_accept"));
            player.closeContainer();
            return;
        }
        NationStore.TradeOffer pending = store.tradeOfferBetween(this.ownNation, this.otherNation).orElse(null);
        if (pending == null || !this.ownNation.id.equals(pending.receiver)) {
            player.sendSystemMessage(NationText.message("nationwars.trade.error.unavailable"));
            player.closeContainer();
            return;
        }
        if (!sameTradeOffer(this.incomingSnapshot, pending)) {
            player.sendSystemMessage(NationText.message("nationwars.trade.error.stale"));
            player.closeContainer();
            return;
        }
        NationStore.Nation proposer = store.nationById(pending.proposer).orElse(this.otherNation);
        NationStore.Nation receiver = store.nationById(pending.receiver).orElse(this.ownNation);
        if (!store.applyTradeOffer(pending)) {
            player.sendSystemMessage(NationText.message("nationwars.trade.error.apply"));
            this.refreshAndSync();
            return;
        }
        store.notifyNation(player.getServer(), proposer, NationText.message("nationwars.trade.accepted_by", receiver.name));
        store.notifyNation(player.getServer(), receiver, NationText.message("nationwars.trade.accepted_with", proposer.name));
        player.closeContainer();
    }

    private void rejectTrade(ServerPlayer player) {
        if (!tradeEnabled(player)) {
            return;
        }
        NationStore store = NationStore.get();
        if (!store.isCurrentNation(this.ownNation) || !store.isCurrentNation(this.otherNation)
            || !store.isOwner(player.getUUID(), this.ownNation)) {
            player.sendSystemMessage(NationText.message("nationwars.trade.error.leader_reject"));
            player.closeContainer();
            return;
        }
        NationStore.TradeOffer pending = store.tradeOfferBetween(this.ownNation, this.otherNation).orElse(null);
        if (pending == null || !this.ownNation.id.equals(pending.receiver)) {
            player.sendSystemMessage(NationText.message("nationwars.trade.error.unavailable"));
            player.closeContainer();
            return;
        }
        if (!sameTradeOffer(this.incomingSnapshot, pending)) {
            player.sendSystemMessage(NationText.message("nationwars.trade.error.stale"));
            player.closeContainer();
            return;
        }
        store.removeTradeOffer(pending);
        store.notifyNation(player.getServer(), this.otherNation, NationText.message("nationwars.trade.rejected_by", this.ownNation.name));
        store.notifyNation(player.getServer(), this.ownNation, NationText.message("nationwars.trade.rejected"));
        player.closeContainer();
    }

    private List<Component> tradeLore() {
        ArrayList<Component> lore = new ArrayList<Component>();
        lore.add(NationText.tr("nationwars.gui.common.between", this.ownNation.name, this.otherNation.name));
        lore.add(NationText.tr(this.viewingIncoming ? "nationwars.gui.common.incoming_from" : "nationwars.gui.common.draft_from",
            this.viewingIncoming ? this.otherNation.name : this.ownNation.name));
        lore.add(this.currentIncomeAgreement());
        lore.add(summary(this.draft));
        return lore;
    }

    private List<Component> incomeControlLore(boolean requested) {
        ArrayList<Component> lore = new ArrayList<>();
        lore.add(NationText.tr("nationwars.gui.trade.pays_passive", requested ? this.otherNation.name : this.ownNation.name));
        lore.add(NationText.tr("nationwars.gui.trade.income_click"));
        lore.add(NationText.tr("nationwars.gui.trade.income_shift"));
        lore.add(NationText.tr("nationwars.gui.trade.income_replace"));
        lore.add(NationText.tr("nationwars.gui.trade.income_end"));
        lore.add(this.currentIncomeAgreement());
        return lore;
    }

    private Component currentIncomeAgreement() {
        NationStore store = NationStore.get();
        double ownPays = store.incomeAgreementFrom(this.ownNation, this.otherNation);
        double otherPays = store.incomeAgreementFrom(this.otherNation, this.ownNation);
        if (ownPays > 0.0) {
            return NationText.tr("nationwars.gui.trade.current_payment", this.ownNation.name, this.otherNation.name, NationStore.roundMoney(ownPays));
        }
        if (otherPays > 0.0) {
            return NationText.tr("nationwars.gui.trade.current_payment", this.otherNation.name, this.ownNation.name, NationStore.roundMoney(otherPays));
        }
        return NationText.tr("nationwars.gui.trade.current_none");
    }

    private Component primaryLabel() {
        String key = this.viewingIncoming ? "nationwars.gui.trade.accept" : NationStore.get().tradeOfferBetween(this.ownNation, this.otherNation)
            .filter(offer -> this.ownNation.id.equals(offer.proposer)).isPresent()
            ? "nationwars.gui.trade.replace" : "nationwars.gui.trade.send";
        return NationText.tr(key);
    }

    private void refreshAndSync() {
        this.refresh();
        this.broadcastChanges();
    }

    private static boolean tradeEnabled(ServerPlayer player) {
        if (NationWarsConfig.get().allowTrade) {
            return true;
        }
        player.sendSystemMessage(NationText.message("nationwars.trade.error.disabled"));
        player.closeContainer();
        return false;
    }

    private static void toggle(Set<String> selected, String claimId) {
        if (!selected.remove(claimId)) {
            selected.add(claimId);
        }
    }

    static boolean isEmptyOffer(NationStore.TradeOffer offer) {
        return NationStore.isEmptyTradeOffer(offer);
    }

    private static Component summary(NationStore.TradeOffer offer) {
        List<Component> parts = new ArrayList<Component>();
        if (!offer.requestedClaims.isEmpty()) {
            parts.add(NationText.tr("nationwars.trade.summary.requests_claims", offer.requestedClaims.size()));
        }
        if (offer.requestedMoney > 0.0) {
            parts.add(NationText.tr("nationwars.trade.summary.requests_money", NationStore.roundMoney(offer.requestedMoney)));
        }
        if (offer.requestedIncomePerMinute > 0.0) {
            parts.add(NationText.tr("nationwars.trade.summary.requests_income", NationStore.roundMoney(offer.requestedIncomePerMinute)));
        }
        if (!offer.offeredClaims.isEmpty()) {
            parts.add(NationText.tr("nationwars.trade.summary.offers_claims", offer.offeredClaims.size()));
        }
        if (offer.offeredMoney > 0.0) {
            parts.add(NationText.tr("nationwars.trade.summary.offers_money", NationStore.roundMoney(offer.offeredMoney)));
        }
        if (offer.offeredIncomePerMinute > 0.0) {
            parts.add(NationText.tr("nationwars.trade.summary.offers_income", NationStore.roundMoney(offer.offeredIncomePerMinute)));
        }
        if (offer.incomeTermsSpecified && offer.requestedIncomePerMinute <= 0.0 && offer.offeredIncomePerMinute <= 0.0) {
            parts.add(NationText.tr("nationwars.trade.summary.ends_income"));
        }
        return parts.isEmpty() ? NationText.tr("nationwars.trade.summary.empty") : NationText.join(parts);
    }

    private static NationStore.TradeOffer emptyOffer(NationStore.Nation proposer, NationStore.Nation receiver) {
        NationStore.TradeOffer offer = new NationStore.TradeOffer();
        offer.proposer = proposer.id;
        offer.receiver = receiver.id;
        return offer;
    }

    private static NationStore.TradeOffer copyOffer(NationStore.TradeOffer source) {
        NationStore.TradeOffer copy = new NationStore.TradeOffer();
        copy.id = source.id;
        copy.proposer = source.proposer;
        copy.receiver = source.receiver;
        copy.requestedClaims.addAll(source.requestedClaims);
        copy.offeredClaims.addAll(source.offeredClaims);
        copy.requestedMoney = NationStore.roundMoney(source.requestedMoney);
        copy.offeredMoney = NationStore.roundMoney(source.offeredMoney);
        copy.requestedIncomePerMinute = NationStore.roundMoney(source.requestedIncomePerMinute);
        copy.offeredIncomePerMinute = NationStore.roundMoney(source.offeredIncomePerMinute);
        copy.incomeTermsSpecified = source.incomeTermsSpecified;
        copy.createdTick = source.createdTick;
        return copy;
    }

    private static boolean sameTradeOffer(NationStore.TradeOffer expected, NationStore.TradeOffer current) {
        return expected != null && current != null
            && expected.id == current.id
            && expected.createdTick == current.createdTick
            && expected.proposer.equals(current.proposer)
            && expected.receiver.equals(current.receiver)
            && expected.requestedClaims.equals(current.requestedClaims)
            && expected.offeredClaims.equals(current.offeredClaims)
            && Double.compare(expected.requestedMoney, current.requestedMoney) == 0
            && Double.compare(expected.offeredMoney, current.offeredMoney) == 0
            && Double.compare(expected.requestedIncomePerMinute, current.requestedIncomePerMinute) == 0
            && Double.compare(expected.offeredIncomePerMinute, current.offeredIncomePerMinute) == 0
            && expected.incomeTermsSpecified == current.incomeTermsSpecified;
    }

    static NationStore.TradeOffer flipOfferForReceiver(NationStore.TradeOffer source) {
        return NationStore.flipTradeOfferForReceiver(source);
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
        REQUEST_MONEY_UP,
        REQUEST_MONEY_DOWN,
        OFFER_MONEY_UP,
        OFFER_MONEY_DOWN,
        REQUEST_INCOME_CHANGE,
        OFFER_INCOME_CHANGE,
        CLEAR,
        PRIMARY,
        REJECT,
        CLOSE,
        REQUEST_PREV,
        REQUEST_NEXT,
        OFFER_PREV,
        OFFER_NEXT
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
