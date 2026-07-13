/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.ChatFormatting
 *  net.minecraft.core.BlockPos
 *  net.minecraft.core.BlockPos$MutableBlockPos
 *  net.minecraft.core.Direction
 *  net.minecraft.core.Holder
 *  net.minecraft.core.registries.BuiltInRegistries
 *  net.minecraft.core.registries.Registries
 *  net.minecraft.network.chat.Component
 *  net.minecraft.resources.ResourceKey
 *  net.minecraft.resources.ResourceLocation
 *  net.minecraft.server.MinecraftServer
 *  net.minecraft.server.level.ServerBossEvent
 *  net.minecraft.server.level.ServerLevel
 *  net.minecraft.server.level.ServerPlayer
 *  net.minecraft.tags.FluidTags
 *  net.minecraft.world.BossEvent$BossBarColor
 *  net.minecraft.world.BossEvent$BossBarOverlay
 *  net.minecraft.world.InteractionResult
 *  net.minecraft.world.effect.MobEffectInstance
 *  net.minecraft.world.effect.MobEffects
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.entity.LivingEntity
 *  net.minecraft.world.entity.player.Player
 *  net.minecraft.world.item.ItemStack
 *  net.minecraft.world.item.Items
 *  net.minecraft.world.item.enchantment.EnchantmentHelper
 *  net.minecraft.world.item.enchantment.Enchantments
 *  net.minecraft.world.level.ChunkPos
 *  net.minecraft.world.level.Level
 *  net.minecraft.world.level.block.BarrelBlock
 *  net.minecraft.world.level.block.Block
 *  net.minecraft.world.level.block.ChestBlock
 *  net.minecraft.world.level.block.CropBlock
 *  net.minecraft.world.level.block.DoorBlock
 *  net.minecraft.world.level.block.FenceGateBlock
 *  net.minecraft.world.level.block.ShulkerBoxBlock
 *  net.minecraft.world.level.block.TrapDoorBlock
 *  net.minecraft.world.level.block.state.BlockState
 *  net.minecraft.world.level.levelgen.Heightmap$Types
 *  net.neoforged.bus.api.EventPriority
 *  net.neoforged.bus.api.SubscribeEvent
 *  net.neoforged.neoforge.event.entity.player.PlayerEvent$PlayerLoggedInEvent
 *  net.neoforged.neoforge.event.entity.player.PlayerEvent$TabListNameFormat
 *  net.neoforged.neoforge.event.entity.player.PlayerInteractEvent$RightClickBlock
 *  net.neoforged.neoforge.event.level.BlockEvent$BreakEvent
 *  net.neoforged.neoforge.event.level.BlockEvent$EntityPlaceEvent
 *  net.neoforged.neoforge.event.level.ExplosionEvent$Detonate
 *  net.neoforged.neoforge.event.server.ServerStartedEvent
 *  net.neoforged.neoforge.event.server.ServerStoppingEvent
 *  net.neoforged.neoforge.event.tick.PlayerTickEvent$Post
 *  net.neoforged.neoforge.event.tick.ServerTickEvent$Post
 *  xaero.pac.common.server.api.OpenPACServerAPI
 */
package dev.moth.nationwars;

import dev.moth.nationwars.ClaimKey;
import dev.moth.nationwars.Doctrine;
import dev.moth.nationwars.Ideology;
import dev.moth.nationwars.NationStore;
import dev.moth.nationwars.NationWars;
import dev.moth.nationwars.NationWarsConfig;
import dev.moth.nationwars.OpacClaimsBridge;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import xaero.pac.common.server.api.OpenPACServerAPI;
import dev.moth.nationwars.service.EconomyService;
import dev.moth.nationwars.service.WarService;

public final class NationEvents {
    private static final int INCOME_INTERVAL_TICKS = 1200;
    private static final int MAINTENANCE_INTERVAL_TICKS = 12000;
    private static final double ACCESS_FEE = 50.0;
    private static final double BUILD_REWARD = 5.0;
    private static final double BUILD_REWARD_CHANCE = 0.2;
    private static final long ACTION_ACCESS_PASS_TICKS = 2L;
    private static final int OPAC_SYNC_RETRY_INTERVAL_TICKS = 200;
    private static final Random RANDOM = new Random();
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
    private static final Set<String> NO_BUILD_REWARD_BLOCKS = Set.of("dirt", "grass_block", "coarse_dirt", "rooted_dirt", "podzol", "mycelium", "mud", "sand", "red_sand", "gravel", "clay", "stone", "cobblestone", "deepslate", "cobbled_deepslate", "netherrack", "end_stone", "snow", "snow_block", "ice", "packed_ice", "blue_ice", "oak_leaves", "spruce_leaves", "birch_leaves", "jungle_leaves", "acacia_leaves", "dark_oak_leaves", "mangrove_leaves", "cherry_leaves", "azalea_leaves", "flowering_azalea_leaves", "short_grass", "tall_grass", "fern", "large_fern", "torch", "soul_torch", "redstone_torch", "scaffolding");
    private static long nextIncomeTick = -1L;
    private static long nextMaintenanceTick = -1L;
    private static long nextOpacSyncTick = -1L;
    private static int opacSyncRetries = 0;
    private static final Map<String, CaptureProgress> CAPTURE_PROGRESS = new HashMap<String, CaptureProgress>();
    private static final Map<String, ServerBossEvent> CAPTURE_BARS = new HashMap<String, ServerBossEvent>();
    private static final Map<String, Long> ACCESS_COOLDOWNS = new HashMap<String, Long>();
    private static final Map<String, Long> BUILD_REWARD_COOLDOWNS = new HashMap<String, Long>();
    private static final Map<UUID, Long> OPAC_FULL_PASSES = new HashMap<UUID, Long>();
    private static final Set<UUID> WAR_DEATHS = new java.util.HashSet<UUID>();
    private static final Map<UUID, Long> WAR_RESPAWN_LOCKS = new HashMap<UUID, Long>();

    private NationEvents() {
    }

    @SubscribeEvent
    public static void serverStarted(ServerStartedEvent event) {
        NationWarsConfig.load();
        OpacClaimsBridge.forceMaxPlayerClaimsZero();
        OpacClaimsBridge.activatePrimaryPartySystem(event.getServer());
        NationStore.load(event.getServer());
        NationEvents.migrateNaturalCoastClaims(event.getServer());
        NationEvents.refreshAllTabListNames(event.getServer());
        long tick = event.getServer().getTickCount();
        nextIncomeTick = tick + 1200L;
        nextMaintenanceTick = tick + 12000L;
        NationEvents.scheduleOpacSync(tick + 100L, 3);
    }

    @SubscribeEvent
    public static void serverStopping(ServerStoppingEvent event) {
        NationStore.get().save();
        NationEvents.clearOpacPasses(event.getServer());
        NationEvents.clearCaptureBars();
        CAPTURE_PROGRESS.clear();
        ACCESS_COOLDOWNS.clear();
        BUILD_REWARD_COOLDOWNS.clear();
        WAR_DEATHS.clear();
        WAR_RESPAWN_LOCKS.clear();
        nextOpacSyncTick = -1L;
        opacSyncRetries = 0;
    }

    @SubscribeEvent
    public static void serverTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        long tick = server.getTickCount();
        if (nextIncomeTick < 0L) {
            nextIncomeTick = tick + 1200L;
        }
        if (nextMaintenanceTick < 0L) {
            nextMaintenanceTick = tick + 12000L;
        }
        if (opacSyncRetries > 0 && tick >= nextOpacSyncTick) {
            NationEvents.syncOpac(server);
            long l = nextOpacSyncTick = --opacSyncRetries > 0 ? tick + 200L : -1L;
        }
        if (tick >= nextIncomeTick) {
            NationEvents.payPassiveIncome(server);
            nextIncomeTick = tick + 1200L;
        }
        if (tick >= nextMaintenanceTick) {
            NationEvents.chargeMaintenance(server);
            nextMaintenanceTick = tick + 12000L;
        }
        if (tick % 20L == 0L) {
            NationStore store = NationStore.get();
            store.tickTruces(server, NationStore.persistentNow());
            store.clearDisabledDefenseCalls();
            if (NationWarsConfig.get().noMercy) {
                store.clearAllPeaceDeals();
            }
        }
        NationEvents.decayCaptureProgress(server, tick);
        NationEvents.cleanupCooldowns(server, tick);
    }

    @SubscribeEvent
    public static void playerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer)) {
            return;
        }
        ServerPlayer player2 = (ServerPlayer)player;
        NationStore.get().updatePlayerIdentity(player2);
        long tick = player2.getServer().getTickCount();
        if (opacSyncRetries <= 0) {
            NationEvents.scheduleOpacSync(tick + 40L, 1);
        } else {
            nextOpacSyncTick = Math.min(nextOpacSyncTick, tick + 40L);
        }
        player2.refreshTabListName();
    }

    @SubscribeEvent
    public static void playerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        NationStore store = NationStore.get();
        store.nationOf(player.getUUID()).filter(nation -> !store.activeWarsOf(nation).isEmpty()).ifPresent(nation -> WAR_DEATHS.add(player.getUUID()));
    }

    @SubscribeEvent
    public static void playerRespawned(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !WAR_DEATHS.remove(player.getUUID())) {
            return;
        }
        WAR_RESPAWN_LOCKS.put(player.getUUID(), (long)player.getServer().getTickCount() + 400L);
        player.sendSystemMessage(NationText.message("nationwars.war.respawn_lock", 20));
    }

    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void tabListName(PlayerEvent.TabListNameFormat event) {
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer)) {
            return;
        }
        ServerPlayer player2 = (ServerPlayer)player;
        try {
            Optional<NationStore.Nation> nation = NationStore.get().nationOf(player2.getUUID());
            if (nation.isPresent()) {
                MutableComponent prefix = NationText.tr("nationwars.tab.nation_prefix", nation.get().name).withStyle(ChatFormatting.GOLD);
                event.setDisplayName((Component)prefix.append((Component)Component.literal((String)player2.getGameProfile().getName()).withStyle(ChatFormatting.WHITE)));
            } else {
                MutableComponent prefix = NationText.tr("nationwars.tab.no_nation_prefix").withStyle(ChatFormatting.DARK_GRAY);
                event.setDisplayName((Component)prefix.append((Component)Component.literal((String)player2.getGameProfile().getName()).withStyle(ChatFormatting.GRAY)));
            }
        }
        catch (IllegalStateException ignored) {
            // Keep the vanilla display name while the Nation Wars store is unavailable.
        }
    }

    public static void refreshAllTabListNames(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.refreshTabListName();
        }
    }

    @SubscribeEvent
    public static void playerTick(PlayerTickEvent.Post event) {
        Player player2 = event.getEntity();
        if (!(player2 instanceof ServerPlayer player)) {
            return;
        }
        if (player.tickCount % 20 != 0) {
            return;
        }
        if (player.tickCount % 100 == 0) {
            player.refreshTabListName();
        }
        NationEvents.applyClaimedLandEffects(player);
        NationEvents.handleCaptureTick(player);
    }

    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void blockBreakAccess(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        if (!(player instanceof ServerPlayer)) {
            return;
        }
        ServerPlayer player2 = (ServerPlayer)player;
        if (event.getState().getBlock() instanceof BedBlock && NationEvents.isEnemyWarClaim(player2, player2.serverLevel(), event.getPos())) {
            event.setCanceled(true);
            player2.displayClientMessage(NationText.tr("nationwars.war.enemy_bed_protected"), true);
            return;
        }
        if (NationEvents.isPeacePendingWarClaim(player2, player2.serverLevel(), event.getPos())) {
            event.setCanceled(true);
            player2.displayClientMessage(NationText.tr("nationwars.war.actions_paused"), true);
            return;
        }
        if (NationEvents.shouldBlockScorchedEarthAction(player2, player2.serverLevel(), event.getPos(), player2.getServer().getTickCount())) {
            event.setCanceled(true);
            player2.displayClientMessage(NationText.tr("nationwars.war.scorched_earth.break_blocked"), true);
            return;
        }
        NationEvents.grantClaimActionPassIfNeeded(player2, event.getPos(), player2.getServer().getTickCount());
    }

    @SubscribeEvent(priority=EventPriority.LOWEST, receiveCanceled=true)
    public static void blockBreakReward(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        if (!(player instanceof ServerPlayer)) {
            return;
        }
        ServerPlayer player2 = (ServerPlayer)player;
        try {
            if (event.isCanceled()) {
                return;
            }
            double reward = NationEvents.rewardForBrokenBlock(player2, event.getState());
            if (reward <= 0.0) {
                return;
            }
            NationStore.get().addPlayerMoney(player2.getUUID(), reward);
            player2.displayClientMessage(NationText.tr("nationwars.money.gained", NationStore.roundMoney(reward)), true);
        }
        finally {
            NationEvents.revokeTemporaryOpacPass(player2);
        }
    }

    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void blockPlaceAccess(BlockEvent.EntityPlaceEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof ServerPlayer)) {
            return;
        }
        ServerPlayer player = (ServerPlayer)entity;
        if (NationEvents.isPeacePendingWarClaim(player, player.serverLevel(), event.getPos())) {
            event.setCanceled(true);
            player.displayClientMessage(NationText.tr("nationwars.war.actions_paused"), true);
            return;
        }
        if (NationEvents.shouldBlockScorchedEarthAction(player, player.serverLevel(), event.getPos(), player.getServer().getTickCount())) {
            event.setCanceled(true);
            player.displayClientMessage(NationText.tr("nationwars.war.scorched_earth.build_blocked"), true);
            return;
        }
        NationEvents.grantClaimActionPassIfNeeded(player, event.getPos(), player.getServer().getTickCount());
    }

    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void explosionAccess(ExplosionEvent.Detonate event) {
        Level level = event.getLevel();
        if (!(level instanceof ServerLevel)) {
            return;
        }
        ServerLevel level2 = (ServerLevel)level;
        LivingEntity livingEntity = event.getExplosion().getIndirectSourceEntity();
        if (!(livingEntity instanceof ServerPlayer)) {
            return;
        }
        ServerPlayer player = (ServerPlayer)livingEntity;
        long tick = player.getServer().getTickCount();
        boolean removedBlockedClaimBlock = event.getAffectedBlocks().removeIf(pos -> level2.getBlockState(pos).getBlock() instanceof BedBlock && NationEvents.isEnemyWarClaim(player, level2, pos) || !NationEvents.canExplosionAffect(player, level2, pos));
        if (!event.getAffectedBlocks().isEmpty() || removedBlockedClaimBlock) {
            NationEvents.grantActionOpacPass(player, tick);
        }
    }

    @SubscribeEvent(priority=EventPriority.LOWEST, receiveCanceled=true)
    public static void explosionAccessCleanup(ExplosionEvent.Detonate event) {
        LivingEntity source = event.getExplosion().getIndirectSourceEntity();
        if (source instanceof ServerPlayer player) {
            NationEvents.revokeTemporaryOpacPass(player);
        }
    }

    @SubscribeEvent(priority=EventPriority.LOWEST, receiveCanceled=true)
    public static void blockPlaceReward(BlockEvent.EntityPlaceEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof ServerPlayer)) {
            return;
        }
        ServerPlayer player = (ServerPlayer)entity;
        try {
            long tick = player.getServer().getTickCount();
            if (event.isCanceled() || NationEvents.doesNotPayBuildReward(event.getPlacedBlock().getBlock())) {
                return;
            }
            double reward = NationEvents.buildRewardForPlacedBlock(player, event.getPos());
            if (reward <= 0.0) {
                return;
            }
            NationStore store = NationStore.get();
            String positionKey = NationEvents.buildRewardPositionKey(player.serverLevel(), event.getPos());
            if (store.hasRewardedBuildPosition(positionKey)) {
                return;
            }
            String cooldownKey = String.valueOf(player.getUUID()) + ":build";
            if (BUILD_REWARD_COOLDOWNS.getOrDefault(cooldownKey, 0L) > tick) {
                return;
            }
            BUILD_REWARD_COOLDOWNS.put(cooldownKey, tick + 20L);
            boolean award = RANDOM.nextDouble() <= BUILD_REWARD_CHANCE;
            if (!store.resolveBuildingRewardAttempt(player.getUUID(), positionKey, reward, award)) {
                return;
            }
            player.displayClientMessage(NationText.tr("nationwars.money.building_gained", reward), true);
        }
        finally {
            NationEvents.revokeTemporaryOpacPass(player);
        }
    }

    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void rightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        ClaimKey claim;
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer)) {
            return;
        }
        ServerPlayer player2 = (ServerPlayer)player;
        ServerLevel level = player2.serverLevel();
        BlockPos pos = event.getPos();
        long tick = player2.getServer().getTickCount();
        if (NationEvents.isFluidBucket(player2.getItemInHand(event.getHand()))) {
            if (NationEvents.shouldCancelFluidBucketUse(player2, level, pos, event.getFace(), tick)) {
                event.setCancellationResult(InteractionResult.FAIL);
                event.setCanceled(true);
            }
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (!NationEvents.isPaidAccessBlock(state.getBlock())) {
            return;
        }
        NationStore store = NationStore.get();
        Optional<NationStore.Nation> owner = store.nationOwning(claim = ClaimKey.of(level, new ChunkPos(pos)));
        if (owner.isEmpty() || store.isMember(player2.getUUID(), owner.get())) {
            return;
        }
        if (store.isRaidActive(claim.id(), NationStore.persistentNow())) {
            NationEvents.grantActionOpacPass(player2, tick);
            return;
        }
        if (NationEvents.isPeacePendingWarClaim(player2, owner.get())) {
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
            player2.displayClientMessage(NationText.tr("nationwars.war.actions_paused"), true);
            return;
        }
        if (NationEvents.isActiveWarClaim(player2, owner.get())) {
            if (NationEvents.isWarRespawnLocked(player2, tick)) {
                player2.displayClientMessage(NationText.tr("nationwars.war.actions_respawn_timer"), true);
                return;
            }
            NationEvents.grantActionOpacPass(player2, tick);
            return;
        }
        String cooldownKey = String.valueOf(player2.getUUID()) + ":" + pos.asLong();
        if (ACCESS_COOLDOWNS.getOrDefault(cooldownKey, 0L) > tick) {
            return;
        }
        if (!store.withdrawPlayerMoney(player2.getUUID(), 50.0)) {
            event.setCanceled(true);
            player2.sendSystemMessage(NationText.message("nationwars.claim.access.need_money", NationStore.roundMoney(ACCESS_FEE), owner.get().name));
            return;
        }
        ACCESS_COOLDOWNS.put(cooldownKey, tick + 60L);
        NationEvents.grantActionOpacPass(player2, tick);
        player2.displayClientMessage(NationText.tr("nationwars.claim.access.fee", NationStore.roundMoney(ACCESS_FEE)), true);
        store.notifyNation(player2.getServer(), owner.get(), NationText.tr("nationwars.claim.access.paid_notice", player2.getGameProfile().getName(), pos.getX(), pos.getY(), pos.getZ()));
    }

    @SubscribeEvent(priority=EventPriority.LOWEST, receiveCanceled=true)
    public static void rightClickBlockCleanup(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            NationEvents.revokeTemporaryOpacPass(player);
        }
    }

    private static void payPassiveIncome(MinecraftServer server) {
        NationStore store = NationStore.get();
        Map<String, NationStore.Nation> nationsById = new LinkedHashMap<String, NationStore.Nation>();
        Map<String, Double> grossIncome = new LinkedHashMap<String, Double>();
        for (NationStore.Nation nation : store.nations()) {
            nationsById.put(nation.id, nation);
            grossIncome.put(nation.id, Math.max(0.0, NationEvents.passiveIncomePerMinute(store, nation)));
        }

        Map<String, List<NationStore.IncomeTransfer>> transfersByPayer = new LinkedHashMap<String, List<NationStore.IncomeTransfer>>();
        for (NationStore.IncomeTransfer transfer : store.incomeTransfers()) {
            if (transfer == null || transfer.payer == null || transfer.receiver == null
                || transfer.payer.equals(transfer.receiver) || !nationsById.containsKey(transfer.payer)
                || !nationsById.containsKey(transfer.receiver) || !Double.isFinite(transfer.amountPerMinute)
                || transfer.amountPerMinute <= 0.0) {
                continue;
            }
            transfersByPayer.computeIfAbsent(transfer.payer, ignored -> new java.util.ArrayList<NationStore.IncomeTransfer>()).add(transfer);
        }

        Map<String, Double> outgoing = new HashMap<String, Double>();
        Map<String, Double> incoming = new HashMap<String, Double>();
        for (Map.Entry<String, List<NationStore.IncomeTransfer>> entry : transfersByPayer.entrySet()) {
            List<NationStore.IncomeTransfer> transfers = entry.getValue();
            double available = grossIncome.getOrDefault(entry.getKey(), 0.0);
            double obligation = transfers.stream().mapToDouble(transfer -> transfer.amountPerMinute).sum();
            if (available <= 0.0 || obligation <= 0.0) {
                continue;
            }
            double distributable = NationStore.roundMoney(Math.min(available, obligation));
            double remaining = distributable;
            for (int index = 0; index < transfers.size(); ++index) {
                NationStore.IncomeTransfer transfer = transfers.get(index);
                double paid = index == transfers.size() - 1
                    ? remaining
                    : NationStore.roundMoney(distributable * transfer.amountPerMinute / obligation);
                paid = Math.max(0.0, Math.min(remaining, paid));
                remaining = NationStore.roundMoney(remaining - paid);
                outgoing.merge(transfer.payer, paid, (first, second) -> NationStore.roundMoney(first + second));
                incoming.merge(transfer.receiver, paid, (first, second) -> NationStore.roundMoney(first + second));
            }
        }

        for (NationStore.Nation nation : store.nations()) {
            double generated = grossIncome.getOrDefault(nation.id, 0.0);
            double received = incoming.getOrDefault(nation.id, 0.0);
            double diverted = Math.min(generated, outgoing.getOrDefault(nation.id, 0.0));
            nation.balance = NationStore.roundMoney(nation.balance + generated - diverted + received);
        }
        store.save();
    }

    static double passiveIncomePerTenMinutes(NationStore store, NationStore.Nation nation) {
        return NationStore.roundMoney(NationEvents.passiveIncomePerMinute(store, nation) * 10.0);
    }

    private static double passiveIncomePerMinute(NationStore store, NationStore.Nation nation) {
        long tick = NationStore.persistentNow();
        List<String> ownedClaims = store.claimsOf(nation);
        boolean capitalActive = nation.capitalClaim != null && !nation.capitalClaim.isBlank()
            && !store.isClaimParalyzed(nation.capitalClaim, tick);
        int activeCoasts = (int)ownedClaims.stream()
            .filter(claimId -> !store.isClaimParalyzed(claimId, tick) && store.isCoastClaim(claimId)).count();
        int activeCities = (int)nation.cityClaims.stream()
            .filter(ownedClaims::contains).filter(claimId -> !store.isClaimParalyzed(claimId, tick)).count();
        return EconomyService.passiveIncomePerMinute(nation.doctrine(), nation.upgradeLevel,
            capitalActive, activeCoasts, activeCities);
    }

    static double capitalIncomePerMinute(NationStore.Nation nation) {
        if (nation == null) {
            return 0.0;
        }
        return EconomyService.capitalIncomePerMinute(nation.doctrine(), nation.upgradeLevel);
    }

    static double maintenanceDuePerInterval(NationStore store, NationStore.Nation nation) {
        int lostCoreClaims = nation.lostCoreClaims == null ? 0 : nation.lostCoreClaims.size();
        boolean declaredActiveWar = store.wars().stream().anyMatch(war -> war.active && nation.id.equals(war.attacker));
        return EconomyService.maintenanceDue(store.claimCount(nation), store.capturedClaimsHeldBy(nation),
            nation.doctrine(), declaredActiveWar, lostCoreClaims, nation.lostCoreTerritory);
    }

    private static boolean isCarolLifestyleActive(NationStore.Nation nation) {
        int used = nation.usedSpecialWarLeaveIdeologies == null ? 0 : nation.usedSpecialWarLeaveIdeologies.size();
        return EconomyService.carolLifestyleActive(nation.doctrine(), used, Ideology.values().length);
    }

    private static void chargeMaintenance(MinecraftServer server) {
        NationStore store = NationStore.get();
        for (NationStore.Nation nation : store.nations()) {
            double due;
            if (NationEvents.isCarolLifestyleActive(nation) && nation.balance > 0.0) {
                double loss = Math.min(nation.balance, 10.0 + (double)RANDOM.nextInt(41));
                nation.balance = NationStore.roundMoney(nation.balance - loss);
                store.notifyNation(server, nation, NationText.message("nationwars.maintenance.carol_drain", NationStore.roundMoney(loss)));
            }
            if ((due = NationEvents.maintenanceDuePerInterval(store, nation)) <= 0.0) continue;
            if (nation.balance + 1.0E-4 >= due) {
                nation.balance = NationStore.roundMoney(nation.balance - due);
                store.notifyNation(server, nation, NationText.message("nationwars.maintenance.paid", due));
                continue;
            }
            boolean lostClaim = store.removeBorderClaim(nation);
            if (lostClaim) {
                store.notifyNation(server, nation, NationText.message("nationwars.maintenance.claim_lost"));
                continue;
            }
            store.notifyNation(server, nation, NationText.message("nationwars.maintenance.no_claim_removed"));
        }
        store.save();
    }

    private static void handleCaptureTick(ServerPlayer player) {
        int required;
        NationStore store = NationStore.get();
        long tick = player.getServer().getTickCount();
        if (NationEvents.isWarRespawnLocked(player, tick)) {
            NationEvents.clearCapture(player);
            long seconds = Math.max(1L, (WAR_RESPAWN_LOCKS.get(player.getUUID()) - tick + 19L) / 20L);
            player.displayClientMessage(NationText.tr("nationwars.war.capture_lock", seconds), true);
            return;
        }
        Optional<NationStore.Nation> attacker = store.nationOf(player.getUUID());
        if (attacker.isEmpty()) {
            NationEvents.clearCapture(player);
            return;
        }
        ClaimKey claim = ClaimKey.of(player.serverLevel(), player.chunkPosition());
        Optional<NationStore.Nation> defender = store.nationOwning(claim);
        if (defender.isEmpty() || defender.get().id.equals(attacker.get().id)) {
            NationEvents.hideCaptureBars(player);
            return;
        }
        Optional<NationStore.War> maybeWar = store.activeWarForCapture(attacker.get(), defender.get());
        if (maybeWar.isEmpty()) {
            NationEvents.hideCaptureBars(player);
            return;
        }
        NationStore.War war = maybeWar.get();
        String progressKey = String.valueOf(player.getUUID()) + ":" + claim.id();
        if (store.isClaimCapturedInOtherActiveWar(war, claim.id())) {
            NationEvents.removeCapture(progressKey);
            player.displayClientMessage(NationText.tr("nationwars.war.capture.other_war"), true);
            return;
        }
        NationEvents.hideCaptureBarsExcept(player, progressKey);
        CaptureProgress progress = CAPTURE_PROGRESS.computeIfAbsent(progressKey, ignored -> new CaptureProgress(((NationStore.Nation)attacker.get()).id, ((NationStore.Nation)defender.get()).id, claim.id()));
        if (!progress.attackerNation.equals(attacker.get().id) || !progress.defenderNation.equals(defender.get().id)) {
            NationEvents.removeCapture(progressKey);
            return;
        }
        if (war.peaceDeal != null) {
            progress.requiredSeconds = required = NationEvents.requiredCaptureSeconds(player, attacker.get(), defender.get(), war, claim);
            NationEvents.updateCaptureBar(player.getServer(), player.getUUID(), progressKey, defender.get(),
                NationText.tr("nationwars.war.capture.label.peace", attacker.get().name, defender.get().name, claim.shortName()),
                progress.seconds, required, BossEvent.BossBarColor.YELLOW);
            player.displayClientMessage(NationText.tr("nationwars.war.capture.peace_paused"), true);
            return;
        }
        if (NationEvents.defenderPresent(player, maybeWar.get(), defender.get())) {
            int required2;
            progress.requiredSeconds = required2 = NationEvents.requiredCaptureSeconds(player, attacker.get(), defender.get(), maybeWar.get(), claim);
            NationEvents.updateCaptureBar(player.getServer(), player.getUUID(), progressKey, defender.get(),
                NationText.tr("nationwars.war.capture.label.defended", attacker.get().name, defender.get().name, claim.shortName()), progress.seconds, required2, BossEvent.BossBarColor.YELLOW);
            return;
        }
        ++progress.seconds;
        progress.requiredSeconds = required = NationEvents.requiredCaptureSeconds(player, attacker.get(), defender.get(), war, claim);
        NationEvents.updateCaptureBar(player.getServer(), player.getUUID(), progressKey, defender.get(),
            NationText.tr("nationwars.war.capture.label.active", attacker.get().name, defender.get().name, claim.shortName()), progress.seconds, required, BossEvent.BossBarColor.GREEN);
        if (progress.seconds >= required) {
            boolean capitalCaptured = claim.id().equals(defender.get().capitalClaim);
            boolean capturedWarCore = store.isCoreClaimForWar(war, defender.get(), claim.id());
            int defenderClaimsBefore = store.claimCount(defender.get());
            if (!store.captureClaim(war, attacker.get(), defender.get(), claim.id())) {
                NationEvents.removeCapture(progressKey);
                return;
            }
            if (store.claimCount(defender.get()) <= 0) {
                NationEvents.eliminateNation(player.getServer(), store, war, attacker.get(), defender.get());
                NationEvents.removeCapture(progressKey);
                return;
            }
            if (capitalCaptured) {
                NationEvents.applyCapitulation(player.getServer(), store, war, attacker.get(), defender.get(), defenderClaimsBefore, capturedWarCore ? 1 : 0);
                NationEvents.removeCapture(progressKey);
                return;
            }
            store.save();
            NationEvents.removeCapture(progressKey);
            store.notifyNation(player.getServer(), attacker.get(), NationText.tr("nationwars.war.capture.attacker_notice", claim.shortName(), defender.get().name));
            store.notifyNation(player.getServer(), defender.get(), NationText.tr("nationwars.war.capture.defender_notice", attacker.get().name, claim.shortName()));
        }
    }

    private static int requiredCaptureSeconds(ServerPlayer player, NationStore.Nation attacker, NationStore.Nation defender, NationStore.War war, ClaimKey claim) {
        NationStore store = NationStore.get();
        return WarService.captureSeconds(attacker.doctrine(), defender.doctrine(), store.isCoastClaim(claim.id()),
            NationEvents.isHillOrMountainClaim(player.serverLevel(), claim),
            store.isCapturedClaimTracked(war, claim.id()), NationEvents.attackersPresent(player, war, attacker));
    }

    private static boolean isCoastOrRiverClaim(ServerLevel level, ClaimKey claim) {
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

    private static boolean isCoastOrRiverClaim(MinecraftServer server, String claimId) {
        try {
            ClaimKey claim = ClaimKey.parse(claimId);
            ResourceKey key = ResourceKey.create((ResourceKey)Registries.DIMENSION, (ResourceLocation)ResourceLocation.parse((String)claim.dimension()));
            ServerLevel level = server.getLevel(key);
            return level != null && NationEvents.isCoastOrRiverClaim(level, claim);
        }
        catch (RuntimeException exception) {
            return false;
        }
    }

    private static void migrateNaturalCoastClaims(MinecraftServer server) {
        NationStore store = NationStore.get();
        if (store.coastClaimsMigrated()) {
            return;
        }
        Set<String> detectedCoasts = new HashSet<String>();
        for (NationStore.Nation nation : store.nations()) {
            for (String claimId : store.claimsOf(nation)) {
                if (NationEvents.isCoastOrRiverClaim(server, claimId)) {
                    detectedCoasts.add(claimId);
                }
            }
        }
        store.finishCoastClaimsMigration(detectedCoasts);
        NationWars.LOGGER.info("Migrated natural-water status for {} existing claims.", (Object)detectedCoasts.size());
    }

    private static boolean isHillOrMountainClaim(ServerLevel level, ClaimKey claim) {
        int startX = claim.x() << 4;
        int startZ = claim.z() << 4;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (int x = startX; x < startX + 16; x += 4) {
            for (int z = startZ; z < startZ + 16; z += 4) {
                int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
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
        }
        int count = 0;
        ChunkPos chunk = reference.chunkPosition();
        for (ServerPlayer other : reference.getServer().getPlayerList().getPlayers()) {
            Optional<NationStore.Nation> otherNation = store.nationOf(other.getUUID());
            if (!other.level().dimension().equals(reference.level().dimension()) || !other.chunkPosition().equals((Object)chunk) || NationEvents.isWarRespawnLocked(other, other.getServer().getTickCount()) || !otherNation.isPresent() || store.sideOf(war, otherNation.get()) != attackerSide) continue;
            ++count;
        }
        return count;
    }

    private static boolean defenderPresent(ServerPlayer attacker, NationStore.War war, NationStore.Nation defender) {
        NationStore store = NationStore.get();
        int defendingSide = store.sideOf(war, defender);
        ChunkPos attackerChunk = attacker.chunkPosition();
        for (ServerPlayer other : attacker.getServer().getPlayerList().getPlayers()) {
            Optional<NationStore.Nation> otherNation = store.nationOf(other.getUUID());
            if (other == attacker || !other.level().dimension().equals(attacker.level().dimension()) || !other.chunkPosition().equals((Object)attackerChunk)
                || NationEvents.isWarRespawnLocked(other, other.getServer().getTickCount()) || otherNation.isEmpty()
                || store.sideOf(war, otherNation.get()) != defendingSide) continue;
            return true;
        }
        return false;
    }

    private static void decayCaptureProgress(MinecraftServer server, long tick) {
        if (tick % 40L != 0L) {
            return;
        }
        NationStore store = NationStore.get();
        Iterator<Map.Entry<String, CaptureProgress>> iterator = CAPTURE_PROGRESS.entrySet().iterator();
        while (iterator.hasNext()) {
            UUID playerId;
            ClaimKey claim;
            Map.Entry<String, CaptureProgress> entry = iterator.next();
            CaptureProgress progress = entry.getValue();
            try {
                claim = ClaimKey.parse(progress.claimId);
                playerId = UUID.fromString(entry.getKey().substring(0, entry.getKey().indexOf(58)));
            }
            catch (RuntimeException exception) {
                iterator.remove();
                continue;
            }
            NationStore.Nation attacker = store.nationById(progress.attackerNation).orElse(null);
            NationStore.Nation defender = store.nationById(progress.defenderNation).orElse(null);
            Optional<NationStore.War> activeWar = attacker == null || defender == null
                ? Optional.empty()
                : store.activeWarForCapture(attacker, defender);
            if (attacker == null || defender == null || store.nationOwning(claim).map(owner -> !owner.id.equals(defender.id)).orElse(true).booleanValue() || activeWar.isEmpty()) {
                iterator.remove();
                NationEvents.removeCaptureBar(entry.getKey());
                continue;
            }
            if (activeWar.get().peaceDeal != null) {
                continue;
            }
            if (NationEvents.playerStillInClaim(server, playerId, claim)) continue;
            NationEvents.hideCaptureBarFromPlayer(server, entry.getKey(), playerId);
            --progress.seconds;
            if (progress.seconds <= 0) {
                iterator.remove();
                NationEvents.removeCaptureBar(entry.getKey());
                continue;
            }
            NationEvents.updateCaptureBar(server, null, entry.getKey(), defender,
                NationText.tr("nationwars.war.capture.label.draining", attacker.name, defender.name, claim.shortName()),
                progress.seconds, Math.max(1, progress.requiredSeconds), BossEvent.BossBarColor.RED);
        }
    }

    private static boolean playerStillInClaim(MinecraftServer server, UUID playerId, ClaimKey claim) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        return player != null && ClaimKey.of(player.serverLevel(), player.chunkPosition()).equals(claim);
    }

    private static void updateCaptureBar(MinecraftServer server, UUID attackerId, String progressKey, NationStore.Nation defender, Component label, int seconds, int required, BossEvent.BossBarColor color) {
        int shownSeconds = Math.max(0, Math.min(seconds, required));
        ServerBossEvent bar = CAPTURE_BARS.computeIfAbsent(progressKey, ignored -> new ServerBossEvent(label, color, BossEvent.BossBarOverlay.PROGRESS));
        bar.setName(NationText.tr("nationwars.war.capture.progress", label, shownSeconds, required));
        bar.setColor(color);
        bar.setProgress(Math.max(0.0f, Math.min(1.0f, (float)shownSeconds / (float)required)));
        bar.setVisible(true);
        NationEvents.syncCaptureBarPlayers(server, bar, attackerId, defender);
    }

    private static void syncCaptureBarPlayers(MinecraftServer server, ServerBossEvent bar, UUID attackerId, NationStore.Nation defender) {
        ServerPlayer attacker;
        NationStore store = NationStore.get();
        for (ServerPlayer player : List.copyOf(bar.getPlayers())) {
            boolean attacker2 = attackerId != null && player.getUUID().equals(attackerId);
            if (attacker2 || store.isMember(player.getUUID(), defender)) continue;
            bar.removePlayer(player);
        }
        if (attackerId != null && (attacker = server.getPlayerList().getPlayer(attackerId)) != null && !bar.getPlayers().contains(attacker)) {
            bar.addPlayer(attacker);
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!store.isMember(player.getUUID(), defender) || bar.getPlayers().contains(player)) continue;
            bar.addPlayer(player);
        }
    }

    private static void removeCapture(String progressKey) {
        CAPTURE_PROGRESS.remove(progressKey);
        NationEvents.removeCaptureBar(progressKey);
    }

    private static void removeCaptureBar(String progressKey) {
        ServerBossEvent bar = CAPTURE_BARS.remove(progressKey);
        if (bar != null) {
            bar.removeAllPlayers();
        }
    }

    private static void hideCaptureBars(ServerPlayer player) {
        String prefix = String.valueOf(player.getUUID()) + ":";
        for (Map.Entry<String, ServerBossEvent> entry : CAPTURE_BARS.entrySet()) {
            if (!entry.getKey().startsWith(prefix)) continue;
            entry.getValue().removePlayer(player);
        }
    }

    private static void hideCaptureBarsExcept(ServerPlayer player, String keepKey) {
        String prefix = String.valueOf(player.getUUID()) + ":";
        for (Map.Entry<String, ServerBossEvent> entry : CAPTURE_BARS.entrySet()) {
            if (!entry.getKey().startsWith(prefix) || entry.getKey().equals(keepKey)) continue;
            entry.getValue().removePlayer(player);
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
        String prefix = String.valueOf(player.getUUID()) + ":";
        Iterator<String> iterator = CAPTURE_PROGRESS.keySet().iterator();
        while (iterator.hasNext()) {
            String key = iterator.next();
            if (!key.startsWith(prefix)) continue;
            iterator.remove();
            NationEvents.removeCaptureBar(key);
        }
    }

    private static void clearCaptureExcept(ServerPlayer player, String claimId) {
        String prefix = String.valueOf(player.getUUID()) + ":";
        String current = prefix + claimId;
        Iterator<String> iterator = CAPTURE_PROGRESS.keySet().iterator();
        while (iterator.hasNext()) {
            String key = iterator.next();
            if (!key.startsWith(prefix) || key.equals(current)) continue;
            iterator.remove();
            NationEvents.removeCaptureBar(key);
        }
    }

    private static void applyClaimedLandEffects(ServerPlayer player) {
        NationStore store = NationStore.get();
        Optional<NationStore.Nation> nation = store.nationOf(player.getUUID());
        if (nation.isEmpty() || !nation.get().doctrine().claimedLandSpeed) {
            return;
        }
        ClaimKey claim = ClaimKey.of(player.serverLevel(), player.chunkPosition());
        if (nation.get().id.equals(store.nationOwning(claim).map(owner -> owner.id).orElse("")) && !store.isCapturedClaimHeldBy(nation.get(), claim.id())) {
            int amplifier = store.activeWarsOf(nation.get()).isEmpty() ? 1 : 0;
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, amplifier, true, false));
        }
    }

    private static double rewardForBrokenBlock(ServerPlayer player, BlockState state) {
        CropBlock crop;
        Block block = state.getBlock();
        String path = BuiltInRegistries.BLOCK.getKey(block).getPath().toLowerCase();
        double base = 0.0;
        if (path.endsWith("_ore") || path.equals("ancient_debris")) {
            if (NationEvents.hasSilkTouch(player)) {
                return 0.0;
            }
            base = NationEvents.oreReward(path);
        } else if (block instanceof CropBlock && (crop = (CropBlock)block).isMaxAge(state)) {
            base = 1.0;
        }
        return NationStore.roundMoney(base * NationEvents.incomeMultiplier(player));
    }

    private static boolean doesNotPayBuildReward(Block block) {
        return NO_BUILD_REWARD_BLOCKS.contains(BuiltInRegistries.BLOCK.getKey(block).getPath());
    }

    private static double oreReward(String path) {
        if (path.contains("diamond")) {
            return 12.0;
        }
        if (path.contains("emerald")) {
            return 10.0;
        }
        if (path.equals("ancient_debris")) {
            return 20.0;
        }
        if (path.contains("gold")) {
            return 6.0;
        }
        if (path.contains("lapis") || path.contains("redstone")) {
            return 4.0;
        }
        if (path.contains("iron")) {
            return 3.0;
        }
        if (path.contains("copper") || path.contains("quartz")) {
            return 2.0;
        }
        if (path.contains("coal")) {
            return 1.0;
        }
        return 2.0;
    }

    private static boolean hasSilkTouch(ServerPlayer player) {
        ItemStack tool = player.getMainHandItem();
        if (tool.isEmpty()) {
            return false;
        }
        try {
            return EnchantmentHelper.getItemEnchantmentLevel((Holder)player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH), (ItemStack)tool) > 0;
        }
        catch (RuntimeException exception) {
            return false;
        }
    }

    private static double buildRewardForPlacedBlock(ServerPlayer player, BlockPos pos) {
        NationStore store = NationStore.get();
        Optional<NationStore.Nation> nation = store.nationOf(player.getUUID());
        if (nation.isEmpty() || nation.get().doctrine() != Doctrine.ITALIAN) {
            return 0.0;
        }
        ClaimKey claim = ClaimKey.of(player.serverLevel(), new ChunkPos(pos));
        if (!nation.get().id.equals(store.nationOwning(claim).map(owner -> owner.id).orElse(""))
            || store.isCapturedClaimHeldBy(nation.get(), claim.id())) {
            return 0.0;
        }
        return BUILD_REWARD;
    }

    private static String buildRewardPositionKey(ServerLevel level, BlockPos pos) {
        return level.dimension().location() + ":" + pos.getX() + ":" + pos.getY() + ":" + pos.getZ();
    }

    private static double incomeMultiplier(ServerPlayer player) {
        return NationStore.get().nationOf(player.getUUID()).map(nation -> nation.doctrine().incomeMultiplier).orElse(1.0);
    }

    private static boolean isPaidAccessBlock(Block block) {
        return block instanceof DoorBlock || block instanceof TrapDoorBlock || block instanceof FenceGateBlock || block instanceof ChestBlock || block instanceof BarrelBlock || block instanceof ShulkerBoxBlock;
    }

    private static boolean isFluidBucket(ItemStack stack) {
        return stack.is(Items.LAVA_BUCKET) || stack.is(Items.WATER_BUCKET) || stack.is(Items.POWDER_SNOW_BUCKET) || stack.is(Items.COD_BUCKET) || stack.is(Items.SALMON_BUCKET) || stack.is(Items.PUFFERFISH_BUCKET) || stack.is(Items.TROPICAL_FISH_BUCKET) || stack.is(Items.AXOLOTL_BUCKET) || stack.is(Items.TADPOLE_BUCKET);
    }

    private static boolean shouldCancelFluidBucketUse(ServerPlayer player, ServerLevel level, BlockPos clickedPos, Direction face, long tick) {
        if (NationEvents.isFluidBlockedAt(player, level, clickedPos, tick)) {
            return true;
        }
        BlockPos targetPos = face == null ? clickedPos : clickedPos.relative(face);
        return !targetPos.equals((Object)clickedPos) && NationEvents.isFluidBlockedAt(player, level, targetPos, tick);
    }

    private static boolean isFluidBlockedAt(ServerPlayer player, ServerLevel level, BlockPos pos, long tick) {
        ClaimKey claim;
        NationStore store = NationStore.get();
        Optional<NationStore.Nation> owner = store.nationOwning(claim = ClaimKey.of(level, new ChunkPos(pos)));
        if (owner.isEmpty()) {
            return false;
        }
        if (store.isRaidActive(claim.id(), NationStore.persistentNow())) {
            NationEvents.grantActionOpacPass(player, tick);
            return false;
        }
        if (store.isMember(player.getUUID(), owner.get())) {
            NationEvents.grantActionOpacPass(player, tick);
            return false;
        }
        if (NationEvents.isActiveWarClaim(player, owner.get()) && !NationEvents.isPeacePendingWarClaim(player, owner.get())
            && NationWarsConfig.get().scorchedEarth && !NationEvents.isWarRespawnLocked(player, tick)) {
            NationEvents.grantActionOpacPass(player, tick);
            return false;
        }
        player.sendSystemMessage(NationText.message("nationwars.claim.fluid_blocked", owner.get().name));
        return true;
    }

    private static void grantClaimActionPassIfNeeded(ServerPlayer player, BlockPos pos, long tick) {
        NationStore store = NationStore.get();
        ClaimKey claim = ClaimKey.of(player.serverLevel(), new ChunkPos(pos));
        Optional<NationStore.Nation> owner = store.nationOwning(claim);
        if (owner.isEmpty()) {
            return;
        }
        if (store.isRaidActive(claim.id(), NationStore.persistentNow()) || store.isMember(player.getUUID(), owner.get())) {
            NationEvents.grantActionOpacPass(player, tick);
            return;
        }
        Optional<NationStore.Nation> own = store.nationOf(player.getUUID());
        if (own.isPresent() && NationWarsConfig.get().scorchedEarth
            && store.activeWarForCapture(own.get(), owner.get()).filter(war -> war.peaceDeal == null).isPresent()
            && !NationEvents.isWarRespawnLocked(player, tick)) {
            NationEvents.grantActionOpacPass(player, tick);
        }
    }

    private static boolean canExplosionAffect(ServerPlayer player, ServerLevel level, BlockPos pos) {
        NationStore store = NationStore.get();
        Optional<NationStore.Nation> own = store.nationOf(player.getUUID());
        ClaimKey claim = ClaimKey.of(level, new ChunkPos(pos));
        Optional<NationStore.Nation> owner = store.nationOwning(claim);
        if (owner.isEmpty()) {
            return true;
        }
        if (store.isRaidActive(claim.id(), NationStore.persistentNow())) {
            return true;
        }
        if (own.isEmpty()) {
            return false;
        }
        return store.isMember(player.getUUID(), owner.get()) || NationWarsConfig.get().scorchedEarth
            && store.activeWarForCapture(own.get(), owner.get()).filter(war -> war.peaceDeal == null).isPresent()
            && !NationEvents.isWarRespawnLocked(player, player.getServer().getTickCount());
    }

    private static boolean shouldBlockScorchedEarthAction(ServerPlayer player, ServerLevel level, BlockPos pos, long tick) {
        if (NationWarsConfig.get().scorchedEarth) {
            return false;
        }
        NationStore store = NationStore.get();
        ClaimKey claim = ClaimKey.of(level, new ChunkPos(pos));
        Optional<NationStore.Nation> owner = store.nationOwning(claim);
        if (owner.isEmpty() || store.isRaidActive(claim.id(), NationStore.persistentNow()) || store.isMember(player.getUUID(), owner.get())) {
            return false;
        }
        Optional<NationStore.Nation> own = store.nationOf(player.getUUID());
        return own.isPresent() && store.activeWarForCapture(own.get(), owner.get()).isPresent() && !NationEvents.isWarRespawnLocked(player, tick);
    }

    private static boolean isActiveWarClaim(ServerPlayer player, NationStore.Nation owner) {
        NationStore store = NationStore.get();
        return store.nationOf(player.getUUID()).filter(nation -> !nation.id.equals(owner.id)).flatMap(nation -> store.activeWarForCapture((NationStore.Nation)nation, owner)).isPresent();
    }

    private static boolean isPeacePendingWarClaim(ServerPlayer player, ServerLevel level, BlockPos pos) {
        NationStore.Nation owner = NationStore.get().nationOwning(ClaimKey.of(level, new ChunkPos(pos))).orElse(null);
        return owner != null && NationEvents.isPeacePendingWarClaim(player, owner);
    }

    private static boolean isPeacePendingWarClaim(ServerPlayer player, NationStore.Nation owner) {
        NationStore store = NationStore.get();
        return store.nationOf(player.getUUID()).filter(nation -> !nation.id.equals(owner.id))
            .flatMap(nation -> store.activeWarForCapture(nation, owner)).map(war -> war.peaceDeal != null).orElse(false);
    }

    private static void grantActionOpacPass(ServerPlayer player, long tick) {
        NationEvents.grantTemporaryOpacPass(player, tick + ACTION_ACCESS_PASS_TICKS);
    }

    private static void applyCapitulation(MinecraftServer server, NationStore store, NationStore.War war, NationStore.Nation attacker, NationStore.Nation defender, int defenderClaimsBefore, int alreadyTransferred) {
        Optional<String> claim;
        int warCoreClaims = store.coreClaimCountForWar(war, defender);
        int surrenderBase = warCoreClaims > 0 ? warCoreClaims : defenderClaimsBefore;
        int target = Math.max(1, (int)Math.ceil((double)surrenderBase * 0.25 * defender.doctrine().surrenderLandMultiplier));
        for (int transferred = alreadyTransferred; transferred < target && !(claim = store.borderClaimsOf(defender).stream()
            .filter(id -> !id.equals(defender.capitalClaim) && store.isCoreClaimForWar(war, defender, id)
                && !store.isClaimCapturedInOtherActiveWar(war, id)).findFirst()).isEmpty(); ++transferred) {
            if (!store.captureClaim(war, attacker, defender, claim.get())) {
                break;
            }
        }
        store.notifyNation(server, attacker, NationText.message("nationwars.war.capitulation.attacker", defender.name));
        store.notifyNation(server, defender, NationText.message("nationwars.war.capitulation.defender"));
        if (store.claimCount(defender) <= 0) {
            NationEvents.eliminateNation(server, store, war, attacker, defender);
            return;
        }
        if (store.isPrimaryWarParticipant(war, defender)) {
            store.endWar(war);
        } else {
            store.removeWarParticipant(war, defender);
            store.notifyNation(server, attacker, NationText.message("nationwars.war.capitulation.continues", defender.name));
        }
    }

    private static void eliminateNation(MinecraftServer server, NationStore store, NationStore.War war, NationStore.Nation conqueror, NationStore.Nation defeated) {
        Map<NationStore.Nation, Integer> weights = store.capturedClaimWeightsAgainst(war, defeated, conqueror);
        int totalWeight = weights.values().stream().mapToInt(Integer::intValue).sum();
        double loot = defeated.balance;
        defeated.balance = 0.0;
        try {
            loot = NationStore.roundMoney(loot + store.confiscatePlayerMoneyForTransaction(UUID.fromString(defeated.owner)));
        }
        catch (RuntimeException exception) {
            loot = NationStore.roundMoney(loot);
        }
        if (loot > 0.0 && totalWeight > 0) {
            double distributed = 0.0;
            int index = 0;
            int recipientCount = weights.size();
            for (Map.Entry<NationStore.Nation, Integer> entry : weights.entrySet()) {
                double share = ++index == recipientCount ? NationStore.roundMoney(loot - distributed) : NationStore.roundMoney(loot * (double)entry.getValue().intValue() / (double)totalWeight);
                distributed = NationStore.roundMoney(distributed + share);
                entry.getKey().balance = NationStore.roundMoney(entry.getKey().balance + share);
                store.notifyNation(server, entry.getKey(), NationText.message("nationwars.war.elimination.share", defeated.name, share));
            }
        }
        store.notifyNation(server, defeated, NationText.message("nationwars.war.elimination.deleted"));
        store.notifyNation(server, conqueror, NationText.message("nationwars.war.elimination.conqueror", defeated.name));
        if (store.isPrimaryWarParticipant(war, defeated)) {
            store.endWar(war);
        } else {
            store.removeWarParticipant(war, defeated);
        }
        store.deleteNation(defeated);
        NationEvents.refreshAllTabListNames(server);
    }

    private static void scheduleOpacSync(long tick, int retries) {
        nextOpacSyncTick = nextOpacSyncTick < 0L ? tick : Math.min(nextOpacSyncTick, tick);
        opacSyncRetries = Math.max(opacSyncRetries, retries);
    }

    private static void syncOpac(MinecraftServer server) {
        try {
            OpacClaimsBridge.syncAll(server, NationStore.get());
        }
        catch (RuntimeException exception) {
            NationWars.LOGGER.warn("Automatic OPAC claim sync failed; it will retry if retries remain.", (Throwable)exception);
        }
    }

    private static void cleanupCooldowns(MinecraftServer server, long tick) {
        NationEvents.removeExpired(ACCESS_COOLDOWNS, tick);
        NationEvents.removeExpired(BUILD_REWARD_COOLDOWNS, tick);
        NationEvents.removeExpiredOpacPasses(server, tick);
        WAR_RESPAWN_LOCKS.entrySet().removeIf(entry -> entry.getValue() <= tick);
    }

    private static boolean isWarRespawnLocked(ServerPlayer player, long tick) {
        return WAR_RESPAWN_LOCKS.getOrDefault(player.getUUID(), 0L) > tick;
    }

    private static boolean isEnemyWarClaim(ServerPlayer player, ServerLevel level, BlockPos pos) {
        NationStore store = NationStore.get();
        NationStore.Nation own = store.nationOf(player.getUUID()).orElse(null);
        NationStore.Nation owner = store.nationOwning(ClaimKey.of(level, new ChunkPos(pos))).orElse(null);
        return own != null && owner != null && !own.id.equals(owner.id) && store.activeWarForCapture(own, owner).isPresent();
    }

    private static void removeExpired(Map<String, Long> map, long tick) {
        Iterator<Map.Entry<String, Long>> iterator = map.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue() >= tick) continue;
            iterator.remove();
        }
    }

    private static void grantTemporaryOpacPass(ServerPlayer player, long expiresAtTick) {
        UUID playerId = player.getUUID();
        OPAC_FULL_PASSES.put(playerId, expiresAtTick);
        OpenPACServerAPI.get((MinecraftServer)player.getServer()).getChunkProtection().giveFullPass(playerId);
    }

    private static void revokeTemporaryOpacPass(ServerPlayer player) {
        UUID playerId = player.getUUID();
        if (OPAC_FULL_PASSES.remove(playerId) != null) {
            OpenPACServerAPI.get((MinecraftServer)player.getServer()).getChunkProtection().removeFullPass(playerId);
        }
    }

    private static void removeExpiredOpacPasses(MinecraftServer server, long tick) {
        Iterator<Map.Entry<UUID, Long>> iterator = OPAC_FULL_PASSES.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Long> entry = iterator.next();
            if (entry.getValue() >= tick) continue;
            OpenPACServerAPI.get((MinecraftServer)server).getChunkProtection().removeFullPass(entry.getKey());
            iterator.remove();
        }
    }

    private static void clearOpacPasses(MinecraftServer server) {
        for (UUID playerId : OPAC_FULL_PASSES.keySet()) {
            OpenPACServerAPI.get((MinecraftServer)server).getChunkProtection().removeFullPass(playerId);
        }
        OPAC_FULL_PASSES.clear();
    }

    private static final class CaptureProgress {
        private final String attackerNation;
        private final String defenderNation;
        private final String claimId;
        private int seconds;
        private int requiredSeconds = 50;

        private CaptureProgress(String attackerNation, String defenderNation, String claimId) {
            this.attackerNation = attackerNation;
            this.defenderNation = defenderNation;
            this.claimId = claimId;
        }
    }
}
