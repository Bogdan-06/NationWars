package dev.moth.nationwars;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal(
                                          "nation"
                                       )
                                       .requires(source -> source.hasPermission(0)))
                                    .then(Commands.literal("doctrines").executes(NationCommands::doctrines)))
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
               .then(Commands.literal("balance").executes(NationCommands::nationBalance)))
            .then(Commands.literal("deposit").then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01)).executes(NationCommands::deposit)))
      );
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("war")
                     .requires(source -> source.hasPermission(0)))
                  .then(Commands.literal("justify").then(Commands.argument("country", StringArgumentType.word()).executes(NationCommands::justifyWar))))
               .then(Commands.literal("declare").then(Commands.argument("country", StringArgumentType.word()).executes(NationCommands::declareWar))))
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
      player.sendSystemMessage(Component.literal("Doctrine: " + doctrine.displayName + " (" + doctrine.id + ")"));
      player.sendSystemMessage(Component.literal("Claims: " + store.claimCount(nation) + ", free claims left: " + nation.freeClaimsRemaining));
      player.sendSystemMessage(Component.literal("Treasury: $" + NationStore.roundMoney(nation.balance)));
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
               double cost = claimCost(store, nation.get());
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
      ((CommandSourceStack)context.getSource())
         .sendSuccess(
            () -> Component.literal("american: income | soviet: cheap land | british: maintenance | german: war speed | romanian: market income"), false
         );
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

   private static int justifyWar(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
      ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
      NationStore store = NationStore.get();
      Optional<NationStore.Nation> attacker = store.nationOf(player.getUUID());
      Optional<NationStore.Nation> defender = store.nationByName(StringArgumentType.getString(context, "country"));
      if (!attacker.isEmpty() && !defender.isEmpty() && !attacker.get().id.equals(defender.get().id)) {
         Optional<NationStore.War> existing = store.warBetween(attacker.get(), defender.get());
         if (existing.isPresent() && existing.get().active) {
            fail(player, "That war is already active.");
            return 0;
         } else {
            NationStore.War war = store.getOrCreateWar(attacker.get(), defender.get());
            war.attacker = attacker.get().id;
            war.defender = defender.get().id;
            war.active = false;
            war.attackerCapturedClaims.clear();
            war.peaceOffers.clear();
            war.defenderStartingClaims = 0;
            int seconds = (int)Math.round((double)attacker.get().doctrine().justificationSeconds * attacker.get().doctrine().justificationMultiplier);
            war.justificationCompleteTick = (long)player.getServer().getTickCount() + (long)seconds * 20L;
            store.save();
            store.notifyNation(player.getServer(), defender.get(), Component.literal(attacker.get().name + " is justifying a war against you."));
            ok(player, "War justification started. Ready in " + seconds + " seconds.");
            return 1;
         }
      } else {
         fail(player, "Pick another existing nation.");
         return 0;
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
            } else {
               war.active = true;
               war.defenderStartingClaims = store.claimCount(defender.get());
               store.save();
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

   private static int warStatus(CommandContext<CommandSourceStack> context) {
      NationStore store = NationStore.get();
      List<NationStore.War> wars = store.wars().stream().sorted(Comparator.comparing(warx -> warx.id)).toList();
      if (wars.isEmpty()) {
         ((CommandSourceStack)context.getSource()).sendSuccess(() -> Component.literal("No wars or justifications."), false);
         return 1;
      } else {
         MinecraftServer server = ((CommandSourceStack)context.getSource()).getServer();

         for (NationStore.War war : wars) {
            String state = war.active
               ? "active"
               : "justifying, " + Math.max(0L, (war.justificationCompleteTick - (long)server.getTickCount()) / 20L) + "s left";
            ((CommandSourceStack)context.getSource()).sendSuccess(() -> Component.literal(war.attacker + " -> " + war.defender + ": " + state), false);
         }

         return 1;
      }
   }

   private static int peace(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
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
            war.peaceOffers.add(own.get().id);
            if (war.peaceOffers.contains(other.get().id)) {
               store.endWar(war);
               store.notifyNation(player.getServer(), own.get(), Component.literal("Peace accepted with " + other.get().name + "."));
               store.notifyNation(player.getServer(), other.get(), Component.literal("Peace accepted with " + own.get().name + "."));
            } else {
               store.save();
               store.notifyNation(
                  player.getServer(), other.get(), Component.literal(own.get().name + " offered peace. Use /peace " + own.get().id + " to accept.")
               );
               ok(player, "Peace offer sent.");
            }

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
               int target = Math.max((int)Math.ceil((double)war.defenderStartingClaims * 0.25), war.attackerCapturedClaims.size());

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

   private static double claimCost(NationStore store, NationStore.Nation nation) {
      double expansion = 1.0 + (double)Math.max(0, store.claimCount(nation) - 1) * 0.1;
      return NationStore.roundMoney(100.0 * expansion * nation.doctrine().claimCostMultiplier);
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

   private static void ok(ServerPlayer player, String message) {
      player.sendSystemMessage(Component.literal("[NationWars] " + message));
   }

   private static void fail(ServerPlayer player, String message) {
      player.sendSystemMessage(Component.literal("[NationWars] " + message));
   }
}
