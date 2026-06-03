package dev.moth.nationwars;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.BossEvent.BossBarColor;
import net.minecraft.world.BossEvent.BossBarOverlay;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.TabListNameFormat;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock;
import net.neoforged.neoforge.event.level.BlockEvent.BreakEvent;
import net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent.Detonate;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent.Post;
import xaero.pac.common.server.api.OpenPACServerAPI;

public final class NationEvents {
   private static final int INCOME_INTERVAL_TICKS = 1200;
   private static final int MAINTENANCE_INTERVAL_TICKS = 12000;
   private static final double CAPITAL_INCOME = 12.0;
   private static final double MAINTENANCE_PER_CLAIM = 8.0;
   private static final double ACCESS_FEE = 50.0;
   private static final double CITY_INCOME = 6.0;
   private static final double BUILD_REWARD = 5.0;
   private static final double BUILD_REWARD_CHANCE = 0.2;
   private static final double CAPTURED_CLAIM_MAINTENANCE_MULTIPLIER = 2.0;
   private static final long ACTION_ACCESS_PASS_TICKS = 2L;
   private static final int OPAC_SYNC_RETRY_INTERVAL_TICKS = 200;
   private static final Random RANDOM = new Random();
   private static final Set<String> NO_BUILD_REWARD_BLOCKS = Set.of(
      "dirt",
      "grass_block",
      "coarse_dirt",
      "rooted_dirt",
      "podzol",
      "mycelium",
      "mud",
      "sand",
      "red_sand",
      "gravel",
      "clay",
      "stone",
      "cobblestone",
      "deepslate",
      "cobbled_deepslate",
      "netherrack",
      "end_stone",
      "snow",
      "snow_block",
      "ice",
      "packed_ice",
      "blue_ice",
      "oak_leaves",
      "spruce_leaves",
      "birch_leaves",
      "jungle_leaves",
      "acacia_leaves",
      "dark_oak_leaves",
      "mangrove_leaves",
      "cherry_leaves",
      "azalea_leaves",
      "flowering_azalea_leaves",
      "short_grass",
      "tall_grass",
      "fern",
      "large_fern",
      "torch",
      "soul_torch",
      "redstone_torch",
      "scaffolding"
   );
   private static long nextIncomeTick = -1L;
   private static long nextMaintenanceTick = -1L;
   private static long nextOpacSyncTick = -1L;
   private static int opacSyncRetries = 0;
   private static final Map<String, NationEvents.CaptureProgress> CAPTURE_PROGRESS = new HashMap<>();
   private static final Map<String, ServerBossEvent> CAPTURE_BARS = new HashMap<>();
   private static final Map<String, Long> ACCESS_COOLDOWNS = new HashMap<>();
   private static final Map<String, Long> BUILD_REWARD_COOLDOWNS = new HashMap<>();
   private static final Map<UUID, Long> OPAC_FULL_PASSES = new HashMap<>();

   private NationEvents() {
   }

   @SubscribeEvent
   public static void serverStarted(ServerStartedEvent event) {
      OpacClaimsBridge.forceMaxPlayerClaimsZero();
      NationStore.load(event.getServer());
      long tick = (long)event.getServer().getTickCount();
      nextIncomeTick = tick + 1200L;
      nextMaintenanceTick = tick + 12000L;
      scheduleOpacSync(tick + 100L, 3);
   }

   @SubscribeEvent
   public static void serverStopping(ServerStoppingEvent event) {
      NationStore.get().save();
      clearOpacPasses(event.getServer());
      clearCaptureBars();
      CAPTURE_PROGRESS.clear();
      ACCESS_COOLDOWNS.clear();
      BUILD_REWARD_COOLDOWNS.clear();
      nextOpacSyncTick = -1L;
      opacSyncRetries = 0;
   }

   @SubscribeEvent
   public static void serverTick(Post event) {
      MinecraftServer server = event.getServer();
      long tick = (long)server.getTickCount();
      if (nextIncomeTick < 0L) {
         nextIncomeTick = tick + 1200L;
      }

      if (nextMaintenanceTick < 0L) {
         nextMaintenanceTick = tick + 12000L;
      }

      if (opacSyncRetries > 0 && tick >= nextOpacSyncTick) {
         syncOpac(server);
         opacSyncRetries--;
         nextOpacSyncTick = opacSyncRetries > 0 ? tick + 200L : -1L;
      }

      if (tick >= nextIncomeTick) {
         payPassiveIncome(server);
         nextIncomeTick = tick + 1200L;
      }

      if (tick >= nextMaintenanceTick) {
         chargeMaintenance(server);
         nextMaintenanceTick = tick + 12000L;
      }

      decayCaptureProgress(server, tick);
      completeSpyMissions(server, tick);
      cleanupCooldowns(server, tick);
   }

   @SubscribeEvent
   public static void playerLoggedIn(PlayerLoggedInEvent event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         long var4 = (long)player.getServer().getTickCount();
         if (opacSyncRetries <= 0) {
            scheduleOpacSync(var4 + 40L, 1);
         } else {
            nextOpacSyncTick = Math.min(nextOpacSyncTick, var4 + 40L);
         }

         player.refreshTabListName();
      }
   }

   @SubscribeEvent
   public static void tabListName(TabListNameFormat event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         try {
            Optional<NationStore.Nation> nation = NationStore.get().nationOf(player.getUUID());
            if (nation.isPresent()) {
               event.setDisplayName(
                  Component.literal("[" + nation.get().name + "] ")
                     .withStyle(ChatFormatting.GOLD)
                     .append(Component.literal(player.getGameProfile().getName()).withStyle(ChatFormatting.WHITE))
               );
            } else {
               event.setDisplayName(
                  Component.literal("[No Nation] ")
                     .withStyle(ChatFormatting.DARK_GRAY)
                     .append(Component.literal(player.getGameProfile().getName()).withStyle(ChatFormatting.GRAY))
               );
            }
         } catch (IllegalStateException var3) {
            event.setDisplayName(null);
         }
      }
   }

   @SubscribeEvent
   public static void playerTick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) {
      if (event.getEntity() instanceof ServerPlayer player && player.tickCount % 20 == 0) {
         if (player.tickCount % 100 == 0) {
            player.refreshTabListName();
         }

         applyClaimedLandEffects(player);
         handleCaptureTick(player);
         return;
      }
   }

   @SubscribeEvent(
      priority = EventPriority.HIGHEST
   )
   public static void blockBreakAccess(BreakEvent event) {
      if (event.getPlayer() instanceof ServerPlayer player) {
         grantClaimActionPassIfNeeded(player, event.getPos(), (long)player.getServer().getTickCount());
      }
   }

   @SubscribeEvent(
      priority = EventPriority.LOWEST
   )
   public static void blockBreakReward(BreakEvent event) {
      if (event.getPlayer() instanceof ServerPlayer player) {
         if (!event.isCanceled()) {
            double reward = rewardForBrokenBlock(player, event.getState());
            if (!(reward <= 0.0)) {
               NationStore.get().addPlayerMoney(player.getUUID(), reward);
               player.displayClientMessage(Component.literal("+$" + NationStore.roundMoney(reward)), true);
            }
         }
      }
   }

   @SubscribeEvent(
      priority = EventPriority.HIGHEST
   )
   public static void blockPlaceAccess(EntityPlaceEvent event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         grantClaimActionPassIfNeeded(player, event.getPos(), (long)player.getServer().getTickCount());
      }
   }

   @SubscribeEvent(
      priority = EventPriority.HIGHEST
   )
   public static void explosionAccess(Detonate event) {
      if (event.getLevel() instanceof ServerLevel level) {
         if (event.getExplosion().getIndirectSourceEntity() instanceof ServerPlayer player) {
            long var7 = (long)player.getServer().getTickCount();
            boolean removedBlockedClaimBlock = event.getAffectedBlocks().removeIf(pos -> !canExplosionAffect(player, level, pos));
            if (!event.getAffectedBlocks().isEmpty() || removedBlockedClaimBlock) {
               grantActionOpacPass(player, var7);
            }
         }
      }
   }

   @SubscribeEvent(
      priority = EventPriority.LOWEST
   )
   public static void blockPlaceReward(EntityPlaceEvent event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         long tick = (long)player.getServer().getTickCount();
         if (!event.isCanceled() && !doesNotPayBuildReward(event.getPlacedBlock().getBlock())) {
            double reward = buildRewardForPlacedBlock(player, event.getPos());
            if (!(reward <= 0.0)) {
               String key = player.getUUID() + ":build";
               if (BUILD_REWARD_COOLDOWNS.getOrDefault(key, 0L) <= tick) {
                  BUILD_REWARD_COOLDOWNS.put(key, tick + 20L);
                  if (!(RANDOM.nextDouble() > 0.2)) {
                     NationStore.get().addPlayerMoney(player.getUUID(), reward);
                     player.displayClientMessage(Component.literal("+$" + reward + " building"), true);
                  }
               }
            }
         }
      }
   }

   @SubscribeEvent(
      priority = EventPriority.HIGHEST
   )
   public static void rightClickBlock(RightClickBlock event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         ServerLevel var11 = player.serverLevel();
         BlockPos pos = event.getPos();
         long tick = (long)player.getServer().getTickCount();
         if (isFluidBucket(player.getItemInHand(event.getHand()))) {
            if (shouldCancelFluidBucketUse(player, var11, pos, event.getFace(), tick)) {
               event.setCancellationResult(InteractionResult.FAIL);
               event.setCanceled(true);
            }
         } else {
            BlockState state = var11.getBlockState(pos);
            if (isPaidAccessBlock(state.getBlock())) {
               NationStore store = NationStore.get();
               ClaimKey claim = ClaimKey.of(var11, new ChunkPos(pos));
               Optional<NationStore.Nation> owner = store.nationOwning(claim);
               if (!owner.isEmpty() && !store.isMember(player.getUUID(), owner.get())) {
                  if (isActiveWarClaim(player, owner.get())) {
                     grantActionOpacPass(player, tick);
                  } else {
                     String cooldownKey = player.getUUID() + ":" + pos.asLong();
                     if (ACCESS_COOLDOWNS.getOrDefault(cooldownKey, 0L) <= tick) {
                        if (!store.withdrawPlayerMoney(player.getUUID(), 50.0)) {
                           event.setCanceled(true);
                           player.sendSystemMessage(Component.literal("[NationWars] You need $50.0 to open this in " + owner.get().name + "."));
                        } else {
                           ACCESS_COOLDOWNS.put(cooldownKey, tick + 60L);
                           grantActionOpacPass(player, tick);
                           player.displayClientMessage(Component.literal("-$50.0 access fee"), true);
                           store.notifyNation(
                              player.getServer(),
                              owner.get(),
                              Component.literal(
                                 player.getGameProfile().getName()
                                    + " paid to open a protected block at "
                                    + pos.getX()
                                    + ", "
                                    + pos.getY()
                                    + ", "
                                    + pos.getZ()
                                    + "."
                              )
                           );
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static void payPassiveIncome(MinecraftServer server) {
      NationStore store = NationStore.get();

      for (NationStore.Nation nation : store.nations()) {
         double income = passiveIncomePerMinute(store, nation);
         if (income > 0.0) {
            nation.balance = NationStore.roundMoney(nation.balance + income);
         }
      }

      store.save();
   }

   static double passiveIncomePerTenMinutes(NationStore store, NationStore.Nation nation) {
      return NationStore.roundMoney(passiveIncomePerMinute(store, nation) * 10.0);
   }

   private static double passiveIncomePerMinute(NationStore store, NationStore.Nation nation) {
      double income = 0.0;
      if (nation.doctrine().capitalProducesIncome && nation.capitalClaim != null && !nation.capitalClaim.isBlank()) {
         income += 12.0;
      }

      List<String> ownedClaims = store.claimsOf(nation);
      if (nation.doctrine() == Doctrine.BRITISH) {
         for (String claimId : ownedClaims) {
            if (!claimId.equals(nation.capitalClaim) && isCoastOrRiverClaim(store.server(), claimId)) {
               income += 6.0;
            }
         }
      }

      for (String cityClaim : nation.cityClaims) {
         if (ownedClaims.contains(cityClaim)) {
            income += 6.0;
         }
      }

      return NationStore.roundMoney(income * nation.doctrine().incomeMultiplier);
   }

   static double maintenanceDuePerInterval(NationStore store, NationStore.Nation nation) {
      int claims = store.claimCount(nation);
      if (claims <= 1) {
         return 0.0;
      } else {
         double maintenanceMultiplier = maintenanceMultiplier(store, nation);
         int capturedClaims = store.capturedClaimsHeldBy(nation);
         double normalDue = (double)claims * 8.0 * maintenanceMultiplier;
         double capturedPremium = (double)capturedClaims * 8.0 * maintenanceMultiplier * 1.0;
         return NationStore.roundMoney(normalDue + capturedPremium);
      }
   }

   private static double maintenanceMultiplier(NationStore store, NationStore.Nation nation) {
      double maintenanceMultiplier = nation.doctrine().maintenanceMultiplier;
      if (nation.doctrine() == Doctrine.FRENCH && hasDeclaredActiveWar(store, nation)) {
         maintenanceMultiplier *= 3.0;
      }

      if (nation.doctrine() == Doctrine.ROMANIAN && nation.lostCoreTerritory) {
         maintenanceMultiplier *= 1.1;
      }

      return maintenanceMultiplier;
   }

   private static boolean hasDeclaredActiveWar(NationStore store, NationStore.Nation nation) {
      return store.wars().stream().anyMatch(war -> war.active && nation.id.equals(war.attacker));
   }

   private static boolean isCarolLifestyleActive(NationStore.Nation nation) {
      return nation.doctrine().randomTreasuryDrain
         && (nation.usedSpecialWarLeaveIdeologies == null || nation.usedSpecialWarLeaveIdeologies.size() < Ideology.values().length);
   }

   private static void chargeMaintenance(MinecraftServer server) {
      NationStore store = NationStore.get();

      for (NationStore.Nation nation : store.nations()) {
         if (isCarolLifestyleActive(nation) && nation.balance > 0.0) {
            double loss = Math.min(nation.balance, 10.0 + (double)RANDOM.nextInt(41));
            nation.balance = NationStore.roundMoney(nation.balance - loss);
            store.notifyNation(
               server, nation, Component.literal("[NationWars] Carol II Lifestyle drained $" + NationStore.roundMoney(loss) + " from the treasury.")
            );
         }

         double due = maintenanceDuePerInterval(store, nation);
         if (!(due <= 0.0)) {
            if (nation.balance + 1.0E-4 >= due) {
               nation.balance = NationStore.roundMoney(nation.balance - due);
               store.notifyNation(server, nation, Component.literal("[NationWars] You paid $" + due + " for your claim maintenance."));
            } else {
               boolean lostClaim = store.removeBorderClaim(nation);
               if (lostClaim && nation.doctrine() == Doctrine.FRENCH) {
                  store.removeBorderClaim(nation);
               }

               if (lostClaim) {
                  store.notifyNation(server, nation, Component.literal("[NationWars] Maintenance failed. A border claim was lost."));
               } else {
                  store.notifyNation(server, nation, Component.literal("[NationWars] Maintenance failed, but no non-capital border claim could be removed."));
               }
            }
         }
      }

      store.save();
   }

   private static void handleCaptureTick(ServerPlayer player) {
      NationStore store = NationStore.get();
      Optional<NationStore.Nation> attacker = store.nationOf(player.getUUID());
      if (attacker.isEmpty()) {
         clearCapture(player);
      } else {
         ClaimKey claim = ClaimKey.of(player.serverLevel(), player.chunkPosition());
         Optional<NationStore.Nation> defender = store.nationOwning(claim);
         if (!defender.isEmpty() && !defender.get().id.equals(attacker.get().id)) {
            Optional<NationStore.War> maybeWar = store.activeWarForCapture(attacker.get(), defender.get());
            if (maybeWar.isEmpty()) {
               hideCaptureBars(player);
            } else {
               String progressKey = player.getUUID() + ":" + claim.id();
               hideCaptureBarsExcept(player, progressKey);
               NationEvents.CaptureProgress progress = CAPTURE_PROGRESS.computeIfAbsent(
                  progressKey, ignored -> new NationEvents.CaptureProgress(attacker.get().id, defender.get().id, claim.id())
               );
               if (!progress.attackerNation.equals(attacker.get().id) || !progress.defenderNation.equals(defender.get().id)) {
                  removeCapture(progressKey);
               } else if (defenderPresent(player, defender.get())) {
                  int required = requiredCaptureSeconds(player, attacker.get(), defender.get(), maybeWar.get(), claim);
                  updateCaptureBar(player, progressKey, "Capture paused: " + defender.get().name, progress.seconds, required, BossBarColor.YELLOW);
               } else {
                  NationStore.War war = maybeWar.get();
                  progress.seconds++;
                  int required = requiredCaptureSeconds(player, attacker.get(), defender.get(), war, claim);
                  updateCaptureBar(player, progressKey, "Capturing " + defender.get().name, progress.seconds, required, BossBarColor.GREEN);
                  if (progress.seconds >= required) {
                     int capturingSide = store.sideOf(war, attacker.get());
                     boolean capitalCaptured = claim.id().equals(defender.get().capitalClaim);
                     int defenderClaimsBefore = store.claimCount(defender.get());
                     store.transferClaim(claim.id(), attacker.get());
                     recordWarCapture(store, war, attacker.get(), claim.id(), capturingSide);
                     if (store.claimCount(defender.get()) <= 0) {
                        eliminateNation(player.getServer(), store, war, attacker.get(), defender.get());
                        removeCapture(progressKey);
                        return;
                     }

                     if (capitalCaptured) {
                        applyCapitulation(player.getServer(), store, war, attacker.get(), defender.get(), defenderClaimsBefore, capturingSide, 1);
                        removeCapture(progressKey);
                        return;
                     }

                     store.save();
                     removeCapture(progressKey);
                     store.notifyNation(
                        player.getServer(), attacker.get(), Component.literal("Captured " + claim.shortName() + " from " + defender.get().name + ".")
                     );
                     store.notifyNation(player.getServer(), defender.get(), Component.literal(attacker.get().name + " captured " + claim.shortName() + "."));
                  }
               }
            }
         } else {
            hideCaptureBars(player);
         }
      }
   }

   private static int requiredCaptureSeconds(ServerPlayer player, NationStore.Nation attacker, NationStore.Nation defender, NationStore.War war, ClaimKey claim) {
      NationStore store = NationStore.get();
      double required = (double)attacker.doctrine().captureSeconds * defender.doctrine().defenseCaptureMultiplier;
      if (defender.doctrine() == Doctrine.FRENCH) {
         required += 25.0;
      }

      if (defender.doctrine() == Doctrine.BRITISH && isCoastOrRiverClaim(player.serverLevel(), claim)) {
         required -= 10.0;
      }

      if (defender.doctrine() == Doctrine.ITALIAN && isHillOrMountainClaim(player.serverLevel(), claim)) {
         required += 15.0;
      }

      if (attacker.doctrine() == Doctrine.ITALIAN && store.isCapturedClaimTracked(war, claim.id())) {
         required += 10.0;
      }

      if (defender.doctrine() == Doctrine.SOVIET && attackersPresent(player, war, attacker) < 2) {
         required *= 2.0;
      }

      return Math.max(10, (int)Math.round(required));
   }

   private static boolean isCoastOrRiverClaim(ServerLevel level, ClaimKey claim) {
      int startX = claim.x() << 4;
      int startZ = claim.z() << 4;
      MutableBlockPos position = new MutableBlockPos();

      for (int x = startX; x < startX + 16; x += 4) {
         for (int z = startZ; z < startZ + 16; z += 4) {
            int surfaceY = level.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, x, z);

            for (int y = surfaceY; y >= surfaceY - 6 && y > level.getMinBuildHeight(); y--) {
               position.set(x, y, z);
               if (level.getFluidState(position).is(FluidTags.WATER)) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   private static boolean isCoastOrRiverClaim(MinecraftServer server, String claimId) {
      try {
         ClaimKey claim = ClaimKey.parse(claimId);
         ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(claim.dimension()));
         ServerLevel level = server.getLevel(key);
         return level != null && isCoastOrRiverClaim(level, claim);
      } catch (RuntimeException var5) {
         return false;
      }
   }

   private static boolean isHillOrMountainClaim(ServerLevel level, ClaimKey claim) {
      int startX = claim.x() << 4;
      int startZ = claim.z() << 4;
      int minY = Integer.MAX_VALUE;
      int maxY = Integer.MIN_VALUE;

      for (int x = startX; x < startX + 16; x += 4) {
         for (int z = startZ; z < startZ + 16; z += 4) {
            int surfaceY = level.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            minY = Math.min(minY, surfaceY);
            maxY = Math.max(maxY, surfaceY);
         }
      }

      return maxY >= 95 || maxY - minY >= 18;
   }

   private static int attackersPresent(ServerPlayer reference, NationStore.War war, NationStore.Nation attacker) {
      NationStore store = NationStore.get();
      int attackerSide = store.sideOf(war, attacker);
      if (attackerSide == 0) {
         return 0;
      } else {
         int count = 0;
         ChunkPos chunk = reference.chunkPosition();

         for (ServerPlayer other : reference.getServer().getPlayerList().getPlayers()) {
            Optional<NationStore.Nation> otherNation = store.nationOf(other.getUUID());
            if (other.level().dimension().equals(reference.level().dimension())
               && other.chunkPosition().equals(chunk)
               && otherNation.isPresent()
               && store.sideOf(war, otherNation.get()) == attackerSide) {
               count++;
            }
         }

         return count;
      }
   }

   private static boolean defenderPresent(ServerPlayer attacker, NationStore.Nation defender) {
      ChunkPos attackerChunk = attacker.chunkPosition();

      for (ServerPlayer other : attacker.getServer().getPlayerList().getPlayers()) {
         if (other != attacker
            && other.level().dimension().equals(attacker.level().dimension())
            && other.chunkPosition().equals(attackerChunk)
            && NationStore.get().isMember(other.getUUID(), defender)) {
            return true;
         }
      }

      return false;
   }

   private static void decayCaptureProgress(MinecraftServer server, long tick) {
      if (tick % 40L == 0L) {
         NationStore store = NationStore.get();
         Iterator<Entry<String, NationEvents.CaptureProgress>> iterator = CAPTURE_PROGRESS.entrySet().iterator();

         while (iterator.hasNext()) {
            Entry<String, NationEvents.CaptureProgress> entry = iterator.next();
            NationEvents.CaptureProgress progress = entry.getValue();

            ClaimKey claim;
            UUID playerId;
            try {
               claim = ClaimKey.parse(progress.claimId);
               playerId = UUID.fromString(entry.getKey().substring(0, entry.getKey().indexOf(58)));
            } catch (RuntimeException var11) {
               iterator.remove();
               continue;
            }

            NationStore.Nation attacker = store.nationById(progress.attackerNation).orElse(null);
            NationStore.Nation defender = store.nationById(progress.defenderNation).orElse(null);
            if (attacker == null
               || defender == null
               || store.nationOwning(claim).map(owner -> !owner.id.equals(defender.id)).orElse(true)
               || store.activeWarForCapture(attacker, defender).isEmpty()) {
               iterator.remove();
               removeCaptureBar(entry.getKey());
            } else if (!playerStillInClaim(server, playerId, claim)) {
               hideCaptureBarFromPlayer(server, entry.getKey(), playerId);
               progress.seconds--;
               if (progress.seconds <= 0) {
                  iterator.remove();
                  removeCaptureBar(entry.getKey());
               }
            }
         }
      }
   }

   private static boolean playerStillInClaim(MinecraftServer server, UUID playerId, ClaimKey claim) {
      ServerPlayer player = server.getPlayerList().getPlayer(playerId);
      return player != null && ClaimKey.of(player.serverLevel(), player.chunkPosition()).equals(claim);
   }

   private static void updateCaptureBar(ServerPlayer player, String progressKey, String label, int seconds, int required, BossBarColor color) {
      int shownSeconds = Math.max(0, Math.min(seconds, required));
      ServerBossEvent bar = CAPTURE_BARS.computeIfAbsent(progressKey, ignored -> new ServerBossEvent(Component.literal(label), color, BossBarOverlay.PROGRESS));
      bar.setName(Component.literal(label + " " + shownSeconds + "/" + required + "s"));
      bar.setColor(color);
      bar.setProgress(Math.max(0.0F, Math.min(1.0F, (float)shownSeconds / (float)required)));
      bar.setVisible(true);
      if (!bar.getPlayers().contains(player)) {
         bar.addPlayer(player);
      }
   }

   private static void removeCapture(String progressKey) {
      CAPTURE_PROGRESS.remove(progressKey);
      removeCaptureBar(progressKey);
   }

   private static void removeCaptureBar(String progressKey) {
      ServerBossEvent bar = CAPTURE_BARS.remove(progressKey);
      if (bar != null) {
         bar.removeAllPlayers();
      }
   }

   private static void hideCaptureBars(ServerPlayer player) {
      for (ServerBossEvent bar : CAPTURE_BARS.values()) {
         bar.removePlayer(player);
      }
   }

   private static void hideCaptureBarsExcept(ServerPlayer player, String keepKey) {
      for (Entry<String, ServerBossEvent> entry : CAPTURE_BARS.entrySet()) {
         if (!entry.getKey().equals(keepKey)) {
            entry.getValue().removePlayer(player);
         }
      }
   }

   private static void hideCaptureBarFromPlayer(MinecraftServer server, String progressKey, UUID playerId) {
      ServerBossEvent bar = CAPTURE_BARS.get(progressKey);
      ServerPlayer player = server.getPlayerList().getPlayer(playerId);
      if (bar != null && player != null) {
         bar.removePlayer(player);
      }
   }

   private static void clearCaptureBars() {
      for (ServerBossEvent bar : CAPTURE_BARS.values()) {
         bar.removeAllPlayers();
      }

      CAPTURE_BARS.clear();
   }

   private static void clearCapture(ServerPlayer player) {
      String prefix = player.getUUID() + ":";
      Iterator<String> iterator = CAPTURE_PROGRESS.keySet().iterator();

      while (iterator.hasNext()) {
         String key = iterator.next();
         if (key.startsWith(prefix)) {
            iterator.remove();
            removeCaptureBar(key);
         }
      }
   }

   private static void clearCaptureExcept(ServerPlayer player, String claimId) {
      String prefix = player.getUUID() + ":";
      String current = prefix + claimId;
      Iterator<String> iterator = CAPTURE_PROGRESS.keySet().iterator();

      while (iterator.hasNext()) {
         String key = iterator.next();
         if (key.startsWith(prefix) && !key.equals(current)) {
            iterator.remove();
            removeCaptureBar(key);
         }
      }
   }

   private static void applyClaimedLandEffects(ServerPlayer player) {
      NationStore store = NationStore.get();
      Optional<NationStore.Nation> nation = store.nationOf(player.getUUID());
      if (!nation.isEmpty() && nation.get().doctrine().claimedLandSpeed) {
         ClaimKey claim = ClaimKey.of(player.serverLevel(), player.chunkPosition());
         if (isCoreClaim(store, nation.get(), claim.id())) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, 1, true, false));
         }
      }
   }

   private static void completeSpyMissions(MinecraftServer server, long tick) {
      if (tick % 20L == 0L) {
         NationStore store = NationStore.get();

         for (NationStore.SpyMission mission : store.dueSpyMissions(tick)) {
            UUID spyId;
            try {
               spyId = UUID.fromString(mission.spyPlayer);
            } catch (IllegalArgumentException var9) {
               store.removeSpyMission(mission);
               continue;
            }

            ServerPlayer spy = server.getPlayerList().getPlayer(spyId);
            if (spy != null) {
               NationStore.Nation target = store.nationById(mission.target).orElse(null);
               if (target == null) {
                  store.removeSpyMission(mission);
               } else {
                  spy.sendSystemMessage(Component.literal("[NationWars] Spy report on " + target.name + ":"));
                  spy.sendSystemMessage(Component.literal("Doctrine: " + target.doctrine().displayName + " (" + target.doctrine().ideology.displayName + ")"));
                  spy.sendSystemMessage(Component.literal("Treasury: $" + NationStore.roundMoney(target.balance)));
                  spy.sendSystemMessage(Component.literal("Members: " + target.members.size()));
                  spy.sendSystemMessage(Component.literal("Claims: " + store.claimCount(target)));
                  store.removeSpyMission(mission);
               }
            }
         }
      }
   }

   private static double rewardForBrokenBlock(ServerPlayer player, BlockState state) {
      Block block = state.getBlock();
      String path = BuiltInRegistries.BLOCK.getKey(block).getPath().toLowerCase();
      double base = 0.0;
      if (!path.endsWith("_ore") && !path.equals("ancient_debris")) {
         if (block instanceof CropBlock crop && crop.isMaxAge(state)) {
            base = 1.0;
         }
      } else {
         if (hasSilkTouch(player)) {
            return 0.0;
         }

         base = oreReward(path);
      }

      return NationStore.roundMoney(base * incomeMultiplier(player));
   }

   private static boolean doesNotPayBuildReward(Block block) {
      return NO_BUILD_REWARD_BLOCKS.contains(BuiltInRegistries.BLOCK.getKey(block).getPath());
   }

   private static double oreReward(String path) {
      if (path.contains("diamond")) {
         return 12.0;
      } else if (path.contains("emerald")) {
         return 10.0;
      } else if (path.equals("ancient_debris")) {
         return 20.0;
      } else if (path.contains("gold")) {
         return 6.0;
      } else if (path.contains("lapis") || path.contains("redstone")) {
         return 4.0;
      } else if (path.contains("iron")) {
         return 3.0;
      } else if (path.contains("copper") || path.contains("quartz")) {
         return 2.0;
      } else {
         return path.contains("coal") ? 1.0 : 2.0;
      }
   }

   private static boolean hasSilkTouch(ServerPlayer player) {
      ItemStack tool = player.getMainHandItem();
      if (tool.isEmpty()) {
         return false;
      } else {
         try {
            return EnchantmentHelper.getItemEnchantmentLevel(
                  player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH), tool
               )
               > 0;
         } catch (RuntimeException var3) {
            return false;
         }
      }
   }

   private static double buildRewardForPlacedBlock(ServerPlayer player, BlockPos pos) {
      NationStore store = NationStore.get();
      Optional<NationStore.Nation> nation = store.nationOf(player.getUUID());
      if (!nation.isEmpty() && nation.get().doctrine() == Doctrine.ITALIAN) {
         ClaimKey claim = ClaimKey.of(player.serverLevel(), new ChunkPos(pos));
         return !isCoreClaim(store, nation.get(), claim.id()) ? 0.0 : 5.0;
      } else {
         return 0.0;
      }
   }

   private static boolean isCoreClaim(NationStore store, NationStore.Nation nation, String claimId) {
      return nation.id.equals(store.nationOwning(ClaimKey.parse(claimId)).map(owner -> owner.id).orElse("")) && !store.isCapturedClaimHeldBy(nation, claimId);
   }

   private static double incomeMultiplier(ServerPlayer player) {
      return NationStore.get().nationOf(player.getUUID()).map(nation -> nation.doctrine().incomeMultiplier).orElse(1.0);
   }

   private static boolean isPaidAccessBlock(Block block) {
      return block instanceof DoorBlock
         || block instanceof TrapDoorBlock
         || block instanceof FenceGateBlock
         || block instanceof ChestBlock
         || block instanceof BarrelBlock
         || block instanceof ShulkerBoxBlock;
   }

   private static boolean isFluidBucket(ItemStack stack) {
      return stack.is(Items.LAVA_BUCKET)
         || stack.is(Items.WATER_BUCKET)
         || stack.is(Items.POWDER_SNOW_BUCKET)
         || stack.is(Items.COD_BUCKET)
         || stack.is(Items.SALMON_BUCKET)
         || stack.is(Items.PUFFERFISH_BUCKET)
         || stack.is(Items.TROPICAL_FISH_BUCKET)
         || stack.is(Items.AXOLOTL_BUCKET)
         || stack.is(Items.TADPOLE_BUCKET);
   }

   private static boolean shouldCancelFluidBucketUse(ServerPlayer player, ServerLevel level, BlockPos clickedPos, Direction face, long tick) {
      if (isFluidBlockedAt(player, level, clickedPos, tick)) {
         return true;
      } else {
         BlockPos targetPos = face == null ? clickedPos : clickedPos.relative(face);
         return !targetPos.equals(clickedPos) && isFluidBlockedAt(player, level, targetPos, tick);
      }
   }

   private static boolean isFluidBlockedAt(ServerPlayer player, ServerLevel level, BlockPos pos, long tick) {
      NationStore store = NationStore.get();
      ClaimKey claim = ClaimKey.of(level, new ChunkPos(pos));
      Optional<NationStore.Nation> owner = store.nationOwning(claim);
      if (owner.isEmpty()) {
         return false;
      } else if (store.isMember(player.getUUID(), owner.get())) {
         grantActionOpacPass(player, tick);
         return false;
      } else if (isActiveWarClaim(player, owner.get())) {
         grantActionOpacPass(player, tick);
         return false;
      } else {
         player.sendSystemMessage(Component.literal("[NationWars] You cannot place fluids in " + owner.get().name + "'s claim."));
         return true;
      }
   }

   private static void grantClaimActionPassIfNeeded(ServerPlayer player, BlockPos pos, long tick) {
      NationStore store = NationStore.get();
      Optional<NationStore.Nation> own = store.nationOf(player.getUUID());
      if (!own.isEmpty()) {
         ClaimKey claim = ClaimKey.of(player.serverLevel(), new ChunkPos(pos));
         Optional<NationStore.Nation> owner = store.nationOwning(claim);
         if (!owner.isEmpty()) {
            if (store.isMember(player.getUUID(), owner.get()) || store.activeWarForCapture(own.get(), owner.get()).isPresent()) {
               grantActionOpacPass(player, tick);
            }
         }
      }
   }

   private static boolean canExplosionAffect(ServerPlayer player, ServerLevel level, BlockPos pos) {
      NationStore store = NationStore.get();
      Optional<NationStore.Nation> own = store.nationOf(player.getUUID());
      ClaimKey claim = ClaimKey.of(level, new ChunkPos(pos));
      Optional<NationStore.Nation> owner = store.nationOwning(claim);
      if (owner.isEmpty()) {
         return true;
      } else {
         return own.isEmpty() ? false : store.isMember(player.getUUID(), owner.get()) || store.activeWarForCapture(own.get(), owner.get()).isPresent();
      }
   }

   private static boolean isActiveWarClaim(ServerPlayer player, NationStore.Nation owner) {
      NationStore store = NationStore.get();
      return store.nationOf(player.getUUID())
         .filter(nation -> !nation.id.equals(owner.id))
         .flatMap(nation -> store.activeWarForCapture(nation, owner))
         .isPresent();
   }

   private static void grantActionOpacPass(ServerPlayer player, long tick) {
      grantTemporaryOpacPass(player, tick + 2L);
   }

   private static void applyCapitulation(
      MinecraftServer server,
      NationStore store,
      NationStore.War war,
      NationStore.Nation attacker,
      NationStore.Nation defender,
      int defenderClaimsBefore,
      int capturingSide,
      int alreadyTransferred
   ) {
      int target = Math.max(1, (int)Math.ceil((double)defenderClaimsBefore * 0.25 * defender.doctrine().surrenderLandMultiplier));

      for (int transferred = alreadyTransferred; transferred < target; transferred++) {
         Optional<String> claim = store.borderClaimsOf(defender).stream().filter(id -> !id.equals(defender.capitalClaim)).findFirst();
         if (claim.isEmpty()) {
            break;
         }

         store.transferClaim(claim.get(), attacker);
         recordWarCapture(store, war, attacker, claim.get(), capturingSide);
      }

      store.notifyNation(server, attacker, Component.literal("[NationWars] " + defender.name + " capitulated after losing its capital."));
      store.notifyNation(server, defender, Component.literal("[NationWars] Your nation capitulated after losing its capital."));
      if (store.claimCount(defender) <= 0) {
         eliminateNation(server, store, war, attacker, defender);
      } else {
         store.endWar(war);
      }
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

   private static void eliminateNation(
      MinecraftServer server, NationStore store, NationStore.War war, NationStore.Nation conqueror, NationStore.Nation defeated
   ) {
      Map<NationStore.Nation, Integer> weights = store.capturedClaimWeightsAgainst(war, defeated, conqueror);
      int totalWeight = weights.values().stream().mapToInt(Integer::intValue).sum();
      double loot = defeated.balance;
      defeated.balance = 0.0;

      try {
         loot = NationStore.roundMoney(loot + store.confiscatePlayerMoney(UUID.fromString(defeated.owner)));
      } catch (RuntimeException var17) {
         loot = NationStore.roundMoney(loot);
      }

      if (loot > 0.0 && totalWeight > 0) {
         double distributed = 0.0;
         int index = 0;
         int recipientCount = weights.size();

         for (Entry<NationStore.Nation, Integer> entry : weights.entrySet()) {
            index++;
            double share = index == recipientCount
               ? NationStore.roundMoney(loot - distributed)
               : NationStore.roundMoney(loot * (double)entry.getValue().intValue() / (double)totalWeight);
            distributed = NationStore.roundMoney(distributed + share);
            entry.getKey().balance = NationStore.roundMoney(entry.getKey().balance + share);
            store.notifyNation(
               server,
               entry.getKey(),
               Component.literal("[NationWars] " + defeated.name + " was eliminated. Your nation received $" + share + " from the split.")
            );
         }
      }

      store.notifyNation(server, defeated, Component.literal("[NationWars] Your nation lost all territory and was deleted. You can create a new nation."));
      store.notifyNation(server, conqueror, Component.literal("[NationWars] " + defeated.name + " lost all territory and was eliminated."));
      store.endWar(war);
      store.deleteNation(defeated);

      for (ServerPlayer player : server.getPlayerList().getPlayers()) {
         player.refreshTabListName();
      }
   }

   private static void scheduleOpacSync(long tick, int retries) {
      nextOpacSyncTick = nextOpacSyncTick < 0L ? tick : Math.min(nextOpacSyncTick, tick);
      opacSyncRetries = Math.max(opacSyncRetries, retries);
   }

   private static void syncOpac(MinecraftServer server) {
      try {
         OpacClaimsBridge.syncAll(server, NationStore.get());
      } catch (RuntimeException var2) {
         NationWars.LOGGER.warn("Automatic OPAC claim sync failed; it will retry if retries remain.", var2);
      }
   }

   private static void cleanupCooldowns(MinecraftServer server, long tick) {
      removeExpired(ACCESS_COOLDOWNS, tick);
      removeExpired(BUILD_REWARD_COOLDOWNS, tick);
      removeExpiredOpacPasses(server, tick);
   }

   private static void removeExpired(Map<String, Long> map, long tick) {
      Iterator<Entry<String, Long>> iterator = map.entrySet().iterator();

      while (iterator.hasNext()) {
         if (iterator.next().getValue() < tick) {
            iterator.remove();
         }
      }
   }

   private static void grantTemporaryOpacPass(ServerPlayer player, long expiresAtTick) {
      UUID playerId = player.getUUID();
      OPAC_FULL_PASSES.put(playerId, expiresAtTick);
      OpenPACServerAPI.get(player.getServer()).getChunkProtection().giveFullPass(playerId);
   }

   private static void removeExpiredOpacPasses(MinecraftServer server, long tick) {
      Iterator<Entry<UUID, Long>> iterator = OPAC_FULL_PASSES.entrySet().iterator();

      while (iterator.hasNext()) {
         Entry<UUID, Long> entry = iterator.next();
         if (entry.getValue() < tick) {
            OpenPACServerAPI.get(server).getChunkProtection().removeFullPass(entry.getKey());
            iterator.remove();
         }
      }
   }

   private static void clearOpacPasses(MinecraftServer server) {
      for (UUID playerId : OPAC_FULL_PASSES.keySet()) {
         OpenPACServerAPI.get(server).getChunkProtection().removeFullPass(playerId);
      }

      OPAC_FULL_PASSES.clear();
   }

   private static final class CaptureProgress {
      private final String attackerNation;
      private final String defenderNation;
      private final String claimId;
      private int seconds;

      private CaptureProgress(String attackerNation, String defenderNation, String claimId) {
         this.attackerNation = attackerNation;
         this.defenderNation = defenderNation;
         this.claimId = claimId;
      }
   }
}
