package dev.moth.nationwars;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class SpyCommands {
    private static final double AGENCY_COST = 3500.0;
    private static final int BASE_SPY_LIMIT = 10;
    private static final int FRENCH_BONUS_SPIES = 5;
    private static final double FRENCH_BONUS_SPY_COST = 300.0;
    private static final int TRAVEL_SECONDS = 60;
    private static final int COUNTERSPY_DURATION_SECONDS = 900;
    private static final int INFILTRATION_DURATION_SECONDS = 600;
    private static final int PARALYZE_DURATION_SECONDS = 600;
    private static final int RAID_DURATION_SECONDS = 300;
    private static final Random RANDOM = new Random();

    private SpyCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("spy").requires(source -> source.hasPermission(0) && !NationWarsConfig.get().disableEspionage)
            .then(Commands.literal("create").executes(SpyCommands::createAgency))
            .then(Commands.literal("hire").executes(SpyCommands::hireSpy))
            .then(Commands.literal("set")
                .then(Commands.argument("country", StringArgumentType.word())
                    .then(Commands.argument("amount", IntegerArgumentType.integer(1)).executes(SpyCommands::setSpies))))
            .then(Commands.literal("mission").executes(SpyCommands::openMissionMenu))
            .then(Commands.literal("info")
                .then(Commands.literal("show")
                    .then(Commands.argument("country", StringArgumentType.word()).executes(SpyCommands::showInfo)))
                .then(Commands.literal("update")
                    .then(Commands.argument("country", StringArgumentType.word()).executes(SpyCommands::updateInfo))))
            .then(Commands.literal("status").executes(SpyCommands::status)));
    }

    @SubscribeEvent
    public static void serverTick(ServerTickEvent.Post event) {
        long serverTick = event.getServer().getTickCount();
        if (serverTick % 20L != 0L) {
            return;
        }
        long tick = NationStore.persistentNow();
        NationStore store = NationStore.get();
        boolean changed = false;
        for (NationStore.Nation nation : store.nations()) {
            if (nation.spyAgency == null) {
                continue;
            }
            for (NationStore.SpyUnit spy : nation.spyAgency.spies) {
                if ("traveling".equals(spy.status) && spy.availableTick <= tick) {
                    boolean destinationExists = store.nationById(spy.country).isPresent();
                    spy.status = destinationExists ? "stationed" : "idle";
                    if (!destinationExists) {
                        spy.country = "";
                    }
                    spy.availableTick = 0L;
                    store.notifyNation(event.getServer(), nation, destinationExists
                        ? NationText.message("nationwars.spy.travel.arrived", spyLabel(spy), countryName(store, spy.country))
                        : NationText.message("nationwars.spy.travel.destination_gone", spyLabel(spy)));
                    changed = true;
                } else if ("counterspy".equals(spy.status) && spy.availableTick <= tick) {
                    beginRecovery(spy, tick);
                    store.notifyNation(event.getServer(), nation, NationText.message("nationwars.spy.counterspy.finished", spyLabel(spy), SpyRecovery.SECONDS));
                    changed = true;
                } else if ("recovering".equals(spy.status) && spy.availableTick <= tick) {
                    boolean countryExists = store.nationById(spy.country).isPresent();
                    spy.status = countryExists ? "stationed" : "idle";
                    spy.mission = "";
                    spy.targetChunk = "";
                    spy.availableTick = 0L;
                    if (!countryExists) {
                        spy.country = "";
                    }
                    store.notifyNation(event.getServer(), nation, countryExists
                        ? NationText.message("nationwars.spy.recovery.stationed", spyLabel(spy), countryName(store, spy.country))
                        : NationText.message("nationwars.spy.recovery.home", spyLabel(spy)));
                    changed = true;
                }
            }
        }
        for (NationStore.SpyMission mission : new ArrayList<>(store.dueSpyMissions(tick))) {
            resolveMission(event.getServer(), store, mission, tick);
            changed = true;
        }
        changed |= store.clearExpiredSpyEffects(tick);
        if (changed) {
            store.save();
        }
    }

    public static int maxSpies(NationStore.Nation nation) {
        return BASE_SPY_LIMIT + (nation.doctrine() == Doctrine.FRENCH ? FRENCH_BONUS_SPIES : 0);
    }

    private static int createAgency(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        NationStore store = NationStore.get();
        NationStore.Nation nation = requireOwnerNation(player, store);
        if (nation == null) {
            return 0;
        }
        if (nation.spyAgency != null) {
            fail(player, "nationwars.spy.error.agency_exists");
            return 0;
        }
        if (!canSpend(player, store, nation, AGENCY_COST)) {
            return 0;
        }
        nation.balance = NationStore.roundMoney(nation.balance - AGENCY_COST);
        store.createSpyAgency(nation);
        ok(player, "nationwars.spy.agency.created");
        return 1;
    }

    private static int hireSpy(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        NationStore store = NationStore.get();
        NationStore.Nation nation = requireOwnerNation(player, store);
        if (nation == null || !requireAgency(player, nation)) {
            return 0;
        }
        int next = nation.spyAgency.spies.size() + 1;
        if (next > maxSpies(nation)) {
            fail(player, "nationwars.spy.error.limit", maxSpies(nation));
            return 0;
        }
        double cost = next > BASE_SPY_LIMIT ? FRENCH_BONUS_SPY_COST : hireCost(next);
        if (!canSpend(player, store, nation, cost)) {
            return 0;
        }
        nation.balance = NationStore.roundMoney(nation.balance - cost);
        NationStore.SpyUnit spy = store.hireSpy(nation);
        ok(player, "nationwars.spy.hired", spy.id, NationStore.roundMoney(cost));
        return 1;
    }

    private static int setSpies(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        NationStore store = NationStore.get();
        NationStore.Nation nation = requireOwnerNation(player, store);
        NationStore.Nation target = store.nationByName(StringArgumentType.getString(context, "country")).orElse(null);
        int amount = IntegerArgumentType.getInteger(context, "amount");
        if (nation == null || !requireAgency(player, nation)) {
            return 0;
        }
        if (target == null) {
            fail(player, "nationwars.command.error.country_missing");
            return 0;
        }
        List<NationStore.SpyUnit> available = nation.spyAgency.spies.stream().filter(spy -> "idle".equals(spy.status) || "stationed".equals(spy.status)).limit(amount).toList();
        if (available.size() < amount) {
            fail(player, "nationwars.spy.error.available", amount, available.size());
            return 0;
        }
        long arrival = NationStore.persistentNow() + TRAVEL_SECONDS * 20L;
        for (NationStore.SpyUnit spy : available) {
            spy.country = target.id;
            spy.status = "traveling";
            spy.mission = "";
            spy.targetChunk = "";
            spy.availableTick = arrival;
        }
        store.save();
        ok(player, amount == 1 ? "nationwars.spy.travel.sent_one" : "nationwars.spy.travel.sent_many", TRAVEL_SECONDS);
        return 1;
    }

    private static int openMissionMenu(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        NationStore store = NationStore.get();
        NationStore.Nation nation = requireOwnerNation(player, store);
        if (nation == null || !requireAgency(player, nation)) {
            return 0;
        }
        player.openMenu((MenuProvider)new SimpleMenuProvider((containerId, inventory, viewer) -> new SpyMissionMenu(containerId, inventory), NationText.tr("nationwars.gui.spy_mission.title")));
        return 1;
    }

    static boolean launchMission(ServerPlayer player, String targetId, String missionId, List<String> chunks) {
        if (NationWarsConfig.get().disableEspionage) {
            fail(player, "nationwars.spy.error.disabled");
            return false;
        }
        NationStore store = NationStore.get();
        NationStore.Nation nation = requireOwnerNation(player, store);
        NationStore.Nation target = store.nationById(targetId).orElse(null);
        MissionType type = MissionType.byId(missionId).orElse(null);
        if (nation == null || !requireAgency(player, nation)) {
            return false;
        }
        if (target == null || type == null) {
            fail(player, "nationwars.spy.error.unknown_target_mission");
            return false;
        }
        if (type == MissionType.COUNTERSPY && !target.id.equals(nation.id)) {
            fail(player, "nationwars.spy.error.counterspy_own");
            return false;
        }
        if (type != MissionType.COUNTERSPY && target.id.equals(nation.id)) {
            fail(player, "nationwars.spy.error.only_counterspy_own");
            return false;
        }
        if (chunks == null || chunks.size() != type.chunkCount || new java.util.HashSet<>(chunks).size() != chunks.size()) {
            fail(player, type.chunkCount == 0 ? "nationwars.spy.error.no_chunk"
                : type.chunkCount == 1 ? "nationwars.spy.error.one_chunk" : "nationwars.spy.error.chunk_count", type.chunkCount);
            return false;
        }
        for (String chunk : chunks) {
            try {
                if (!target.id.equals(store.nationOwning(ClaimKey.parse(chunk)).map(owner -> owner.id).orElse(""))) {
                    fail(player, "nationwars.spy.error.claim_owner", target.name);
                    return false;
                }
            } catch (RuntimeException exception) {
                fail(player, "nationwars.spy.error.invalid_claim");
                return false;
            }
        }
        if (type == MissionType.RAID && !raidTargetAllowed(store, nation, target, player.getServer())) {
            fail(player, "nationwars.spy.error.raid_requirements");
            return false;
        }
        NationStore.SpyUnit spy = nation.spyAgency.spies.stream()
            .filter(unit -> "stationed".equals(unit.status) && target.id.equals(unit.country))
            .findFirst().orElse(null);
        if (spy == null) {
            fail(player, "nationwars.spy.error.no_stationed", target.name);
            return false;
        }
        if (!canSpend(player, store, nation, type.cost)) {
            return false;
        }
        nation.balance = NationStore.roundMoney(nation.balance - type.cost);
        spy.status = "mission";
        spy.mission = type.id;
        spy.targetChunk = chunks.isEmpty() ? "" : chunks.get(0);
        spy.availableTick = NationStore.persistentNow() + type.seconds * 20L;
        store.createSpyMission(nation, target, spy, type.id, chunks, spy.availableTick);
        ok(player, "nationwars.spy.mission.sent", spy.id, target.name, missionName(type.id), type.seconds);
        return true;
    }

    static List<MissionOption> missionOptions() {
        List<MissionOption> options = new ArrayList<>();
        for (MissionType type : MissionType.values()) {
            options.add(new MissionOption(type.id, type.cost, type.seconds, (int)Math.round(type.failureChance * 100.0), type.chunkCount));
        }
        return List.copyOf(options);
    }

    record MissionOption(String id, double cost, int seconds, int failurePercent, int chunkCount) {
    }

    private static int showInfo(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        NationStore store = NationStore.get();
        NationStore.Nation viewer = store.nationOf(player.getUUID()).orElse(null);
        NationStore.Nation target = store.nationByName(StringArgumentType.getString(context, "country")).orElse(null);
        if (viewer == null || target == null || !requireAgency(player, viewer)) {
            return 0;
        }
        NationStore.SpyIntel intel = store.spyIntel(viewer, target, false);
        if (intel == null) {
            fail(player, "nationwars.spy.error.no_intel", target.name);
            return 0;
        }
        player.sendSystemMessage(NationText.message("nationwars.spy.intel.header", intelValue(intel, "name", intel.name)));
        showField(player, intel, "doctrine", intel.doctrine);
        showField(player, intel, "ideology", intel.ideology);
        showField(player, intel, "balance", intel.balance);
        showField(player, intel, "capital", intel.capital);
        showField(player, intel, "size", intel.size);
        showField(player, intel, "members", intel.members);
        showField(player, intel, "faction", intel.faction);
        showField(player, intel, "guarantees", intel.guarantees);
        return 1;
    }

    private static int updateInfo(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        NationStore store = NationStore.get();
        NationStore.Nation viewer = requireOwnerNation(player, store);
        NationStore.Nation target = store.nationByName(StringArgumentType.getString(context, "country")).orElse(null);
        if (viewer == null || target == null || !requireAgency(player, viewer)) {
            return 0;
        }
        NationStore.SpyIntel intel = store.spyIntel(viewer, target, false);
        if (intel == null || intel.known.isEmpty()) {
            fail(player, "nationwars.spy.error.no_intel_update");
            return 0;
        }
        double cost = intel.known.size() * 100.0;
        if (!canSpend(player, store, viewer, cost)) {
            return 0;
        }
        viewer.balance = NationStore.roundMoney(viewer.balance - cost);
        refreshKnownIntel(player.getServer(), store, target, intel);
        store.save();
        ok(player, "nationwars.spy.intel.updated", intel.known.size(), NationStore.roundMoney(cost));
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        player.openMenu((MenuProvider)new SimpleMenuProvider((containerId, inventory, viewer) -> new SpyMenu(containerId, inventory), NationText.tr("nationwars.gui.spy.title")));
        return 1;
    }

    private static void resolveMission(MinecraftServer server, NationStore store, NationStore.SpyMission mission, long tick) {
        NationStore.Nation nation = store.nationById(mission.spyNation).orElse(null);
        NationStore.Nation target = store.nationById(mission.target).orElse(null);
        MissionType type = MissionType.byId(mission.type).orElse(null);
        NationStore.SpyUnit spy = nation == null ? null : store.spyUnit(nation, mission.spyId).orElse(null);
        if (nation == null || target == null || type == null || spy == null) {
            if (spy != null) {
                beginRecovery(spy, tick);
            }
            store.removeSpyMission(mission);
            return;
        }
        if (type.chunkCount > 0 && (mission.chunks == null || mission.chunks.size() != type.chunkCount || new java.util.HashSet<>(mission.chunks).size() != mission.chunks.size()
            || mission.chunks.stream().anyMatch(chunk -> !isOwnedBy(store, chunk, target)))) {
            beginRecovery(spy, tick);
            store.notifyNation(server, nation, NationText.message("nationwars.spy.mission.cancelled_ownership", spyLabel(spy), missionName(type.id), SpyRecovery.SECONDS));
            store.removeSpyMission(mission);
            return;
        }
        String claimId = mission.chunks == null || mission.chunks.isEmpty() ? "" : mission.chunks.get(0);
        if (type.counterable && !claimId.isBlank() && counterspyBlocks(store, target, claimId, tick)) {
            beginRecovery(spy, tick);
            store.notifyNation(server, nation, NationText.message("nationwars.spy.mission.blocked", spyLabel(spy), missionName(type.id), SpyRecovery.SECONDS));
            store.notifyNation(server, target, NationText.message("nationwars.spy.mission.blocked_defender", missionName(type.id), shortChunk(claimId)));
            store.removeSpyMission(mission);
            return;
        }
        if (RANDOM.nextDouble() < type.failureChance) {
            beginRecovery(spy, tick);
            store.notifyNation(server, nation, NationText.message("nationwars.spy.mission.failed", spyLabel(spy), missionName(type.id), target.name, SpyRecovery.SECONDS));
            store.removeSpyMission(mission);
            return;
        }
        switch (type) {
            case COUNTERSPY -> {
                spy.status = "counterspy";
                spy.mission = "counterspy";
                spy.targetChunk = claimId;
                spy.availableTick = tick + COUNTERSPY_DURATION_SECONDS * 20L;
                store.notifyNation(server, nation, NationText.message("nationwars.spy.counterspy.active", spyLabel(spy), shortChunk(claimId), COUNTERSPY_DURATION_SECONDS));
            }
            case DOCTRINE -> revealDoctrine(store, nation, target, tick);
            case TREASURY -> revealField(store, nation, target, "balance", tick);
            case MEMBERS -> revealField(store, nation, target, "members", tick);
            case FACTION -> {
                revealField(store, nation, target, "faction", tick);
                revealField(store, nation, target, "guarantees", tick);
            }
            case SIZE -> revealField(store, nation, target, "size", tick);
            case SCOUT -> resolveScout(server, store, nation, target, mission, tick);
            case INFILTRATE -> {
                store.disableCounterspy(claimId, tick + INFILTRATION_DURATION_SECONDS * 20L);
                if (claimId.equals(target.capitalClaim)) {
                    store.blockSpending(target, tick + INFILTRATION_DURATION_SECONDS * 20L);
                }
            }
            case PARALYZE -> store.paralyzeClaim(claimId, tick + PARALYZE_DURATION_SECONDS * 20L);
            case STEAL -> resolveSteal(store, nation, target, claimId);
            case RAID -> {
                if (!raidTargetAllowed(store, nation, target, server)) {
                    beginRecovery(spy, tick);
                    store.notifyNation(server, nation, NationText.message("nationwars.spy.raid.failed_requirements"));
                    store.removeSpyMission(mission);
                    return;
                } else {
                    store.raidClaim(claimId, tick + RAID_DURATION_SECONDS * 20L);
                }
            }
        }
        if (type != MissionType.COUNTERSPY) {
            beginRecovery(spy, tick);
        }
        store.notifyNation(server, nation, type == MissionType.COUNTERSPY
            ? NationText.message("nationwars.spy.mission.completed_counterspy", spyLabel(spy), missionName(type.id), target.name)
            : NationText.message("nationwars.spy.mission.completed", spyLabel(spy), missionName(type.id), target.name, SpyRecovery.SECONDS));
        store.removeSpyMission(mission);
    }

    private static boolean isOwnedBy(NationStore store, String claimId, NationStore.Nation nation) {
        try {
            return nation.id.equals(store.nationOwning(ClaimKey.parse(claimId)).map(owner -> owner.id).orElse(""));
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static boolean counterspyBlocks(NationStore store, NationStore.Nation target, String claimId, long tick) {
        if (store.isCounterspyDisabled(claimId, tick) || target.spyAgency == null) {
            return false;
        }
        boolean active = target.spyAgency.spies.stream().anyMatch(spy -> "counterspy".equals(spy.status) && claimId.equals(spy.targetChunk) && spy.availableTick > tick);
        if (!active) {
            return false;
        }
        return target.doctrine() != Doctrine.GERMAN || RANDOM.nextBoolean();
    }

    private static void revealDoctrine(NationStore store, NationStore.Nation viewer, NationStore.Nation target, long tick) {
        revealField(store, viewer, target, "doctrine", tick);
        revealField(store, viewer, target, "ideology", tick);
    }

    private static void revealField(NationStore store, NationStore.Nation viewer, NationStore.Nation target, String field, long tick) {
        NationStore.SpyIntel intel = store.spyIntel(viewer, target, true);
        intel.known.add(field);
        refreshField(store.server(), store, target, intel, field);
        intel.updatedTick = tick;
    }

    private static void resolveScout(MinecraftServer server, NationStore store, NationStore.Nation viewer, NationStore.Nation target, NationStore.SpyMission mission, long tick) {
        List<String> capitals = mission.chunks.stream().filter(chunk -> chunk.equals(target.capitalClaim)).toList();
        if (capitals.isEmpty()) {
            store.notifyNation(server, viewer, NationText.message("nationwars.spy.scout.no_capital"));
            return;
        }
        String capital = capitals.get(0);
        NationStore.SpyIntel intel = store.spyIntel(viewer, target, true);
        intel.known.add("capital");
        intel.capital = shortChunk(capital);
        intel.updatedTick = tick;
        store.notifyNation(server, viewer, NationText.message("nationwars.spy.scout.capital", shortChunk(capital)));
    }

    private static void resolveSteal(NationStore store, NationStore.Nation thief, NationStore.Nation target, String claimId) {
        if (!claimId.equals(target.capitalClaim)) {
            store.notifyNation(store.server(), thief, NationText.message("nationwars.spy.steal.not_capital"));
            return;
        }
        double stolen = NationStore.roundMoney(target.balance * 0.45);
        target.balance = NationStore.roundMoney(target.balance - stolen);
        thief.balance = NationStore.roundMoney(thief.balance + stolen);
        store.notifyNation(store.server(), thief, NationText.message("nationwars.spy.steal.success", stolen, target.name));
        store.notifyNation(store.server(), target, NationText.message("nationwars.spy.steal.target", stolen));
    }

    private static void refreshKnownIntel(MinecraftServer server, NationStore store, NationStore.Nation target, NationStore.SpyIntel intel) {
        for (String field : Set.copyOf(intel.known)) {
            refreshField(server, store, target, intel, field);
        }
        intel.updatedTick = NationStore.persistentNow();
    }

    private static void refreshField(MinecraftServer server, NationStore store, NationStore.Nation target, NationStore.SpyIntel intel, String field) {
        switch (field) {
            case "name" -> intel.name = target.name;
            case "doctrine" -> intel.doctrine = target.doctrine().displayName + " (" + target.doctrine().id + ")";
            case "ideology" -> intel.ideology = target.doctrine().ideology.displayName;
            case "balance" -> intel.balance = "$" + NationStore.roundMoney(target.balance);
            case "capital" -> intel.capital = target.capitalClaim == null || target.capitalClaim.isBlank() ? "none" : shortChunk(target.capitalClaim);
            case "size" -> intel.size = store.claimCount(target) + " claims";
            case "members" -> intel.members = memberNames(server, target);
            case "faction" -> intel.faction = store.allianceOf(target).map(alliance -> alliance.name + " [" + alliance.members.stream().map(id -> countryName(store, id)).sorted().collect(Collectors.joining(", ")) + "]").orElse("none");
            case "guarantees" -> intel.guarantees = store.guarantorsOf(target).stream().map(nation -> nation.name).collect(Collectors.joining(", ", "", store.guarantorsOf(target).isEmpty() ? "none" : ""));
            default -> {
            }
        }
    }

    private static String memberNames(MinecraftServer server, NationStore.Nation nation) {
        return nation.members.stream().map(id -> {
            try {
                UUID uuid = UUID.fromString(id);
                ServerPlayer online = server.getPlayerList().getPlayer(uuid);
                return online == null ? id.substring(0, Math.min(8, id.length())) : online.getGameProfile().getName();
            } catch (RuntimeException ignored) {
                return id;
            }
        }).sorted(String::compareToIgnoreCase).collect(Collectors.joining(", "));
    }

    private static boolean raidTargetAllowed(NationStore store, NationStore.Nation attacker, NationStore.Nation target, MinecraftServer server) {
        int ownSize = Math.max(1, store.claimCount(attacker));
        int targetSize = store.claimCount(target);
        boolean sizeAllowed = targetSize * 2 >= ownSize && targetSize <= ownSize * 2;
        boolean online = server.getPlayerList().getPlayers().stream().anyMatch(player -> store.isMember(player.getUUID(), target));
        return sizeAllowed && online;
    }

    private static NationStore.Nation requireOwnerNation(ServerPlayer player, NationStore store) {
        NationStore.Nation nation = store.nationOf(player.getUUID()).orElse(null);
        if (nation == null) {
            fail(player, "nationwars.command.error.create_or_join");
            return null;
        }
        if (!store.isOwner(player.getUUID(), nation)) {
            fail(player, "nationwars.spy.error.owner_only");
            return null;
        }
        return nation;
    }

    private static boolean requireAgency(ServerPlayer player, NationStore.Nation nation) {
        if (nation.spyAgency != null) {
            return true;
        }
        fail(player, "nationwars.spy.error.create_agency");
        return false;
    }

    private static boolean canSpend(ServerPlayer player, NationStore store, NationStore.Nation nation, double cost) {
        long tick = NationStore.persistentNow();
        if (store.isSpendingBlocked(nation, tick)) {
            long seconds = Math.max(1L, (store.spendingBlockedUntil(nation) - tick + 19L) / 20L);
            fail(player, "nationwars.command.error.spending_blocked_time", seconds);
            return false;
        }
        if (nation.balance + 0.0001 < cost) {
            fail(player, "nationwars.command.error.treasury_needs", NationStore.roundMoney(cost));
            return false;
        }
        return true;
    }

    private static double hireCost(int spyNumber) {
        return NationStore.roundMoney(250.0 + (spyNumber - 1) * 150.0);
    }

    private static void beginRecovery(NationStore.SpyUnit spy, long tick) {
        spy.status = "recovering";
        spy.mission = "";
        spy.targetChunk = "";
        spy.availableTick = SpyRecovery.deadline(tick);
    }

    private static String countryName(NationStore store, String id) {
        if (id == null || id.isBlank()) {
            return "none";
        }
        return store.nationById(id).map(nation -> nation.name).orElse(id);
    }

    private static Component spyLabel(NationStore.SpyUnit spy) {
        return NationText.tr("nationwars.spy.label", spy.id);
    }

    private static String shortChunk(String claimId) {
        try {
            ClaimKey claim = ClaimKey.parse(claimId);
            return claim.x() + ";" + claim.z();
        } catch (RuntimeException ignored) {
            return claimId;
        }
    }

    private static Component intelValue(NationStore.SpyIntel intel, String field, String value) {
        if (!intel.known.contains(field) || value == null || value.isBlank()) {
            return NationText.tr("nationwars.spy.intel.unknown");
        }
        if ("none".equalsIgnoreCase(value)) {
            return NationText.tr("nationwars.common.none");
        }
        if ("ideology".equals(field)) {
            for (Ideology ideology : Ideology.values()) {
                if (ideology.displayName.equalsIgnoreCase(value)) {
                    return NationText.ideologyName(ideology);
                }
            }
        }
        if ("doctrine".equals(field)) {
            for (Doctrine doctrine : Doctrine.values()) {
                if (value.equals(doctrine.displayName + " (" + doctrine.id + ")")) {
                    return NationText.tr("nationwars.spy.intel.doctrine_value", NationText.doctrineName(doctrine), doctrine.id);
                }
            }
        }
        if ("size".equals(field) && value.endsWith(" claims")) {
            return NationText.tr("nationwars.spy.intel.size_value", value.substring(0, value.length() - " claims".length()));
        }
        return Component.literal(value);
    }

    private static void showField(ServerPlayer player, NationStore.SpyIntel intel, String field, String value) {
        player.sendSystemMessage(NationText.tr("nationwars.spy.intel.field." + field, intelValue(intel, field, value)));
    }

    private static Component missionName(String id) {
        return NationText.tr("nationwars.spy.mission." + id + ".name");
    }

    private static void ok(ServerPlayer player, String key, Object... arguments) {
        player.sendSystemMessage(NationText.message(key, arguments));
    }

    private static void fail(ServerPlayer player, String key, Object... arguments) {
        player.sendSystemMessage(NationText.message(key, arguments));
    }

    private enum MissionType {
        COUNTERSPY("counterspy", 0.0, 60, 0.0, 1, false),
        DOCTRINE("doctrine", 250.0, 90, 0.15, 0, false),
        TREASURY("treasury", 200.0, 90, 0.15, 0, false),
        MEMBERS("members", 100.0, 60, 0.15, 0, false),
        FACTION("faction", 200.0, 90, 0.15, 0, false),
        SIZE("size", 100.0, 60, 0.15, 0, false),
        SCOUT("scout", 300.0, 120, 0.15, 3, false),
        INFILTRATE("infiltrate", 500.0, 120, 0.30, 1, true),
        PARALYZE("paralyze", 300.0, 120, 0.30, 1, true),
        STEAL("steal", 700.0, 180, 0.40, 1, true),
        RAID("raid", 1200.0, 180, 0.50, 1, true);

        private final String id;
        private final double cost;
        private final int seconds;
        private final double failureChance;
        private final int chunkCount;
        private final boolean counterable;

        MissionType(String id, double cost, int seconds, double failureChance, int chunkCount, boolean counterable) {
            this.id = id;
            this.cost = cost;
            this.seconds = seconds;
            this.failureChance = failureChance;
            this.chunkCount = chunkCount;
            this.counterable = counterable;
        }

        private static Optional<MissionType> byId(String id) {
            if (id == null) {
                return Optional.empty();
            }
            String normalized = id.trim().toLowerCase(Locale.ROOT);
            for (MissionType type : values()) {
                if (type.id.equals(normalized)) {
                    return Optional.of(type);
                }
            }
            return Optional.empty();
        }
    }
}
