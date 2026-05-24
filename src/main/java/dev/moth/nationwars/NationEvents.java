package dev.moth.nationwars;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.ChunkPos;
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
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock;
import net.neoforged.neoforge.event.level.BlockEvent.BreakEvent;
import net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent.Post;
import xaero.pac.common.server.api.OpenPACServerAPI;

public final class NationEvents {
   private static final int INCOME_INTERVAL_TICKS = 1200;
   private static final int MAINTENANCE_INTERVAL_TICKS = 12000;
   private static final double CAPITAL_INCOME = 12.0;
   private static final double MAINTENANCE_PER_CLAIM = 8.0;
   private static final double ACCESS_FEE = 5.0;
   private static final double CITY_INCOME = 6.0;
   private static final int OPAC_SYNC_RETRY_INTERVAL_TICKS = 200;
   private static final Random RANDOM = new Random();
   private static long nextIncomeTick = -1L;
   private static long nextMaintenanceTick = -1L;
   private static long nextOpacSyncTick = -1L;
   private static int opacSyncRetries = 0;
   private static final Map<String, NationEvents.CaptureProgress> CAPTURE_PROGRESS = new HashMap<>();
   private static final Map<String, Long> ACCESS_COOLDOWNS = new HashMap<>();
   private static final Map<String, Long> BUILD_REWARD_COOLDOWNS = new HashMap<>();
   private static final Map<UUID, Long> OPAC_FULL_PASSES = new HashMap<>();

   private NationEvents() {
   }

   @SubscribeEvent
   public static void serverStarted(ServerStartedEvent event) {
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
      }
   }

   @SubscribeEvent
   public static void playerTick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) {
      if (event.getEntity() instanceof ServerPlayer player && player.tickCount % 20 == 0) {
         applyClaimedLandEffects(player);
         handleCaptureTick(player);
         return;
      }
   }

   @SubscribeEvent
   public static void blockBreak(BreakEvent event) {
      if (!event.isCanceled() && event.getPlayer() instanceof ServerPlayer player) {
         double reward = rewardForBrokenBlock(player, event.getState());
         if (!(reward <= 0.0)) {
            NationStore.get().addPlayerMoney(player.getUUID(), reward);
            player.displayClientMessage(Component.literal("+$" + NationStore.roundMoney(reward)), true);
         }
      }
   }

   @SubscribeEvent
   public static void blockPlace(EntityPlaceEvent event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         long tick = (long)player.getServer().getTickCount();
         String key = player.getUUID() + ":build";
         if (BUILD_REWARD_COOLDOWNS.getOrDefault(key, 0L) <= tick) {
            BUILD_REWARD_COOLDOWNS.put(key, tick + 20L);
            double reward = NationStore.roundMoney(0.25 * incomeMultiplier(player));
            NationStore.get().addPlayerMoney(player.getUUID(), reward);
            player.displayClientMessage(Component.literal("+$" + reward + " building"), true);
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
         BlockState state = var11.getBlockState(pos);
         if (isPaidAccessBlock(state.getBlock())) {
            NationStore store = NationStore.get();
            ClaimKey claim = ClaimKey.of(var11, new ChunkPos(pos));
            Optional<NationStore.Nation> owner = store.nationOwning(claim);
            if (!owner.isEmpty() && !store.isMember(player.getUUID(), owner.get())) {
               long tick = (long)player.getServer().getTickCount();
               String cooldownKey = player.getUUID() + ":" + pos.asLong();
               if (ACCESS_COOLDOWNS.getOrDefault(cooldownKey, 0L) <= tick) {
                  if (!store.withdrawPlayerMoney(player.getUUID(), 5.0)) {
                     event.setCanceled(true);
                     player.sendSystemMessage(Component.literal("[NationWars] You need $5.0 to open this in " + owner.get().name + "."));
                  } else {
                     ACCESS_COOLDOWNS.put(cooldownKey, tick + 60L);
                     grantTemporaryOpacPass(player, tick + 60L);
                     player.displayClientMessage(Component.literal("-$5.0 access fee"), true);
                     store.notifyNation(
                        player.getServer(),
                        owner.get(),
                        Component.literal(
                           player.getGameProfile().getName() + " paid to open a protected block at " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + "."
                        )
                     );
                  }
               }
            }
         }
      }
   }

   private static void payPassiveIncome(MinecraftServer server) {
      NationStore store = NationStore.get();

      for (NationStore.Nation nation : store.nations()) {
         if (nation.doctrine().capitalProducesIncome && nation.capitalClaim != null && !nation.capitalClaim.isBlank()) {
            nation.balance = NationStore.roundMoney(nation.balance + 12.0 * nation.doctrine().incomeMultiplier);
         }

         for (String cityClaim : nation.cityClaims) {
            if (store.claimsOf(nation).contains(cityClaim)) {
               nation.balance = NationStore.roundMoney(nation.balance + 6.0 * nation.doctrine().incomeMultiplier);
            }
         }
      }

      store.save();
   }

   private static void chargeMaintenance(MinecraftServer server) {
      NationStore store = NationStore.get();

      for (NationStore.Nation nation : store.nations()) {
         if (nation.doctrine().randomTreasuryDrain && nation.balance > 0.0) {
            double loss = Math.min(nation.balance, 10.0 + (double)RANDOM.nextInt(41));
            nation.balance = NationStore.roundMoney(nation.balance - loss);
            store.notifyNation(
               server, nation, Component.literal("[NationWars] Carol II Lifestyle drained $" + NationStore.roundMoney(loss) + " from the treasury.")
            );
         }

         int claims = store.claimCount(nation);
         if (claims > 1) {
            double maintenanceMultiplier = nation.doctrine().maintenanceMultiplier;
            if (nation.doctrine() == Doctrine.ROMANIAN && nation.lostCoreTerritory) {
               maintenanceMultiplier *= 1.5;
            }

            double due = NationStore.roundMoney((double)claims * 8.0 * maintenanceMultiplier);
            if (nation.balance + 1.0E-4 >= due) {
               nation.balance = NationStore.roundMoney(nation.balance - due);
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
               clearCapture(player);
            } else if (defenderPresent(player, defender.get())) {
               player.displayClientMessage(Component.literal("Capture blocked by defender presence"), true);
               clearCapture(player);
            } else {
               NationStore.War war = maybeWar.get();
               String progressKey = player.getUUID() + ":" + claim.id();
               NationEvents.CaptureProgress progress = CAPTURE_PROGRESS.computeIfAbsent(
                  progressKey, ignored -> new NationEvents.CaptureProgress(attacker.get().id, defender.get().id, claim.id())
               );
               if (!progress.attackerNation.equals(attacker.get().id)) {
                  CAPTURE_PROGRESS.remove(progressKey);
               } else {
                  progress.seconds++;
                  int required = requiredCaptureSeconds(player, attacker.get(), defender.get(), war, claim);
                  player.displayClientMessage(Component.literal("Capturing " + defender.get().name + " " + progress.seconds + "/" + required + "s"), true);
                  if (progress.seconds >= required) {
                     int capturingSide = store.sideOf(war, attacker.get());
                     store.transferClaim(claim.id(), attacker.get());
                     if (capturingSide > 0) {
                        war.attackerCapturedClaims.add(claim.id());
                     } else {
                        war.attackerCapturedClaims.remove(claim.id());
                     }

                     store.save();
                     CAPTURE_PROGRESS.remove(progressKey);
                     store.notifyNation(
                        player.getServer(), attacker.get(), Component.literal("Captured " + claim.shortName() + " from " + defender.get().name + ".")
                     );
                     store.notifyNation(player.getServer(), defender.get(), Component.literal(attacker.get().name + " captured " + claim.shortName() + "."));
                  }
               }
            }
         } else {
            clearCapture(player);
         }
      }
   }

   private static int requiredCaptureSeconds(ServerPlayer player, NationStore.Nation attacker, NationStore.Nation defender, NationStore.War war, ClaimKey claim) {
      double required = (double)attacker.doctrine().captureSeconds * defender.doctrine().defenseCaptureMultiplier;
      if (attacker.doctrine() == Doctrine.BRITISH && defender.doctrine().ideology == Ideology.FASCIST) {
         required *= 0.75;
      }

      if (defender.doctrine() == Doctrine.BRITISH && isCoastalClaim(player.serverLevel(), claim)) {
         required *= 0.75;
      }

      if (attacker.doctrine() == Doctrine.ITALIAN && war.attackerCapturedClaims.contains(claim.id())) {
         required *= 1.5;
      }

      if (defender.doctrine() == Doctrine.SOVIET && claim.id().equals(defender.capitalClaim) && attackersPresent(player, attacker) < 2) {
         required *= 2.0;
      }

      return Math.max(10, (int)Math.round(required));
   }

   private static boolean isCoastalClaim(ServerLevel level, ClaimKey claim) {
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

   private static int attackersPresent(ServerPlayer reference, NationStore.Nation attacker) {
      int count = 0;
      ChunkPos chunk = reference.chunkPosition();

      for (ServerPlayer other : reference.getServer().getPlayerList().getPlayers()) {
         if (other.level().dimension().equals(reference.level().dimension())
            && other.chunkPosition().equals(chunk)
            && NationStore.get().isMember(other.getUUID(), attacker)) {
            count++;
         }
      }

      return count;
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

   private static void clearCapture(ServerPlayer player) {
      String prefix = player.getUUID() + ":";
      CAPTURE_PROGRESS.keySet().removeIf(key -> key.startsWith(prefix));
   }

   private static void applyClaimedLandEffects(ServerPlayer player) {
      NationStore store = NationStore.get();
      Optional<NationStore.Nation> nation = store.nationOf(player.getUUID());
      if (!nation.isEmpty() && nation.get().doctrine().claimedLandSpeed) {
         ClaimKey claim = ClaimKey.of(player.serverLevel(), player.chunkPosition());
         if (store.nationOwning(claim).map(owner -> owner.id.equals(nation.get().id)).orElse(false)) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, 0, true, false));
         }
      }
   }

   private static void completeSpyMissions(MinecraftServer server, long tick) {
      NationStore store = NationStore.get();

      for (NationStore.SpyMission mission : store.dueSpyMissions(tick)) {
         ServerPlayer spy = server.getPlayerList().getPlayer(UUID.fromString(mission.spyPlayer));
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

   private static double rewardForBrokenBlock(ServerPlayer player, BlockState state) {
      Block block = state.getBlock();
      String path = BuiltInRegistries.BLOCK.getKey(block).getPath().toLowerCase();
      double base = 0.0;
      if (path.endsWith("_ore") || path.equals("ancient_debris")) {
         base = 3.0;
      } else if (block instanceof CropBlock crop && crop.isMaxAge(state)) {
         base = 1.0;
      }

      return NationStore.roundMoney(base * incomeMultiplier(player));
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
