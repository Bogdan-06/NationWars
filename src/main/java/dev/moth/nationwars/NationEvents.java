package dev.moth.nationwars;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
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
   private static long nextIncomeTick = -1L;
   private static long nextMaintenanceTick = -1L;
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
   }

   @SubscribeEvent
   public static void serverStopping(ServerStoppingEvent event) {
      NationStore.get().save();
      clearOpacPasses(event.getServer());
      CAPTURE_PROGRESS.clear();
      ACCESS_COOLDOWNS.clear();
      BUILD_REWARD_COOLDOWNS.clear();
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

      if (tick >= nextIncomeTick) {
         payPassiveIncome(server);
         nextIncomeTick = tick + 1200L;
      }

      if (tick >= nextMaintenanceTick) {
         chargeMaintenance(server);
         nextMaintenanceTick = tick + 12000L;
      }

      cleanupCooldowns(server, tick);
   }

   @SubscribeEvent
   public static void playerTick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) {
      if (event.getEntity() instanceof ServerPlayer player && player.tickCount % 20 == 0) {
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
         if (nation.capitalClaim != null && !nation.capitalClaim.isBlank()) {
            nation.balance = NationStore.roundMoney(nation.balance + 12.0 * nation.doctrine().incomeMultiplier);
         }
      }

      store.save();
   }

   private static void chargeMaintenance(MinecraftServer server) {
      NationStore store = NationStore.get();

      for (NationStore.Nation nation : store.nations()) {
         int claims = store.claimCount(nation);
         if (claims > 1) {
            double due = NationStore.roundMoney((double)claims * 8.0 * nation.doctrine().maintenanceMultiplier);
            if (nation.balance + 1.0E-4 >= due) {
               nation.balance = NationStore.roundMoney(nation.balance - due);
            } else if (store.removeBorderClaim(nation)) {
               store.notifyNation(server, nation, Component.literal("[NationWars] Maintenance failed. A border claim was lost."));
            } else {
               store.notifyNation(server, nation, Component.literal("[NationWars] Maintenance failed, but no non-capital border claim could be removed."));
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
                  int required = attacker.get().doctrine().captureSeconds;
                  player.displayClientMessage(Component.literal("Capturing " + defender.get().name + " " + progress.seconds + "/" + required + "s"), true);
                  if (progress.seconds >= required) {
                     store.transferClaim(claim.id(), attacker.get());
                     war.attackerCapturedClaims.add(claim.id());
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
