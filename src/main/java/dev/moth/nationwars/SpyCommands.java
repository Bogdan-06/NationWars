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
        dispatcher.register(Commands.literal("spy").requires(source -> source.hasPermission(0))
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
                    store.notifyNation(event.getServer(), nation, Component.literal("[NationWars] " + spyLabel(spy)
                        + (destinationExists ? " arrived in " + countryName(store, spy.country) + "." : "'s destination no longer exists, so the spy returned home.")));
                    changed = true;
                } else if ("counterspy".equals(spy.status) && spy.availableTick <= tick) {
                    beginRecovery(spy, tick);
                    store.notifyNation(event.getServer(), nation, Component.literal("[NationWars] " + spyLabel(spy) + " finished their counterspy assignment and will recover for " + SpyRecovery.SECONDS + " seconds."));
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
                    store.notifyNation(event.getServer(), nation, Component.literal("[NationWars] " + spyLabel(spy)
                        + (countryExists ? " recovered and is stationed in " + countryName(store, spy.country) + " again." : " recovered and returned home.")));
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
            fail(player, "Your nation already has a spy agency.");
            return 0;
        }
        if (!canSpend(player, store, nation, AGENCY_COST)) {
            return 0;
        }
        nation.balance = NationStore.roundMoney(nation.balance - AGENCY_COST);
        store.createSpyAgency(nation);
        ok(player, "You have successfully created a spy agency.");
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
            fail(player, "Your spy limit is " + maxSpies(nation) + ".");
            return 0;
        }
        double cost = next > BASE_SPY_LIMIT ? FRENCH_BONUS_SPY_COST : hireCost(next);
        if (!canSpend(player, store, nation, cost)) {
            return 0;
        }
        nation.balance = NationStore.roundMoney(nation.balance - cost);
        NationStore.SpyUnit spy = store.hireSpy(nation);
        ok(player, "You have hired spy " + spy.id + ", which cost $" + NationStore.roundMoney(cost) + ".");
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
            fail(player, "That country does not exist.");
            return 0;
        }
        List<NationStore.SpyUnit> available = nation.spyAgency.spies.stream().filter(spy -> "idle".equals(spy.status) || "stationed".equals(spy.status)).limit(amount).toList();
        if (available.size() < amount) {
            fail(player, "You need " + amount + " available spies, but only " + available.size() + " are idle or stationed.");
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
        ok(player, (amount == 1 ? "The spy will" : "The spies will") + " arrive at the destination in " + TRAVEL_SECONDS + " seconds.");
        return 1;
    }

    private static int openMissionMenu(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        NationStore store = NationStore.get();
        NationStore.Nation nation = requireOwnerNation(player, store);
        if (nation == null || !requireAgency(player, nation)) {
            return 0;
        }
        player.openMenu((MenuProvider)new SimpleMenuProvider((containerId, inventory, viewer) -> new SpyMissionMenu(containerId, inventory), Component.literal("spy mission")));
        return 1;
    }

    static boolean launchMission(ServerPlayer player, String targetId, String missionId, List<String> chunks) {
        NationStore store = NationStore.get();
        NationStore.Nation nation = requireOwnerNation(player, store);
        NationStore.Nation target = store.nationById(targetId).orElse(null);
        MissionType type = MissionType.byId(missionId).orElse(null);
        if (nation == null || !requireAgency(player, nation)) {
            return false;
        }
        if (target == null || type == null) {
            fail(player, "Unknown country or mission. Reopen /spy mission.");
            return false;
        }
        if (type == MissionType.COUNTERSPY && !target.id.equals(nation.id)) {
            fail(player, "Counterspies can only defend your own country.");
            return false;
        }
        if (chunks == null || chunks.size() != type.chunkCount || new java.util.HashSet<>(chunks).size() != chunks.size()) {
            fail(player, type.chunkCount == 0 ? "That mission does not use a chunk." : "That mission needs exactly " + type.chunkCount + " chunk" + (type.chunkCount == 1 ? "" : "s") + ".");
            return false;
        }
        for (String chunk : chunks) {
            try {
                if (!target.id.equals(store.nationOwning(ClaimKey.parse(chunk)).map(owner -> owner.id).orElse(""))) {
                    fail(player, "Every selected chunk must currently be owned by " + target.name + ".");
                    return false;
                }
            } catch (RuntimeException exception) {
                fail(player, "A selected chunk is invalid. Reopen /spy mission.");
                return false;
            }
        }
        if (type == MissionType.RAID && !raidTargetAllowed(store, nation, target, player.getServer())) {
            fail(player, "RAID requires an online target between half and twice your nation's size.");
            return false;
        }
        NationStore.SpyUnit spy = nation.spyAgency.spies.stream()
            .filter(unit -> "stationed".equals(unit.status) && target.id.equals(unit.country))
            .findFirst().orElse(null);
        if (spy == null) {
            fail(player, "No stationed spy is available in " + target.name + ". Use /spy set first.");
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
        ok(player, "You've sent spy " + spy.id + " to " + target.name + " on mission " + type.id + ", which will take " + type.seconds + " seconds.");
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
            fail(player, "You have no intelligence on " + target.name + ".");
            return 0;
        }
        player.sendSystemMessage(Component.literal("[NationWars] Known intelligence on " + value(intel, "name", intel.name) + ":"));
        showField(player, intel, "doctrine", "Doctrine", intel.doctrine);
        showField(player, intel, "ideology", "Ideology", intel.ideology);
        showField(player, intel, "balance", "Treasury", intel.balance);
        showField(player, intel, "capital", "Capital", intel.capital);
        showField(player, intel, "size", "Size", intel.size);
        showField(player, intel, "members", "Members", intel.members);
        showField(player, intel, "faction", "Faction", intel.faction);
        showField(player, intel, "guarantees", "Guarantees", intel.guarantees);
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
            fail(player, "You have no intelligence to update.");
            return 0;
        }
        double cost = intel.known.size() * 100.0;
        if (!canSpend(player, store, viewer, cost)) {
            return 0;
        }
        viewer.balance = NationStore.roundMoney(viewer.balance - cost);
        refreshKnownIntel(player.getServer(), store, target, intel);
        store.save();
        ok(player, "Updated " + intel.known.size() + " known intelligence fields for $" + NationStore.roundMoney(cost) + ".");
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        player.openMenu((MenuProvider)new SimpleMenuProvider((containerId, inventory, viewer) -> new SpyMenu(containerId, inventory), Component.literal("spies")));
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
            store.notifyNation(server, nation, Component.literal("[NationWars] " + spyLabel(spy) + "'s " + type.id + " mission was cancelled because the selected territory changed ownership. Recovery: " + SpyRecovery.SECONDS + " seconds."));
            store.removeSpyMission(mission);
            return;
        }
        String claimId = mission.chunks == null || mission.chunks.isEmpty() ? "" : mission.chunks.get(0);
        if (type.counterable && !claimId.isBlank() && counterspyBlocks(store, target, claimId, tick)) {
            beginRecovery(spy, tick);
            store.notifyNation(server, nation, Component.literal("[NationWars] " + spyLabel(spy) + "'s " + type.id + " mission was blocked by a counterspy. Recovery: " + SpyRecovery.SECONDS + " seconds."));
            store.notifyNation(server, target, Component.literal("[NationWars] A counterspy blocked an enemy " + type.id + " mission at " + shortChunk(claimId) + "."));
            store.removeSpyMission(mission);
            return;
        }
        if (RANDOM.nextDouble() < type.failureChance) {
            beginRecovery(spy, tick);
            store.notifyNation(server, nation, Component.literal("[NationWars] " + spyLabel(spy) + " failed mission " + type.id + " in " + target.name + ". Recovery: " + SpyRecovery.SECONDS + " seconds."));
            store.removeSpyMission(mission);
            return;
        }
        switch (type) {
            case COUNTERSPY -> {
                spy.status = "counterspy";
                spy.mission = "counterspy";
                spy.targetChunk = claimId;
                spy.availableTick = tick + COUNTERSPY_DURATION_SECONDS * 20L;
                store.notifyNation(server, nation, Component.literal("[NationWars] " + spyLabel(spy) + " is counterspying at " + shortChunk(claimId) + " for " + COUNTERSPY_DURATION_SECONDS + " seconds."));
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
                    store.notifyNation(server, nation, Component.literal("[NationWars] RAID failed because the target no longer meets the size or online requirements."));
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
        store.notifyNation(server, nation, Component.literal("[NationWars] " + spyLabel(spy) + " completed mission " + type.id + " in " + target.name
            + (type == MissionType.COUNTERSPY ? "." : ". Recovery: " + SpyRecovery.SECONDS + " seconds.")));
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
            store.notifyNation(server, viewer, Component.literal("[NationWars] None of the selected chunks are the capital."));
            return;
        }
        String capital = capitals.get(0);
        NationStore.SpyIntel intel = store.spyIntel(viewer, target, true);
        intel.known.add("capital");
        intel.capital = shortChunk(capital);
        intel.updatedTick = tick;
        store.notifyNation(server, viewer, Component.literal("[NationWars] Chunk " + shortChunk(capital) + " is the capital city."));
    }

    private static void resolveSteal(NationStore store, NationStore.Nation thief, NationStore.Nation target, String claimId) {
        if (!claimId.equals(target.capitalClaim)) {
            store.notifyNation(store.server(), thief, Component.literal("[NationWars] STEAL failed: the selected chunk was not the capital."));
            return;
        }
        double stolen = NationStore.roundMoney(target.balance * 0.45);
        target.balance = NationStore.roundMoney(target.balance - stolen);
        thief.balance = NationStore.roundMoney(thief.balance + stolen);
        store.notifyNation(store.server(), thief, Component.literal("[NationWars] Your spy stole $" + stolen + " from " + target.name + "."));
        store.notifyNation(store.server(), target, Component.literal("[NationWars] A spy stole $" + stolen + " from your treasury."));
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
            fail(player, "Create or join a nation first.");
            return null;
        }
        if (!store.isOwner(player.getUUID(), nation)) {
            fail(player, "Only the nation owner can manage the spy agency.");
            return null;
        }
        return nation;
    }

    private static boolean requireAgency(ServerPlayer player, NationStore.Nation nation) {
        if (nation.spyAgency != null) {
            return true;
        }
        fail(player, "Create a spy agency first with /spy create.");
        return false;
    }

    private static boolean canSpend(ServerPlayer player, NationStore store, NationStore.Nation nation, double cost) {
        long tick = NationStore.persistentNow();
        if (store.isSpendingBlocked(nation, tick)) {
            long seconds = Math.max(1L, (store.spendingBlockedUntil(nation) - tick + 19L) / 20L);
            fail(player, "Your capital is infiltrated; treasury spending is blocked for " + seconds + " seconds.");
            return false;
        }
        if (nation.balance + 0.0001 < cost) {
            fail(player, "Nation treasury needs $" + NationStore.roundMoney(cost) + ".");
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

    private static String spyLabel(NationStore.SpyUnit spy) {
        return "Spy " + spy.id;
    }

    private static String shortChunk(String claimId) {
        try {
            ClaimKey claim = ClaimKey.parse(claimId);
            return claim.x() + ";" + claim.z();
        } catch (RuntimeException ignored) {
            return claimId;
        }
    }

    private static String value(NationStore.SpyIntel intel, String field, String value) {
        return intel.known.contains(field) && value != null && !value.isBlank() ? value : "Unknown";
    }

    private static void showField(ServerPlayer player, NationStore.SpyIntel intel, String field, String label, String value) {
        player.sendSystemMessage(Component.literal(label + ": " + value(intel, field, value)));
    }

    private static void ok(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal("[NationWars] " + message));
    }

    private static void fail(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal("[NationWars] " + message));
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
