/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.brigadier.CommandDispatcher
 *  com.mojang.brigadier.arguments.ArgumentType
 *  com.mojang.brigadier.arguments.DoubleArgumentType
 *  com.mojang.brigadier.arguments.IntegerArgumentType
 *  com.mojang.brigadier.arguments.StringArgumentType
 *  com.mojang.brigadier.builder.LiteralArgumentBuilder
 *  com.mojang.brigadier.builder.RequiredArgumentBuilder
 *  com.mojang.brigadier.context.CommandContext
 *  com.mojang.brigadier.exceptions.CommandSyntaxException
 *  net.minecraft.commands.CommandSourceStack
 *  net.minecraft.commands.Commands
 *  net.minecraft.core.HolderLookup$Provider
 *  net.minecraft.network.chat.Component
 *  net.minecraft.server.MinecraftServer
 *  net.minecraft.server.level.ServerLevel
 *  net.minecraft.server.level.ServerPlayer
 *  net.minecraft.world.MenuProvider
 *  net.minecraft.world.SimpleMenuProvider
 *  net.minecraft.world.item.ItemStack
 *  net.minecraft.world.item.Items
 *  net.minecraft.world.level.ChunkPos
 *  net.neoforged.bus.api.SubscribeEvent
 *  net.neoforged.neoforge.event.RegisterCommandsEvent
 *  net.neoforged.neoforge.event.ServerChatEvent
 *  net.neoforged.neoforge.event.entity.player.PlayerEvent$PlayerLoggedOutEvent
 *  net.neoforged.neoforge.event.server.ServerStoppingEvent
 *  net.neoforged.neoforge.event.tick.ServerTickEvent$Post
 */
package dev.moth.nationwars;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;
import dev.moth.nationwars.ClaimKey;
import dev.moth.nationwars.Doctrine;
import dev.moth.nationwars.DoctrineMenu;
import dev.moth.nationwars.Ideology;
import dev.moth.nationwars.MarketMenu;
import dev.moth.nationwars.NationCreateMenu;
import dev.moth.nationwars.NationEvents;
import dev.moth.nationwars.NationStore;
import dev.moth.nationwars.NationsMenu;
import dev.moth.nationwars.OpacClaimsBridge;
import dev.moth.nationwars.PeaceDealMenu;
import dev.moth.nationwars.WarMenu;
import dev.moth.nationwars.service.EconomyService;
import dev.moth.nationwars.service.WarService;
import dev.moth.nationwars.command.AllianceCommand;
import dev.moth.nationwars.command.MarketCommand;
import dev.moth.nationwars.command.NationCommand;
import dev.moth.nationwars.command.PuppetCommand;
import dev.moth.nationwars.command.WarCommand;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class NationCommands {
    private static final double BASE_CLAIM_COST = 100.0;
    private static final double CASUS_FOEDERIS_COST = 300.0;
    private static final double MIN_LAND_PURCHASE_COST = 250.0;
    private static final double LAND_PURCHASE_MULTIPLIER = 2.0;
    static final int WAR_JUSTIFICATION_SECONDS = 90;
    private static final int PEACE_REJECT_COOLDOWN_SECONDS = 300;
    private static final int ROMANIAN_WAR_LEAVE_COOLDOWN_SECONDS = 1800;
    private static final int CREATE_NAME_TIMEOUT_SECONDS = 60;
    private static final Map<UUID, PendingNationCreation> PENDING_NATION_NAMES = new HashMap<UUID, PendingNationCreation>();
    private static final Set<String> NATURAL_WATER_BIOMES = Set.of(
        "minecraft:warm_ocean",
        "minecraft:lukewarm_ocean",
        "minecraft:deep_lukewarm_ocean",
        "minecraft:ocean",
        "minecraft:deep_ocean",
        "minecraft:cold_ocean",
        "minecraft:deep_cold_ocean",
        "minecraft:river",
        "minecraft:beach",
        "minecraft:snowy_beach",
        "minecraft:stony_shore"
    );

    private NationCommands() {
    }

    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        NationCommands.removeRootCommand(dispatcher, "openpac-parties");
        MarketCommand.register(dispatcher);
        NationCommand.register(dispatcher);
        AllianceCommand.register(dispatcher);
        WarCommand.register(dispatcher);
        PuppetCommand.register(dispatcher);
    }

    private static void removeRootCommand(CommandDispatcher<CommandSourceStack> dispatcher, String command) {
        for (String fieldName : List.of("children", "literals", "arguments")) {
            try {
                java.lang.reflect.Field field = CommandNode.class.getDeclaredField(fieldName);
                field.setAccessible(true);
                Object value = field.get(dispatcher.getRoot());
                if (value instanceof Map<?, ?> map) {
                    map.keySet().removeIf(key -> {
                        String literal = String.valueOf(key).toLowerCase(java.util.Locale.ROOT);
                        return literal.equals(command) || literal.endsWith(":" + command);
                    });
                }
            } catch (ReflectiveOperationException ignored) {
                // Execution is still blocked by blockOpenPacParties below if Brigadier changes internally.
            }
        }
    }

    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void blockOpenPacParties(CommandEvent event) {
        String input = event.getParseResults().getReader().getString().stripLeading();
        String token = input.split("\\s+", 2)[0];
        if (token.startsWith("/")) {
            token = token.substring(1);
        }
        token = token.toLowerCase(java.util.Locale.ROOT);
        if (!token.equals("openpac-parties") && !token.endsWith(":openpac-parties")) {
            return;
        }
        event.getParseResults().getContext().getSource().sendFailure(
            NationText.tr("nationwars.command.openpac_parties.disabled"));
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void serverTick(ServerTickEvent.Post event) {
        if (PENDING_NATION_NAMES.isEmpty() || (long)event.getServer().getTickCount() % 20L != 0L) {
            return;
        }
        long tick = event.getServer().getTickCount();
        Iterator<Map.Entry<UUID, PendingNationCreation>> iterator = PENDING_NATION_NAMES.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, PendingNationCreation> entry = iterator.next();
            if (tick <= entry.getValue().expiresTick) continue;
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player != null) {
                NationCommands.fail(player, "nationwars.command.nation.create.timeout");
            }
            iterator.remove();
        }
    }

    @SubscribeEvent
    public static void loggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        PENDING_NATION_NAMES.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void serverStopping(ServerStoppingEvent event) {
        PENDING_NATION_NAMES.clear();
    }

    @SubscribeEvent
    public static void chatNameInput(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        PendingNationCreation pending = PENDING_NATION_NAMES.get(player.getUUID());
        if (pending == null) {
            return;
        }
        event.setCanceled(true);
        long tick = player.getServer().getTickCount();
        if (tick > pending.expiresTick) {
            PENDING_NATION_NAMES.remove(player.getUUID());
            NationCommands.fail(player, "nationwars.command.nation.create.timeout");
            return;
        }
        String name = event.getRawText().trim();
        if (name.equalsIgnoreCase("cancel")) {
            PENDING_NATION_NAMES.remove(player.getUUID());
            NationCommands.fail(player, "nationwars.command.nation.create.cancelled");
            return;
        }
        if (NationCommands.createNationWithDoctrine(player, name, pending.doctrine)) {
            PENDING_NATION_NAMES.remove(player.getUUID());
            return;
        }
        NationCommands.fail(player, "nationwars.command.nation.create.retry");
    }

    public static int openNationCreateMenuUnnamed(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
        NationStore store = NationStore.get();
        return NationCommands.openNationCreateMenu(player, store, "");
    }

    public static int openNationCreateMenu(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
        NationStore store = NationStore.get();
        return NationCommands.openNationCreateMenu(player, store, StringArgumentType.getString(context, (String)"name"));
    }

    public static int createNationDirect(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String doctrineId = StringArgumentType.getString(context, "doctrine");
        Optional<Doctrine> doctrine = Doctrine.byId(doctrineId);
        if (doctrine.isEmpty()) {
            NationCommands.fail(player, "nationwars.command.nation.create.unknown_doctrine", doctrineId, Doctrine.choices());
            return 0;
        }
        String name = StringArgumentType.getString(context, "name");
        return NationCommands.createNationWithDoctrine(player, name, doctrine.get()) ? 1 : 0;
    }

    private static int openNationCreateMenu(ServerPlayer player, NationStore store, String name) {
        PENDING_NATION_NAMES.remove(player.getUUID());
        if (!NationCommands.canStartNationCreation(player, store, name)) {
            return 0;
        }
        if (store.availableDoctrines().isEmpty()) {
            NationCommands.fail(player, "nationwars.command.nation.create.no_doctrines");
            return 0;
        }
        Component title = name.isBlank()
            ? NationText.tr("nationwars.gui.nation_create.title")
            : NationText.tr("nationwars.gui.nation_create.title_named", name);
        player.openMenu((MenuProvider)new SimpleMenuProvider((containerId, inventory, viewer) -> new NationCreateMenu(containerId, inventory, name), title));
        return 1;
    }

    static boolean createNationWithDoctrine(ServerPlayer player, String name, Doctrine doctrine) {
        NationStore store = NationStore.get();
        if (NationStore.nationKey(name).length() < 3) {
            NationCommands.fail(player, "nationwars.command.nation.create.name_short");
            return false;
        }
        if (!NationCommands.canStartNationCreation(player, store, name)) {
            return false;
        }
        if (store.isDoctrineTaken(doctrine)) {
            if (NationWarsConfig.get().isDoctrineDisabled(doctrine)) {
                NationCommands.fail(player, "nationwars.command.nation.create.doctrine_disabled", NationText.doctrineName(doctrine));
            } else {
                NationCommands.fail(player, "nationwars.command.nation.create.doctrine_taken", NationText.doctrineName(doctrine));
            }
            return false;
        }
        ClaimKey capital = NationCommands.currentClaim(player);
        if (!NationCommands.validateNewClaimLocation(player, capital)) {
            return false;
        }
        if (store.nationOwning(capital).isPresent()) {
            NationCommands.fail(player, "nationwars.command.claim.already_claimed");
            return false;
        }
        if (!OpacClaimsBridge.canMirrorClaim(player.getServer(), capital, player.getUUID())) {
            NationCommands.fail(player, "nationwars.command.claim.opac_conflict");
            return false;
        }
        NationStore.Nation nation = store.createNation(player, name, doctrine, capital);
        store.setCoastClaim(capital.id(), NationCommands.isNaturalCoastClaim(player.serverLevel(), capital));
        PENDING_NATION_NAMES.remove(player.getUUID());
        NationEvents.refreshAllTabListNames(player.getServer());
        NationCommands.ok(player, "nationwars.command.nation.create.success", nation.name, NationText.doctrineName(doctrine), nation.id, capital.shortName());
        return true;
    }

    static void requestNationName(ServerPlayer player, Doctrine doctrine) {
        NationStore store = NationStore.get();
        if (!NationCommands.canStartNationCreation(player, store, "")) {
            return;
        }
        if (store.isDoctrineTaken(doctrine)) {
            if (NationWarsConfig.get().isDoctrineDisabled(doctrine)) {
                NationCommands.fail(player, "nationwars.command.nation.create.doctrine_disabled", NationText.doctrineName(doctrine));
            } else {
                NationCommands.fail(player, "nationwars.command.nation.create.doctrine_taken", NationText.doctrineName(doctrine));
            }
            return;
        }
        PENDING_NATION_NAMES.put(player.getUUID(), new PendingNationCreation(doctrine, (long)player.getServer().getTickCount() + 1200L));
        player.closeContainer();
        NationCommands.ok(player, "nationwars.command.nation.create.prompt", NationText.doctrineName(doctrine), CREATE_NAME_TIMEOUT_SECONDS);
    }

    private static boolean canStartNationCreation(ServerPlayer player, NationStore store, String name) {
        if (store.hasNation(player.getUUID())) {
            NationCommands.fail(player, "nationwars.command.error.already_in_nation");
            return false;
        }
        if (!name.isBlank()) {
            String key = NationStore.nationKey(name);
            if (key.length() < 3) {
                NationCommands.fail(player, "nationwars.command.nation.create.name_short");
                return false;
            }
            if (store.nationByName(name).isPresent()) {
                NationCommands.fail(player, "nationwars.command.nation.create.exists");
                return false;
            }
        }
        return true;
    }

    public static int joinNation(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
        NationStore store = NationStore.get();
        if (store.hasNation(player.getUUID())) {
            NationCommands.fail(player, "nationwars.command.error.already_in_nation");
            return 0;
        }
        Optional<NationStore.Nation> nation = store.nationByName(StringArgumentType.getString(context, (String)"name"));
        if (nation.isEmpty()) {
            NationCommands.fail(player, "nationwars.command.error.nation_missing");
            return 0;
        }
        if (!NationWarsConfig.get().rejoinNation && store.hasFormerMembership(player.getUUID(), nation.get())) {
            NationCommands.fail(player, "nationwars.command.nation.join.rejoin_disabled", nation.get().name);
            return 0;
        }
        JoinPolicy policy = nation.get().joinPolicy();
        if (policy == JoinPolicy.CLOSED) {
            NationCommands.fail(player, "nationwars.command.nation.join.closed", nation.get().name);
            return 0;
        }
        if (!policy.allowsJoin(policy == JoinPolicy.INVITE_ONLY && store.hasNationInvite(nation.get(), player.getUUID()))) {
            NationCommands.fail(player, "nationwars.command.nation.join.invite_required", nation.get().name);
            return 0;
        }
        store.addMember(player.getUUID(), nation.get());
        store.recordPlayerName(player);
        PENDING_NATION_NAMES.remove(player.getUUID());
        NationEvents.refreshAllTabListNames(player.getServer());
        OpacClaimsBridge.syncAll(player.getServer(), store);
        NationCommands.ok(player, "nationwars.command.nation.join.success", nation.get().name);
        return 1;
    }

    public static int acceptNationInvitation(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        NationStore store = NationStore.get();
        NationStore.Nation nation = store.nationByName(StringArgumentType.getString(context, "nation")).orElse(null);
        if (nation == null) {
            NationCommands.fail(player, "nationwars.command.error.nation_missing");
            return 0;
        }
        if (!store.hasNationInvite(nation, player.getUUID())) {
            NationCommands.fail(player, "nationwars.command.nation.invite.none", nation.name);
            return 0;
        }
        if (store.hasNation(player.getUUID())) {
            NationCommands.fail(player, "nationwars.command.error.already_in_nation");
            return 0;
        }
        if (!NationWarsConfig.get().rejoinNation && store.hasFormerMembership(player.getUUID(), nation)) {
            NationCommands.fail(player, "nationwars.command.nation.join.rejoin_disabled", nation.name);
            return 0;
        }
        store.addMember(player.getUUID(), nation);
        store.recordPlayerName(player);
        NationEvents.refreshAllTabListNames(player.getServer());
        OpacClaimsBridge.syncAll(player.getServer(), store);
        NationCommands.ok(player, "nationwars.command.nation.join.success", nation.name);
        return 1;
    }

    public static int rejectNationInvitation(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        NationStore store = NationStore.get();
        NationStore.Nation nation = store.nationByName(StringArgumentType.getString(context, "nation")).orElse(null);
        if (nation == null || !store.removeNationInvite(nation, player.getUUID())) {
            NationCommands.fail(player, "nationwars.command.nation.invite.none", nation == null ? StringArgumentType.getString(context, "nation") : nation.name);
            return 0;
        }
        NationCommands.ok(player, "nationwars.command.nation.invite.rejected", nation.name);
        return 1;
    }

    public static int setNationJoinPolicy(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        NationStore store = NationStore.get();
        NationStore.Nation nation = store.nationOf(player.getUUID()).orElse(null);
        if (nation == null || !NationCommands.requireNationOwner(player, store, nation)) {
            return 0;
        }
        JoinPolicy policy = JoinPolicy.parse(StringArgumentType.getString(context, "policy")).orElse(null);
        if (policy == null) {
            NationCommands.fail(player, "nationwars.command.nation.joinpolicy.invalid");
            return 0;
        }
        store.setJoinPolicy(nation, policy);
        NationCommands.ok(player, "nationwars.command.nation.joinpolicy.success", NationText.tr("nationwars.join_policy." + policy.name().toLowerCase(java.util.Locale.ROOT)));
        return 1;
    }

    public static int inviteNationMember(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerPlayer invited = EntityArgument.getPlayer(context, "player");
        NationStore store = NationStore.get();
        NationStore.Nation nation = store.nationOf(player.getUUID()).orElse(null);
        if (nation == null || !NationCommands.requireNationOwner(player, store, nation)) {
            return 0;
        }
        if (nation.joinPolicy() != JoinPolicy.INVITE_ONLY) {
            NationCommands.fail(player, "nationwars.command.nation.invite.policy", NationText.tr("nationwars.join_policy." + nation.joinPolicy().name().toLowerCase(java.util.Locale.ROOT)));
            return 0;
        }
        if (store.hasNation(invited.getUUID())) {
            NationCommands.fail(player, "nationwars.command.nation.invite.already_member", invited.getGameProfile().getName());
            return 0;
        }
        if (!NationWarsConfig.get().rejoinNation && store.hasFormerMembership(invited.getUUID(), nation)) {
            NationCommands.fail(player, "nationwars.command.nation.invite.rejoin_disabled", invited.getGameProfile().getName());
            return 0;
        }
        if (!store.inviteMember(nation, invited.getUUID())) {
            NationCommands.fail(player, "nationwars.command.nation.invite.duplicate");
            return 0;
        }
        store.recordPlayerName(invited);
        invited.sendSystemMessage(NationText.message("nationwars.command.nation.invite.received", nation.name, nation.id, NationStore.NATION_INVITATION_SECONDS));
        NationCommands.ok(player, "nationwars.command.nation.invite.sent", invited.getGameProfile().getName(), nation.name);
        return 1;
    }

    public static int upgradeNation(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        NationStore store = NationStore.get();
        NationStore.Nation nation = store.nationOf(player.getUUID()).orElse(null);
        if (nation == null || !NationCommands.requireNationOwner(player, store, nation)) {
            return 0;
        }
        if (nation.doctrine() == Doctrine.AMERICAN) {
            NationCommands.fail(player, "nationwars.command.nation.upgrade.usa_unavailable");
            return 0;
        }
        if (nation.upgradeLevel >= 4) {
            NationCommands.fail(player, "nationwars.command.nation.upgrade.maximum");
            return 0;
        }
        double[] prices = {1000.0, 1500.0, 2000.0, 2500.0};
        double cost = prices[nation.upgradeLevel];
        if (!NationCommands.canNationSpend(player, store, nation, cost)) {
            return 0;
        }
        if (!store.purchaseUpgrade(nation, cost)) {
            NationCommands.fail(player, "nationwars.command.nation.upgrade.failed");
            return 0;
        }
        NationCommands.ok(player, "nationwars.command.nation.upgrade.success", nation.upgradeLevel, cost, nation.freeClaimsRemaining);
        return 1;
    }

    public static int leaveNation(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
        NationStore store = NationStore.get();
        Optional<NationStore.Nation> nation = store.nationOf(player.getUUID());
        if (nation.isEmpty()) {
            NationCommands.fail(player, "nationwars.command.error.not_in_nation");
            return 0;
        }
        if (!NationWarsConfig.get().leaveNation) {
            NationCommands.fail(player, "nationwars.command.nation.leave.disabled");
            return 0;
        }
        if (store.isOwner(player.getUUID(), nation.get())) {
            NationCommands.fail(player, "nationwars.command.nation.leave.owner");
            return 0;
        }
        if (!store.removeMember(player.getUUID(), nation.get(), true)) {
            NationCommands.fail(player, "nationwars.command.nation.leave.failed");
            return 0;
        }
        NationEvents.refreshAllTabListNames(player.getServer());
        store.notifyNation(player.getServer(), nation.get(), NationText.message("nationwars.command.nation.leave.broadcast", player.getGameProfile().getName(), nation.get().name));
        NationCommands.ok(player, "nationwars.command.nation.leave.success", nation.get().name);
        return 1;
    }

    public static int kickNationMember(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
        NationStore store = NationStore.get();
        Optional<NationStore.Nation> nation = store.nationOf(player.getUUID());
        if (nation.isEmpty()) {
            NationCommands.fail(player, "nationwars.command.error.not_in_nation");
            return 0;
        }
        if (!store.isOwner(player.getUUID(), nation.get())) {
            NationCommands.fail(player, "nationwars.command.nation.kick.owner_only");
            return 0;
        }
        String memberName = StringArgumentType.getString(context, "member");
        Optional<UUID> memberId = NationCommands.resolveNationMember(player, store, nation.get(), memberName);
        if (memberId.isEmpty()) {
            NationCommands.fail(player, "nationwars.command.nation.kick.missing", memberName);
            return 0;
        }
        if (store.isOwner(memberId.get(), nation.get())) {
            NationCommands.fail(player, "nationwars.command.nation.kick.leader");
            return 0;
        }
        String displayName = store.playerName(memberId.get()).orElse(memberName);
        if (!store.removeMember(memberId.get(), nation.get(), true)) {
            NationCommands.fail(player, "nationwars.command.nation.kick.failed");
            return 0;
        }
        ServerPlayer kicked = player.getServer().getPlayerList().getPlayer(memberId.get());
        if (kicked != null) {
            kicked.sendSystemMessage(NationText.message("nationwars.command.nation.kick.received", nation.get().name));
        }
        NationEvents.refreshAllTabListNames(player.getServer());
        store.notifyNation(player.getServer(), nation.get(), NationText.message("nationwars.command.nation.kick.broadcast", displayName, nation.get().name));
        NationCommands.ok(player, "nationwars.command.nation.kick.success", displayName, nation.get().name);
        return 1;
    }

    public static int ownNationInfo(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
        Optional<NationStore.Nation> nation = NationStore.get().nationOf(player.getUUID());
        if (nation.isEmpty()) {
            NationCommands.fail(player, "nationwars.command.error.not_in_nation");
            return 0;
        }
        return NationCommands.describeNation(player, nation.get());
    }

    public static int nationInfo(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
        if (!NationWarsConfig.get().satellites) {
            NationCommands.fail(player, "nationwars.command.nation.info.disabled");
            return 0;
        }
        Optional<NationStore.Nation> nation = NationStore.get().nationByName(StringArgumentType.getString(context, (String)"name"));
        if (nation.isEmpty()) {
            NationCommands.fail(player, "nationwars.command.error.nation_missing");
            return 0;
        }
        return NationCommands.describeNation(player, nation.get());
    }

    private static int describeNation(ServerPlayer player, NationStore.Nation nation) {
        NationStore store = NationStore.get();
        Doctrine doctrine = nation.doctrine();
        player.sendSystemMessage(NationText.tr("nationwars.command.nation.info.name", nation.name));
        player.sendSystemMessage(NationText.tr("nationwars.command.nation.info.id", nation.id));
        player.sendSystemMessage(NationText.tr("nationwars.command.nation.info.leader", nation.ownerName));
        player.sendSystemMessage(NationText.tr("nationwars.command.nation.info.doctrine", NationText.doctrineName(doctrine), doctrine.id, NationText.ideologyName(doctrine.ideology)));
        player.sendSystemMessage(NationText.tr("nationwars.command.nation.info.claims", store.claimCount(nation), nation.freeClaimsRemaining));
        player.sendSystemMessage(NationText.tr(doctrine == Doctrine.AMERICAN
            ? "nationwars.command.nation.info.upgrades_usa" : "nationwars.command.nation.info.upgrades", nation.upgradeLevel));
        player.sendSystemMessage(NationText.tr("nationwars.command.nation.info.cities", nation.cityClaims.size()));
        player.sendSystemMessage(NationText.tr("nationwars.command.nation.info.treasury", NationStore.roundMoney(nation.balance)));
        player.sendSystemMessage(NationText.tr("nationwars.command.nation.info.maintenance", NationEvents.maintenanceDuePerInterval(store, nation)));
        player.sendSystemMessage(NationText.tr("nationwars.command.nation.info.income", NationEvents.currentIncomePerCycle(store, nation)));
        store.allianceOf(nation).ifPresent(alliance -> player.sendSystemMessage(NationText.tr("nationwars.command.nation.info.alliance", alliance.name)));
        String guarantors = store.guarantorsOf(nation).stream().map(guarantor -> guarantor.name).collect(Collectors.joining(", "));
        player.sendSystemMessage(NationText.tr(guarantors.isBlank()
            ? "nationwars.command.nation.info.guaranteed_none" : "nationwars.command.nation.info.guaranteed", guarantors));
        String capital = nation.capitalClaim == null || nation.capitalClaim.isBlank() ? null : ClaimKey.parse(nation.capitalClaim).shortName();
        player.sendSystemMessage(capital == null
            ? NationText.tr("nationwars.command.nation.info.capital_none")
            : NationText.tr("nationwars.command.nation.info.capital", capital));
        return 1;
    }

    public static int claim(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
        NationStore store = NationStore.get();
        Optional<NationStore.Nation> nation = store.nationOf(player.getUUID());
        if (nation.isEmpty()) {
            NationCommands.fail(player, "nationwars.command.error.create_or_join");
            return 0;
        }
        if (!NationCommands.requireNationOwner(player, store, nation.get())) {
            return 0;
        }
        if (!store.canPuppetClaim(nation.get())) {
            NationCommands.fail(player, "nationwars.command.claim.puppet_points_low");
            return 0;
        }
        ClaimKey claim = NationCommands.currentClaim(player);
        if (!NationCommands.validateNewClaimLocation(player, claim)) {
            return 0;
        }
        if (store.nationOwning(claim).isPresent()) {
            NationCommands.fail(player, "nationwars.command.claim.already_claimed");
            return 0;
        }
        if (!OpacClaimsBridge.canMirrorClaim(player.getServer(), claim, UUID.fromString(nation.get().owner))) {
            NationCommands.fail(player, "nationwars.command.claim.opac_conflict");
            return 0;
        }
        boolean hasClaimInDimension = store.hasClaimInDimension(nation.get(), claim.dimension());
        if (!NationWarsConfig.get().colonialism && hasClaimInDimension && !NationCommands.touchesNationClaim(store, nation.get(), claim)) {
            NationCommands.fail(player, "nationwars.command.claim.not_adjacent");
            return 0;
        }
        if (nation.get().freeClaimsRemaining > 0) {
            --nation.get().freeClaimsRemaining;
        } else {
            double cost = NationCommands.claimCost(store, nation.get(), claim);
            if (!NationCommands.canNationSpend(player, store, nation.get(), cost)) {
                return 0;
            }
            nation.get().balance = NationStore.roundMoney(nation.get().balance - cost);
        }
        store.claim(nation.get(), claim);
        store.setCoastClaim(claim.id(), NationCommands.isNaturalCoastClaim(player.serverLevel(), claim));
        NationCommands.ok(player, "nationwars.command.claim.success", claim.shortName());
        return 1;
    }

    private static int buyClaim(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
        NationStore store = NationStore.get();
        Optional<NationStore.Nation> buyer = store.nationOf(player.getUUID());
        if (buyer.isEmpty()) {
            NationCommands.fail(player, "nationwars.command.error.create_or_join");
            return 0;
        }
        if (!store.isOwner(player.getUUID(), buyer.get())) {
            NationCommands.fail(player, "nationwars.command.land.owner_only");
            return 0;
        }
        ClaimKey claim = NationCommands.currentClaim(player);
        Optional<NationStore.Nation> seller = store.nationOwning(claim);
        if (seller.isEmpty() || seller.get().id.equals(buyer.get().id)) {
            NationCommands.fail(player, "nationwars.command.land.stand_in_other");
            return 0;
        }
        if (claim.id().equals(seller.get().capitalClaim)) {
            NationCommands.fail(player, "nationwars.command.land.capital");
            return 0;
        }
        if (!NationCommands.touchesNationClaim(store, buyer.get(), claim)) {
            NationCommands.fail(player, "nationwars.command.land.not_adjacent");
            return 0;
        }
        if (store.activeWarForCapture(buyer.get(), seller.get()).isPresent() || store.activeWarForCapture(seller.get(), buyer.get()).isPresent()) {
            NationCommands.fail(player, "nationwars.command.land.at_war");
            return 0;
        }
        double cost = NationStore.roundMoney(Math.max(250.0, NationCommands.claimCost(store, buyer.get(), claim) * 2.0));
        if (!NationCommands.canNationSpend(player, store, buyer.get(), cost)) {
            return 0;
        }
        Optional<NationStore.LandPurchaseOffer> existing = store.landPurchaseOfferForClaim(buyer.get().id, seller.get().id, claim.id());
        if (existing.isPresent()) {
            NationCommands.fail(player, "nationwars.command.land.offer_duplicate", existing.get().id);
            return 0;
        }
        NationStore.LandPurchaseOffer offer = store.createLandPurchaseOffer(buyer.get(), seller.get(), claim.id(), cost, player, player.getServer().getTickCount());
        store.notifyNation(player.getServer(), seller.get(), NationText.message("nationwars.command.land.offer_received", buyer.get().name, cost, claim.shortName(), offer.id));
        NationCommands.ok(player, "nationwars.command.land.offer_sent", offer.id, seller.get().name, claim.shortName(), cost);
        return 1;
    }

    private static int buyClaimList(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
        NationStore store = NationStore.get();
        Optional<NationStore.Nation> nation = store.nationOf(player.getUUID());
        if (nation.isEmpty()) {
            NationCommands.fail(player, "nationwars.command.error.create_or_join");
            return 0;
        }
        List<NationStore.LandPurchaseOffer> offers = store.landPurchaseOffersFor(nation.get());
        if (offers.isEmpty()) {
            NationCommands.ok(player, "nationwars.command.land.list_empty");
            return 1;
        }
        player.sendSystemMessage(NationText.message("nationwars.command.land.list_title"));
        for (NationStore.LandPurchaseOffer offer : offers) {
            player.sendSystemMessage(NationCommands.describeOffer(store, offer, nation.get()));
        }
        return 1;
    }

    private static int buyClaimAccept(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ClaimKey claim;
        ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
        int id = IntegerArgumentType.getInteger(context, (String)"id");
        NationStore store = NationStore.get();
        Optional<NationStore.LandPurchaseOffer> maybeOffer = store.landPurchaseOffer(id);
        if (maybeOffer.isEmpty()) {
            NationCommands.fail(player, "nationwars.command.land.offer_missing");
            return 0;
        }
        NationStore.LandPurchaseOffer offer = maybeOffer.get();
        NationStore.Nation seller = store.nationById(offer.seller).orElse(null);
        NationStore.Nation buyer = store.nationById(offer.buyer).orElse(null);
        if (seller == null || buyer == null) {
            store.removeLandPurchaseOffer(offer);
            NationCommands.fail(player, "nationwars.command.land.offer_stale");
            return 0;
        }
        if (!store.isOwner(player.getUUID(), seller)) {
            NationCommands.fail(player, "nationwars.command.land.accept_seller_only");
            return 0;
        }
        try {
            claim = ClaimKey.parse(offer.claimId);
        }
        catch (RuntimeException exception) {
            store.removeLandPurchaseOffer(offer);
            NationCommands.fail(player, "nationwars.command.land.invalid_claim");
            return 0;
        }
        if (!NationCommands.validateLandPurchaseOffer(player, store, offer, buyer, seller, claim, true)) {
            return 0;
        }
        buyer.balance = NationStore.roundMoney(buyer.balance - offer.price);
        seller.balance = NationStore.roundMoney(seller.balance + offer.price);
        store.transferClaim(claim.id(), buyer);
        buyer.coreClaims.add(claim.id());
        store.removeLandPurchaseOffer(offer);
        store.notifyNation(player.getServer(), buyer, NationText.message("nationwars.command.land.accepted_buyer", seller.name, claim.shortName(), offer.price));
        store.notifyNation(player.getServer(), seller, NationText.message("nationwars.command.land.accepted_seller", buyer.name, claim.shortName(), offer.price));
        return 1;
    }

    private static int buyClaimReject(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
        int id = IntegerArgumentType.getInteger(context, (String)"id");
        NationStore store = NationStore.get();
        Optional<NationStore.LandPurchaseOffer> maybeOffer = store.landPurchaseOffer(id);
        if (maybeOffer.isEmpty()) {
            NationCommands.fail(player, "nationwars.command.land.offer_missing");
            return 0;
        }
        NationStore.LandPurchaseOffer offer = maybeOffer.get();
        NationStore.Nation seller = store.nationById(offer.seller).orElse(null);
        NationStore.Nation buyer = store.nationById(offer.buyer).orElse(null);
        if (seller == null || buyer == null) {
            store.removeLandPurchaseOffer(offer);
            NationCommands.fail(player, "nationwars.command.land.offer_stale");
            return 0;
        }
        boolean sellerOwner = store.isOwner(player.getUUID(), seller);
        boolean buyerOwner = store.isOwner(player.getUUID(), buyer);
        if (!sellerOwner && !buyerOwner) {
            NationCommands.fail(player, "nationwars.command.land.reject_parties_only");
            return 0;
        }
        ClaimKey claim = ClaimKey.parse(offer.claimId);
        store.removeLandPurchaseOffer(offer);
        if (sellerOwner) {
            store.notifyNation(player.getServer(), buyer, NationText.message("nationwars.command.land.rejected_buyer", seller.name, claim.shortName()));
            NationCommands.ok(player, "nationwars.command.land.rejected", offer.id);
        } else {
            store.notifyNation(player.getServer(), seller, NationText.message("nationwars.command.land.cancelled_seller", buyer.name, claim.shortName()));
            NationCommands.ok(player, "nationwars.command.land.cancelled", offer.id);
        }
        return 1;
    }

    private static boolean validateLandPurchaseOffer(ServerPlayer player, NationStore store, NationStore.LandPurchaseOffer offer, NationStore.Nation buyer, NationStore.Nation seller, ClaimKey claim, boolean removeStale) {
        if (!seller.id.equals(store.nationOwning(claim).map(owned -> owned.id).orElse(""))) {
            if (removeStale) {
                store.removeLandPurchaseOffer(offer);
            }
            NationCommands.fail(player, "nationwars.command.land.no_longer_owned");
            return false;
        }
        if (claim.id().equals(seller.capitalClaim)) {
            if (removeStale) {
                store.removeLandPurchaseOffer(offer);
            }
            NationCommands.fail(player, "nationwars.command.land.now_capital");
            return false;
        }
        if (!NationCommands.touchesNationClaim(store, buyer, claim)) {
            NationCommands.fail(player, "nationwars.command.land.no_longer_adjacent");
            return false;
        }
        if (store.activeWarForCapture(buyer, seller).isPresent() || store.activeWarForCapture(seller, buyer).isPresent()) {
            NationCommands.fail(player, "nationwars.command.land.parties_at_war");
            return false;
        }
        if (buyer.balance + 1.0E-4 < offer.price) {
            NationCommands.fail(player, "nationwars.command.land.buyer_funds", buyer.name);
            return false;
        }
        if (store.isSpendingBlocked(buyer, NationStore.persistentNow())) {
            NationCommands.fail(player, "nationwars.command.land.buyer_infiltrated", buyer.name);
            return false;
        }
        return true;
    }

    private static Component describeOffer(NationStore store, NationStore.LandPurchaseOffer offer, NationStore.Nation viewerNation) {
        String buyer = store.nationById(offer.buyer).map(nation -> nation.name).orElse(offer.buyer);
        String seller = store.nationById(offer.seller).map(nation -> nation.name).orElse(offer.seller);
        String claim = ClaimKey.parse(offer.claimId).shortName();
        String key = viewerNation.id.equals(offer.seller)
            ? "nationwars.command.land.list_incoming" : "nationwars.command.land.list_outgoing";
        return NationText.tr(key, offer.id, buyer, offer.price, seller, claim);
    }

    public static int buyCity(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
        NationStore store = NationStore.get();
        Optional<NationStore.Nation> nation = store.nationOf(player.getUUID());
        if (nation.isEmpty()) {
            NationCommands.fail(player, "nationwars.command.error.create_or_join");
            return 0;
        }
        if (!store.isOwner(player.getUUID(), nation.get())) {
            NationCommands.fail(player, "nationwars.command.city.owner_only");
            return 0;
        }
        if (!nation.get().doctrine().canBuyCities) {
            NationCommands.fail(player, "nationwars.command.city.usa_only");
            return 0;
        }
        ClaimKey claim = NationCommands.currentClaim(player);
        if (!nation.get().id.equals(store.nationOwning(claim).map(owned -> owned.id).orElse(""))) {
            NationCommands.fail(player, "nationwars.command.city.stand_in_claim");
            return 0;
        }
        if (store.isCapturedClaimHeldBy(nation.get(), claim.id())) {
            NationCommands.fail(player, "nationwars.command.city.occupied");
            return 0;
        }
        double cityCost = NationCommands.claimCost(store, nation.get(), claim);
        if (!NationCommands.canNationSpend(player, store, nation.get(), cityCost)) {
            return 0;
        }
        if (!store.addCityClaim(nation.get(), claim.id(), cityCost)) {
            NationCommands.fail(player, "nationwars.command.city.failed", NationStore.roundMoney(cityCost));
            return 0;
        }
        NationCommands.ok(player, "nationwars.command.city.success", claim.shortName(), NationStore.roundMoney(cityCost));
        return 1;
    }

    public static int guarantee(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        if (!NationWarsConfig.get().guarantees) {
            NationCommands.fail(player, "nationwars.command.guarantee.disabled");
            return 0;
        }
        NationStore store = NationStore.get();
        NationStore.Nation guarantor = store.nationOf(player.getUUID()).orElse(null);
        NationStore.Nation target = store.nationByName(StringArgumentType.getString(context, "country")).orElse(null);
        if (guarantor == null || target == null || guarantor.id.equals(target.id)) {
            NationCommands.fail(player, "nationwars.command.error.country_other");
            return 0;
        }
        if (!NationCommands.requireNationOwner(player, store, guarantor)) {
            return 0;
        }
        if (store.sameAlliance(guarantor, target)) {
            NationCommands.fail(player, "nationwars.command.guarantee.same_alliance");
            return 0;
        }
        if (!store.addGuarantee(guarantor, target)) {
            NationCommands.fail(player, "nationwars.command.guarantee.duplicate", target.name);
            return 0;
        }
        store.notifyNation(player.getServer(), target, NationText.message("nationwars.command.guarantee.received", guarantor.name));
        NationCommands.ok(player, "nationwars.command.guarantee.success", target.name);
        return 1;
    }

    public static int removeGuarantee(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        if (!NationWarsConfig.get().guarantees) {
            NationCommands.fail(player, "nationwars.command.guarantee.disabled");
            return 0;
        }
        NationStore store = NationStore.get();
        NationStore.Nation guarantor = store.nationOf(player.getUUID()).orElse(null);
        NationStore.Nation target = store.nationByName(StringArgumentType.getString(context, "country")).orElse(null);
        if (guarantor == null || target == null || !NationCommands.requireNationOwner(player, store, guarantor)) {
            return 0;
        }
        if (!store.removeGuarantee(guarantor, target)) {
            NationCommands.fail(player, "nationwars.command.guarantee.missing", target.name);
            return 0;
        }
        store.notifyNation(player.getServer(), target, NationText.message("nationwars.command.guarantee.withdrawn_received", guarantor.name));
        NationCommands.ok(player, "nationwars.command.guarantee.withdrawn", target.name);
        return 1;
    }

    public static int unclaim(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
        NationStore store = NationStore.get();
        Optional<NationStore.Nation> nation = store.nationOf(player.getUUID());
        if (nation.isEmpty()) {
            NationCommands.fail(player, "nationwars.command.error.create_or_join");
            return 0;
        }
        if (!store.isOwner(player.getUUID(), nation.get())) {
            NationCommands.fail(player, "nationwars.command.unclaim.owner_only");
            return 0;
        }
        ClaimKey claim = NationCommands.currentClaim(player);
        if (!store.unclaim(nation.get(), claim)) {
            NationCommands.fail(player, "nationwars.command.unclaim.invalid");
            return 0;
        }
        NationCommands.ok(player, "nationwars.command.unclaim.success", claim.shortName());
        return 1;
    }

    public static int nationBalance(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
        NationStore store = NationStore.get();
        Optional<NationStore.Nation> nation = store.nationOf(player.getUUID());
        if (nation.isEmpty()) {
            NationCommands.fail(player, "nationwars.command.error.not_in_nation");
            return 0;
        }
        NationCommands.ok(player, "nationwars.command.balance.nation", store.playerBalance(player.getUUID()), nation.get().name, NationStore.roundMoney(nation.get().balance));
        return 1;
    }

    public static int deposit(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
        NationStore store = NationStore.get();
        Optional<NationStore.Nation> nation = store.nationOf(player.getUUID());
        if (nation.isEmpty()) {
            NationCommands.fail(player, "nationwars.command.error.not_in_nation");
            return 0;
        }
        double amount = DoubleArgumentType.getDouble(context, (String)"amount");
        if (!store.depositToNation(player.getUUID(), nation.get(), amount)) {
            NationCommands.fail(player, "nationwars.command.error.player_funds");
            return 0;
        }
        NationCommands.ok(player, "nationwars.command.deposit.success", NationStore.roundMoney(amount), nation.get().name);
        return 1;
    }

    public static int doctrines(CommandContext<CommandSourceStack> context) {
        ((CommandSourceStack)context.getSource()).sendSuccess(() -> NationText.tr("nationwars.command.doctrines.title", Doctrine.choices()), false);
        for (Doctrine doctrine : Doctrine.values()) {
            ((CommandSourceStack)context.getSource()).sendSuccess(() -> NationText.tr("nationwars.command.doctrines.header", doctrine.id, NationText.doctrineName(doctrine), NationText.ideologyName(doctrine.ideology)), false);
            ((CommandSourceStack)context.getSource()).sendSuccess(() -> NationText.tr("nationwars.command.doctrines.stats", doctrine.freeClaims, doctrine.claimCostMultiplier, doctrine.maintenanceMultiplier, doctrine.captureSeconds), false);
            for (Component perk : NationText.doctrinePerks(doctrine)) {
                ((CommandSourceStack)context.getSource()).sendSuccess(() -> NationText.tr("nationwars.command.doctrines.perk", perk), false);
            }
        }
        return 1;
    }

    public static int openDoctrinesMenu(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
        player.openMenu((MenuProvider)new SimpleMenuProvider((containerId, inventory, viewer) -> new DoctrineMenu(containerId, inventory), NationText.tr("nationwars.gui.doctrine.title")));
        return 1;
    }

    public static int syncOpac(CommandContext<CommandSourceStack> context) {
        OpacClaimsBridge.activatePrimaryPartySystem(((CommandSourceStack)context.getSource()).getServer());
        OpacClaimsBridge.syncAll(((CommandSourceStack)context.getSource()).getServer(), NationStore.get());
        NationEvents.refreshAllTabListNames(((CommandSourceStack)context.getSource()).getServer());
        ((CommandSourceStack)context.getSource()).sendSuccess(() -> NationText.tr("nationwars.command.syncopac.success"), true);
        return 1;
    }

    public static int money(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
        NationCommands.ok(player, "nationwars.command.balance.player", NationStore.get().playerBalance(player.getUUID()));
        return 1;
    }

    public static int blockedOpenPacParties(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
        NationCommands.fail(player, "nationwars.command.openpac_parties.disabled");
        return 0;
    }

    public static int openMarket(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
        player.openMenu((MenuProvider)new SimpleMenuProvider((containerId, inventory, viewer) -> new MarketMenu(containerId, inventory), NationText.tr("nationwars.gui.market.title")));
        return 1;
    }

    public static int openWarMenu(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
        player.openMenu((MenuProvider)new SimpleMenuProvider((containerId, inventory, viewer) -> new WarMenu(containerId, inventory), NationText.tr("nationwars.gui.war.title")));
        return 1;
    }

    public static int openTradeMenu(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
        if (!NationWarsConfig.get().allowTrade) {
            NationCommands.fail(player, "nationwars.command.trade.disabled");
            return 0;
        }
        NationStore store = NationStore.get();
        Optional<NationStore.Nation> own = store.nationOf(player.getUUID());
        Optional<NationStore.Nation> other = store.nationByName(StringArgumentType.getString(context, "country"));
        if (own.isEmpty() || other.isEmpty() || own.get().id.equals(other.get().id)) {
            NationCommands.fail(player, "nationwars.command.trade.invalid_target");
            return 0;
        }
        if (!NationCommands.requireNationOwner(player, store, own.get())) {
            return 0;
        }
        if (store.activeWarForCapture(own.get(), other.get()).isPresent() || store.activeWarForCapture(other.get(), own.get()).isPresent()) {
            NationCommands.fail(player, "nationwars.command.trade.at_war");
            return 0;
        }
        player.openMenu((MenuProvider)new SimpleMenuProvider((containerId, inventory, viewer) -> new NationTradeMenu(containerId, inventory, own.get(), other.get()), NationText.tr("nationwars.gui.trade.title", other.get().name)));
        return 1;
    }

    public static int sellHand(CommandContext<CommandSourceStack> context, double requestedPrice) throws CommandSyntaxException {
        double price;
        ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) {
            NationCommands.fail(player, "nationwars.command.market.hold_item");
            return 0;
        }
        double d = price = requestedPrice > 0.0 ? requestedPrice : NationCommands.appraise(stack);
        if (price <= 0.0) {
            NationCommands.fail(player, "nationwars.command.market.price_required");
            return 0;
        }
        int count = stack.getCount();
        Component itemName = stack.getHoverName();
        ItemStack listed = stack.copy();
        stack.setCount(0);
        NationStore.MarketListing listing = NationStore.get().createMarketListing(player, listed, price);
        NationCommands.ok(player, "nationwars.command.market.listed", count, itemName, NationStore.roundMoney(price), listing.id);
        return 1;
    }

    public static int cancelListing(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        int id;
        ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
        NationStore store = NationStore.get();
        NationStore.MarketListing listing = store.marketListing(id = IntegerArgumentType.getInteger(context, (String)"id")).orElse(null);
        if (listing == null) {
            NationCommands.fail(player, "nationwars.command.market.listing_missing");
            return 0;
        }
        if (!listing.seller.equals(player.getUUID().toString())) {
            NationCommands.fail(player, "nationwars.command.market.cancel_own");
            return 0;
        }
        ItemStack stack = store.listingStack(listing, (HolderLookup.Provider)player.registryAccess());
        store.removeMarketListing(id);
        if (!stack.isEmpty()) {
            player.getInventory().placeItemBackInInventory(stack);
        }
        NationCommands.ok(player, "nationwars.command.market.cancelled", id);
        return 1;
    }

    public static int nations(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
        player.openMenu((MenuProvider)new SimpleMenuProvider((containerId, inventory, viewer) -> new NationsMenu(containerId, inventory), NationText.tr("nationwars.gui.nations.title")));
        return 1;
    }

    public static int allianceCreate(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
        NationStore store = NationStore.get();
        Optional<NationStore.Nation> nation = store.nationOf(player.getUUID());
        if (nation.isEmpty() || !store.isOwner(player.getUUID(), nation.get())) {
            NationCommands.fail(player, "nationwars.command.alliance.create.owner_only");
            return 0;
        }
        String name = StringArgumentType.getString(context, (String)"name");
        if (!store.createAlliance(nation.get(), name)) {
            NationCommands.fail(player, "nationwars.command.alliance.create.failed");
            return 0;
        }
        NationCommands.ok(player, "nationwars.command.alliance.create.success", name);
        return 1;
    }

    public static int allianceInvite(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
        NationStore store = NationStore.get();
        Optional<NationStore.Nation> inviter = store.nationOf(player.getUUID());
        Optional<NationStore.Nation> invited = store.nationByName(StringArgumentType.getString(context, (String)"country"));
        if (inviter.isEmpty() || invited.isEmpty()) {
            NationCommands.fail(player, "nationwars.command.error.both_nations");
            return 0;
        }
        if (!NationCommands.requireNationOwner(player, store, inviter.get())) {
            return 0;
        }
        Optional<NationStore.Alliance> alliance = store.allianceOf(inviter.get());
        if (alliance.isEmpty()) {
            NationCommands.fail(player, "nationwars.command.alliance.not_member");
            return 0;
        }
        if (!store.inviteToAlliance(alliance.get(), inviter.get(), invited.get())) {
            NationCommands.fail(player, "nationwars.command.alliance.invite.failed");
            return 0;
        }
        store.notifyNation(player.getServer(), invited.get(), NationText.message("nationwars.command.alliance.invite.received", inviter.get().name, alliance.get().name, alliance.get().id));
        NationCommands.ok(player, "nationwars.command.alliance.invite.sent", invited.get().name);
        return 1;
    }

    public static int allianceAccept(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
        NationStore store = NationStore.get();
        Optional<NationStore.Nation> nation = store.nationOf(player.getUUID());
        Optional<NationStore.Alliance> alliance = store.allianceByName(StringArgumentType.getString(context, (String)"name"));
        if (nation.isEmpty() || alliance.isEmpty()) {
            NationCommands.fail(player, "nationwars.command.alliance.accept.no_match");
            return 0;
        }
        if (!store.isOwner(player.getUUID(), nation.get())) {
            NationCommands.fail(player, "nationwars.command.alliance.accept.owner_only");
            return 0;
        }
        if (!store.acceptAllianceInvite(alliance.get(), nation.get())) {
            NationCommands.fail(player, "nationwars.command.alliance.accept.no_invite");
            return 0;
        }
        store.notifyNation(player.getServer(), nation.get(), NationText.message("nationwars.command.alliance.accept.success", alliance.get().name));
        return 1;
    }

    public static int allianceKick(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
        NationStore store = NationStore.get();
        Optional<NationStore.Nation> actor = store.nationOf(player.getUUID());
        Optional<NationStore.Nation> kicked = store.nationByName(StringArgumentType.getString(context, (String)"country"));
        if (actor.isEmpty() || kicked.isEmpty()) {
            NationCommands.fail(player, "nationwars.command.error.both_nations");
            return 0;
        }
        if (!NationCommands.requireNationOwner(player, store, actor.get())) {
            return 0;
        }
        Optional<NationStore.Alliance> alliance = store.allianceOf(actor.get());
        if (alliance.isEmpty() || !store.kickFromAlliance(alliance.get(), actor.get(), kicked.get())) {
            NationCommands.fail(player, "nationwars.command.alliance.kick.leader_only");
            return 0;
        }
        store.notifyNation(player.getServer(), kicked.get(), NationText.message("nationwars.command.alliance.kick.received", alliance.get().name));
        NationCommands.ok(player, "nationwars.command.alliance.kick.success", kicked.get().name, alliance.get().name);
        return 1;
    }

    public static int allianceInfo(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
        Optional<NationStore.Nation> nation = NationStore.get().nationOf(player.getUUID());
        if (nation.isEmpty()) {
            NationCommands.fail(player, "nationwars.command.error.not_in_nation");
            return 0;
        }
        Optional<NationStore.Alliance> alliance = NationStore.get().allianceOf(nation.get());
        if (alliance.isEmpty()) {
            NationCommands.fail(player, "nationwars.command.alliance.not_member");
            return 0;
        }
        return NationCommands.describeAlliance((CommandSourceStack)context.getSource(), alliance.get());
    }

    public static int allianceInfoNamed(CommandContext<CommandSourceStack> context) {
        Optional<NationStore.Alliance> alliance = NationStore.get().allianceByName(StringArgumentType.getString(context, (String)"name"));
        if (alliance.isEmpty()) {
            ((CommandSourceStack)context.getSource()).sendFailure(NationText.tr("nationwars.command.alliance.info.missing"));
            return 0;
        }
        return NationCommands.describeAlliance((CommandSourceStack)context.getSource(), alliance.get());
    }

    public static int alliances(CommandContext<CommandSourceStack> context) {
        NationStore store = NationStore.get();
        if (store.alliances().isEmpty()) {
            ((CommandSourceStack)context.getSource()).sendSuccess(() -> NationText.tr("nationwars.command.alliances.empty"), false);
            return 1;
        }
        for (NationStore.Alliance alliance : store.alliances()) {
            ((CommandSourceStack)context.getSource()).sendSuccess(() -> NationText.tr("nationwars.command.alliances.entry", alliance.name, alliance.members.size()), false);
        }
        return 1;
    }

    private static int describeAlliance(CommandSourceStack source, NationStore.Alliance alliance) {
        NationStore store = NationStore.get();
        String members = alliance.members.stream().map(id -> store.nationById((String)id).map(nation -> nation.name).orElse((String)id)).sorted().collect(Collectors.joining(", "));
        source.sendSuccess(() -> NationText.tr("nationwars.command.alliance.info.name", alliance.name), false);
        source.sendSuccess(() -> NationText.tr("nationwars.command.alliance.info.leader", store.nationById(alliance.leader).map(nation -> nation.name).orElse(alliance.leader)), false);
        source.sendSuccess(() -> NationText.tr("nationwars.command.alliance.info.members", members), false);
        return 1;
    }

    public static int truceStatus(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        NationStore store = NationStore.get();
        NationStore.Nation nation = store.nationOf(player.getUUID()).orElse(null);
        if (nation == null) {
            NationCommands.fail(player, "nationwars.command.error.not_in_nation");
            return 0;
        }
        List<NationStore.Truce> truces = store.trucesOf(nation);
        if (truces.isEmpty()) {
            NationCommands.ok(player, "nationwars.command.truce.none");
        } else {
            player.sendSystemMessage(NationText.message("nationwars.command.truce.title"));
            long now = NationStore.persistentNow();
            for (NationStore.Truce truce : truces) {
                String otherId = nation.id.equals(truce.first) ? truce.second : truce.first;
                String other = store.nationById(otherId).map(value -> value.name).orElse(otherId);
                long seconds = Math.max(0L, (truce.expiresTick - now + 19L) / 20L);
                player.sendSystemMessage(NationText.tr("nationwars.command.truce.entry", other, seconds));
            }
        }
        return 1;
    }

    public static int offerTruce(CommandContext<CommandSourceStack> context, boolean renewal) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        NationStore store = NationStore.get();
        NationStore.Nation proposer = store.nationOf(player.getUUID()).orElse(null);
        NationStore.Nation receiver = store.nationByName(StringArgumentType.getString(context, "country")).orElse(null);
        if (proposer == null || receiver == null || proposer.id.equals(receiver.id)) {
            NationCommands.fail(player, "nationwars.command.error.nation_other");
            return 0;
        }
        if (!NationCommands.requireNationOwner(player, store, proposer)) {
            return 0;
        }
        if (!store.offerTruce(proposer, receiver, renewal)) {
            NationCommands.fail(player, renewal
                ? "nationwars.command.truce.renew_unavailable"
                : "nationwars.command.truce.offer_unavailable");
            return 0;
        }
        store.notifyNation(player.getServer(), receiver, NationText.message(renewal
            ? "nationwars.command.truce.renew_received" : "nationwars.command.truce.offer_received", proposer.name, proposer.id));
        NationCommands.ok(player, renewal
            ? "nationwars.command.truce.renew_sent" : "nationwars.command.truce.offer_sent", receiver.name);
        return 1;
    }

    public static int acceptTruce(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        NationStore store = NationStore.get();
        NationStore.Nation receiver = store.nationOf(player.getUUID()).orElse(null);
        NationStore.Nation proposer = store.nationByName(StringArgumentType.getString(context, "country")).orElse(null);
        if (receiver == null || proposer == null || !NationCommands.requireNationOwner(player, store, receiver)) {
            return 0;
        }
        NationStore.Truce truce = store.acceptTruce(receiver, proposer).orElse(null);
        if (truce == null) {
            NationCommands.fail(player, "nationwars.command.truce.accept_missing");
            return 0;
        }
        long seconds = Math.max(0L, (truce.expiresTick - NationStore.persistentNow() + 19L) / 20L);
        store.notifyNation(player.getServer(), proposer, NationText.message("nationwars.command.truce.accepted_proposer", receiver.name, seconds));
        store.notifyNation(player.getServer(), receiver, NationText.message("nationwars.command.truce.accepted_receiver", proposer.name, seconds));
        return 1;
    }

    public static int rejectTruce(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        NationStore store = NationStore.get();
        NationStore.Nation receiver = store.nationOf(player.getUUID()).orElse(null);
        NationStore.Nation proposer = store.nationByName(StringArgumentType.getString(context, "country")).orElse(null);
        if (receiver == null || proposer == null || !NationCommands.requireNationOwner(player, store, receiver)) {
            return 0;
        }
        if (!store.rejectTruce(receiver, proposer)) {
            NationCommands.fail(player, "nationwars.command.truce.reject_missing");
            return 0;
        }
        store.notifyNation(player.getServer(), proposer, NationText.message("nationwars.command.truce.rejected_proposer", receiver.name));
        NationCommands.ok(player, "nationwars.command.truce.rejected", proposer.name);
        return 1;
    }

    public static int justifyWar(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
        NationStore store = NationStore.get();
        Optional<NationStore.Nation> attacker = store.nationOf(player.getUUID());
        Optional<NationStore.Nation> defender = store.nationByName(StringArgumentType.getString(context, (String)"country"));
        if (attacker.isEmpty() || defender.isEmpty() || attacker.get().id.equals(defender.get().id)) {
            NationCommands.fail(player, "nationwars.command.error.nation_other");
            return 0;
        }
        if (!NationCommands.requireNationOwner(player, store, attacker.get())) {
            return 0;
        }
        if (!NationCommands.canStartHostilities(player, store, attacker.get(), defender.get())) {
            return 0;
        }
        if (attacker.get().doctrine().pacifist) {
            NationCommands.fail(player, "nationwars.command.war.justify.pacifist", NationText.doctrineName(attacker.get().doctrine()));
            return 0;
        }
        Optional<NationStore.War> existing = store.warBetween(attacker.get(), defender.get());
        if (existing.isPresent()) {
            NationStore.War existingWar = existing.get();
            if (existingWar.active) {
                NationCommands.fail(player, "nationwars.command.war.already_active");
                return 0;
            }
            if (existingWar.pendingDefenderResponse) {
                NationCommands.fail(player, "nationwars.command.war.awaiting_response");
                return 0;
            }
            if (attacker.get().id.equals(existingWar.attacker)) {
                NationCommands.fail(player, "nationwars.command.war.justify.already", defender.get().id);
            } else {
                NationCommands.fail(player, "nationwars.command.war.justify.incoming", defender.get().name);
            }
            return 0;
        }
        NationStore.War war = store.getOrCreateWar(attacker.get(), defender.get());
        war.attacker = attacker.get().id;
        war.defender = defender.get().id;
        war.active = false;
        war.pendingDefenderResponse = false;
        war.independenceWar = false;
        war.independencePuppet = "";
        war.attackerCapturedClaims.clear();
        war.capturedClaimsByNation.clear();
        war.originalClaimOwners.clear();
        war.coreClaimsByNation.clear();
        war.joinRequests.clear();
        war.defenseCalls.clear();
        war.peaceDeal = null;
        war.peaceOffers.clear();
        war.attackerSide.clear();
        war.defenderSide.clear();
        war.attackerSide.add(attacker.get().id);
        war.defenderSide.add(defender.get().id);
        war.defenderStartingClaims = 0;
        int seconds = NationCommands.warJustificationSeconds(attacker.get(), defender.get());
        war.justificationCompleteTick = NationStore.persistentNow() + (long)seconds * 20L;
        store.save();
        store.notifyNation(player.getServer(), defender.get(), NationText.tr("nationwars.command.war.justify.received", attacker.get().name));
        NationCommands.ok(player, "nationwars.command.war.justify.started", seconds);
        return 1;
    }

    public static int declareWar(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
        NationStore store = NationStore.get();
        Optional<NationStore.Nation> attacker = store.nationOf(player.getUUID());
        Optional<NationStore.Nation> defender = store.nationByName(StringArgumentType.getString(context, (String)"country"));
        if (attacker.isEmpty() || defender.isEmpty() || attacker.get().id.equals(defender.get().id)) {
            NationCommands.fail(player, "nationwars.command.war.target_missing");
            return 0;
        }
        if (!NationCommands.requireNationOwner(player, store, attacker.get())) {
            return 0;
        }
        if (!NationCommands.canStartHostilities(player, store, attacker.get(), defender.get())) {
            return 0;
        }
        if (attacker.get().doctrine().pacifist) {
            NationCommands.fail(player, "nationwars.command.war.declare.pacifist", NationText.doctrineName(attacker.get().doctrine()));
            return 0;
        }
        Optional<NationStore.War> maybeWar = store.warBetween(attacker.get(), defender.get());
        if (maybeWar.isEmpty() && NationWarsConfig.get().instantWar) {
            NationStore.War created = store.getOrCreateWar(attacker.get(), defender.get());
            created.attacker = attacker.get().id;
            created.defender = defender.get().id;
            created.active = false;
            created.pendingDefenderResponse = false;
            created.independenceWar = false;
            created.independencePuppet = "";
            created.attackerCapturedClaims.clear();
            created.capturedClaimsByNation.clear();
            created.originalClaimOwners.clear();
            created.coreClaimsByNation.clear();
            created.joinRequests.clear();
            created.defenseCalls.clear();
            created.peaceDeal = null;
            created.peaceOffers.clear();
            created.attackerSide.clear();
            created.defenderSide.clear();
            created.attackerSide.add(attacker.get().id);
            created.defenderSide.add(defender.get().id);
            created.defenderStartingClaims = 0;
            created.justificationCompleteTick = NationStore.persistentNow();
            maybeWar = Optional.of(created);
        }
        if (maybeWar.isEmpty() || !maybeWar.get().attacker.equals(attacker.get().id)) {
            NationCommands.fail(player, "nationwars.command.war.declare.justify_first");
            return 0;
        }
        NationStore.War war = maybeWar.get();
        if (war.active) {
            NationCommands.fail(player, "nationwars.command.war.already_active");
            return 0;
        }
        if (war.pendingDefenderResponse) {
            NationCommands.fail(player, "nationwars.command.war.awaiting_response");
            return 0;
        }
        MinecraftServer server = player.getServer();
        if (NationStore.persistentNow() < war.justificationCompleteTick) {
            long secondsLeft = Math.max(1L, (war.justificationCompleteTick - NationStore.persistentNow()) / 20L);
            NationCommands.fail(player, "nationwars.command.war.declare.not_ready", secondsLeft);
            return 0;
        }
        if (!NationCommands.hasOnlineMember(server, store, defender.get())) {
            NationCommands.fail(player, "nationwars.command.war.declare.target_offline");
            return 0;
        }
        if (NationCommands.canDefenderRejectWar(store, attacker.get(), defender.get())) {
            war.pendingDefenderResponse = true;
            war.active = false;
            store.save();
            store.notifyNation(server, defender.get(), NationText.message("nationwars.command.war.declare.rejectable_received", attacker.get().name, attacker.get().id));
            store.notifyNation(server, attacker.get(), NationText.message("nationwars.command.war.declare.awaiting", defender.get().name));
            return 1;
        }
        war.active = true;
        war.pendingDefenderResponse = false;
        war.defenderStartingClaims = store.claimCount(defender.get());
        store.snapshotWarCores(war, attacker.get());
        store.snapshotWarCores(war, defender.get());
        store.save();
        NationCommands.createAllianceDefenseCalls(server, store, war, defender.get());
        NationCommands.callGuarantors(server, store, war, defender.get());
        store.notifyNation(server, defender.get(), NationText.tr("nationwars.command.war.declare.received", attacker.get().name));
        store.notifyNation(server, attacker.get(), NationText.tr("nationwars.command.war.declare.success", defender.get().name));
        return 1;
    }

    public static int acceptWarDeclaration(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
        NationStore store = NationStore.get();
        Optional<NationStore.Nation> defender = store.nationOf(player.getUUID());
        Optional<NationStore.Nation> attacker = store.nationByName(StringArgumentType.getString(context, (String)"country"));
        if (defender.isEmpty() || attacker.isEmpty()) {
            NationCommands.fail(player, "nationwars.command.error.both_nations");
            return 0;
        }
        if (!NationCommands.requireNationOwner(player, store, defender.get())) {
            return 0;
        }
        Optional<NationStore.War> maybeWar = store.warBetween(attacker.get(), defender.get());
        if (maybeWar.isEmpty() || !maybeWar.get().pendingDefenderResponse || !maybeWar.get().defender.equals(defender.get().id)) {
            NationCommands.fail(player, "nationwars.command.war.declaration_missing");
            return 0;
        }
        NationStore.War war = maybeWar.get();
        war.active = true;
        war.pendingDefenderResponse = false;
        war.defenderStartingClaims = store.claimCount(defender.get());
        store.snapshotWarCores(war, attacker.get());
        store.snapshotWarCores(war, defender.get());
        store.save();
        NationCommands.createAllianceDefenseCalls(player.getServer(), store, war, defender.get());
        NationCommands.callGuarantors(player.getServer(), store, war, defender.get());
        store.notifyNation(player.getServer(), attacker.get(), NationText.message("nationwars.command.war.accept.attacker", defender.get().name));
        store.notifyNation(player.getServer(), defender.get(), NationText.message("nationwars.command.war.accept.defender", attacker.get().name));
        return 1;
    }

    public static int rejectWarDeclaration(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
        NationStore store = NationStore.get();
        Optional<NationStore.Nation> defender = store.nationOf(player.getUUID());
        Optional<NationStore.Nation> attacker = store.nationByName(StringArgumentType.getString(context, (String)"country"));
        if (defender.isEmpty() || attacker.isEmpty()) {
            NationCommands.fail(player, "nationwars.command.error.both_nations");
            return 0;
        }
        if (!NationCommands.requireNationOwner(player, store, defender.get())) {
            return 0;
        }
        Optional<NationStore.War> maybeWar = store.warBetween(attacker.get(), defender.get());
        if (maybeWar.isEmpty() || !maybeWar.get().pendingDefenderResponse || !maybeWar.get().defender.equals(defender.get().id)) {
            NationCommands.fail(player, "nationwars.command.war.declaration_missing");
            return 0;
        }
        if (attacker.get().doctrine().canRejectWarDeclarations) {
            store.recordWarDeclarationRejection(attacker.get(), defender.get());
        }
        store.endWar(maybeWar.get());
        store.notifyNation(player.getServer(), attacker.get(), NationText.message("nationwars.command.war.reject.attacker", defender.get().name));
        store.notifyNation(player.getServer(), defender.get(), NationText.message("nationwars.command.war.reject.defender"));
        return 1;
    }

    public static int requestWarJoin(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
        NationStore store = NationStore.get();
        Optional<NationStore.Nation> requester = store.nationOf(player.getUUID());
        Optional<NationStore.Nation> sponsor = store.nationByName(StringArgumentType.getString(context, (String)"country"));
        if (requester.isEmpty() || sponsor.isEmpty() || requester.get().id.equals(sponsor.get().id)) {
            NationCommands.fail(player, "nationwars.command.war.join.invalid_sponsor");
            return 0;
        }
        if (!NationCommands.requireNationOwner(player, store, requester.get())) {
            return 0;
        }
        List<NationStore.War> candidateWars = store.activeWarsOf(sponsor.get());
        NationStore.Nation selectedEnemy = null;
        try {
            String enemyName = StringArgumentType.getString(context, "enemy");
            selectedEnemy = store.nationByName(enemyName).orElse(null);
            if (selectedEnemy == null) {
                NationCommands.fail(player, "nationwars.command.war.join.enemy_missing");
                return 0;
            }
            NationStore.Nation enemy = selectedEnemy;
            candidateWars = candidateWars.stream().filter(war -> store.areOpposingWarSides(war, sponsor.get(), enemy)).toList();
        }
        catch (IllegalArgumentException ignored) {
            // The optional enemy argument was not supplied.
        }
        if (candidateWars.isEmpty()) {
            NationCommands.fail(player, "nationwars.command.war.join.sponsor_inactive", sponsor.get().name);
            return 0;
        }
        if (candidateWars.size() > 1) {
            String enemies = candidateWars.stream()
                .flatMap(war -> java.util.stream.Stream.concat(war.attackerSide.stream(), war.defenderSide.stream()))
                .map(store::nationById).flatMap(Optional::stream)
                .filter(nation -> store.activeWarForCapture(sponsor.get(), nation).isPresent())
                .map(nation -> nation.name).distinct().sorted(String::compareToIgnoreCase).collect(Collectors.joining(", "));
            NationCommands.fail(player, "nationwars.command.war.join.multiple", sponsor.get().name, sponsor.get().id, enemies);
            return 0;
        }
        if (!store.addWarJoinRequest(candidateWars.get(0), requester.get(), sponsor.get())) {
            NationCommands.fail(player, "nationwars.command.war.join.failed");
            return 0;
        }
        store.notifyNation(player.getServer(), sponsor.get(), NationText.message("nationwars.command.war.join.received", requester.get().name, requester.get().id));
        NationCommands.ok(player, "nationwars.command.war.join.sent", sponsor.get().name);
        return 1;
    }

    public static int acceptWarJoin(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
        NationStore store = NationStore.get();
        Optional<NationStore.Nation> acceptor = store.nationOf(player.getUUID());
        Optional<NationStore.Nation> requester = store.nationByName(StringArgumentType.getString(context, (String)"country"));
        if (acceptor.isEmpty() || requester.isEmpty()) {
            NationCommands.fail(player, "nationwars.command.error.both_nations");
            return 0;
        }
        if (!NationCommands.requireNationOwner(player, store, acceptor.get())) {
            return 0;
        }
        for (NationStore.War war : store.activeWarsOf(acceptor.get())) {
            if (!store.acceptWarJoinRequest(war, requester.get(), acceptor.get())) continue;
            store.notifyNation(player.getServer(), requester.get(), NationText.message("nationwars.command.war.join.accepted_requester", acceptor.get().name));
            store.notifyNation(player.getServer(), acceptor.get(), NationText.message("nationwars.command.war.join.accepted_sponsor", requester.get().name));
            return 1;
        }
        NationCommands.fail(player, "nationwars.command.war.join.request_missing");
        return 0;
    }

    public static int acceptAllianceDefense(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
        NationStore store = NationStore.get();
        Optional<NationStore.Nation> ally = store.nationOf(player.getUUID());
        Optional<NationStore.Nation> caller = store.nationByName(StringArgumentType.getString(context, (String)"country"));
        if (ally.isEmpty() || caller.isEmpty()) {
            NationCommands.fail(player, "nationwars.command.error.both_nations");
            return 0;
        }
        if (!NationCommands.requireNationOwner(player, store, ally.get())) {
            return 0;
        }
        for (NationStore.War war : store.activeWarsOf(caller.get())) {
            if (!store.acceptWarDefenseCall(war, ally.get(), caller.get())) continue;
            store.notifyNation(player.getServer(), caller.get(), NationText.message("nationwars.command.war.defense.accepted_caller", ally.get().name));
            store.notifyNation(player.getServer(), ally.get(), NationText.message("nationwars.command.war.defense.accepted_ally", caller.get().name));
            return 1;
        }
        NationCommands.fail(player, "nationwars.command.war.defense.missing");
        return 0;
    }

    public static int declineAllianceDefense(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
        NationStore store = NationStore.get();
        Optional<NationStore.Nation> ally = store.nationOf(player.getUUID());
        Optional<NationStore.Nation> caller = store.nationByName(StringArgumentType.getString(context, (String)"country"));
        if (ally.isEmpty() || caller.isEmpty()) {
            NationCommands.fail(player, "nationwars.command.error.both_nations");
            return 0;
        }
        if (!NationCommands.requireNationOwner(player, store, ally.get())) {
            return 0;
        }
        for (NationStore.War war : store.activeWarsOf(caller.get())) {
            if (!store.rejectWarDefenseCall(war, ally.get(), caller.get())) continue;
            double basePenalty = 250.0;
            double requestedPenalty = ally.get().doctrine() == Doctrine.FRENCH ? basePenalty * 3.0 : basePenalty;
            double penalty = requestedPenalty;
            ally.get().balance = NationStore.roundMoney(ally.get().balance - penalty);
            store.save();
            store.notifyNation(player.getServer(), ally.get(), NationText.message("nationwars.command.war.defense.declined_ally", NationStore.roundMoney(penalty), caller.get().name));
            store.notifyNation(player.getServer(), caller.get(), NationText.message("nationwars.command.war.defense.declined_caller", ally.get().name));
            return 1;
        }
        NationCommands.fail(player, "nationwars.command.war.defense.missing");
        return 0;
    }

    public static int rejectWarJoin(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
        NationStore store = NationStore.get();
        Optional<NationStore.Nation> rejector = store.nationOf(player.getUUID());
        Optional<NationStore.Nation> requester = store.nationByName(StringArgumentType.getString(context, (String)"country"));
        if (rejector.isEmpty() || requester.isEmpty()) {
            NationCommands.fail(player, "nationwars.command.error.both_nations");
            return 0;
        }
        if (!NationCommands.requireNationOwner(player, store, rejector.get())) {
            return 0;
        }
        for (NationStore.War war : store.activeWarsOf(rejector.get())) {
            if (!store.rejectWarJoinRequest(war, requester.get(), rejector.get())) continue;
            store.notifyNation(player.getServer(), requester.get(), NationText.message("nationwars.command.war.join.rejected_requester", rejector.get().name));
            NationCommands.ok(player, "nationwars.command.war.join.rejected", requester.get().name);
            return 1;
        }
        NationCommands.fail(player, "nationwars.command.war.join.request_missing");
        return 0;
    }

    public static int leaveWar(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
        NationStore store = NationStore.get();
        Optional<NationStore.Nation> own = store.nationOf(player.getUUID());
        Optional<NationStore.Nation> other = store.nationByName(StringArgumentType.getString(context, (String)"country"));
        if (own.isEmpty() || other.isEmpty()) {
            NationCommands.fail(player, "nationwars.command.error.both_nations");
            return 0;
        }
        if (!NationCommands.requireNationOwner(player, store, own.get())) {
            return 0;
        }
        if (!own.get().doctrine().canLeaveWarSafely) {
            NationCommands.fail(player, "nationwars.command.war.leave.romania_only");
            return 0;
        }
        String ideologyKey = other.get().doctrine().ideology.name();
        if (own.get().usedSpecialWarLeaveIdeologies.contains(ideologyKey)) {
            NationCommands.fail(player, "nationwars.command.war.leave.ideology_used", NationText.ideologyName(other.get().doctrine().ideology));
            return 0;
        }
        if (own.get().usedSpecialWarLeaveIdeologies.size() >= Ideology.values().length) {
            NationCommands.fail(player, "nationwars.command.war.leave.all_used");
            return 0;
        }
        long tick = NationStore.persistentNow();
        long cooldownUntil = own.get().lastSpecialWarLeaveTick + (long)ROMANIAN_WAR_LEAVE_COOLDOWN_SECONDS * 20L;
        if (own.get().lastSpecialWarLeaveTick > 0L && tick < cooldownUntil) {
            long secondsLeft = Math.max(1L, (cooldownUntil - tick) / 20L);
            NationCommands.fail(player, "nationwars.command.war.leave.cooldown", secondsLeft);
            return 0;
        }
        Optional<NationStore.War> maybeWar = store.activeWarForCapture(own.get(), other.get()).or(() -> store.activeWarForCapture((NationStore.Nation)other.get(), (NationStore.Nation)own.get()));
        if (maybeWar.isEmpty()) {
            NationCommands.fail(player, "nationwars.command.war.active_missing");
            return 0;
        }
        own.get().usedSpecialWarLeaveIdeologies.add(ideologyKey);
        own.get().lastSpecialWarLeaveTick = tick;
        store.leaveWarSafely(maybeWar.get(), own.get());
        store.save();
        boolean carolRemoved = own.get().usedSpecialWarLeaveIdeologies.size() >= Ideology.values().length;
        store.notifyNation(player.getServer(), own.get(), NationText.message(carolRemoved
            ? "nationwars.command.war.leave.success_final" : "nationwars.command.war.leave.success"));
        store.notifyNation(player.getServer(), other.get(), NationText.message("nationwars.command.war.leave.enemy", own.get().name));
        return 1;
    }

    private static int warOverview(CommandContext<CommandSourceStack> context) {
        ((CommandSourceStack)context.getSource()).sendSuccess(() -> NationText.tr("nationwars.command.war.overview.title"), false);
        ((CommandSourceStack)context.getSource()).sendSuccess(() -> NationText.tr("nationwars.command.war.overview.status"), false);
        ((CommandSourceStack)context.getSource()).sendSuccess(() -> NationText.tr("nationwars.command.war.overview.declare"), false);
        ((CommandSourceStack)context.getSource()).sendSuccess(() -> NationText.tr("nationwars.command.war.overview.join"), false);
        ((CommandSourceStack)context.getSource()).sendSuccess(() -> NationText.tr("nationwars.command.war.overview.defend"), false);
        ((CommandSourceStack)context.getSource()).sendSuccess(() -> NationText.tr("nationwars.command.war.overview.peace"), false);
        return 1;
    }

    public static int warStatus(CommandContext<CommandSourceStack> context) {
        NationStore store = NationStore.get();
        List<NationStore.War> wars = store.wars().stream().sorted(Comparator.comparing(war -> war.id)).toList();
        if (wars.isEmpty()) {
            ((CommandSourceStack)context.getSource()).sendSuccess(() -> NationText.tr("nationwars.command.war.status.empty"), false);
            return 1;
        }
        MinecraftServer server = ((CommandSourceStack)context.getSource()).getServer();
        for (NationStore.War war2 : wars) {
            String attacker = store.nationById(war2.attacker).map(nation -> nation.name).orElse(war2.attacker);
            String defender = store.nationById(war2.defender).map(nation -> nation.name).orElse(war2.defender);
            ((CommandSourceStack)context.getSource()).sendSuccess(() -> NationText.tr("nationwars.command.war.status.match", attacker, defender), false);
            if (war2.active) {
                ((CommandSourceStack)context.getSource()).sendSuccess(() -> NationText.tr("nationwars.command.war.status.active"), false);
                ((CommandSourceStack)context.getSource()).sendSuccess(() -> NationText.tr("nationwars.command.war.status.attackers", NationCommands.nationNames(store, war2.attackerSide)), false);
                ((CommandSourceStack)context.getSource()).sendSuccess(() -> NationText.tr("nationwars.command.war.status.defenders", NationCommands.nationNames(store, war2.defenderSide)), false);
                if (war2.joinRequests.isEmpty()) continue;
                ((CommandSourceStack)context.getSource()).sendSuccess(() -> NationText.tr("nationwars.command.war.status.pending", NationCommands.nationNames(store, war2.joinRequests.keySet())), false);
                continue;
            }
            if (war2.pendingDefenderResponse) {
                ((CommandSourceStack)context.getSource()).sendSuccess(() -> NationText.tr("nationwars.command.war.status.waiting"), false);
                continue;
            }
            long secondsLeft = Math.max(0L, (war2.justificationCompleteTick - NationStore.persistentNow()) / 20L);
            ((CommandSourceStack)context.getSource()).sendSuccess(() -> NationText.tr("nationwars.command.war.status.justifying", secondsLeft), false);
        }
        return 1;
    }

    private static void createAllianceDefenseCalls(MinecraftServer server, NationStore store, NationStore.War war, NationStore.Nation defender) {
        if (!NationWarsConfig.get().factions) {
            return;
        }
        store.allianceOf(defender).ifPresent(alliance -> {
            for (String allyId : alliance.members) {
                NationStore.Nation ally;
                if (allyId.equals(defender.id) || (ally = (NationStore.Nation)store.nationById(allyId).orElse(null)) == null || store.isWarParticipant(war, ally) || !store.addWarDefenseCall(war, ally, defender, "alliance")) continue;
                store.notifyNation(server, ally, NationText.message("nationwars.command.war.defense.called_alliance", defender.name, defender.id));
            }
        });
    }

    private static void callGuarantors(MinecraftServer server, NationStore store, NationStore.War war, NationStore.Nation defender) {
        if (!NationWarsConfig.get().guarantees) {
            return;
        }
        for (NationStore.Nation guarantor : store.guarantorsOf(defender)) {
            if (store.sameAlliance(guarantor, defender)) {
                continue;
            }
            if (store.isWarParticipant(war, guarantor) || !store.addWarDefenseCall(war, guarantor, defender, "guarantee")) {
                continue;
            }
            store.notifyNation(server, guarantor, NationText.message("nationwars.command.war.defense.called_guarantee", defender.name, defender.id));
            store.notifyNation(server, defender, NationText.message("nationwars.command.war.defense.guarantor_called", guarantor.name));
        }
    }

    public static int peace(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
        NationStore store = NationStore.get();
        Optional<NationStore.Nation> own = store.nationOf(player.getUUID());
        Optional<NationStore.Nation> other = store.nationByName(StringArgumentType.getString(context, (String)"country"));
        if (own.isEmpty() || other.isEmpty()) {
            NationCommands.fail(player, "nationwars.command.error.both_nations");
            return 0;
        }
        if (!NationCommands.requireNationOwner(player, store, own.get())) {
            return 0;
        }
        Optional<NationStore.War> maybeWar = store.activeWarForCapture(own.get(), other.get()).or(() -> store.activeWarForCapture((NationStore.Nation)other.get(), (NationStore.Nation)own.get()));
        if (maybeWar.isEmpty()) {
            NationCommands.fail(player, "nationwars.command.war.active_missing");
            return 0;
        }
        NationStore.War war = maybeWar.get();
        if (!(war.peaceDeal != null && own.get().id.equals(war.peaceDeal.receiver) || store.peaceCooldownUntil(own.get(), other.get()) <= NationStore.persistentNow())) {
            long secondsLeft = Math.max(1L, (store.peaceCooldownUntil(own.get(), other.get()) - NationStore.persistentNow()) / 20L);
            NationCommands.fail(player, "nationwars.command.peace.cooldown", secondsLeft);
            return 0;
        }
        player.openMenu((MenuProvider)new SimpleMenuProvider((containerId, inventory, viewer) -> new PeaceDealMenu(containerId, inventory, (NationStore.Nation)own.get(), (NationStore.Nation)other.get(), war), NationText.tr("nationwars.gui.peace.title")));
        return 1;
    }

    public static int rejectPeace(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
        NationStore store = NationStore.get();
        Optional<NationStore.Nation> own = store.nationOf(player.getUUID());
        Optional<NationStore.Nation> other = store.nationByName(StringArgumentType.getString(context, (String)"country"));
        if (own.isEmpty() || other.isEmpty()) {
            NationCommands.fail(player, "nationwars.command.error.both_nations");
            return 0;
        }
        if (!NationCommands.requireNationOwner(player, store, own.get())) {
            return 0;
        }
        Optional<NationStore.War> maybeWar = store.activeWarForCapture(own.get(), other.get()).or(() -> store.activeWarForCapture((NationStore.Nation)other.get(), (NationStore.Nation)own.get()));
        if (maybeWar.isEmpty()) {
            NationCommands.fail(player, "nationwars.command.war.active_missing");
            return 0;
        }
        NationStore.War war = maybeWar.get();
        if (war.peaceDeal == null || !own.get().id.equals(war.peaceDeal.receiver) || !other.get().id.equals(war.peaceDeal.proposer)) {
            NationCommands.fail(player, "nationwars.command.peace.incoming_missing");
            return 0;
        }
        store.clearPeaceDeal(war);
        store.setPeaceCooldown(other.get(), own.get(), NationStore.persistentNow() + 6000L);
        store.notifyNation(player.getServer(), other.get(), NationText.message("nationwars.command.peace.rejected_proposer", own.get().name, PEACE_REJECT_COOLDOWN_SECONDS));
        store.notifyNation(player.getServer(), own.get(), NationText.message("nationwars.command.peace.rejected_receiver", other.get().name));
        return 1;
    }

    public static int surrender(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
        NationStore store = NationStore.get();
        Optional<NationStore.Nation> own = store.nationOf(player.getUUID());
        Optional<NationStore.Nation> other = store.nationByName(StringArgumentType.getString(context, (String)"country"));
        if (own.isEmpty() || other.isEmpty()) {
            NationCommands.fail(player, "nationwars.command.error.both_nations");
            return 0;
        }
        if (!NationCommands.requireNationOwner(player, store, own.get())) {
            return 0;
        }
        Optional<NationStore.War> maybeWar = store.activeWarForCapture(own.get(), other.get()).or(() -> store.activeWarForCapture((NationStore.Nation)other.get(), (NationStore.Nation)own.get()));
        if (maybeWar.isEmpty()) {
            NationCommands.fail(player, "nationwars.command.war.active_missing");
            return 0;
        }
        NationStore.War war = maybeWar.get();
        int ownSide = store.sideOf(war, own.get());
        int otherSide = store.sideOf(war, other.get());
        if (ownSide == 0 || otherSide == 0 || ownSide == otherSide) {
            NationCommands.fail(player, "nationwars.command.surrender.not_enemy");
            return 0;
        }
        if (store.isIndependenceWar(war)) {
            NationStore.Nation puppet = store.nationById(war.independencePuppet).orElse(null);
            NationStore.Nation master = puppet == null ? null : store.masterOf(puppet).orElse(null);
            if (puppet == null || master == null) {
                NationCommands.fail(player, "nationwars.command.puppet.war.invalid");
                return 0;
            }
            boolean puppetWon = own.get().id.equals(master.id);
            NationStore.IndependenceResolution resolution = store.resolveIndependenceWar(war, puppetWon);
            if (!resolution.resolved()) {
                NationCommands.fail(player, "nationwars.command.puppet.war.invalid");
                return 0;
            }
            String resultKey = puppetWon ? "nationwars.puppet.war.puppet_victory" : "nationwars.puppet.war.master_victory";
            store.notifyNation(player.getServer(), puppet,
                NationText.message(resultKey, puppet.name, master.name, resolution.points(), resolution.lostWars()));
            store.notifyNation(player.getServer(), master,
                NationText.message(resultKey, puppet.name, master.name, resolution.points(), resolution.lostWars()));
            return 1;
        }
        if (!store.isPrimaryWarParticipant(war, own.get())) {
            return NationCommands.surrenderJoinedNation(player, store, war, own.get(), other.get());
        }
        if (!store.isPrimaryWarParticipant(war, other.get())) {
            NationCommands.fail(player, "nationwars.command.surrender.primary_only");
            return 0;
        }
        if (ownSide < 0) {
            Optional<String> claim;
            int capturedFromSurrenderingNation = store.capturedClaimsHeldBySideAgainst(war, otherSide, own.get());
            int startingClaims = war.defenderStartingClaims > 0 ? war.defenderStartingClaims : store.claimCount(own.get()) + capturedFromSurrenderingNation;
            int target = Math.max(1, (int)Math.ceil((double)startingClaims * 0.25 * own.get().doctrine().surrenderLandMultiplier));
            int alreadyCaptured = capturedFromSurrenderingNation;
            for (int transferred = alreadyCaptured; transferred < target
                && !(claim = store.borderClaimsOf(own.get()).stream().filter(id -> !id.equals(((NationStore.Nation)own.get()).capitalClaim))
                    .filter(id -> store.isCoreClaimForWar(war, own.get(), id))
                    .filter(id -> !store.isClaimCapturedInOtherActiveWar(war, id))
                    .findFirst().or(() -> store.claimsOf((NationStore.Nation)own.get()).stream()
                        .filter(id -> store.isCoreClaimForWar(war, own.get(), id))
                        .filter(id -> !store.isClaimCapturedInOtherActiveWar(war, id)).findFirst())).isEmpty(); ++transferred) {
                store.captureClaim(war, other.get(), own.get(), claim.get());
            }
            double treasuryShare = NationStore.roundMoney(own.get().balance * 0.5);
            own.get().balance = NationStore.roundMoney(own.get().balance - treasuryShare);
            other.get().balance = NationStore.roundMoney(other.get().balance + treasuryShare);
            double leaderMoney = 0.0;
            try {
                leaderMoney = store.confiscatePlayerMoneyForTransaction(UUID.fromString(own.get().owner));
                other.get().balance = NationStore.roundMoney(other.get().balance + leaderMoney);
            }
            catch (RuntimeException runtimeException) {
                // empty catch block
            }
            boolean deleteLoser = store.claimCount(own.get()) <= 0;
            store.endWar(war);
            store.notifyNation(player.getServer(), own.get(), NationText.tr("nationwars.command.surrender.loser", other.get().name, NationStore.roundMoney(treasuryShare + leaderMoney)));
            store.notifyNation(player.getServer(), other.get(), NationText.tr("nationwars.command.surrender.winner", own.get().name, NationStore.roundMoney(treasuryShare + leaderMoney)));
            if (deleteLoser) {
                store.notifyNation(player.getServer(), own.get(), NationText.message("nationwars.command.surrender.deleted"));
                store.deleteNation(own.get());
                NationEvents.refreshAllTabListNames(player.getServer());
            } else {
                store.save();
            }
            return 1;
        }
        if (ownSide > 0) {
            int returned = store.returnCapturedClaimsHeldBySide(war, ownSide, other.get());
            store.endWar(war);
            store.notifyNation(player.getServer(), own.get(), NationText.tr("nationwars.command.surrender.attacker", returned));
            store.notifyNation(player.getServer(), other.get(), NationText.tr("nationwars.command.surrender.attacker_enemy", own.get().name, returned));
            return 1;
        }
        NationCommands.fail(player, "nationwars.command.surrender.not_participant");
        return 0;
    }

    private static int surrenderJoinedNation(ServerPlayer player, NationStore store, NationStore.War war, NationStore.Nation own, NationStore.Nation other) {
        int returned = store.returnCapturedClaimsInvolving(war, own);
        if (!store.removeWarParticipant(war, own)) {
            NationCommands.fail(player, "nationwars.command.surrender.leave_failed");
            return 0;
        }
        store.notifyNation(player.getServer(), own, NationText.message("nationwars.command.surrender.joined", other.name, returned));
        store.notifyNation(player.getServer(), other, NationText.message("nationwars.command.surrender.joined_enemy", own.name, returned));
        return 1;
    }

    private static ClaimKey currentClaim(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        ChunkPos chunk = player.chunkPosition();
        return ClaimKey.of(level, chunk);
    }

    private static boolean validateNewClaimLocation(ServerPlayer player, ClaimKey claim) {
        ServerLevel level = player.serverLevel();
        NationWarsConfig config = NationWarsConfig.get();
        if (level.dimension().equals(Level.NETHER) && !config.claimNether) {
            NationCommands.fail(player, "nationwars.command.claim.nether_disabled");
            return false;
        }
        if (level.dimension().equals(Level.END) && !config.claimEnd) {
            NationCommands.fail(player, "nationwars.command.claim.end_disabled");
            return false;
        }
        if (level.dimension().equals(Level.OVERWORLD) && NationCommands.intersectsSpawnProtection(level, claim, config.spawnProtection)) {
            NationCommands.fail(player, "nationwars.command.claim.spawn_protection", config.spawnProtection);
            return false;
        }
        return true;
    }

    private static boolean intersectsSpawnProtection(ServerLevel level, ClaimKey claim, int radius) {
        if (radius <= 0) {
            return false;
        }
        BlockPos spawn = level.getSharedSpawnPos();
        int minX = claim.x() << 4;
        int minZ = claim.z() << 4;
        int maxX = minX + 15;
        int maxZ = minZ + 15;
        return maxX >= spawn.getX() - radius && minX <= spawn.getX() + radius
            && maxZ >= spawn.getZ() - radius && minZ <= spawn.getZ() + radius;
    }

    static boolean isNaturalCoastClaim(ServerLevel level, ClaimKey claim) {
        if (!level.dimension().equals(Level.OVERWORLD)) {
            return false;
        }
        int startX = (claim.x() << 4) - 8;
        int startZ = (claim.z() << 4) - 8;
        int endX = (claim.x() << 4) + 23;
        int endZ = (claim.z() << 4) + 23;
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        for (int x = startX; x <= endX; x += 4) {
            for (int z = startZ; z <= endZ; z += 4) {
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                position.set(x, y, z);
                String biome = level.getBiome((BlockPos)position).unwrapKey()
                    .map(key -> key.location().toString())
                    .orElse("");
                if (NATURAL_WATER_BIOMES.contains(biome)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean touchesNationClaim(NationStore store, NationStore.Nation nation, ClaimKey claim) {
        for (String owned : store.claimsOf(nation)) {
            if (store.isCapturedClaimHeldBy(nation, owned)) continue;
            if (!claim.touches(ClaimKey.parse(owned))) continue;
            return true;
        }
        return false;
    }

    private static Optional<UUID> resolveNationMember(ServerPlayer actor, NationStore store, NationStore.Nation nation, String memberNameOrUuid) {
        ServerPlayer online = actor.getServer().getPlayerList().getPlayerByName(memberNameOrUuid);
        if (online != null && store.isMember(online.getUUID(), nation)) {
            store.recordPlayerName(online);
            return Optional.of(online.getUUID());
        }
        return store.memberIdByName(nation, memberNameOrUuid);
    }

    private static double claimCost(NationStore store, NationStore.Nation nation, ClaimKey claim) {
        ClaimKey capital;
        int distanceChunks = 0;
        if (nation.doctrine().distanceClaimScaling && nation.capitalClaim != null && !nation.capitalClaim.isBlank() && (capital = ClaimKey.parse(nation.capitalClaim)).dimension().equals(claim.dimension())) {
            distanceChunks = Math.abs(capital.x() - claim.x()) + Math.abs(capital.z() - claim.z());
        }
        return EconomyService.claimCost(store.claimCount(nation), distanceChunks, nation.doctrine());
    }

    private static boolean canDefenderRejectWar(NationStore store, NationStore.Nation attacker, NationStore.Nation defender) {
        return attacker.doctrine().canRejectWarDeclarations && store.claimCount(defender) > store.claimCount(attacker) && !store.hasRejectedWarDeclaration(attacker, defender);
    }

    private static boolean canStartHostilities(ServerPlayer player, NationStore store, NationStore.Nation attacker, NationStore.Nation defender) {
        if (attacker == null || defender == null || attacker.id.equals(defender.id)) {
            NationCommands.fail(player, "nationwars.command.war.self");
            return false;
        }
        if (NationWarsConfig.get().puppets && store.isPuppet(attacker)) {
            NationCommands.fail(player, "nationwars.command.war.puppet_cannot_declare");
            return false;
        }
        if (NationWarsConfig.get().puppets && (store.isMasterOf(attacker, defender) || store.isMasterOf(defender, attacker))) {
            NationCommands.fail(player, "nationwars.command.war.master_puppet_forbidden");
            return false;
        }
        if (store.activeTruce(attacker, defender).isPresent()) {
            NationCommands.fail(player, "nationwars.command.war.truce_active", defender.name);
            return false;
        }
        if (store.activeWarRelationship(attacker, defender).isPresent()) {
            NationCommands.fail(player, "nationwars.command.war.same_active_war");
            return false;
        }
        if (store.sameAlliance(attacker, defender)) {
            NationCommands.fail(player, "nationwars.command.war.same_alliance");
            return false;
        }
        if (store.guarantees(attacker, defender) || store.guarantees(defender, attacker)) {
            NationCommands.fail(player, "nationwars.command.war.guarantee_active");
            return false;
        }
        return true;
    }

    static int warJustificationSeconds(NationStore.Nation attacker, NationStore.Nation defender) {
        return WarService.justificationSeconds(attacker.doctrine(), defender.doctrine());
    }

    private static double appraise(ItemStack stack) {
        double each = 0.2;
        if (stack.is(Items.DIAMOND)) {
            each = 30.0;
        } else if (stack.is(Items.EMERALD)) {
            each = 24.0;
        } else if (stack.is(Items.GOLD_INGOT)) {
            each = 10.0;
        } else if (stack.is(Items.IRON_INGOT)) {
            each = 5.0;
        } else if (stack.is(Items.COPPER_INGOT)) {
            each = 2.0;
        } else if (stack.is(Items.COAL)) {
            each = 1.0;
        } else if (stack.is(Items.WHEAT) || stack.is(Items.CARROT) || stack.is(Items.POTATO) || stack.is(Items.BEETROOT)) {
            each = 1.5;
        }
        return NationStore.roundMoney(each * (double)stack.getCount());
    }

    private static String nationNames(NationStore store, Iterable<String> ids) {
        ArrayList<String> names = new ArrayList<String>();
        for (String id : ids) {
            names.add(store.nationById(id).map(nation -> nation.name).orElse(id));
        }
        names.sort(String::compareToIgnoreCase);
        return String.join((CharSequence)",", names);
    }

    private static boolean requireNationOwner(ServerPlayer player, NationStore store, NationStore.Nation nation) {
        if (store.isOwner(player.getUUID(), nation)) {
            return true;
        }
        NationCommands.fail(player, "nationwars.command.error.owner_only");
        return false;
    }

    private static boolean canNationSpend(ServerPlayer player, NationStore store, NationStore.Nation nation, double amount) {
        long tick = NationStore.persistentNow();
        if (store.isSpendingBlocked(nation, tick)) {
            long seconds = Math.max(1L, (store.spendingBlockedUntil(nation) - tick + 19L) / 20L);
            NationCommands.fail(player, "nationwars.command.error.spending_blocked_time", seconds);
            return false;
        }
        if (nation.balance + 1.0E-4 < amount) {
            NationCommands.fail(player, "nationwars.command.error.treasury_needs", NationStore.roundMoney(amount));
            return false;
        }
        return true;
    }

    private static boolean hasOnlineMember(MinecraftServer server, NationStore store, NationStore.Nation nation) {
        return server.getPlayerList().getPlayers().stream().anyMatch(player -> store.isMember(player.getUUID(), nation));
    }

    private static void ok(ServerPlayer player, String key, Object... arguments) {
        player.sendSystemMessage(NationText.message(key, arguments));
    }

    private static void fail(ServerPlayer player, String key, Object... arguments) {
        player.sendSystemMessage(NationText.message(key, arguments));
    }

    private record PendingNationCreation(Doctrine doctrine, long expiresTick) {
    }
}
