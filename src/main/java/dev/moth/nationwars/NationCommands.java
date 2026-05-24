package dev.moth.nationwars;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class NationCommands {
   private static final double BASE_CLAIM_COST = 100.0;
   private static final double CITY_COST = 500.0;
   private static final double CASUS_FOEDERIS_COST = 300.0;
   private static final int SPY_SECONDS = 120;
   private static final int ROMANIAN_WAR_LEAVE_COOLDOWN_SECONDS = 1800;

   private NationCommands() {
   }

   @SubscribeEvent
   public static void register(RegisterCommandsEvent event) {
      CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("money").requires(source -> source.hasPermission(0)))
            .executes(NationCommands::money)
      );
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("market")
                     .requires(source -> source.hasPermission(0)))
                  .executes(NationCommands::openMarket))
               .then(
                  ((LiteralArgumentBuilder)Commands.literal("sellhand").executes(context -> sellHand(context, -1.0)))
                     .then(
                        Commands.argument("price", DoubleArgumentType.doubleArg(0.01))
                           .executes(context -> sellHand(context, DoubleArgumentType.getDouble(context, "price")))
                     )
               ))
            .then(Commands.literal("cancel").then(Commands.argument("id", IntegerArgumentType.integer(1)).executes(NationCommands::cancelListing)))
      );
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("nations").requires(source -> source.hasPermission(0)))
            .executes(NationCommands::nations)
      );
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("spy").requires(source -> source.hasPermission(0)))
            .then(Commands.argument("country", StringArgumentType.word()).executes(NationCommands::spy))
      );
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal(
                                             "nation"
                                          )
                                          .requires(source -> source.hasPermission(0)))
                                       .then(
                                          ((LiteralArgumentBuilder)Commands.literal("doctrines").executes(NationCommands::openDoctrinesMenu))
                                             .then(Commands.literal("list").executes(NationCommands::doctrines))
                                       ))
                                    .then(
                                       ((LiteralArgumentBuilder)Commands.literal("syncopac").requires(source -> source.hasPermission(2)))
                                          .executes(NationCommands::syncOpac)
                                    ))
                                 .then(
                                    Commands.literal("create")
                                       .then(
                                          ((RequiredArgumentBuilder)Commands.argument("name", StringArgumentType.word())
                                                .executes(NationCommands::openNationCreateMenu))
                                             .then(Commands.argument("doctrine", StringArgumentType.word()).executes(NationCommands::createNationCommand))
                                       )
                                 ))
                              .then(Commands.literal("join").then(Commands.argument("name", StringArgumentType.word()).executes(NationCommands::joinNation))))
                           .then(
                              ((LiteralArgumentBuilder)Commands.literal("info").executes(NationCommands::ownNationInfo))
                                 .then(Commands.argument("name", StringArgumentType.word()).executes(NationCommands::nationInfo))
                           ))
                        .then(Commands.literal("claim").executes(NationCommands::claim)))
                     .then(Commands.literal("unclaim").executes(NationCommands::unclaim)))
                  .then(Commands.literal("city").executes(NationCommands::buyCity)))
               .then(Commands.literal("balance").executes(NationCommands::nationBalance)))
            .then(Commands.literal("deposit").then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01)).executes(NationCommands::deposit)))
      );
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal(
                              "alliance"
                           )
                           .requires(source -> source.hasPermission(0)))
                        .then(Commands.literal("create").then(Commands.argument("name", StringArgumentType.word()).executes(NationCommands::allianceCreate))))
                     .then(Commands.literal("invite").then(Commands.argument("country", StringArgumentType.word()).executes(NationCommands::allianceInvite))))
                  .then(Commands.literal("accept").then(Commands.argument("name", StringArgumentType.word()).executes(NationCommands::allianceAccept))))
               .then(Commands.literal("kick").then(Commands.argument("country", StringArgumentType.word()).executes(NationCommands::allianceKick))))
            .then(
               ((LiteralArgumentBuilder)Commands.literal("info").executes(NationCommands::allianceInfo))
                  .then(Commands.argument("name", StringArgumentType.word()).executes(NationCommands::allianceInfoNamed))
            )
      );
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("alliances").requires(source -> source.hasPermission(0)))
            .executes(NationCommands::alliances)
      );
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal(
                                                   "war"
                                                )
                                                .requires(source -> source.hasPermission(0)))
                                             .executes(NationCommands::warOverview))
                                          .then(
                                             Commands.literal("justify")
                                                .then(Commands.argument("country", StringArgumentType.word()).executes(NationCommands::justifyWar))
                                          ))
                                       .then(
                                          Commands.literal("declare")
                                             .then(Commands.argument("country", StringArgumentType.word()).executes(NationCommands::declareWar))
                                       ))
                                    .then(
                                       Commands.literal("accept")
                                          .then(Commands.argument("country", StringArgumentType.word()).executes(NationCommands::acceptWarDeclaration))
                                    ))
                                 .then(
                                    Commands.literal("reject")
                                       .then(Commands.argument("country", StringArgumentType.word()).executes(NationCommands::rejectWarDeclaration))
                                 ))
                              .then(
                                 Commands.literal("join")
                                    .then(Commands.argument("country", StringArgumentType.word()).executes(NationCommands::requestWarJoin))
                              ))
                           .then(
                              Commands.literal("acceptjoin")
                                 .then(Commands.argument("country", StringArgumentType.word()).executes(NationCommands::acceptWarJoin))
                           ))
                        .then(
                           Commands.literal("rejectjoin").then(Commands.argument("country", StringArgumentType.word()).executes(NationCommands::rejectWarJoin))
                        ))
                     .then(
                        Commands.literal("defend")
                           .then(Commands.argument("country", StringArgumentType.word()).executes(NationCommands::acceptAllianceDefense))
                     ))
                  .then(
                     Commands.literal("declinedefense")
                        .then(Commands.argument("country", StringArgumentType.word()).executes(NationCommands::declineAllianceDefense))
                  ))
               .then(Commands.literal("leave").then(Commands.argument("country", StringArgumentType.word()).executes(NationCommands::leaveWar))))
            .then(Commands.literal("status").executes(NationCommands::warStatus))
      );
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("peace").requires(source -> source.hasPermission(0)))
            .then(Commands.argument("country", StringArgumentType.word()).executes(NationCommands::peace))
      );
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("surrender").requires(source -> source.hasPermission(0)))
            .then(Commands.argument("country", StringArgumentType.word()).executes(NationCommands::surrender))
      );
   }

   private static int openNationCreateMenu(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
      ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
      NationStore store = NationStore.get();
      String name = StringArgumentType.getString(context, "name");
      if (!canStartNationCreation(player, store, name)) {
         return 0;
      } else if (store.availableDoctrines().isEmpty()) {
         fail(player, "All doctrines are already taken.");
         return 0;
      } else {
         player.openMenu(
            new SimpleMenuProvider((containerId, inventory, viewer) -> new NationCreateMenu(containerId, inventory, name), Component.literal("Create " + name))
         );
         return 1;
      }
   }

   private static int createNationCommand(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
      ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
      String name = StringArgumentType.getString(context, "name");
      Optional<Doctrine> doctrine = Doctrine.byId(StringArgumentType.getString(context, "doctrine"));
      if (doctrine.isEmpty()) {
         fail(player, "Unknown doctrine. Use /nation doctrines.");
         return 0;
      } else {
         return createNationWithDoctrine(player, name, doctrine.get()) ? 1 : 0;
      }
   }

   static boolean createNationWithDoctrine(ServerPlayer player, String name, Doctrine doctrine) {
      NationStore store = NationStore.get();
      if (!canStartNationCreation(player, store, name)) {
         return false;
      } else if (store.isDoctrineTaken(doctrine)) {
         fail(player, doctrine.displayName + " is already taken by another nation.");
         return false;
      } else {
         ClaimKey capital = currentClaim(player);
         if (store.nationOwning(capital).isPresent()) {
            fail(player, "This chunk is already claimed.");
            return false;
         } else if (!OpacClaimsBridge.canMirrorClaim(player.getServer(), capital, player.getUUID())) {
            fail(player, "Open Parties and Claims already has this chunk claimed. Unclaim it there first.");
            return false;
         } else {
            NationStore.Nation nation = store.createNation(player, name, doctrine, capital);
            player.refreshTabListName();
            ok(player, "Created " + nation.name + " with " + doctrine.displayName + ". Capital: " + capital.shortName());
            return true;
         }
      }
   }

   private static boolean canStartNationCreation(ServerPlayer player, NationStore store, String name) {
      String key = NationStore.nationKey(name);
      if (key.length() < 3) {
         fail(player, "Nation names need at least 3 letters or numbers.");
         return false;
      } else if (store.hasNation(player.getUUID())) {
         fail(player, "You are already in a nation.");
         return false;
      } else if (store.nationByName(name).isPresent()) {
         fail(player, "That nation already exists.");
         return false;
      } else {
         return true;
      }
   }

   private static int joinNation(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
      ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
      NationStore store = NationStore.get();
      if (store.hasNation(player.getUUID())) {
         fail(player, "You are already in a nation.");
         return 0;
      } else {
         Optional<NationStore.Nation> nation = store.nationByName(StringArgumentType.getString(context, "name"));
         if (nation.isEmpty()) {
            fail(player, "No nation with that name exists.");
            return 0;
         } else {
            store.addMember(player.getUUID(), nation.get());
            player.refreshTabListName();
            ok(player, "Joined " + nation.get().name + ".");
            return 1;
         }
      }
   }

   private static int ownNationInfo(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
      ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
      Optional<NationStore.Nation> nation = NationStore.get().nationOf(player.getUUID());
      if (nation.isEmpty()) {
         fail(player, "You are not in a nation.");
         return 0;
      } else {
         return describeNation(player, nation.get());
      }
   }

   private static int nationInfo(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
      ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
      Optional<NationStore.Nation> nation = NationStore.get().nationByName(StringArgumentType.getString(context, "name"));
      if (nation.isEmpty()) {
         fail(player, "No nation with that name exists.");
         return 0;
      } else {
         return describeNation(player, nation.get());
      }
   }

   private static int describeNation(ServerPlayer player, NationStore.Nation nation) {
      NationStore store = NationStore.get();
      Doctrine doctrine = nation.doctrine();
      player.sendSystemMessage(Component.literal("Nation: " + nation.name));
      player.sendSystemMessage(Component.literal("Leader: " + nation.ownerName));
      player.sendSystemMessage(Component.literal("Doctrine: " + doctrine.displayName + " (" + doctrine.id + ", " + doctrine.ideology.displayName + ")"));
      player.sendSystemMessage(Component.literal("Claims: " + store.claimCount(nation) + ", free claims left: " + nation.freeClaimsRemaining));
      player.sendSystemMessage(Component.literal("Cities: " + nation.cityClaims.size()));
      player.sendSystemMessage(Component.literal("Treasury: $" + NationStore.roundMoney(nation.balance)));
      store.allianceOf(nation).ifPresent(alliance -> player.sendSystemMessage(Component.literal("Alliance: " + alliance.name)));
      player.sendSystemMessage(
         Component.literal(
            "Capital: " + (nation.capitalClaim != null && !nation.capitalClaim.isBlank() ? ClaimKey.parse(nation.capitalClaim).shortName() : "none")
         )
      );
      return 1;
   }

   private static int claim(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
      ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
      NationStore store = NationStore.get();
      Optional<NationStore.Nation> nation = store.nationOf(player.getUUID());
      if (nation.isEmpty()) {
         fail(player, "Create or join a nation first.");
         return 0;
      } else {
         ClaimKey claim = currentClaim(player);
         if (store.nationOwning(claim).isPresent()) {
            fail(player, "This chunk is already claimed.");
            return 0;
         } else if (!OpacClaimsBridge.canMirrorClaim(player.getServer(), claim, UUID.fromString(nation.get().owner))) {
            fail(player, "Open Parties and Claims already has this chunk claimed. Unclaim it there first.");
            return 0;
         } else if (store.claimCount(nation.get()) > 0 && !touchesNationClaim(store, nation.get(), claim)) {
            fail(player, "New claims must touch one of your nation's chunks.");
            return 0;
         } else {
            if (nation.get().freeClaimsRemaining > 0) {
               nation.get().freeClaimsRemaining--;
            } else {
               double cost = claimCost(store, nation.get(), claim);
               if (nation.get().balance + 1.0E-4 < cost) {
                  fail(player, "Nation treasury needs $" + cost + " for this claim.");
                  return 0;
               }

               nation.get().balance = NationStore.roundMoney(nation.get().balance - cost);
            }

            store.claim(nation.get(), claim);
            ok(player, "Claimed " + claim.shortName() + ".");
            return 1;
         }
      }
   }

   private static int buyCity(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
      ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
      NationStore store = NationStore.get();
      Optional<NationStore.Nation> nation = store.nationOf(player.getUUID());
      if (nation.isEmpty()) {
         fail(player, "Create or join a nation first.");
         return 0;
      } else if (!store.isOwner(player.getUUID(), nation.get())) {
         fail(player, "Only the nation owner can buy city income claims.");
         return 0;
      } else if (!nation.get().doctrine().canBuyCities) {
         fail(player, "Only the United States doctrine can buy city income claims.");
         return 0;
      } else {
         ClaimKey claim = currentClaim(player);
         if (!nation.get().id.equals(store.nationOwning(claim).map(owned -> owned.id).orElse(""))) {
            fail(player, "Stand in one of your nation's claims.");
            return 0;
         } else if (!store.addCityClaim(nation.get(), claim.id(), 500.0)) {
            fail(player, "This claim is already a city or your treasury needs $" + NationStore.roundMoney(500.0) + ".");
            return 0;
         } else {
            ok(player, "City income enabled for " + claim.shortName() + ".");
            return 1;
         }
      }
   }

   private static int unclaim(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
      ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
      NationStore store = NationStore.get();
      Optional<NationStore.Nation> nation = store.nationOf(player.getUUID());
      if (nation.isEmpty()) {
         fail(player, "Create or join a nation first.");
         return 0;
      } else if (!store.isOwner(player.getUUID(), nation.get())) {
         fail(player, "Only the nation owner can unclaim chunks.");
         return 0;
      } else {
         ClaimKey claim = currentClaim(player);
         if (!store.unclaim(nation.get(), claim)) {
            fail(player, "This is not your claim, or it is your capital.");
            return 0;
         } else {
            ok(player, "Unclaimed " + claim.shortName() + ".");
            return 1;
         }
      }
   }

   private static int nationBalance(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
      ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
      NationStore store = NationStore.get();
      Optional<NationStore.Nation> nation = store.nationOf(player.getUUID());
      if (nation.isEmpty()) {
         fail(player, "You are not in a nation.");
         return 0;
      } else {
         ok(player, "Player: $" + store.playerBalance(player.getUUID()) + " | " + nation.get().name + ": $" + NationStore.roundMoney(nation.get().balance));
         return 1;
      }
   }

   private static int deposit(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
      ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
      NationStore store = NationStore.get();
      Optional<NationStore.Nation> nation = store.nationOf(player.getUUID());
      if (nation.isEmpty()) {
         fail(player, "You are not in a nation.");
         return 0;
      } else {
         double amount = DoubleArgumentType.getDouble(context, "amount");
         if (!store.depositToNation(player.getUUID(), nation.get(), amount)) {
            fail(player, "You do not have enough money.");
            return 0;
         } else {
            ok(player, "Deposited $" + NationStore.roundMoney(amount) + " to " + nation.get().name + ".");
            return 1;
         }
      }
   }

   private static int doctrines(CommandContext<CommandSourceStack> context) {
      ((CommandSourceStack)context.getSource()).sendSuccess(() -> Component.literal("Doctrines: " + Doctrine.choices()), false);

      for (Doctrine doctrine : Doctrine.values()) {
         String header = doctrine.id + " - " + doctrine.displayName + " (" + doctrine.ideology.displayName + ")";
         String stats = "  free "
            + doctrine.freeClaims
            + " | claim x"
            + doctrine.claimCostMultiplier
            + " | maint x"
            + doctrine.maintenanceMultiplier
            + " | cap "
            + doctrine.captureSeconds
            + "s";
         ((CommandSourceStack)context.getSource()).sendSuccess(() -> Component.literal(header), false);
         ((CommandSourceStack)context.getSource()).sendSuccess(() -> Component.literal(stats), false);
      }

      return 1;
   }

   private static int openDoctrinesMenu(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
      ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
      player.openMenu(new SimpleMenuProvider((containerId, inventory, viewer) -> new DoctrineMenu(containerId, inventory), Component.literal("Doctrines")));
      return 1;
   }

   private static int syncOpac(CommandContext<CommandSourceStack> context) {
      OpacClaimsBridge.syncAll(((CommandSourceStack)context.getSource()).getServer(), NationStore.get());
      ((CommandSourceStack)context.getSource()).sendSuccess(() -> Component.literal("NationWars claims synced to Open Parties and Claims."), true);
      return 1;
   }

   private static int money(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
      ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
      ok(player, "Balance: $" + NationStore.get().playerBalance(player.getUUID()));
      return 1;
   }

   private static int openMarket(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
      ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
      player.openMenu(new SimpleMenuProvider((containerId, inventory, viewer) -> new MarketMenu(containerId, inventory), Component.literal("Market")));
      return 1;
   }

   private static int sellHand(CommandContext<CommandSourceStack> context, double requestedPrice) throws CommandSyntaxException {
      ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
      ItemStack stack = player.getMainHandItem();
      if (stack.isEmpty()) {
         fail(player, "Hold an item to sell it.");
         return 0;
      } else {
         double price = requestedPrice > 0.0 ? requestedPrice : appraise(stack);
         if (price <= 0.0) {
            fail(player, "Set a price with /market sellhand <price>.");
            return 0;
         } else {
            int count = stack.getCount();
            String itemName = stack.getHoverName().getString();
            ItemStack listed = stack.copy();
            stack.setCount(0);
            NationStore.MarketListing listing = NationStore.get().createMarketListing(player, listed, price);
            ok(player, "Listed " + count + "x " + itemName + " for $" + NationStore.roundMoney(price) + " as listing #" + listing.id + ".");
            return 1;
         }
      }
   }

   private static int cancelListing(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
      ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
      NationStore store = NationStore.get();
      int id = IntegerArgumentType.getInteger(context, "id");
      NationStore.MarketListing listing = store.marketListing(id).orElse(null);
      if (listing == null) {
         fail(player, "No listing with that id exists.");
         return 0;
      } else if (!listing.seller.equals(player.getUUID().toString())) {
         fail(player, "You can only cancel your own listings.");
         return 0;
      } else {
         ItemStack stack = store.listingStack(listing, player.registryAccess());
         store.removeMarketListing(id);
         if (!stack.isEmpty()) {
            player.getInventory().placeItemBackInInventory(stack);
         }

         ok(player, "Canceled listing #" + id + ".");
         return 1;
      }
   }

   private static int nations(CommandContext<CommandSourceStack> context) {
      NationStore store = NationStore.get();
      List<NationStore.Nation> nations = store.nationsSorted();
      if (nations.isEmpty()) {
         ((CommandSourceStack)context.getSource()).sendSuccess(() -> Component.literal("No nations exist yet."), false);
         return 1;
      } else {
         ((CommandSourceStack)context.getSource()).sendSuccess(() -> Component.literal("Nations:"), false);

         for (NationStore.Nation nation : nations) {
            ((CommandSourceStack)context.getSource())
               .sendSuccess(
                  () -> Component.literal(
                        "- "
                           + nation.name
                           + " | leader: "
                           + nation.ownerName
                           + " | claims: "
                           + store.claimCount(nation)
                           + " | members: "
                           + nation.members.size()
                     ),
                  false
               );
         }

         return 1;
      }
   }

   private static int spy(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
      ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
      NationStore store = NationStore.get();
      Optional<NationStore.Nation> spyNation = store.nationOf(player.getUUID());
      Optional<NationStore.Nation> target = store.nationByName(StringArgumentType.getString(context, "country"));
      if (!spyNation.isEmpty() && !target.isEmpty() && !spyNation.get().id.equals(target.get().id)) {
         Optional<NationStore.SpyMission> existingMission = store.activeSpyMission(player.getUUID());
         if (existingMission.isPresent()) {
            long secondsLeft = Math.max(1L, (existingMission.get().completeTick - (long)player.getServer().getTickCount()) / 20L);
            fail(player, "You already have spy mission #" + existingMission.get().id + " running. " + secondsLeft + " seconds left.");
            return 0;
         } else {
            long completeTick = (long)player.getServer().getTickCount() + 2400L;
            NationStore.SpyMission mission = store.createSpyMission(player, spyNation.get(), target.get(), completeTick);
            ok(player, "Spy mission #" + mission.id + " started against " + target.get().name + ". Report in 120 seconds.");
            return 1;
         }
      } else {
         fail(player, "Pick another existing nation to spy on.");
         return 0;
      }
   }

   private static int allianceCreate(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
      ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
      NationStore store = NationStore.get();
      Optional<NationStore.Nation> nation = store.nationOf(player.getUUID());
      if (!nation.isEmpty() && store.isOwner(player.getUUID(), nation.get())) {
         String name = StringArgumentType.getString(context, "name");
         if (!store.createAlliance(nation.get(), name)) {
            fail(player, "Could not create alliance. You may already be in one, or that name is taken.");
            return 0;
         } else {
            ok(player, "Created alliance " + name + ".");
            return 1;
         }
      } else {
         fail(player, "Only a nation owner can create an alliance.");
         return 0;
      }
   }

   private static int allianceInvite(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
      ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
      NationStore store = NationStore.get();
      Optional<NationStore.Nation> inviter = store.nationOf(player.getUUID());
      Optional<NationStore.Nation> invited = store.nationByName(StringArgumentType.getString(context, "country"));
      if (!inviter.isEmpty() && !invited.isEmpty()) {
         Optional<NationStore.Alliance> alliance = store.allianceOf(inviter.get());
         if (alliance.isEmpty()) {
            fail(player, "Your nation is not in an alliance.");
            return 0;
         } else if (!store.inviteToAlliance(alliance.get(), inviter.get(), invited.get())) {
            fail(player, "Could not invite that nation.");
            return 0;
         } else {
            store.notifyNation(
               player.getServer(),
               invited.get(),
               Component.literal(
                  "[NationWars] "
                     + inviter.get().name
                     + " invited you to alliance "
                     + alliance.get().name
                     + ". Use /alliance accept "
                     + alliance.get().id
                     + "."
               )
            );
            ok(player, "Alliance invite sent to " + invited.get().name + ".");
            return 1;
         }
      } else {
         fail(player, "Both sides must be nations.");
         return 0;
      }
   }

   private static int allianceAccept(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
      ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
      NationStore store = NationStore.get();
      Optional<NationStore.Nation> nation = store.nationOf(player.getUUID());
      Optional<NationStore.Alliance> alliance = store.allianceByName(StringArgumentType.getString(context, "name"));
      if (!nation.isEmpty() && !alliance.isEmpty()) {
         if (!store.isOwner(player.getUUID(), nation.get())) {
            fail(player, "Only the nation owner can accept alliance invites.");
            return 0;
         } else if (!store.acceptAllianceInvite(alliance.get(), nation.get())) {
            fail(player, "No invite from that alliance.");
            return 0;
         } else {
            store.notifyNation(player.getServer(), nation.get(), Component.literal("[NationWars] Joined alliance " + alliance.get().name + "."));
            return 1;
         }
      } else {
         fail(player, "No matching nation or alliance.");
         return 0;
      }
   }

   private static int allianceKick(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
      ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
      NationStore store = NationStore.get();
      Optional<NationStore.Nation> actor = store.nationOf(player.getUUID());
      Optional<NationStore.Nation> kicked = store.nationByName(StringArgumentType.getString(context, "country"));
      if (!actor.isEmpty() && !kicked.isEmpty()) {
         Optional<NationStore.Alliance> alliance = store.allianceOf(actor.get());
         if (!alliance.isEmpty() && store.kickFromAlliance(alliance.get(), actor.get(), kicked.get())) {
            store.notifyNation(player.getServer(), kicked.get(), Component.literal("[NationWars] You were kicked from alliance " + alliance.get().name + "."));
            ok(player, "Kicked " + kicked.get().name + " from " + alliance.get().name + ".");
            return 1;
         } else {
            fail(player, "Only the alliance leader can kick member nations.");
            return 0;
         }
      } else {
         fail(player, "Both sides must be nations.");
         return 0;
      }
   }

   private static int allianceInfo(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
      ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
      Optional<NationStore.Nation> nation = NationStore.get().nationOf(player.getUUID());
      if (nation.isEmpty()) {
         fail(player, "You are not in a nation.");
         return 0;
      } else {
         Optional<NationStore.Alliance> alliance = NationStore.get().allianceOf(nation.get());
         if (alliance.isEmpty()) {
            fail(player, "Your nation is not in an alliance.");
            return 0;
         } else {
            return describeAlliance((CommandSourceStack)context.getSource(), alliance.get());
         }
      }
   }

   private static int allianceInfoNamed(CommandContext<CommandSourceStack> context) {
      Optional<NationStore.Alliance> alliance = NationStore.get().allianceByName(StringArgumentType.getString(context, "name"));
      if (alliance.isEmpty()) {
         ((CommandSourceStack)context.getSource()).sendFailure(Component.literal("No alliance with that name exists."));
         return 0;
      } else {
         return describeAlliance((CommandSourceStack)context.getSource(), alliance.get());
      }
   }

   private static int alliances(CommandContext<CommandSourceStack> context) {
      NationStore store = NationStore.get();
      if (store.alliances().isEmpty()) {
         ((CommandSourceStack)context.getSource()).sendSuccess(() -> Component.literal("No alliances exist yet."), false);
         return 1;
      } else {
         for (NationStore.Alliance alliance : store.alliances()) {
            ((CommandSourceStack)context.getSource()).sendSuccess(() -> Component.literal(alliance.name + " | members: " + alliance.members.size()), false);
         }

         return 1;
      }
   }

   private static int describeAlliance(CommandSourceStack source, NationStore.Alliance alliance) {
      NationStore store = NationStore.get();
      String members = alliance.members
         .stream()
         .map(id -> store.nationById(id).map(nation -> nation.name).orElse(id))
         .sorted()
         .collect(Collectors.joining(", "));
      source.sendSuccess(() -> Component.literal("Alliance: " + alliance.name), false);
      source.sendSuccess(() -> Component.literal("Leader: " + store.nationById(alliance.leader).map(nation -> nation.name).orElse(alliance.leader)), false);
      source.sendSuccess(() -> Component.literal("Members: " + members), false);
      return 1;
   }

   private static int justifyWar(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
      ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
      NationStore store = NationStore.get();
      Optional<NationStore.Nation> attacker = store.nationOf(player.getUUID());
      Optional<NationStore.Nation> defender = store.nationByName(StringArgumentType.getString(context, "country"));
      if (attacker.isEmpty() || defender.isEmpty() || attacker.get().id.equals(defender.get().id)) {
         fail(player, "Pick another existing nation.");
         return 0;
      } else if (attacker.get().doctrine().pacifist) {
         fail(player, attacker.get().doctrine().displayName + " cannot justify wars. Join ongoing conflicts instead.");
         return 0;
      } else {
         Optional<NationStore.War> existing = store.warBetween(attacker.get(), defender.get());
         if (existing.isPresent() && existing.get().active) {
            fail(player, "That war is already active.");
            return 0;
         } else {
            NationStore.War war = store.getOrCreateWar(attacker.get(), defender.get());
            war.attacker = attacker.get().id;
            war.defender = defender.get().id;
            war.active = false;
            war.pendingDefenderResponse = false;
            war.attackerCapturedClaims.clear();
            war.peaceOffers.clear();
            war.attackerSide.clear();
            war.defenderSide.clear();
            war.attackerSide.add(attacker.get().id);
            war.defenderSide.add(defender.get().id);
            war.defenderStartingClaims = 0;
            int seconds = justificationSeconds(attacker.get(), defender.get());
            war.justificationCompleteTick = (long)player.getServer().getTickCount() + (long)seconds * 20L;
            store.save();
            store.notifyNation(player.getServer(), defender.get(), Component.literal(attacker.get().name + " is justifying a war against you."));
            ok(player, "War justification started. Ready in " + seconds + " seconds.");
            return 1;
         }
      }
   }

   private static int declareWar(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
      ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
      NationStore store = NationStore.get();
      Optional<NationStore.Nation> attacker = store.nationOf(player.getUUID());
      Optional<NationStore.Nation> defender = store.nationByName(StringArgumentType.getString(context, "country"));
      if (!attacker.isEmpty() && !defender.isEmpty()) {
         Optional<NationStore.War> maybeWar = store.warBetween(attacker.get(), defender.get());
         if (!maybeWar.isEmpty() && maybeWar.get().attacker.equals(attacker.get().id)) {
            NationStore.War war = maybeWar.get();
            MinecraftServer server = player.getServer();
            if ((long)server.getTickCount() < war.justificationCompleteTick) {
               long secondsLeft = Math.max(1L, (war.justificationCompleteTick - (long)server.getTickCount()) / 20L);
               fail(player, "Justification is not ready. " + secondsLeft + " seconds left.");
               return 0;
            } else if (attacker.get().doctrine().canRejectWarDeclarations) {
               war.pendingDefenderResponse = true;
               war.active = false;
               store.save();
               store.notifyNation(
                  server,
                  defender.get(),
                  Component.literal(
                     "[NationWars] "
                        + attacker.get().name
                        + " declared war. Their doctrine lets you reject it. Use /war accept "
                        + attacker.get().id
                        + " or /war reject "
                        + attacker.get().id
                        + "."
                  )
               );
               store.notifyNation(server, attacker.get(), Component.literal("[NationWars] War declaration sent to " + defender.get().name + " for response."));
               return 1;
            } else {
               war.active = true;
               war.pendingDefenderResponse = false;
               war.defenderStartingClaims = store.claimCount(defender.get());
               store.save();
               createAllianceDefenseCalls(server, store, war, defender.get());
               store.notifyNation(server, defender.get(), Component.literal(attacker.get().name + " declared war on you."));
               store.notifyNation(server, attacker.get(), Component.literal("War declared on " + defender.get().name + "."));
               return 1;
            }
         } else {
            fail(player, "You need to justify this war first.");
            return 0;
         }
      } else {
         fail(player, "Pick an existing target nation.");
         return 0;
      }
   }

   private static int acceptWarDeclaration(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
      ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
      NationStore store = NationStore.get();
      Optional<NationStore.Nation> defender = store.nationOf(player.getUUID());
      Optional<NationStore.Nation> attacker = store.nationByName(StringArgumentType.getString(context, "country"));
      if (!defender.isEmpty() && !attacker.isEmpty()) {
         Optional<NationStore.War> maybeWar = store.warBetween(attacker.get(), defender.get());
         if (!maybeWar.isEmpty() && maybeWar.get().pendingDefenderResponse && maybeWar.get().defender.equals(defender.get().id)) {
            NationStore.War war = maybeWar.get();
            war.active = true;
            war.pendingDefenderResponse = false;
            war.defenderStartingClaims = store.claimCount(defender.get());
            store.save();
            createAllianceDefenseCalls(player.getServer(), store, war, defender.get());
            store.notifyNation(player.getServer(), attacker.get(), Component.literal("[NationWars] " + defender.get().name + " accepted the war declaration."));
            store.notifyNation(player.getServer(), defender.get(), Component.literal("[NationWars] War accepted against " + attacker.get().name + "."));
            return 1;
         } else {
            fail(player, "There is no pending war declaration from that nation.");
            return 0;
         }
      } else {
         fail(player, "Both sides must be nations.");
         return 0;
      }
   }

   private static int rejectWarDeclaration(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
      ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
      NationStore store = NationStore.get();
      Optional<NationStore.Nation> defender = store.nationOf(player.getUUID());
      Optional<NationStore.Nation> attacker = store.nationByName(StringArgumentType.getString(context, "country"));
      if (!defender.isEmpty() && !attacker.isEmpty()) {
         Optional<NationStore.War> maybeWar = store.warBetween(attacker.get(), defender.get());
         if (!maybeWar.isEmpty() && maybeWar.get().pendingDefenderResponse && maybeWar.get().defender.equals(defender.get().id)) {
            store.endWar(maybeWar.get());
            store.notifyNation(player.getServer(), attacker.get(), Component.literal("[NationWars] " + defender.get().name + " rejected the war declaration."));
            store.notifyNation(player.getServer(), defender.get(), Component.literal("[NationWars] War declaration rejected."));
            return 1;
         } else {
            fail(player, "There is no pending war declaration from that nation.");
            return 0;
         }
      } else {
         fail(player, "Both sides must be nations.");
         return 0;
      }
   }

   private static int requestWarJoin(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
      ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
      NationStore store = NationStore.get();
      Optional<NationStore.Nation> requester = store.nationOf(player.getUUID());
      Optional<NationStore.Nation> sponsor = store.nationByName(StringArgumentType.getString(context, "country"));
      if (!requester.isEmpty() && !sponsor.isEmpty() && !requester.get().id.equals(sponsor.get().id)) {
         Optional<NationStore.War> maybeWar = store.firstActiveWarOf(sponsor.get());
         if (maybeWar.isEmpty()) {
            fail(player, sponsor.get().name + " is not in an active war.");
            return 0;
         } else if (!store.addWarJoinRequest(maybeWar.get(), requester.get(), sponsor.get())) {
            fail(player, "Could not request to join that war.");
            return 0;
         } else {
            store.notifyNation(
               player.getServer(),
               sponsor.get(),
               Component.literal(
                  "[NationWars] "
                     + requester.get().name
                     + " wants to join your war. Use /war acceptjoin "
                     + requester.get().id
                     + " or /war rejectjoin "
                     + requester.get().id
                     + "."
               )
            );
            ok(player, "War join request sent to " + sponsor.get().name + ".");
            return 1;
         }
      } else {
         fail(player, "Pick another nation in an active war.");
         return 0;
      }
   }

   private static int acceptWarJoin(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
      ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
      NationStore store = NationStore.get();
      Optional<NationStore.Nation> acceptor = store.nationOf(player.getUUID());
      Optional<NationStore.Nation> requester = store.nationByName(StringArgumentType.getString(context, "country"));
      if (!acceptor.isEmpty() && !requester.isEmpty()) {
         for (NationStore.War war : store.activeWarsOf(acceptor.get())) {
            if (store.acceptWarJoinRequest(war, requester.get(), acceptor.get())) {
               store.notifyNation(
                  player.getServer(), requester.get(), Component.literal("[NationWars] " + acceptor.get().name + " accepted your war join request.")
               );
               store.notifyNation(player.getServer(), acceptor.get(), Component.literal("[NationWars] " + requester.get().name + " joined your war."));
               return 1;
            }
         }

         fail(player, "No pending join request from that nation.");
         return 0;
      } else {
         fail(player, "Both sides must be nations.");
         return 0;
      }
   }

   private static int acceptAllianceDefense(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
      ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
      NationStore store = NationStore.get();
      Optional<NationStore.Nation> ally = store.nationOf(player.getUUID());
      Optional<NationStore.Nation> caller = store.nationByName(StringArgumentType.getString(context, "country"));
      if (!ally.isEmpty() && !caller.isEmpty()) {
         for (NationStore.War war : store.activeWarsOf(caller.get())) {
            if (store.acceptWarDefenseCall(war, ally.get(), caller.get())) {
               store.notifyNation(
                  player.getServer(), caller.get(), Component.literal("[NationWars] " + ally.get().name + " answered the alliance defense call.")
               );
               store.notifyNation(player.getServer(), ally.get(), Component.literal("[NationWars] Joined the war to defend " + caller.get().name + "."));
               return 1;
            }
         }

         fail(player, "No pending alliance defense call from that nation.");
         return 0;
      } else {
         fail(player, "Both sides must be nations.");
         return 0;
      }
   }

   private static int declineAllianceDefense(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
      ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
      NationStore store = NationStore.get();
      Optional<NationStore.Nation> ally = store.nationOf(player.getUUID());
      Optional<NationStore.Nation> caller = store.nationByName(StringArgumentType.getString(context, "country"));
      if (!ally.isEmpty() && !caller.isEmpty()) {
         for (NationStore.War war : store.activeWarsOf(caller.get())) {
            if (store.rejectWarDefenseCall(war, ally.get(), caller.get())) {
               if (ally.get().doctrine() == Doctrine.FRENCH && caller.get().doctrine().ideology == Ideology.DEMOCRATIC) {
                  double penalty = Math.min(ally.get().balance, 300.0);
                  ally.get().balance = NationStore.roundMoney(ally.get().balance - penalty);
                  store.save();
                  store.notifyNation(
                     player.getServer(),
                     ally.get(),
                     Component.literal(
                        "[NationWars] Casus Foederis penalty: $" + NationStore.roundMoney(penalty) + " lost for declining to defend " + caller.get().name + "."
                     )
                  );
               } else {
                  ok(player, "Declined the alliance defense call from " + caller.get().name + ".");
               }

               store.notifyNation(
                  player.getServer(), caller.get(), Component.literal("[NationWars] " + ally.get().name + " declined your alliance defense call.")
               );
               return 1;
            }
         }

         fail(player, "No pending alliance defense call from that nation.");
         return 0;
      } else {
         fail(player, "Both sides must be nations.");
         return 0;
      }
   }

   private static int rejectWarJoin(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
      ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
      NationStore store = NationStore.get();
      Optional<NationStore.Nation> rejector = store.nationOf(player.getUUID());
      Optional<NationStore.Nation> requester = store.nationByName(StringArgumentType.getString(context, "country"));
      if (!rejector.isEmpty() && !requester.isEmpty()) {
         for (NationStore.War war : store.activeWarsOf(rejector.get())) {
            if (store.rejectWarJoinRequest(war, requester.get(), rejector.get())) {
               store.notifyNation(
                  player.getServer(), requester.get(), Component.literal("[NationWars] " + rejector.get().name + " rejected your war join request.")
               );
               ok(player, "Rejected war join request from " + requester.get().name + ".");
               return 1;
            }
         }

         fail(player, "No pending join request from that nation.");
         return 0;
      } else {
         fail(player, "Both sides must be nations.");
         return 0;
      }
   }

   private static int leaveWar(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
      ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
      NationStore store = NationStore.get();
      Optional<NationStore.Nation> own = store.nationOf(player.getUUID());
      Optional<NationStore.Nation> other = store.nationByName(StringArgumentType.getString(context, "country"));
      if (own.isEmpty() || other.isEmpty()) {
         fail(player, "Both sides must be nations.");
         return 0;
      } else if (!own.get().doctrine().canLeaveWarSafely) {
         fail(player, "Only Romania can leave a war this way.");
         return 0;
      } else {
         long tick = (long)player.getServer().getTickCount();
         long cooldownTicks = 36000L;
         if (own.get().lastSpecialWarLeaveTick > 0L && tick - own.get().lastSpecialWarLeaveTick < cooldownTicks) {
            long secondsLeft = (cooldownTicks - (tick - own.get().lastSpecialWarLeaveTick)) / 20L;
            fail(player, "King Michael's Coup is on cooldown for " + secondsLeft + " seconds.");
            return 0;
         } else {
            Optional<NationStore.War> maybeWar = store.activeWarForCapture(own.get(), other.get()).or(() -> store.activeWarForCapture(other.get(), own.get()));
            if (maybeWar.isEmpty()) {
               fail(player, "There is no active war with that nation.");
               return 0;
            } else {
               own.get().lastSpecialWarLeaveTick = tick;
               store.leaveWarSafely(maybeWar.get(), own.get());
               store.notifyNation(player.getServer(), own.get(), Component.literal("[NationWars] Left the war with no consequences."));
               store.notifyNation(
                  player.getServer(), other.get(), Component.literal("[NationWars] " + own.get().name + " left the war through King Michael's Coup.")
               );
               return 1;
            }
         }
      }
   }

   private static int warOverview(CommandContext<CommandSourceStack> context) {
      ((CommandSourceStack)context.getSource()).sendSuccess(() -> Component.literal("War commands:"), false);
      ((CommandSourceStack)context.getSource()).sendSuccess(() -> Component.literal("/war status"), false);
      ((CommandSourceStack)context.getSource()).sendSuccess(() -> Component.literal("/war justify <nation> | /war declare <nation>"), false);
      ((CommandSourceStack)context.getSource())
         .sendSuccess(() -> Component.literal("/war join <nation> | /war acceptjoin <nation> | /war rejectjoin <nation>"), false);
      ((CommandSourceStack)context.getSource()).sendSuccess(() -> Component.literal("/war defend <nation> | /war declinedefense <nation>"), false);
      ((CommandSourceStack)context.getSource()).sendSuccess(() -> Component.literal("/peace <nation> | /surrender <nation>"), false);
      return 1;
   }

   private static int warStatus(CommandContext<CommandSourceStack> context) {
      NationStore store = NationStore.get();
      List<NationStore.War> wars = store.wars().stream().sorted(Comparator.comparing(warx -> warx.id)).toList();
      if (wars.isEmpty()) {
         ((CommandSourceStack)context.getSource()).sendSuccess(() -> Component.literal("No wars or justifications."), false);
         return 1;
      } else {
         MinecraftServer server = ((CommandSourceStack)context.getSource()).getServer();

         for (NationStore.War war : wars) {
            String attacker = store.nationById(war.attacker).map(nation -> nation.name).orElse(war.attacker);
            String defender = store.nationById(war.defender).map(nation -> nation.name).orElse(war.defender);
            ((CommandSourceStack)context.getSource()).sendSuccess(() -> Component.literal(attacker + " vs " + defender), false);
            if (war.active) {
               ((CommandSourceStack)context.getSource()).sendSuccess(() -> Component.literal("  active"), false);
               ((CommandSourceStack)context.getSource()).sendSuccess(() -> Component.literal("  attackers: " + nationNames(store, war.attackerSide)), false);
               ((CommandSourceStack)context.getSource()).sendSuccess(() -> Component.literal("  defenders: " + nationNames(store, war.defenderSide)), false);
               if (!war.joinRequests.isEmpty()) {
                  ((CommandSourceStack)context.getSource())
                     .sendSuccess(() -> Component.literal("  pending: " + nationNames(store, war.joinRequests.keySet())), false);
               }
            } else if (war.pendingDefenderResponse) {
               ((CommandSourceStack)context.getSource()).sendSuccess(() -> Component.literal("  waiting for defender response"), false);
            } else {
               long secondsLeft = Math.max(0L, (war.justificationCompleteTick - (long)server.getTickCount()) / 20L);
               ((CommandSourceStack)context.getSource()).sendSuccess(() -> Component.literal("  justifying: " + secondsLeft + "s left"), false);
            }
         }

         return 1;
      }
   }

   private static void createAllianceDefenseCalls(MinecraftServer server, NationStore store, NationStore.War war, NationStore.Nation defender) {
      if (defender.doctrine().ideology == Ideology.DEMOCRATIC) {
         store.allianceOf(defender)
            .ifPresent(
               alliance -> {
                  for (String allyId : alliance.members) {
                     if (!allyId.equals(defender.id)) {
                        NationStore.Nation ally = store.nationById(allyId).orElse(null);
                        if (ally != null && !store.isWarParticipant(war, ally) && store.addWarDefenseCall(war, ally, defender)) {
                           store.notifyNation(
                              server,
                              ally,
                              Component.literal(
                                 "[NationWars] Democratic ally "
                                    + defender.name
                                    + " called for defense. Use /war defend "
                                    + defender.id
                                    + " or /war declinedefense "
                                    + defender.id
                                    + "."
                              )
                           );
                        }
                     }
                  }
               }
            );
      }
   }

   private static int peace(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
      ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
      NationStore store = NationStore.get();
      Optional<NationStore.Nation> own = store.nationOf(player.getUUID());
      Optional<NationStore.Nation> other = store.nationByName(StringArgumentType.getString(context, "country"));
      if (!own.isEmpty() && !other.isEmpty()) {
         Optional<NationStore.War> maybeWar = store.activeWarForCapture(own.get(), other.get()).or(() -> store.activeWarForCapture(other.get(), own.get()));
         if (maybeWar.isEmpty()) {
            fail(player, "There is no active war with that nation.");
            return 0;
         } else {
            NationStore.War war = maybeWar.get();
            player.openMenu(
               new SimpleMenuProvider(
                  (containerId, inventory, viewer) -> new PeaceDealMenu(containerId, inventory, own.get(), other.get(), war),
                  Component.literal("Offer Peace Deal")
               )
            );
            return 1;
         }
      } else {
         fail(player, "Both sides must be nations.");
         return 0;
      }
   }

   private static int surrender(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
      ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
      NationStore store = NationStore.get();
      Optional<NationStore.Nation> own = store.nationOf(player.getUUID());
      Optional<NationStore.Nation> other = store.nationByName(StringArgumentType.getString(context, "country"));
      if (!own.isEmpty() && !other.isEmpty()) {
         Optional<NationStore.War> maybeWar = store.warBetween(own.get(), other.get()).filter(warx -> warx.active);
         if (maybeWar.isEmpty()) {
            fail(player, "There is no active war with that nation.");
            return 0;
         } else {
            NationStore.War war = maybeWar.get();
            if (war.defender.equals(own.get().id)) {
               int target = Math.max(
                  (int)Math.ceil((double)war.defenderStartingClaims * 0.25 * own.get().doctrine().surrenderLandMultiplier), war.attackerCapturedClaims.size()
               );

               while (war.attackerCapturedClaims.size() < target) {
                  Optional<String> claim = store.borderClaimsOf(own.get()).stream().filter(id -> !id.equals(own.get().capitalClaim)).findFirst();
                  if (claim.isEmpty()) {
                     break;
                  }

                  store.transferClaim(claim.get(), other.get());
                  war.attackerCapturedClaims.add(claim.get());
               }

               store.endWar(war);
               store.notifyNation(player.getServer(), own.get(), Component.literal("You surrendered to " + other.get().name + "."));
               store.notifyNation(player.getServer(), other.get(), Component.literal(own.get().name + " surrendered."));
               return 1;
            } else if (!war.attacker.equals(own.get().id)) {
               fail(player, "This nation is not part of that war.");
               return 0;
            } else {
               for (String claimId : war.attackerCapturedClaims) {
                  store.transferClaim(claimId, other.get());
               }

               store.endWar(war);
               store.notifyNation(player.getServer(), own.get(), Component.literal("You surrendered and returned captured claims to " + other.get().name + "."));
               store.notifyNation(player.getServer(), other.get(), Component.literal(own.get().name + " surrendered and returned captured claims."));
               return 1;
            }
         }
      } else {
         fail(player, "Both sides must be nations.");
         return 0;
      }
   }

   private static ClaimKey currentClaim(ServerPlayer player) {
      ServerLevel level = player.serverLevel();
      ChunkPos chunk = player.chunkPosition();
      return ClaimKey.of(level, chunk);
   }

   private static boolean touchesNationClaim(NationStore store, NationStore.Nation nation, ClaimKey claim) {
      for (String owned : store.claimsOf(nation)) {
         if (claim.touches(ClaimKey.parse(owned))) {
            return true;
         }
      }

      return false;
   }

   private static double claimCost(NationStore store, NationStore.Nation nation, ClaimKey claim) {
      double expansion = 1.0 + (double)Math.max(0, store.claimCount(nation) - 1) * 0.1;
      double distance = 1.0;
      if (nation.doctrine().distanceClaimScaling && nation.capitalClaim != null && !nation.capitalClaim.isBlank()) {
         ClaimKey capital = ClaimKey.parse(nation.capitalClaim);
         if (capital.dimension().equals(claim.dimension())) {
            int chunks = Math.abs(capital.x() - claim.x()) + Math.abs(capital.z() - claim.z());
            distance += (double)chunks * 0.03;
         }
      }

      return NationStore.roundMoney(100.0 * expansion * distance * nation.doctrine().claimCostMultiplier);
   }

   private static int justificationSeconds(NationStore.Nation attacker, NationStore.Nation defender) {
      Doctrine attacking = attacker.doctrine();
      Doctrine defending = defender.doctrine();
      double multiplier = attacking.justificationMultiplier * defending.incomingJustificationMultiplier;
      if (attacking.ideology == defending.ideology) {
         multiplier *= 1.5;
      }

      return Math.max(30, (int)Math.round((double)attacking.justificationSeconds * multiplier));
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
      List<String> names = new ArrayList<>();

      for (String id : ids) {
         names.add(store.nationById(id).map(nation -> nation.name).orElse(id));
      }

      names.sort(String::compareToIgnoreCase);
      return String.join(",", names);
   }

   private static void ok(ServerPlayer player, String message) {
      player.sendSystemMessage(Component.literal("[NationWars] " + message));
   }

   private static void fail(ServerPlayer player, String message) {
      player.sendSystemMessage(Component.literal("[NationWars] " + message));
   }
}
