package dev.moth.nationwars;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
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

public final class SpyMissionMenu extends AbstractContainerMenu {
    private static final int DISPLAY_SLOTS = 54;
    private static final int CLAIMS_PER_PAGE = 45;
    private final Inventory playerInventory;
    private final SimpleContainer container;
    private final String[] targetSlots = new String[DISPLAY_SLOTS];
    private final SpyCommands.MissionOption[] missionSlots = new SpyCommands.MissionOption[DISPLAY_SLOTS];
    private final String[] chunkSlots = new String[DISPLAY_SLOTS];
    private final Set<String> selectedChunks = new LinkedHashSet<>();
    private Stage stage = Stage.TARGET;
    private String targetId = "";
    private SpyCommands.MissionOption selectedMission;
    private int page;
    private int targetPage;

    public SpyMissionMenu(int containerId, Inventory playerInventory) {
        super(MenuType.GENERIC_9x6, containerId);
        this.playerInventory = playerInventory;
        this.container = new SimpleContainer(DISPLAY_SLOTS);
        this.container.startOpen(playerInventory.player);
        for (int row = 0; row < 6; ++row) {
            for (int column = 0; column < 9; ++column) {
                this.addSlot(new DisplaySlot(this.container, column + row * 9, 8 + column * 18, 18 + row * 18));
            }
        }
        for (int row = 0; row < 3; ++row) {
            for (int column = 0; column < 9; ++column) {
                this.addSlot(new Slot(playerInventory, column + row * 9 + 9, 8 + column * 18, 139 + row * 18));
            }
        }
        for (int column = 0; column < 9; ++column) {
            this.addSlot(new Slot(playerInventory, column, 8 + column * 18, 197));
        }
        this.refresh();
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        return ItemStack.EMPTY;
    }

    @Override
    public void clicked(int slotIndex, int button, ClickType clickType, Player player) {
        if (slotIndex < 0 || slotIndex >= DISPLAY_SLOTS) {
            super.clicked(slotIndex, button, clickType, player);
            return;
        }
        if (!(player instanceof ServerPlayer serverPlayer) || clickType != ClickType.PICKUP && clickType != ClickType.QUICK_MOVE) {
            return;
        }
        switch (this.stage) {
            case TARGET -> this.clickTarget(serverPlayer, slotIndex);
            case MISSION -> this.clickMission(serverPlayer, slotIndex);
            case CHUNK -> this.clickChunk(serverPlayer, slotIndex);
        }
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.container.stopOpen(player);
    }

    private void clickTarget(ServerPlayer player, int slot) {
        if (slot == 45 && this.targetPage > 0) {
            --this.targetPage;
            this.refreshAndSync();
            return;
        }
        if (slot == 53 && this.targetPage + 1 < this.targetPageCount()) {
            ++this.targetPage;
            this.refreshAndSync();
            return;
        }
        String selected = this.targetSlots[slot];
        if (selected == null) {
            return;
        }
        this.targetId = selected;
        this.stage = Stage.MISSION;
        this.refreshAndSync();
    }

    private void clickMission(ServerPlayer player, int slot) {
        if (slot == 45) {
            this.stage = Stage.TARGET;
            this.targetId = "";
            this.refreshAndSync();
            return;
        }
        SpyCommands.MissionOption mission = this.missionSlots[slot];
        if (mission == null) {
            return;
        }
        if (mission.chunkCount() == 0) {
            if (SpyCommands.launchMission(player, this.targetId, mission.id(), List.of())) {
                player.closeContainer();
            }
            return;
        }
        this.selectedMission = mission;
        this.selectedChunks.clear();
        this.page = 0;
        this.stage = Stage.CHUNK;
        this.refreshAndSync();
    }

    private void clickChunk(ServerPlayer player, int slot) {
        if (slot == 46) {
            this.stage = Stage.MISSION;
            this.selectedChunks.clear();
            this.refreshAndSync();
            return;
        }
        int pageCount = this.pageCount();
        if (slot == 45 && this.page > 0) {
            --this.page;
            this.refreshAndSync();
            return;
        }
        if (slot == 53 && this.page + 1 < pageCount) {
            ++this.page;
            this.refreshAndSync();
            return;
        }
        if (slot == 49 && this.selectedMission != null && this.selectedMission.chunkCount() == 3) {
            if (this.selectedChunks.size() != 3) {
                player.sendSystemMessage(NationText.message("nationwars.spy.error.select_three"));
                return;
            }
            if (SpyCommands.launchMission(player, this.targetId, this.selectedMission.id(), List.copyOf(this.selectedChunks))) {
                player.closeContainer();
            }
            return;
        }
        String claimId = this.chunkSlots[slot];
        if (claimId == null || this.selectedMission == null) {
            return;
        }
        if (this.selectedMission.chunkCount() == 1) {
            if (SpyCommands.launchMission(player, this.targetId, this.selectedMission.id(), List.of(claimId))) {
                player.closeContainer();
            }
            return;
        }
        if (!this.selectedChunks.remove(claimId)) {
            if (this.selectedChunks.size() >= 3) {
                player.sendSystemMessage(NationText.message("nationwars.spy.error.scout_limit"));
                return;
            }
            this.selectedChunks.add(claimId);
        }
        this.refreshAndSync();
    }

    private void refresh() {
        this.container.clearContent();
        Arrays.fill(this.targetSlots, null);
        Arrays.fill(this.missionSlots, null);
        Arrays.fill(this.chunkSlots, null);
        switch (this.stage) {
            case TARGET -> this.refreshTargets();
            case MISSION -> this.refreshMissions();
            case CHUNK -> this.refreshChunks();
        }
    }

    private void refreshTargets() {
        NationStore store = NationStore.get();
        NationStore.Nation nation = store.nationOf(this.playerInventory.player.getUUID()).orElse(null);
        if (nation == null || nation.spyAgency == null) {
            this.setMessage(22, Items.BARRIER, NationText.tr("nationwars.gui.spy.no_agency"), NationText.tr("nationwars.gui.spy.no_agency_lore"));
            return;
        }
        Set<String> stationedCountries = new LinkedHashSet<>();
        nation.spyAgency.spies.stream()
            .filter(spy -> "stationed".equals(spy.status) && spy.country != null && !spy.country.isBlank())
            .forEach(spy -> stationedCountries.add(spy.country));
        List<NationStore.Nation> targets = stationedCountries.stream()
            .map(id -> store.nationById(id).orElse(null))
            .filter(java.util.Objects::nonNull)
            .sorted(Comparator.comparing(target -> target.name.toLowerCase()))
            .toList();
        int pageCount = Math.max(1, (targets.size() + CLAIMS_PER_PAGE - 1) / CLAIMS_PER_PAGE);
        this.targetPage = Math.min(this.targetPage, pageCount - 1);
        int slot = 0;
        for (NationStore.Nation target : targets.stream().skip((long)this.targetPage * CLAIMS_PER_PAGE).limit(CLAIMS_PER_PAGE).toList()) {
            int spies = (int)nation.spyAgency.spies.stream().filter(spy -> "stationed".equals(spy.status) && target.id.equals(spy.country)).count();
            ItemStack stack = new ItemStack(NationIcons.countryBlock(target.doctrine()));
            stack.set(DataComponents.CUSTOM_NAME, Component.literal(target.name));
            stack.set(DataComponents.LORE, new ItemLore(List.of(
                NationText.tr("nationwars.gui.spy_mission.stationed", spies),
                NationText.tr("nationwars.gui.nations.claims", store.claimCount(target)),
                NationText.tr("nationwars.gui.spy_mission.choose_mission")
            )));
            this.container.setItem(slot, stack);
            this.targetSlots[slot] = target.id;
            ++slot;
        }
        if (slot == 0) {
            this.setMessage(22, Items.BARRIER, NationText.tr("nationwars.gui.spy_mission.no_stationed"), NationText.tr("nationwars.gui.spy_mission.no_stationed_lore"));
        }
        if (this.targetPage > 0) {
            this.setMessage(45, Items.ARROW, NationText.tr("nationwars.gui.spy_mission.previous_countries"), NationText.tr("nationwars.gui.common.page_of", this.targetPage, pageCount));
        }
        this.setMessage(49, Items.SPYGLASS, NationText.tr("nationwars.gui.spy_mission.choose_country"), NationText.tr("nationwars.gui.spy_mission.country_page", this.targetPage + 1, pageCount));
        if (this.targetPage + 1 < pageCount) {
            this.setMessage(53, Items.ARROW, NationText.tr("nationwars.gui.spy_mission.next_countries"), NationText.tr("nationwars.gui.common.page_of", this.targetPage + 2, pageCount));
        }
    }

    private int targetPageCount() {
        NationStore.Nation nation = NationStore.get().nationOf(this.playerInventory.player.getUUID()).orElse(null);
        if (nation == null || nation.spyAgency == null) {
            return 1;
        }
        NationStore store = NationStore.get();
        long countries = nation.spyAgency.spies.stream()
            .filter(spy -> "stationed".equals(spy.status) && spy.country != null && !spy.country.isBlank())
            .map(spy -> spy.country).distinct().filter(id -> store.nationById(id).isPresent()).count();
        return Math.max(1, (int)((countries + CLAIMS_PER_PAGE - 1L) / CLAIMS_PER_PAGE));
    }

    private void refreshMissions() {
        NationStore store = NationStore.get();
        NationStore.Nation own = store.nationOf(this.playerInventory.player.getUUID()).orElse(null);
        NationStore.Nation target = store.nationById(this.targetId).orElse(null);
        if (own == null || target == null) {
            this.stage = Stage.TARGET;
            this.refreshTargets();
            return;
        }
        int slot = 0;
        boolean domestic = own.id.equals(target.id);
        for (SpyCommands.MissionOption mission : SpyCommands.missionOptions()) {
            boolean counterspy = "counterspy".equals(mission.id());
            if (domestic != counterspy) {
                continue;
            }
            ItemStack stack = new ItemStack(iconFor(mission.id()));
            stack.set(DataComponents.CUSTOM_NAME, displayMissionName(mission.id()));
            List<Component> lore = new ArrayList<>();
            lore.add(NationText.tr("nationwars.gui.spy_mission.country", target.name));
            lore.add(NationText.tr("nationwars.gui.spy_mission.cost", NationStore.roundMoney(mission.cost())));
            lore.add(NationText.tr("nationwars.gui.spy_mission.time", mission.seconds()));
            lore.add(NationText.tr("nationwars.gui.spy_mission.failure", mission.failurePercent()));
            lore.add(NationText.tr(mission.chunkCount() == 0 ? "nationwars.gui.spy_mission.no_chunk"
                : mission.chunkCount() == 1 ? "nationwars.gui.spy_mission.one_chunk" : "nationwars.gui.spy_mission.three_chunks"));
            stack.set(DataComponents.LORE, new ItemLore(lore));
            this.container.setItem(slot, stack);
            this.missionSlots[slot] = mission;
            ++slot;
        }
        this.setMessage(45, Items.ARROW, NationText.tr("nationwars.gui.spy_mission.back_countries"), NationText.tr("nationwars.gui.spy_mission.back_countries_lore"));
        this.setMessage(49, NationIcons.countryBlock(target.doctrine()), Component.literal(target.name), NationText.tr("nationwars.gui.spy_mission.choose_above"));
    }

    private void refreshChunks() {
        NationStore store = NationStore.get();
        NationStore.Nation target = store.nationById(this.targetId).orElse(null);
        if (target == null || this.selectedMission == null) {
            this.stage = Stage.TARGET;
            this.refreshTargets();
            return;
        }
        List<String> claims = store.claimsOf(target);
        int start = this.page * CLAIMS_PER_PAGE;
        int end = Math.min(start + CLAIMS_PER_PAGE, claims.size());
        for (int index = start; index < end; ++index) {
            int slot = index - start;
            String claimId = claims.get(index);
            ClaimKey claim = ClaimKey.parse(claimId);
            boolean selected = this.selectedChunks.contains(claimId);
            ItemStack stack = new ItemStack(selected ? Items.LIME_DYE : Items.PAPER);
            stack.set(DataComponents.CUSTOM_NAME, Component.literal(claim.x() + ";" + claim.z()));
            stack.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal(claim.dimension()),
                Component.literal(target.name),
                NationText.tr(selected ? "nationwars.gui.spy_mission.selected_remove" : "nationwars.gui.spy_mission.click_select")
            )));
            this.container.setItem(slot, stack);
            this.chunkSlots[slot] = claimId;
        }
        if (claims.isEmpty()) {
            this.setMessage(22, Items.BARRIER, NationText.tr("nationwars.gui.spy_mission.no_claims"), NationText.tr("nationwars.gui.spy_mission.no_claims_lore", target.name));
        }
        if (this.page > 0) {
            this.setMessage(45, Items.ARROW, NationText.tr("nationwars.gui.common.previous_page"), NationText.tr("nationwars.gui.common.page_of", this.page, this.pageCount()));
        }
        this.setMessage(46, Items.BARRIER, NationText.tr("nationwars.gui.spy_mission.back_missions"), NationText.tr("nationwars.gui.spy_mission.back_missions_lore"));
        if (this.selectedMission.chunkCount() == 3) {
            this.setMessage(49, this.selectedChunks.size() == 3 ? Items.LIME_CONCRETE : Items.GRAY_CONCRETE,
                NationText.tr("nationwars.gui.spy_mission.confirm_scout", this.selectedChunks.size()), NationText.tr("nationwars.gui.spy_mission.select_three"));
        } else {
            this.setMessage(49, Items.SPYGLASS, displayMissionName(this.selectedMission.id()), NationText.tr("nationwars.gui.spy_mission.click_launch"));
        }
        if (this.page + 1 < this.pageCount()) {
            this.setMessage(53, Items.ARROW, NationText.tr("nationwars.gui.common.next_page"), NationText.tr("nationwars.gui.common.page_of", this.page + 2, this.pageCount()));
        }
    }

    private int pageCount() {
        NationStore.Nation target = NationStore.get().nationById(this.targetId).orElse(null);
        int claims = target == null ? 0 : NationStore.get().claimCount(target);
        return Math.max(1, (claims + CLAIMS_PER_PAGE - 1) / CLAIMS_PER_PAGE);
    }

    private void setMessage(int slot, Item item, Component title, Component lore) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, title);
        stack.set(DataComponents.LORE, new ItemLore(List.of(lore)));
        this.container.setItem(slot, stack);
    }

    private void refreshAndSync() {
        this.refresh();
        this.broadcastChanges();
    }

    private static Item iconFor(String mission) {
        return switch (mission) {
            case "counterspy" -> Items.SHIELD;
            case "doctrine" -> Items.ENCHANTED_BOOK;
            case "treasury" -> Items.GOLD_INGOT;
            case "members" -> Items.PLAYER_HEAD;
            case "faction" -> Items.WHITE_BANNER;
            case "size" -> Items.MAP;
            case "scout" -> Items.SPYGLASS;
            case "infiltrate" -> Items.IRON_PICKAXE;
            case "paralyze" -> Items.REDSTONE_TORCH;
            case "steal" -> Items.EMERALD;
            case "raid" -> Items.TNT;
            default -> Items.PAPER;
        };
    }

    private static Component displayMissionName(String id) {
        return SpyCommands.missionOptions().stream().anyMatch(option -> option.id().equals(id))
            ? NationText.tr("nationwars.spy.mission." + id + ".display")
            : Component.literal(id);
    }

    private enum Stage {
        TARGET,
        MISSION,
        CHUNK
    }

    private static final class DisplaySlot extends Slot {
        private DisplaySlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }
    }
}
