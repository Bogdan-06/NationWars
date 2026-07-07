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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
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
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
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

    private NationCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("money").requires(source -> source.hasPermission(0)).executes(NationCommands::money));
        dispatcher.register(Commands.literal("market").requires(source -> source.hasPermission(0))
            .executes(NationCommands::openMarket)
            .then(Commands.literal("sellhand")
                .executes(context -> NationCommands.sellHand(context, -1.0))
                .then(Commands.argument("price", DoubleArgumentType.doubleArg(0.01))
                    .executes(context -> NationCommands.sellHand(context, DoubleArgumentType.getDouble(context, "price")))))
            .then(Commands.literal("cancel")
                .then(Commands.argument("id", IntegerArgumentType.integer(1)).executes(NationCommands::cancelListing))));
        dispatcher.register(Commands.literal("nations").requires(source -> source.hasPermission(0)).executes(NationCommands::nations));
        dispatcher.register(Commands.literal("nation").requires(source -> source.hasPermission(0))
            .then(Commands.literal("doctrines").executes(NationCommands::openDoctrinesMenu)
                .then(Commands.literal("list").executes(NationCommands::doctrines)))
            .then(Commands.literal("syncopac").requires(source -> source.hasPermission(2)).executes(NationCommands::syncOpac))
            .then(Commands.literal("create").executes(NationCommands::openNationCreateMenuUnnamed)
                .then(Commands.argument("name", StringArgumentType.word()).executes(NationCommands::openNationCreateMenu)
                    .then(Commands.argument("doctrine", StringArgumentType.word()).executes(NationCommands::createNationDirect))))
            .then(Commands.literal("join").then(Commands.argument("name", StringArgumentType.word()).executes(NationCommands::joinNation)))
            .then(Commands.literal("info").executes(NationCommands::ownNationInfo)
                .then(Commands.argument("name", StringArgumentType.word()).executes(NationCommands::nationInfo)))
            .then(Commands.literal("claim").executes(NationCommands::claim))
            .then(Commands.literal("buyclaim").executes(NationCommands::buyClaim)
                .then(Commands.literal("list").executes(NationCommands::buyClaimList))
                .then(Commands.literal("accept").then(Commands.argument("id", IntegerArgumentType.integer(1)).executes(NationCommands::buyClaimAccept)))
                .then(Commands.literal("reject").then(Commands.argument("id", IntegerArgumentType.integer(1)).executes(NationCommands::buyClaimReject))))
            .then(Commands.literal("unclaim").executes(NationCommands::unclaim))
            .then(Commands.literal("city").executes(NationCommands::buyCity))
            .then(Commands.literal("guarantee")
                .then(Commands.literal("remove").then(Commands.argument("country", StringArgumentType.word()).executes(NationCommands::removeGuarantee)))
                .then(Commands.argument("country", StringArgumentType.word()).executes(NationCommands::guarantee)))
            .then(Commands.literal("balance").executes(NationCommands::nationBalance))
            .then(Commands.literal("deposit").then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01)).executes(NationCommands::deposit))));
        dispatcher.register(Commands.literal("alliance").requires(source -> source.hasPermission(0))
            .then(Commands.literal("create").then(Commands.argument("name", StringArgumentType.word()).executes(NationCommands::allianceCreate)))
            .then(Commands.literal("invite").then(Commands.argument("country", StringArgumentType.word()).executes(NationCommands::allianceInvite)))
            .then(Commands.literal("accept").then(Commands.argument("name", StringArgumentType.word()).executes(NationCommands::allianceAccept)))
            .then(Commands.literal("kick").then(Commands.argument("country", StringArgumentType.word()).executes(NationCommands::allianceKick)))
            .then(Commands.literal("info").executes(NationCommands::allianceInfo)
                .then(Commands.argument("name", StringArgumentType.word()).executes(NationCommands::allianceInfoNamed))));
        dispatcher.register(Commands.literal("alliances").requires(source -> source.hasPermission(0)).executes(NationCommands::alliances));
        dispatcher.register(Commands.literal("wars").requires(source -> source.hasPermission(0)).executes(NationCommands::openWarMenu));
        dispatcher.register(Commands.literal("war").requires(source -> source.hasPermission(0))
            .executes(NationCommands::openWarMenu)
            .then(Commands.literal("justify").then(Commands.argument("country", StringArgumentType.word()).executes(NationCommands::justifyWar)))
            .then(Commands.literal("declare").then(Commands.argument("country", StringArgumentType.word()).executes(NationCommands::declareWar)))
            .then(Commands.literal("accept").then(Commands.argument("country", StringArgumentType.word()).executes(NationCommands::acceptWarDeclaration)))
            .then(Commands.literal("reject").then(Commands.argument("country", StringArgumentType.word()).executes(NationCommands::rejectWarDeclaration)))
            .then(Commands.literal("join").then(Commands.argument("country", StringArgumentType.word())
                .executes(NationCommands::requestWarJoin)
                .then(Commands.argument("enemy", StringArgumentType.word()).executes(NationCommands::requestWarJoin))))
            .then(Commands.literal("acceptjoin").then(Commands.argument("country", StringArgumentType.word()).executes(NationCommands::acceptWarJoin)))
            .then(Commands.literal("rejectjoin").then(Commands.argument("country", StringArgumentType.word()).executes(NationCommands::rejectWarJoin)))
            .then(Commands.literal("defend").then(Commands.argument("country", StringArgumentType.word()).executes(NationCommands::acceptAllianceDefense)))
            .then(Commands.literal("declinedefense").then(Commands.argument("country", StringArgumentType.word()).executes(NationCommands::declineAllianceDefense)))
            .then(Commands.literal("leave").then(Commands.argument("country", StringArgumentType.word()).executes(NationCommands::leaveWar)))
            .then(Commands.literal("status").executes(NationCommands::warStatus)));
        dispatcher.register(Commands.literal("peace").requires(source -> source.hasPermission(0))
            .then(Commands.literal("reject").then(Commands.argument("country", StringArgumentType.word()).executes(NationCommands::rejectPeace)))
            .then(Commands.argument("country", StringArgumentType.word()).executes(NationCommands::peace)));
        dispatcher.register(Commands.literal("surrender").requires(source -> source.hasPermission(0))
            .then(Commands.argument("country", StringArgumentType.word()).executes(NationCommands::surrender)));
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
                NationCommands.fail(player, "Nation creation timed out. Use /nation create to start again.");
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
            NationCommands.fail(player, "Nation creation timed out. Use /nation create to start again.");
            return;
        }
        String name = event.getRawText().trim();
        if (name.equalsIgnoreCase("cancel")) {
            PENDING_NATION_NAMES.remove(player.getUUID());
            NationCommands.fail(player, "Nation creation cancelled.");
            return;
        }
        if (NationCommands.createNationWithDoctrine(player, name, pending.doctrine)) {
            PENDING_NATION_NAMES.remove(player.getUUID());
            return;
        }
        NationCommands.fail(player, "Type another nation name, or type cancel.");
    }

    private static int openNationCreateMenuUnnamed(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
        NationStore store = NationStore.get();
        return NationCommands.openNationCreateMenu(player, store, "");
    }

    private static int openNationCreateMenu(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
        NationStore store = NationStore.get();
        return NationCommands.openNationCreateMenu(player, store, StringArgumentType.getString(context, (String)"name"));
    }

    private static int createNationDirect(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String doctrineId = StringArgumentType.getString(context, "doctrine");
        Optional<Doctrine> doctrine = Doctrine.byId(doctrineId);
        if (doctrine.isEmpty()) {
            NationCommands.fail(player, "Unknown doctrine '" + doctrineId + "'. Choices: " + Doctrine.choices() + ".");
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
            NationCommands.fail(player, "All doctrines are already taken.");
            return 0;
        }
        player.openMenu((MenuProvider)new SimpleMenuProvider((containerId, inventory, viewer) -> new NationCreateMenu(containerId, inventory, name), (Component)Component.literal((String)(name.isBlank() ? "Create Nation" : "Create " + name))));
        return 1;
    }

    static boolean createNationWithDoctrine(ServerPlayer player, String name, Doctrine doctrine) {
        NationStore store = NationStore.get();
        if (NationStore.nationKey(name).length() < 3) {
            NationCommands.fail(player, "Nation names need at least 3 letters or numbers.");
            return false;
        }
        if (!NationCommands.canStartNationCreation(player, store, name)) {
            return false;
        }
        if (store.isDoctrineTaken(doctrine)) {
            NationCommands.fail(player, doctrine.displayName + " is already taken by another nation.");
            return false;
        }
        ClaimKey capital = NationCommands.currentClaim(player);
        if (store.nationOwning(capital).isPresent()) {
            NationCommands.fail(player, "This chunk is already claimed.");
            return false;
        }
        if (!OpacClaimsBridge.canMirrorClaim(player.getServer(), capital, player.getUUID())) {
            NationCommands.fail(player, "Open Parties and Claims already has this chunk claimed. Unclaim it there first.");
            return false;
        }
        NationStore.Nation nation = store.createNation(player, name, doctrine, capital);
        PENDING_NATION_NAMES.remove(player.getUUID());
        NationEvents.refreshAllTabListNames(player.getServer());
        NationCommands.ok(player, "Created " + nation.name + " with " + doctrine.displayName + ". Command ID: " + nation.id + ". Capital: " + capital.shortName());
        return true;
    }

    static void requestNationName(ServerPlayer player, Doctrine doctrine) {
        NationStore store = NationStore.get();
        if (!NationCommands.canStartNationCreation(player, store, "")) {
            return;
        }
        if (store.isDoctrineTaken(doctrine)) {
            NationCommands.fail(player, doctrine.displayName + " is already taken by another nation.");
            return;
        }
        PENDING_NATION_NAMES.put(player.getUUID(), new PendingNationCreation(doctrine, (long)player.getServer().getTickCount() + 1200L));
        player.closeContainer();
        NationCommands.ok(player, "Selected " + doctrine.displayName + ". Type your nation name in chat, or type cancel. Spaces are display-only; commands use the generated ID. You have 60 seconds.");
    }

    private static boolean canStartNationCreation(ServerPlayer player, NationStore store, String name) {
        if (store.hasNation(player.getUUID())) {
            NationCommands.fail(player, "You are already in a nation.");
            return false;
        }
        if (!name.isBlank()) {
            String key = NationStore.nationKey(name);
            if (key.length() < 3) {
                NationCommands.fail(player, "Nation names need at least 3 letters or numbers.");
                return false;
            }
            if (store.nationByName(name).isPresent()) {
                NationCommands.fail(player, "That nation already exists.");
                return false;
            }
        }
        return true;
    }

    private static int joinNation(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
        NationStore store = NationStore.get();
        if (store.hasNation(player.getUUID())) {
            NationCommands.fail(player, "You are already in a nation.");
            return 0;
        }
        Optional<NationStore.Nation> nation = store.nationByName(StringArgumentType.getString(context, (String)"name"));
        if (nation.isEmpty()) {
            NationCommands.fail(player, "No nation with that name exists.");
            return 0;
        }
        store.addMember(player.getUUID(), nation.get());
        PENDING_NATION_NAMES.remove(player.getUUID());
        NationEvents.refreshAllTabListNames(player.getServer());
        NationCommands.ok(player, "Joined " + nation.get().name + ".");
        return 1;
    }

    private static int ownNationInfo(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
        Optional<NationStore.Nation> nation = NationStore.get().nationOf(player.getUUID());
        if (nation.isEmpty()) {
            NationCommands.fail(player, "You are not in a nation.");
            return 0;
        }
        return NationCommands.describeNation(player, nation.get());
    }

    private static int nationInfo(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
        Optional<NationStore.Nation> nation = NationStore.get().nationByName(StringArgumentType.getString(context, (String)"name"));
        if (nation.isEmpty()) {
            NationCommands.fail(player, "No nation with that name exists.");
            return 0;
        }
        return NationCommands.describeNation(player, nation.get());
    }

    private static int describeNation(ServerPlayer player, NationStore.Nation nation) {
        NationStore store = NationStore.get();
        Doctrine doctrine = nation.doctrine();
        player.sendSystemMessage((Component)Component.literal((String)("Nation: " + nation.name)));
        player.sendSystemMessage((Component)Component.literal((String)("Command ID: " + nation.id)));
        player.sendSystemMessage((Component)Component.literal((String)("Leader: " + nation.ownerName)));
        player.sendSystemMessage((Component)Component.literal((String)("Doctrine: " + doctrine.displayName + " (" + doctrine.id + ", " + doctrine.ideology.displayName + ")")));
        player.sendSystemMessage((Component)Component.literal((String)("Claims: " + store.claimCount(nation) + ", free claims left: " + nation.freeClaimsRemaining)));
        player.sendSystemMessage((Component)Component.literal((String)("Cities: " + nation.cityClaims.size())));
        player.sendSystemMessage((Component)Component.literal((String)("Treasury: $" + NationStore.roundMoney(nation.balance))));
        player.sendSystemMessage((Component)Component.literal((String)("Maintenance spending / 10min: $" + NationEvents.maintenanceDuePerInterval(store, nation))));
        player.sendSystemMessage((Component)Component.literal((String)("Passive income / 10min: $" + NationEvents.passiveIncomePerTenMinutes(store, nation))));
        store.allianceOf(nation).ifPresent(alliance -> player.sendSystemMessage((Component)Component.literal((String)("Alliance: " + alliance.name))));
        String guarantors = store.guarantorsOf(nation).stream().map(guarantor -> guarantor.name).collect(Collectors.joining(", "));
        player.sendSystemMessage(Component.literal("Guaranteed by: " + (guarantors.isBlank() ? "none" : guarantors)));
        player.sendSystemMessage((Component)Component.literal((String)("Capital: " + (nation.capitalClaim == null || nation.capitalClaim.isBlank() ? "none" : ClaimKey.parse(nation.capitalClaim).shortName()))));
        return 1;
    }

    private static int claim(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
        NationStore store = NationStore.get();
        Optional<NationStore.Nation> nation = store.nationOf(player.getUUID());
        if (nation.isEmpty()) {
            NationCommands.fail(player, "Create or join a nation first.");
            return 0;
        }
        ClaimKey claim = NationCommands.currentClaim(player);
        if (store.nationOwning(claim).isPresent()) {
            NationCommands.fail(player, "This chunk is already claimed.");
            return 0;
        }
        if (!OpacClaimsBridge.canMirrorClaim(player.getServer(), claim, UUID.fromString(nation.get().owner))) {
            NationCommands.fail(player, "Open Parties and Claims already has this chunk claimed. Unclaim it there first.");
            return 0;
        }
        if (store.claimCount(nation.get()) > 0 && !NationCommands.touchesNationClaim(store, nation.get(), claim)) {
            NationCommands.fail(player, "New claims must touch one of your nation's chunks.");
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
        NationCommands.ok(player, "Claimed " + claim.shortName() + ".");
        return 1;
    }

    private static int buyClaim(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
        NationStore store = NationStore.get();
        Optional<NationStore.Nation> buyer = store.nationOf(player.getUUID());
        if (buyer.isEmpty()) {
            NationCommands.fail(player, "Create or join a nation first.");
            return 0;
        }
        if (!store.isOwner(player.getUUID(), buyer.get())) {
            NationCommands.fail(player, "Only the nation owner can buy land for the nation.");
            return 0;
        }
        ClaimKey claim = NationCommands.currentClaim(player);
        Optional<NationStore.Nation> seller = store.nationOwning(claim);
        if (seller.isEmpty() || seller.get().id.equals(buyer.get().id)) {
            NationCommands.fail(player, "Stand in another nation's claim to buy it.");
            return 0;
        }
        if (claim.id().equals(seller.get().capitalClaim)) {
            NationCommands.fail(player, "You cannot buy another nation's capital.");
            return 0;
        }
        if (!NationCommands.touchesNationClaim(store, buyer.get(), claim)) {
            NationCommands.fail(player, "The claim you buy must border one of your claims.");
            return 0;
        }
        if (store.activeWarForCapture(buyer.get(), seller.get()).isPresent() || store.activeWarForCapture(seller.get(), buyer.get()).isPresent()) {
            NationCommands.fail(player, "You cannot buy land from a nation you are fighting.");
            return 0;
        }
        double cost = NationStore.roundMoney(Math.max(250.0, NationCommands.claimCost(store, buyer.get(), claim) * 2.0));
        if (!NationCommands.canNationSpend(player, store, buyer.get(), cost)) {
            return 0;
        }
        Optional<NationStore.LandPurchaseOffer> existing = store.landPurchaseOfferForClaim(buyer.get().id, seller.get().id, claim.id());
        if (existing.isPresent()) {
            NationCommands.fail(player, "Your nation already has a pending offer #" + existing.get().id + " for this claim.");
            return 0;
        }
        NationStore.LandPurchaseOffer offer = store.createLandPurchaseOffer(buyer.get(), seller.get(), claim.id(), cost, player, player.getServer().getTickCount());
        store.notifyNation(player.getServer(), seller.get(), (Component)Component.literal((String)("[NationWars] " + buyer.get().name + " offered $" + cost + " for " + claim.shortName() + ". Use /nation buyclaim accept " + offer.id + " or /nation buyclaim reject " + offer.id + ".")));
        NationCommands.ok(player, "Sent land purchase offer #" + offer.id + " to " + seller.get().name + " for " + claim.shortName() + " ($" + cost + ").");
        return 1;
    }

    private static int buyClaimList(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
        NationStore store = NationStore.get();
        Optional<NationStore.Nation> nation = store.nationOf(player.getUUID());
        if (nation.isEmpty()) {
            NationCommands.fail(player, "Create or join a nation first.");
            return 0;
        }
        List<NationStore.LandPurchaseOffer> offers = store.landPurchaseOffersFor(nation.get());
        if (offers.isEmpty()) {
            NationCommands.ok(player, "No pending land purchase offers.");
            return 1;
        }
        player.sendSystemMessage((Component)Component.literal((String)"[NationWars] Pending land purchase offers:"));
        for (NationStore.LandPurchaseOffer offer : offers) {
            player.sendSystemMessage((Component)Component.literal((String)("  " + NationCommands.describeOffer(store, offer, nation.get()))));
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
            NationCommands.fail(player, "No pending land purchase offer with that ID.");
            return 0;
        }
        NationStore.LandPurchaseOffer offer = maybeOffer.get();
        NationStore.Nation seller = store.nationById(offer.seller).orElse(null);
        NationStore.Nation buyer = store.nationById(offer.buyer).orElse(null);
        if (seller == null || buyer == null) {
            store.removeLandPurchaseOffer(offer);
            NationCommands.fail(player, "That land purchase offer is stale and was removed.");
            return 0;
        }
        if (!store.isOwner(player.getUUID(), seller)) {
            NationCommands.fail(player, "Only the selling nation owner can accept this offer.");
            return 0;
        }
        try {
            claim = ClaimKey.parse(offer.claimId);
        }
        catch (RuntimeException exception) {
            store.removeLandPurchaseOffer(offer);
            NationCommands.fail(player, "That land purchase offer had an invalid claim and was removed.");
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
        store.notifyNation(player.getServer(), buyer, (Component)Component.literal((String)("[NationWars] " + seller.name + " accepted your offer for " + claim.shortName() + ". Paid $" + offer.price + ".")));
        store.notifyNation(player.getServer(), seller, (Component)Component.literal((String)("[NationWars] Accepted " + buyer.name + "'s offer for " + claim.shortName() + ". Received $" + offer.price + ".")));
        return 1;
    }

    private static int buyClaimReject(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
        int id = IntegerArgumentType.getInteger(context, (String)"id");
        NationStore store = NationStore.get();
        Optional<NationStore.LandPurchaseOffer> maybeOffer = store.landPurchaseOffer(id);
        if (maybeOffer.isEmpty()) {
            NationCommands.fail(player, "No pending land purchase offer with that ID.");
            return 0;
        }
        NationStore.LandPurchaseOffer offer = maybeOffer.get();
        NationStore.Nation seller = store.nationById(offer.seller).orElse(null);
        NationStore.Nation buyer = store.nationById(offer.buyer).orElse(null);
        if (seller == null || buyer == null) {
            store.removeLandPurchaseOffer(offer);
            NationCommands.fail(player, "That land purchase offer is stale and was removed.");
            return 0;
        }
        boolean sellerOwner = store.isOwner(player.getUUID(), seller);
        boolean buyerOwner = store.isOwner(player.getUUID(), buyer);
        if (!sellerOwner && !buyerOwner) {
            NationCommands.fail(player, "Only the buying or selling nation owner can reject this offer.");
            return 0;
        }
        ClaimKey claim = ClaimKey.parse(offer.claimId);
        store.removeLandPurchaseOffer(offer);
        if (sellerOwner) {
            store.notifyNation(player.getServer(), buyer, (Component)Component.literal((String)("[NationWars] " + seller.name + " rejected your offer for " + claim.shortName() + ".")));
            NationCommands.ok(player, "Rejected land purchase offer #" + offer.id + ".");
        } else {
            store.notifyNation(player.getServer(), seller, (Component)Component.literal((String)("[NationWars] " + buyer.name + " cancelled their offer for " + claim.shortName() + ".")));
            NationCommands.ok(player, "Cancelled land purchase offer #" + offer.id + ".");
        }
        return 1;
    }

    private static boolean validateLandPurchaseOffer(ServerPlayer player, NationStore store, NationStore.LandPurchaseOffer offer, NationStore.Nation buyer, NationStore.Nation seller, ClaimKey claim, boolean removeStale) {
        if (!seller.id.equals(store.nationOwning(claim).map(owned -> owned.id).orElse(""))) {
            if (removeStale) {
                store.removeLandPurchaseOffer(offer);
            }
            NationCommands.fail(player, "That claim is no longer owned by the selling nation. Offer removed.");
            return false;
        }
        if (claim.id().equals(seller.capitalClaim)) {
            if (removeStale) {
                store.removeLandPurchaseOffer(offer);
            }
            NationCommands.fail(player, "That claim is now the seller's capital. Offer removed.");
            return false;
        }
        if (!NationCommands.touchesNationClaim(store, buyer, claim)) {
            NationCommands.fail(player, "The offered claim no longer borders the buying nation.");
            return false;
        }
        if (store.activeWarForCapture(buyer, seller).isPresent() || store.activeWarForCapture(seller, buyer).isPresent()) {
            NationCommands.fail(player, "The buying and selling nations are currently at war.");
            return false;
        }
        if (buyer.balance + 1.0E-4 < offer.price) {
            NationCommands.fail(player, buyer.name + " no longer has enough treasury money for this offer.");
            return false;
        }
        if (store.isSpendingBlocked(buyer, NationStore.persistentNow())) {
            NationCommands.fail(player, buyer.name + " cannot spend treasury money while its capital is infiltrated.");
            return false;
        }
        return true;
    }

    private static String describeOffer(NationStore store, NationStore.LandPurchaseOffer offer, NationStore.Nation viewerNation) {
        String buyer = store.nationById(offer.buyer).map(nation -> nation.name).orElse(offer.buyer);
        String seller = store.nationById(offer.seller).map(nation -> nation.name).orElse(offer.seller);
        String claim = ClaimKey.parse(offer.claimId).shortName();
        String side = viewerNation.id.equals(offer.seller) ? "incoming" : "outgoing";
        return "#" + offer.id + " " + side + ": " + buyer + " offers $" + offer.price + " to " + seller + " for " + claim;
    }

    private static int buyCity(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
        NationStore store = NationStore.get();
        Optional<NationStore.Nation> nation = store.nationOf(player.getUUID());
        if (nation.isEmpty()) {
            NationCommands.fail(player, "Create or join a nation first.");
            return 0;
        }
        if (!store.isOwner(player.getUUID(), nation.get())) {
            NationCommands.fail(player, "Only the nation owner can buy city income claims.");
            return 0;
        }
        if (!nation.get().doctrine().canBuyCities) {
            NationCommands.fail(player, "Only the United States doctrine can buy city income claims.");
            return 0;
        }
        ClaimKey claim = NationCommands.currentClaim(player);
        if (!nation.get().id.equals(store.nationOwning(claim).map(owned -> owned.id).orElse(""))) {
            NationCommands.fail(player, "Stand in one of your nation's claims.");
            return 0;
        }
        double cityCost = NationCommands.claimCost(store, nation.get(), claim);
        if (!NationCommands.canNationSpend(player, store, nation.get(), cityCost)) {
            return 0;
        }
        if (!store.addCityClaim(nation.get(), claim.id(), cityCost)) {
            NationCommands.fail(player, "This claim is already a city or your treasury needs $" + NationStore.roundMoney(cityCost) + ".");
            return 0;
        }
        NationCommands.ok(player, "City income enabled for " + claim.shortName() + " for $" + NationStore.roundMoney(cityCost) + ".");
        return 1;
    }

    private static int guarantee(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        NationStore store = NationStore.get();
        NationStore.Nation guarantor = store.nationOf(player.getUUID()).orElse(null);
        NationStore.Nation target = store.nationByName(StringArgumentType.getString(context, "country")).orElse(null);
        if (guarantor == null || target == null || guarantor.id.equals(target.id)) {
            NationCommands.fail(player, "Pick another existing country.");
            return 0;
        }
        if (!NationCommands.requireNationOwner(player, store, guarantor)) {
            return 0;
        }
        if (store.sameAlliance(guarantor, target)) {
            NationCommands.fail(player, "Guarantees are for countries outside your faction.");
            return 0;
        }
        if (!store.addGuarantee(guarantor, target)) {
            NationCommands.fail(player, "Your nation already guarantees " + target.name + ".");
            return 0;
        }
        store.notifyNation(player.getServer(), target, Component.literal("[NationWars] " + guarantor.name + " now guarantees your independence."));
        NationCommands.ok(player, "You now guarantee " + target.name + ".");
        return 1;
    }

    private static int removeGuarantee(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        NationStore store = NationStore.get();
        NationStore.Nation guarantor = store.nationOf(player.getUUID()).orElse(null);
        NationStore.Nation target = store.nationByName(StringArgumentType.getString(context, "country")).orElse(null);
        if (guarantor == null || target == null || !NationCommands.requireNationOwner(player, store, guarantor)) {
            return 0;
        }
        if (!store.removeGuarantee(guarantor, target)) {
            NationCommands.fail(player, "Your nation does not guarantee " + target.name + ".");
            return 0;
        }
        store.notifyNation(player.getServer(), target, Component.literal("[NationWars] " + guarantor.name + " withdrew its guarantee."));
        NationCommands.ok(player, "Guarantee withdrawn from " + target.name + ".");
        return 1;
    }

    private static int unclaim(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
        NationStore store = NationStore.get();
        Optional<NationStore.Nation> nation = store.nationOf(player.getUUID());
        if (nation.isEmpty()) {
            NationCommands.fail(player, "Create or join a nation first.");
            return 0;
        }
        if (!store.isOwner(player.getUUID(), nation.get())) {
            NationCommands.fail(player, "Only the nation owner can unclaim chunks.");
            return 0;
        }
        ClaimKey claim = NationCommands.currentClaim(player);
        if (!store.unclaim(nation.get(), claim)) {
            NationCommands.fail(player, "This is not your claim, or it is your capital.");
            return 0;
        }
        NationCommands.ok(player, "Unclaimed " + claim.shortName() + ".");
        return 1;
    }

    private static int nationBalance(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
        NationStore store = NationStore.get();
        Optional<NationStore.Nation> nation = store.nationOf(player.getUUID());
        if (nation.isEmpty()) {
            NationCommands.fail(player, "You are not in a nation.");
            return 0;
        }
        NationCommands.ok(player, "Player: $" + store.playerBalance(player.getUUID()) + " | " + nation.get().name + ": $" + NationStore.roundMoney(nation.get().balance));
        return 1;
    }

    private static int deposit(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
        NationStore store = NationStore.get();
        Optional<NationStore.Nation> nation = store.nationOf(player.getUUID());
        if (nation.isEmpty()) {
            NationCommands.fail(player, "You are not in a nation.");
            return 0;
        }
        double amount = DoubleArgumentType.getDouble(context, (String)"amount");
        if (!store.depositToNation(player.getUUID(), nation.get(), amount)) {
            NationCommands.fail(player, "You do not have enough money.");
            return 0;
        }
        NationCommands.ok(player, "Deposited $" + NationStore.roundMoney(amount) + " to " + nation.get().name + ".");
        return 1;
    }

    private static int doctrines(CommandContext<CommandSourceStack> context) {
        ((CommandSourceStack)context.getSource()).sendSuccess(() -> Component.literal((String)("Doctrines: " + Doctrine.choices())), false);
        for (Doctrine doctrine : Doctrine.values()) {
            String header = doctrine.id + " - " + doctrine.displayName + " (" + doctrine.ideology.displayName + ")";
            String stats = "  free " + doctrine.freeClaims + " | claim x" + doctrine.claimCostMultiplier + " | maint x" + doctrine.maintenanceMultiplier + " | cap " + doctrine.captureSeconds + "s";
            ((CommandSourceStack)context.getSource()).sendSuccess(() -> Component.literal((String)header), false);
            ((CommandSourceStack)context.getSource()).sendSuccess(() -> Component.literal((String)stats), false);
            for (String perk : doctrine.perkLore()) {
                ((CommandSourceStack)context.getSource()).sendSuccess(() -> Component.literal((String)("  " + perk)), false);
            }
        }
        return 1;
    }

    private static int openDoctrinesMenu(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
        player.openMenu((MenuProvider)new SimpleMenuProvider((containerId, inventory, viewer) -> new DoctrineMenu(containerId, inventory), (Component)Component.literal((String)"Doctrines")));
        return 1;
    }

    private static int syncOpac(CommandContext<CommandSourceStack> context) {
        OpacClaimsBridge.activatePrimaryPartySystem(((CommandSourceStack)context.getSource()).getServer());
        OpacClaimsBridge.syncAll(((CommandSourceStack)context.getSource()).getServer(), NationStore.get());
        NationEvents.refreshAllTabListNames(((CommandSourceStack)context.getSource()).getServer());
        ((CommandSourceStack)context.getSource()).sendSuccess(() -> Component.literal((String)"NationWars claims synced to Open Parties and Claims."), true);
        return 1;
    }

    private static int money(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
        NationCommands.ok(player, "Balance: $" + NationStore.get().playerBalance(player.getUUID()));
        return 1;
    }

    private static int openMarket(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
        player.openMenu((MenuProvider)new SimpleMenuProvider((containerId, inventory, viewer) -> new MarketMenu(containerId, inventory), (Component)Component.literal((String)"Market")));
        return 1;
    }

    private static int openWarMenu(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
        player.openMenu((MenuProvider)new SimpleMenuProvider((containerId, inventory, viewer) -> new WarMenu(containerId, inventory), (Component)Component.literal((String)"Wars")));
        return 1;
    }

    private static int sellHand(CommandContext<CommandSourceStack> context, double requestedPrice) throws CommandSyntaxException {
        double price;
        ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) {
            NationCommands.fail(player, "Hold an item to sell it.");
            return 0;
        }
        double d = price = requestedPrice > 0.0 ? requestedPrice : NationCommands.appraise(stack);
        if (price <= 0.0) {
            NationCommands.fail(player, "Set a price with /market sellhand <price>.");
            return 0;
        }
        int count = stack.getCount();
        String itemName = stack.getHoverName().getString();
        ItemStack listed = stack.copy();
        stack.setCount(0);
        NationStore.MarketListing listing = NationStore.get().createMarketListing(player, listed, price);
        NationCommands.ok(player, "Listed " + count + "x " + itemName + " for $" + NationStore.roundMoney(price) + " as listing #" + listing.id + ".");
        return 1;
    }

    private static int cancelListing(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        int id;
        ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
        NationStore store = NationStore.get();
        NationStore.MarketListing listing = store.marketListing(id = IntegerArgumentType.getInteger(context, (String)"id")).orElse(null);
        if (listing == null) {
            NationCommands.fail(player, "No listing with that id exists.");
            return 0;
        }
        if (!listing.seller.equals(player.getUUID().toString())) {
            NationCommands.fail(player, "You can only cancel your own listings.");
            return 0;
        }
        ItemStack stack = store.listingStack(listing, (HolderLookup.Provider)player.registryAccess());
        store.removeMarketListing(id);
        if (!stack.isEmpty()) {
            player.getInventory().placeItemBackInInventory(stack);
        }
        NationCommands.ok(player, "Canceled listing #" + id + ".");
        return 1;
    }

    private static int nations(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
        player.openMenu((MenuProvider)new SimpleMenuProvider((containerId, inventory, viewer) -> new NationsMenu(containerId, inventory), (Component)Component.literal((String)"Nations")));
        return 1;
    }

    private static int allianceCreate(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
        NationStore store = NationStore.get();
        Optional<NationStore.Nation> nation = store.nationOf(player.getUUID());
        if (nation.isEmpty() || !store.isOwner(player.getUUID(), nation.get())) {
            NationCommands.fail(player, "Only a nation owner can create an alliance.");
            return 0;
        }
        String name = StringArgumentType.getString(context, (String)"name");
        if (!store.createAlliance(nation.get(), name)) {
            NationCommands.fail(player, "Could not create alliance. You may already be in one, or that name is taken.");
            return 0;
        }
        NationCommands.ok(player, "Created alliance " + name + ".");
        return 1;
    }

    private static int allianceInvite(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
        NationStore store = NationStore.get();
        Optional<NationStore.Nation> inviter = store.nationOf(player.getUUID());
        Optional<NationStore.Nation> invited = store.nationByName(StringArgumentType.getString(context, (String)"country"));
        if (inviter.isEmpty() || invited.isEmpty()) {
            NationCommands.fail(player, "Both sides must be nations.");
            return 0;
        }
        if (!NationCommands.requireNationOwner(player, store, inviter.get())) {
            return 0;
        }
        Optional<NationStore.Alliance> alliance = store.allianceOf(inviter.get());
        if (alliance.isEmpty()) {
            NationCommands.fail(player, "Your nation is not in an alliance.");
            return 0;
        }
        if (!store.inviteToAlliance(alliance.get(), inviter.get(), invited.get())) {
            NationCommands.fail(player, "Could not invite that nation.");
            return 0;
        }
        store.notifyNation(player.getServer(), invited.get(), (Component)Component.literal((String)("[NationWars] " + inviter.get().name + " invited you to alliance " + alliance.get().name + ". Use /alliance accept " + alliance.get().id + ".")));
        NationCommands.ok(player, "Alliance invite sent to " + invited.get().name + ".");
        return 1;
    }

    private static int allianceAccept(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
        NationStore store = NationStore.get();
        Optional<NationStore.Nation> nation = store.nationOf(player.getUUID());
        Optional<NationStore.Alliance> alliance = store.allianceByName(StringArgumentType.getString(context, (String)"name"));
        if (nation.isEmpty() || alliance.isEmpty()) {
            NationCommands.fail(player, "No matching nation or alliance.");
            return 0;
        }
        if (!store.isOwner(player.getUUID(), nation.get())) {
            NationCommands.fail(player, "Only the nation owner can accept alliance invites.");
            return 0;
        }
        if (!store.acceptAllianceInvite(alliance.get(), nation.get())) {
            NationCommands.fail(player, "No invite from that alliance.");
            return 0;
        }
        store.notifyNation(player.getServer(), nation.get(), (Component)Component.literal((String)("[NationWars] Joined alliance " + alliance.get().name + ".")));
        return 1;
    }

    private static int allianceKick(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
        NationStore store = NationStore.get();
        Optional<NationStore.Nation> actor = store.nationOf(player.getUUID());
        Optional<NationStore.Nation> kicked = store.nationByName(StringArgumentType.getString(context, (String)"country"));
        if (actor.isEmpty() || kicked.isEmpty()) {
            NationCommands.fail(player, "Both sides must be nations.");
            return 0;
        }
        if (!NationCommands.requireNationOwner(player, store, actor.get())) {
            return 0;
        }
        Optional<NationStore.Alliance> alliance = store.allianceOf(actor.get());
        if (alliance.isEmpty() || !store.kickFromAlliance(alliance.get(), actor.get(), kicked.get())) {
            NationCommands.fail(player, "Only the alliance leader can kick member nations.");
            return 0;
        }
        store.notifyNation(player.getServer(), kicked.get(), (Component)Component.literal((String)("[NationWars] You were kicked from alliance " + alliance.get().name + ".")));
        NationCommands.ok(player, "Kicked " + kicked.get().name + " from " + alliance.get().name + ".");
        return 1;
    }

    private static int allianceInfo(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
        Optional<NationStore.Nation> nation = NationStore.get().nationOf(player.getUUID());
        if (nation.isEmpty()) {
            NationCommands.fail(player, "You are not in a nation.");
            return 0;
        }
        Optional<NationStore.Alliance> alliance = NationStore.get().allianceOf(nation.get());
        if (alliance.isEmpty()) {
            NationCommands.fail(player, "Your nation is not in an alliance.");
            return 0;
        }
        return NationCommands.describeAlliance((CommandSourceStack)context.getSource(), alliance.get());
    }

    private static int allianceInfoNamed(CommandContext<CommandSourceStack> context) {
        Optional<NationStore.Alliance> alliance = NationStore.get().allianceByName(StringArgumentType.getString(context, (String)"name"));
        if (alliance.isEmpty()) {
            ((CommandSourceStack)context.getSource()).sendFailure((Component)Component.literal((String)"No alliance with that name exists."));
            return 0;
        }
        return NationCommands.describeAlliance((CommandSourceStack)context.getSource(), alliance.get());
    }

    private static int alliances(CommandContext<CommandSourceStack> context) {
        NationStore store = NationStore.get();
        if (store.alliances().isEmpty()) {
            ((CommandSourceStack)context.getSource()).sendSuccess(() -> Component.literal((String)"No alliances exist yet."), false);
            return 1;
        }
        for (NationStore.Alliance alliance : store.alliances()) {
            ((CommandSourceStack)context.getSource()).sendSuccess(() -> Component.literal((String)(alliance.name + " | members: " + alliance.members.size())), false);
        }
        return 1;
    }

    private static int describeAlliance(CommandSourceStack source, NationStore.Alliance alliance) {
        NationStore store = NationStore.get();
        String members = alliance.members.stream().map(id -> store.nationById((String)id).map(nation -> nation.name).orElse((String)id)).sorted().collect(Collectors.joining(", "));
        source.sendSuccess(() -> Component.literal((String)("Alliance: " + alliance.name)), false);
        source.sendSuccess(() -> Component.literal((String)("Leader: " + store.nationById(alliance.leader).map(nation -> nation.name).orElse(alliance.leader))), false);
        source.sendSuccess(() -> Component.literal((String)("Members: " + members)), false);
        return 1;
    }

    private static int justifyWar(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
        NationStore store = NationStore.get();
        Optional<NationStore.Nation> attacker = store.nationOf(player.getUUID());
        Optional<NationStore.Nation> defender = store.nationByName(StringArgumentType.getString(context, (String)"country"));
        if (attacker.isEmpty() || defender.isEmpty() || attacker.get().id.equals(defender.get().id)) {
            NationCommands.fail(player, "Pick another existing nation.");
            return 0;
        }
        if (!NationCommands.requireNationOwner(player, store, attacker.get())) {
            return 0;
        }
        if (attacker.get().doctrine().pacifist) {
            NationCommands.fail(player, attacker.get().doctrine().displayName + " cannot justify wars. Join ongoing conflicts instead.");
            return 0;
        }
        Optional<NationStore.War> existing = store.warBetween(attacker.get(), defender.get());
        if (existing.isPresent()) {
            NationStore.War existingWar = existing.get();
            if (existingWar.active) {
                NationCommands.fail(player, "That war is already active.");
                return 0;
            }
            if (existingWar.pendingDefenderResponse) {
                NationCommands.fail(player, "That war declaration is already waiting for a response.");
                return 0;
            }
            if (attacker.get().id.equals(existingWar.attacker)) {
                NationCommands.fail(player, "You are already justifying this war. Use /war declare " + defender.get().id + " when it is ready.");
            } else {
                NationCommands.fail(player, defender.get().name + " is already justifying a war against you.");
            }
            return 0;
        }
        NationStore.War war = store.getOrCreateWar(attacker.get(), defender.get());
        war.attacker = attacker.get().id;
        war.defender = defender.get().id;
        war.active = false;
        war.pendingDefenderResponse = false;
        war.attackerCapturedClaims.clear();
        war.capturedClaimsByNation.clear();
        war.peaceOffers.clear();
        war.attackerSide.clear();
        war.defenderSide.clear();
        war.attackerSide.add(attacker.get().id);
        war.defenderSide.add(defender.get().id);
        war.defenderStartingClaims = 0;
        int seconds = NationCommands.warJustificationSeconds(attacker.get(), defender.get());
        war.justificationCompleteTick = NationStore.persistentNow() + (long)seconds * 20L;
        store.save();
        store.notifyNation(player.getServer(), defender.get(), (Component)Component.literal((String)(attacker.get().name + " is justifying a war against you.")));
        NationCommands.ok(player, "War justification started. Ready in " + seconds + " seconds.");
        return 1;
    }

    private static int declareWar(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
        NationStore store = NationStore.get();
        Optional<NationStore.Nation> attacker = store.nationOf(player.getUUID());
        Optional<NationStore.Nation> defender = store.nationByName(StringArgumentType.getString(context, (String)"country"));
        if (attacker.isEmpty() || defender.isEmpty()) {
            NationCommands.fail(player, "Pick an existing target nation.");
            return 0;
        }
        if (!NationCommands.requireNationOwner(player, store, attacker.get())) {
            return 0;
        }
        Optional<NationStore.War> maybeWar = store.warBetween(attacker.get(), defender.get());
        if (maybeWar.isEmpty() || !maybeWar.get().attacker.equals(attacker.get().id)) {
            NationCommands.fail(player, "You need to justify this war first.");
            return 0;
        }
        NationStore.War war = maybeWar.get();
        if (war.active) {
            NationCommands.fail(player, "That war is already active.");
            return 0;
        }
        if (war.pendingDefenderResponse) {
            NationCommands.fail(player, "That war declaration is already waiting for a response.");
            return 0;
        }
        MinecraftServer server = player.getServer();
        if (NationStore.persistentNow() < war.justificationCompleteTick) {
            long secondsLeft = Math.max(1L, (war.justificationCompleteTick - NationStore.persistentNow()) / 20L);
            NationCommands.fail(player, "Justification is not ready. " + secondsLeft + " seconds left.");
            return 0;
        }
        if (!NationCommands.hasOnlineMember(server, store, defender.get())) {
            NationCommands.fail(player, "You can only declare war while at least one target player is online.");
            return 0;
        }
        if (NationCommands.canDefenderRejectWar(store, attacker.get(), defender.get())) {
            war.pendingDefenderResponse = true;
            war.active = false;
            store.save();
            store.notifyNation(server, defender.get(), (Component)Component.literal((String)("[NationWars] " + attacker.get().name + " declared war. Your bigger territory lets you reject it. Use /war accept " + attacker.get().id + " or /war reject " + attacker.get().id + ".")));
            store.notifyNation(server, attacker.get(), (Component)Component.literal((String)("[NationWars] War declaration sent to " + defender.get().name + " for response.")));
            return 1;
        }
        war.active = true;
        war.pendingDefenderResponse = false;
        war.defenderStartingClaims = store.claimCount(defender.get());
        store.save();
        NationCommands.createAllianceDefenseCalls(server, store, war, defender.get());
        NationCommands.callGuarantors(server, store, war, defender.get());
        store.notifyNation(server, defender.get(), (Component)Component.literal((String)(attacker.get().name + " declared war on you.")));
        store.notifyNation(server, attacker.get(), (Component)Component.literal((String)("War declared on " + defender.get().name + ".")));
        return 1;
    }

    private static int acceptWarDeclaration(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
        NationStore store = NationStore.get();
        Optional<NationStore.Nation> defender = store.nationOf(player.getUUID());
        Optional<NationStore.Nation> attacker = store.nationByName(StringArgumentType.getString(context, (String)"country"));
        if (defender.isEmpty() || attacker.isEmpty()) {
            NationCommands.fail(player, "Both sides must be nations.");
            return 0;
        }
        if (!NationCommands.requireNationOwner(player, store, defender.get())) {
            return 0;
        }
        Optional<NationStore.War> maybeWar = store.warBetween(attacker.get(), defender.get());
        if (maybeWar.isEmpty() || !maybeWar.get().pendingDefenderResponse || !maybeWar.get().defender.equals(defender.get().id)) {
            NationCommands.fail(player, "There is no pending war declaration from that nation.");
            return 0;
        }
        NationStore.War war = maybeWar.get();
        war.active = true;
        war.pendingDefenderResponse = false;
        war.defenderStartingClaims = store.claimCount(defender.get());
        store.save();
        NationCommands.createAllianceDefenseCalls(player.getServer(), store, war, defender.get());
        NationCommands.callGuarantors(player.getServer(), store, war, defender.get());
        store.notifyNation(player.getServer(), attacker.get(), (Component)Component.literal((String)("[NationWars] " + defender.get().name + " accepted the war declaration.")));
        store.notifyNation(player.getServer(), defender.get(), (Component)Component.literal((String)("[NationWars] War accepted against " + attacker.get().name + ".")));
        return 1;
    }

    private static int rejectWarDeclaration(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
        NationStore store = NationStore.get();
        Optional<NationStore.Nation> defender = store.nationOf(player.getUUID());
        Optional<NationStore.Nation> attacker = store.nationByName(StringArgumentType.getString(context, (String)"country"));
        if (defender.isEmpty() || attacker.isEmpty()) {
            NationCommands.fail(player, "Both sides must be nations.");
            return 0;
        }
        if (!NationCommands.requireNationOwner(player, store, defender.get())) {
            return 0;
        }
        Optional<NationStore.War> maybeWar = store.warBetween(attacker.get(), defender.get());
        if (maybeWar.isEmpty() || !maybeWar.get().pendingDefenderResponse || !maybeWar.get().defender.equals(defender.get().id)) {
            NationCommands.fail(player, "There is no pending war declaration from that nation.");
            return 0;
        }
        if (attacker.get().doctrine().canRejectWarDeclarations) {
            store.recordWarDeclarationRejection(attacker.get(), defender.get());
        }
        store.endWar(maybeWar.get());
        store.notifyNation(player.getServer(), attacker.get(), (Component)Component.literal((String)("[NationWars] " + defender.get().name + " rejected the war declaration.")));
        store.notifyNation(player.getServer(), defender.get(), (Component)Component.literal((String)"[NationWars] War declaration rejected."));
        return 1;
    }

    private static int requestWarJoin(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
        NationStore store = NationStore.get();
        Optional<NationStore.Nation> requester = store.nationOf(player.getUUID());
        Optional<NationStore.Nation> sponsor = store.nationByName(StringArgumentType.getString(context, (String)"country"));
        if (requester.isEmpty() || sponsor.isEmpty() || requester.get().id.equals(sponsor.get().id)) {
            NationCommands.fail(player, "Pick another nation in an active war.");
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
                NationCommands.fail(player, "That enemy nation does not exist.");
                return 0;
            }
            NationStore.Nation enemy = selectedEnemy;
            candidateWars = candidateWars.stream().filter(war -> store.areOpposingWarSides(war, sponsor.get(), enemy)).toList();
        }
        catch (IllegalArgumentException ignored) {
            // The optional enemy argument was not supplied.
        }
        if (candidateWars.isEmpty()) {
            NationCommands.fail(player, sponsor.get().name + " is not in an active war.");
            return 0;
        }
        if (candidateWars.size() > 1) {
            String enemies = candidateWars.stream()
                .flatMap(war -> java.util.stream.Stream.concat(war.attackerSide.stream(), war.defenderSide.stream()))
                .map(store::nationById).flatMap(Optional::stream)
                .filter(nation -> store.activeWarForCapture(sponsor.get(), nation).isPresent())
                .map(nation -> nation.name).distinct().sorted(String::compareToIgnoreCase).collect(Collectors.joining(", "));
            NationCommands.fail(player, sponsor.get().name + " is in multiple wars. Use /war join " + sponsor.get().id + " <enemy>. Enemies: " + enemies + ".");
            return 0;
        }
        if (!store.addWarJoinRequest(candidateWars.get(0), requester.get(), sponsor.get())) {
            NationCommands.fail(player, "Could not request to join that war.");
            return 0;
        }
        store.notifyNation(player.getServer(), sponsor.get(), (Component)Component.literal((String)("[NationWars] " + requester.get().name + " wants to join your war. Use /war acceptjoin " + requester.get().id + " or /war rejectjoin " + requester.get().id + ".")));
        NationCommands.ok(player, "War join request sent to " + sponsor.get().name + ".");
        return 1;
    }

    private static int acceptWarJoin(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
        NationStore store = NationStore.get();
        Optional<NationStore.Nation> acceptor = store.nationOf(player.getUUID());
        Optional<NationStore.Nation> requester = store.nationByName(StringArgumentType.getString(context, (String)"country"));
        if (acceptor.isEmpty() || requester.isEmpty()) {
            NationCommands.fail(player, "Both sides must be nations.");
            return 0;
        }
        if (!NationCommands.requireNationOwner(player, store, acceptor.get())) {
            return 0;
        }
        for (NationStore.War war : store.activeWarsOf(acceptor.get())) {
            if (!store.acceptWarJoinRequest(war, requester.get(), acceptor.get())) continue;
            store.notifyNation(player.getServer(), requester.get(), (Component)Component.literal((String)("[NationWars] " + acceptor.get().name + " accepted your war join request.")));
            store.notifyNation(player.getServer(), acceptor.get(), (Component)Component.literal((String)("[NationWars] " + requester.get().name + " joined your war.")));
            return 1;
        }
        NationCommands.fail(player, "No pending join request from that nation.");
        return 0;
    }

    private static int acceptAllianceDefense(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
        NationStore store = NationStore.get();
        Optional<NationStore.Nation> ally = store.nationOf(player.getUUID());
        Optional<NationStore.Nation> caller = store.nationByName(StringArgumentType.getString(context, (String)"country"));
        if (ally.isEmpty() || caller.isEmpty()) {
            NationCommands.fail(player, "Both sides must be nations.");
            return 0;
        }
        if (!NationCommands.requireNationOwner(player, store, ally.get())) {
            return 0;
        }
        for (NationStore.War war : store.activeWarsOf(caller.get())) {
            if (!store.acceptWarDefenseCall(war, ally.get(), caller.get())) continue;
            store.notifyNation(player.getServer(), caller.get(), (Component)Component.literal((String)("[NationWars] " + ally.get().name + " answered the alliance defense call.")));
            store.notifyNation(player.getServer(), ally.get(), (Component)Component.literal((String)("[NationWars] Joined the war to defend " + caller.get().name + ".")));
            return 1;
        }
        NationCommands.fail(player, "No pending alliance defense call from that nation.");
        return 0;
    }

    private static int declineAllianceDefense(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
        NationStore store = NationStore.get();
        Optional<NationStore.Nation> ally = store.nationOf(player.getUUID());
        Optional<NationStore.Nation> caller = store.nationByName(StringArgumentType.getString(context, (String)"country"));
        if (ally.isEmpty() || caller.isEmpty()) {
            NationCommands.fail(player, "Both sides must be nations.");
            return 0;
        }
        if (!NationCommands.requireNationOwner(player, store, ally.get())) {
            return 0;
        }
        for (NationStore.War war : store.activeWarsOf(caller.get())) {
            if (!store.rejectWarDefenseCall(war, ally.get(), caller.get())) continue;
            if (ally.get().doctrine() == Doctrine.FRENCH && caller.get().doctrine().ideology == Ideology.DEMOCRATIC) {
                double penalty = Math.min(ally.get().balance, 300.0);
                ally.get().balance = NationStore.roundMoney(ally.get().balance - penalty);
                store.save();
                store.notifyNation(player.getServer(), ally.get(), (Component)Component.literal((String)("[NationWars] Casus Foederis penalty: $" + NationStore.roundMoney(penalty) + " lost for declining to defend " + caller.get().name + ".")));
            } else {
                NationCommands.ok(player, "Declined the alliance defense call from " + caller.get().name + ".");
            }
            store.notifyNation(player.getServer(), caller.get(), (Component)Component.literal((String)("[NationWars] " + ally.get().name + " declined your alliance defense call.")));
            return 1;
        }
        NationCommands.fail(player, "No pending alliance defense call from that nation.");
        return 0;
    }

    private static int rejectWarJoin(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
        NationStore store = NationStore.get();
        Optional<NationStore.Nation> rejector = store.nationOf(player.getUUID());
        Optional<NationStore.Nation> requester = store.nationByName(StringArgumentType.getString(context, (String)"country"));
        if (rejector.isEmpty() || requester.isEmpty()) {
            NationCommands.fail(player, "Both sides must be nations.");
            return 0;
        }
        if (!NationCommands.requireNationOwner(player, store, rejector.get())) {
            return 0;
        }
        for (NationStore.War war : store.activeWarsOf(rejector.get())) {
            if (!store.rejectWarJoinRequest(war, requester.get(), rejector.get())) continue;
            store.notifyNation(player.getServer(), requester.get(), (Component)Component.literal((String)("[NationWars] " + rejector.get().name + " rejected your war join request.")));
            NationCommands.ok(player, "Rejected war join request from " + requester.get().name + ".");
            return 1;
        }
        NationCommands.fail(player, "No pending join request from that nation.");
        return 0;
    }

    private static int leaveWar(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
        NationStore store = NationStore.get();
        Optional<NationStore.Nation> own = store.nationOf(player.getUUID());
        Optional<NationStore.Nation> other = store.nationByName(StringArgumentType.getString(context, (String)"country"));
        if (own.isEmpty() || other.isEmpty()) {
            NationCommands.fail(player, "Both sides must be nations.");
            return 0;
        }
        if (!NationCommands.requireNationOwner(player, store, own.get())) {
            return 0;
        }
        if (!own.get().doctrine().canLeaveWarSafely) {
            NationCommands.fail(player, "Only Romania can leave a war this way.");
            return 0;
        }
        String ideologyKey = other.get().doctrine().ideology.name();
        if (own.get().usedSpecialWarLeaveIdeologies.contains(ideologyKey)) {
            NationCommands.fail(player, "King Michael's Coup was already used against a " + other.get().doctrine().ideology.displayName + " enemy.");
            return 0;
        }
        if (own.get().usedSpecialWarLeaveIdeologies.size() >= Ideology.values().length) {
            NationCommands.fail(player, "King Michael's Coup has already been used against every ideology.");
            return 0;
        }
        long tick = NationStore.persistentNow();
        long cooldownUntil = own.get().lastSpecialWarLeaveTick + (long)ROMANIAN_WAR_LEAVE_COOLDOWN_SECONDS * 20L;
        if (own.get().lastSpecialWarLeaveTick > 0L && tick < cooldownUntil) {
            long secondsLeft = Math.max(1L, (cooldownUntil - tick) / 20L);
            NationCommands.fail(player, "King Michael's Coup is on cooldown for " + secondsLeft + " seconds.");
            return 0;
        }
        Optional<NationStore.War> maybeWar = store.activeWarForCapture(own.get(), other.get()).or(() -> store.activeWarForCapture((NationStore.Nation)other.get(), (NationStore.Nation)own.get()));
        if (maybeWar.isEmpty()) {
            NationCommands.fail(player, "There is no active war with that nation.");
            return 0;
        }
        own.get().usedSpecialWarLeaveIdeologies.add(ideologyKey);
        own.get().lastSpecialWarLeaveTick = tick;
        store.leaveWarSafely(maybeWar.get(), own.get());
        store.save();
        String carolMessage = own.get().usedSpecialWarLeaveIdeologies.size() >= Ideology.values().length ? " Carol II Lifestyle is now removed." : "";
        store.notifyNation(player.getServer(), own.get(), (Component)Component.literal((String)("[NationWars] Left the war with no consequences." + carolMessage)));
        store.notifyNation(player.getServer(), other.get(), (Component)Component.literal((String)("[NationWars] " + own.get().name + " left the war through King Michael's Coup.")));
        return 1;
    }

    private static int warOverview(CommandContext<CommandSourceStack> context) {
        ((CommandSourceStack)context.getSource()).sendSuccess(() -> Component.literal((String)"War commands:"), false);
        ((CommandSourceStack)context.getSource()).sendSuccess(() -> Component.literal((String)"/war status"), false);
        ((CommandSourceStack)context.getSource()).sendSuccess(() -> Component.literal((String)"/war justify <nation> | /war declare <nation>"), false);
        ((CommandSourceStack)context.getSource()).sendSuccess(() -> Component.literal((String)"/war join <nation> [enemy] | /war acceptjoin <nation> | /war rejectjoin <nation>"), false);
        ((CommandSourceStack)context.getSource()).sendSuccess(() -> Component.literal((String)"/war defend <nation> | /war declinedefense <nation>"), false);
        ((CommandSourceStack)context.getSource()).sendSuccess(() -> Component.literal((String)"/peace <nation> | /surrender <nation>"), false);
        return 1;
    }

    private static int warStatus(CommandContext<CommandSourceStack> context) {
        NationStore store = NationStore.get();
        List<NationStore.War> wars = store.wars().stream().sorted(Comparator.comparing(war -> war.id)).toList();
        if (wars.isEmpty()) {
            ((CommandSourceStack)context.getSource()).sendSuccess(() -> Component.literal((String)"No wars or justifications."), false);
            return 1;
        }
        MinecraftServer server = ((CommandSourceStack)context.getSource()).getServer();
        for (NationStore.War war2 : wars) {
            String attacker = store.nationById(war2.attacker).map(nation -> nation.name).orElse(war2.attacker);
            String defender = store.nationById(war2.defender).map(nation -> nation.name).orElse(war2.defender);
            ((CommandSourceStack)context.getSource()).sendSuccess(() -> Component.literal((String)(attacker + " vs " + defender)), false);
            if (war2.active) {
                ((CommandSourceStack)context.getSource()).sendSuccess(() -> Component.literal((String)"  active"), false);
                ((CommandSourceStack)context.getSource()).sendSuccess(() -> Component.literal((String)("  attackers: " + NationCommands.nationNames(store, war2.attackerSide))), false);
                ((CommandSourceStack)context.getSource()).sendSuccess(() -> Component.literal((String)("  defenders: " + NationCommands.nationNames(store, war2.defenderSide))), false);
                if (war2.joinRequests.isEmpty()) continue;
                ((CommandSourceStack)context.getSource()).sendSuccess(() -> Component.literal((String)("  pending: " + NationCommands.nationNames(store, war2.joinRequests.keySet()))), false);
                continue;
            }
            if (war2.pendingDefenderResponse) {
                ((CommandSourceStack)context.getSource()).sendSuccess(() -> Component.literal((String)"  waiting for defender response"), false);
                continue;
            }
            long secondsLeft = Math.max(0L, (war2.justificationCompleteTick - NationStore.persistentNow()) / 20L);
            ((CommandSourceStack)context.getSource()).sendSuccess(() -> Component.literal((String)("  justifying: " + secondsLeft + "s left")), false);
        }
        return 1;
    }

    private static void createAllianceDefenseCalls(MinecraftServer server, NationStore store, NationStore.War war, NationStore.Nation defender) {
        if (defender.doctrine().ideology != Ideology.DEMOCRATIC) {
            return;
        }
        store.allianceOf(defender).ifPresent(alliance -> {
            for (String allyId : alliance.members) {
                NationStore.Nation ally;
                if (allyId.equals(defender.id) || (ally = (NationStore.Nation)store.nationById(allyId).orElse(null)) == null || store.isWarParticipant(war, ally) || !store.addWarDefenseCall(war, ally, defender)) continue;
                store.notifyNation(server, ally, (Component)Component.literal((String)("[NationWars] Democratic ally " + defender.name + " called for defense. Use /war defend " + defender.id + " or /war declinedefense " + defender.id + ".")));
            }
        });
    }

    private static void callGuarantors(MinecraftServer server, NationStore store, NationStore.War war, NationStore.Nation defender) {
        for (NationStore.Nation guarantor : store.guarantorsOf(defender)) {
            if (store.sameAlliance(guarantor, defender)) {
                continue;
            }
            if (!store.addGuaranteedDefender(war, guarantor)) {
                continue;
            }
            store.notifyNation(server, guarantor, Component.literal("[NationWars] " + defender.name + " was attacked. Your guarantee called you to arms immediately."));
            store.notifyNation(server, defender, Component.literal("[NationWars] " + guarantor.name + " joined your defense under its guarantee."));
        }
    }

    private static int peace(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
        NationStore store = NationStore.get();
        Optional<NationStore.Nation> own = store.nationOf(player.getUUID());
        Optional<NationStore.Nation> other = store.nationByName(StringArgumentType.getString(context, (String)"country"));
        if (own.isEmpty() || other.isEmpty()) {
            NationCommands.fail(player, "Both sides must be nations.");
            return 0;
        }
        if (!NationCommands.requireNationOwner(player, store, own.get())) {
            return 0;
        }
        Optional<NationStore.War> maybeWar = store.activeWarForCapture(own.get(), other.get()).or(() -> store.activeWarForCapture((NationStore.Nation)other.get(), (NationStore.Nation)own.get()));
        if (maybeWar.isEmpty()) {
            NationCommands.fail(player, "There is no active war with that nation.");
            return 0;
        }
        NationStore.War war = maybeWar.get();
        if (!(war.peaceDeal != null && own.get().id.equals(war.peaceDeal.receiver) || store.peaceCooldownUntil(own.get(), other.get()) <= NationStore.persistentNow())) {
            long secondsLeft = Math.max(1L, (store.peaceCooldownUntil(own.get(), other.get()) - NationStore.persistentNow()) / 20L);
            NationCommands.fail(player, "Your last peace offer was rejected. Try again in " + secondsLeft + " seconds.");
            return 0;
        }
        player.openMenu((MenuProvider)new SimpleMenuProvider((containerId, inventory, viewer) -> new PeaceDealMenu(containerId, inventory, (NationStore.Nation)own.get(), (NationStore.Nation)other.get(), war), (Component)Component.literal((String)"Offer Peace Deal")));
        return 1;
    }

    private static int rejectPeace(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
        NationStore store = NationStore.get();
        Optional<NationStore.Nation> own = store.nationOf(player.getUUID());
        Optional<NationStore.Nation> other = store.nationByName(StringArgumentType.getString(context, (String)"country"));
        if (own.isEmpty() || other.isEmpty()) {
            NationCommands.fail(player, "Both sides must be nations.");
            return 0;
        }
        if (!NationCommands.requireNationOwner(player, store, own.get())) {
            return 0;
        }
        Optional<NationStore.War> maybeWar = store.activeWarForCapture(own.get(), other.get()).or(() -> store.activeWarForCapture((NationStore.Nation)other.get(), (NationStore.Nation)own.get()));
        if (maybeWar.isEmpty()) {
            NationCommands.fail(player, "There is no active war with that nation.");
            return 0;
        }
        NationStore.War war = maybeWar.get();
        if (war.peaceDeal == null || !own.get().id.equals(war.peaceDeal.receiver) || !other.get().id.equals(war.peaceDeal.proposer)) {
            NationCommands.fail(player, "There is no incoming peace offer from that nation.");
            return 0;
        }
        store.clearPeaceDeal(war);
        store.setPeaceCooldown(other.get(), own.get(), NationStore.persistentNow() + 6000L);
        store.notifyNation(player.getServer(), other.get(), (Component)Component.literal((String)("[NationWars] " + own.get().name + " rejected your peace offer. You can send another in 300 seconds.")));
        store.notifyNation(player.getServer(), own.get(), (Component)Component.literal((String)("[NationWars] Rejected peace offer from " + other.get().name + ".")));
        return 1;
    }

    private static int surrender(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
        NationStore store = NationStore.get();
        Optional<NationStore.Nation> own = store.nationOf(player.getUUID());
        Optional<NationStore.Nation> other = store.nationByName(StringArgumentType.getString(context, (String)"country"));
        if (own.isEmpty() || other.isEmpty()) {
            NationCommands.fail(player, "Both sides must be nations.");
            return 0;
        }
        if (!NationCommands.requireNationOwner(player, store, own.get())) {
            return 0;
        }
        Optional<NationStore.War> maybeWar = store.activeWarForCapture(own.get(), other.get()).or(() -> store.activeWarForCapture((NationStore.Nation)other.get(), (NationStore.Nation)own.get()));
        if (maybeWar.isEmpty()) {
            NationCommands.fail(player, "There is no active war with that nation.");
            return 0;
        }
        NationStore.War war = maybeWar.get();
        int ownSide = store.sideOf(war, own.get());
        int otherSide = store.sideOf(war, other.get());
        if (ownSide == 0 || otherSide == 0 || ownSide == otherSide) {
            NationCommands.fail(player, "That nation is not on the enemy side of your war.");
            return 0;
        }
        if (!store.isPrimaryWarParticipant(war, own.get())) {
            return NationCommands.surrenderJoinedNation(player, store, war, own.get(), other.get());
        }
        if (!store.isPrimaryWarParticipant(war, other.get())) {
            NationCommands.fail(player, "Main war nations must surrender to the main enemy nation.");
            return 0;
        }
        if (ownSide < 0) {
            Optional<String> claim;
            int startingClaims = war.defenderStartingClaims > 0 ? war.defenderStartingClaims : store.claimCount(own.get()) + store.capturedClaimsHeldBySide(war, otherSide);
            int target = Math.max(1, (int)Math.ceil((double)startingClaims * 0.25 * own.get().doctrine().surrenderLandMultiplier));
            int alreadyCaptured = store.capturedClaimsHeldBySide(war, otherSide);
            for (int transferred = alreadyCaptured; transferred < target
                && !(claim = store.borderClaimsOf(own.get()).stream().filter(id -> !id.equals(((NationStore.Nation)own.get()).capitalClaim))
                    .findFirst().or(() -> store.claimsOf((NationStore.Nation)own.get()).stream().findFirst())).isEmpty(); ++transferred) {
                store.transferClaim(claim.get(), other.get());
                NationCommands.recordWarCapture(store, war, other.get(), claim.get(), otherSide);
            }
            double treasuryShare = NationStore.roundMoney(own.get().balance * 0.5);
            own.get().balance = NationStore.roundMoney(own.get().balance - treasuryShare);
            other.get().balance = NationStore.roundMoney(other.get().balance + treasuryShare);
            double leaderMoney = 0.0;
            try {
                leaderMoney = store.confiscatePlayerMoney(UUID.fromString(own.get().owner));
                other.get().balance = NationStore.roundMoney(other.get().balance + leaderMoney);
            }
            catch (RuntimeException runtimeException) {
                // empty catch block
            }
            boolean deleteLoser = store.claimCount(own.get()) <= 0;
            store.endWar(war);
            store.notifyNation(player.getServer(), own.get(), (Component)Component.literal((String)("You surrendered to " + other.get().name + ". Lost $" + NationStore.roundMoney(treasuryShare + leaderMoney) + ".")));
            store.notifyNation(player.getServer(), other.get(), (Component)Component.literal((String)(own.get().name + " surrendered. Your nation received $" + NationStore.roundMoney(treasuryShare + leaderMoney) + ".")));
            if (deleteLoser) {
                store.notifyNation(player.getServer(), own.get(), (Component)Component.literal((String)"[NationWars] Your nation lost all territory and was deleted. You can create a new nation."));
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
            store.notifyNation(player.getServer(), own.get(), (Component)Component.literal((String)("You surrendered and returned " + returned + " captured claims to " + other.get().name + ".")));
            store.notifyNation(player.getServer(), other.get(), (Component)Component.literal((String)(own.get().name + " surrendered and returned " + returned + " captured claims.")));
            return 1;
        }
        NationCommands.fail(player, "This nation is not part of that war.");
        return 0;
    }

    private static int surrenderJoinedNation(ServerPlayer player, NationStore store, NationStore.War war, NationStore.Nation own, NationStore.Nation other) {
        int returned = 0;
        for (String claimId : new ArrayList<String>(store.capturedClaimsBy(war, own))) {
            store.transferClaim(claimId, other);
            store.removeCapturedClaim(war, claimId);
            ++returned;
        }
        if (!store.removeWarParticipant(war, own)) {
            NationCommands.fail(player, "Could not leave that war.");
            return 0;
        }
        store.notifyNation(player.getServer(), own, (Component)Component.literal((String)("[NationWars] You surrendered to " + other.name + " and left the war. Returned captured claims: " + returned + ".")));
        store.notifyNation(player.getServer(), other, (Component)Component.literal((String)("[NationWars] " + own.name + " surrendered and left the war. Returned captured claims: " + returned + ".")));
        return 1;
    }

    private static void recordWarCapture(NationStore store, NationStore.War war, NationStore.Nation captor, String claimId, int capturingSide) {
        store.recordCapturedClaim(war, captor, claimId);
        if (capturingSide > 0) {
            war.attackerCapturedClaims.add(claimId);
        } else {
            war.attackerCapturedClaims.remove(claimId);
        }
        store.save();
    }

    private static ClaimKey currentClaim(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        ChunkPos chunk = player.chunkPosition();
        return ClaimKey.of(level, chunk);
    }

    private static boolean touchesNationClaim(NationStore store, NationStore.Nation nation, ClaimKey claim) {
        for (String owned : store.claimsOf(nation)) {
            if (!claim.touches(ClaimKey.parse(owned))) continue;
            return true;
        }
        return false;
    }

    private static double claimCost(NationStore store, NationStore.Nation nation, ClaimKey claim) {
        ClaimKey capital;
        double expansion = 1.0 + (double)Math.max(0, store.claimCount(nation) - 1) * 0.1;
        double distance = 1.0;
        if (nation.doctrine().distanceClaimScaling && nation.capitalClaim != null && !nation.capitalClaim.isBlank() && (capital = ClaimKey.parse(nation.capitalClaim)).dimension().equals(claim.dimension())) {
            int chunks = Math.abs(capital.x() - claim.x()) + Math.abs(capital.z() - claim.z());
            distance += (double)chunks * 0.03;
        }
        return NationStore.roundMoney(100.0 * expansion * distance * nation.doctrine().claimCostMultiplier);
    }

    private static boolean canDefenderRejectWar(NationStore store, NationStore.Nation attacker, NationStore.Nation defender) {
        return attacker.doctrine().canRejectWarDeclarations && store.claimCount(defender) > store.claimCount(attacker) && !store.hasRejectedWarDeclaration(attacker, defender);
    }

    static int warJustificationSeconds(NationStore.Nation attacker, NationStore.Nation defender) {
        int seconds = 90;
        if (attacker.doctrine() == Doctrine.GERMAN) {
            seconds -= 40;
        }
        if (defender.doctrine() == Doctrine.ROMANIAN) {
            seconds += 30;
        }
        return Math.max(10, seconds);
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
        NationCommands.fail(player, "Only the nation owner can perform that action.");
        return false;
    }

    private static boolean canNationSpend(ServerPlayer player, NationStore store, NationStore.Nation nation, double amount) {
        long tick = NationStore.persistentNow();
        if (store.isSpendingBlocked(nation, tick)) {
            long seconds = Math.max(1L, (store.spendingBlockedUntil(nation) - tick + 19L) / 20L);
            NationCommands.fail(player, "Your capital is infiltrated; treasury spending is blocked for " + seconds + " seconds.");
            return false;
        }
        if (nation.balance + 1.0E-4 < amount) {
            NationCommands.fail(player, "Nation treasury needs $" + NationStore.roundMoney(amount) + ".");
            return false;
        }
        return true;
    }

    private static boolean hasOnlineMember(MinecraftServer server, NationStore store, NationStore.Nation nation) {
        return server.getPlayerList().getPlayers().stream().anyMatch(player -> store.isMember(player.getUUID(), nation));
    }

    private static void ok(ServerPlayer player, String message) {
        player.sendSystemMessage((Component)Component.literal((String)("[NationWars] " + message)));
    }

    private static void fail(ServerPlayer player, String message) {
        player.sendSystemMessage((Component)Component.literal((String)("[NationWars] " + message)));
    }

    private record PendingNationCreation(Doctrine doctrine, long expiresTick) {
    }
}
