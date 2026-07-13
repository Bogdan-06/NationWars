/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.resources.ResourceLocation
 *  net.minecraft.server.MinecraftServer
 *  net.neoforged.bus.api.SubscribeEvent
 *  net.neoforged.fml.loading.FMLPaths
 *  xaero.pac.common.claims.player.api.IPlayerChunkClaimAPI
 *  xaero.pac.common.event.api.OPACServerAddonRegisterEvent
 *  xaero.pac.common.server.api.OpenPACServerAPI
 *  xaero.pac.common.server.claims.api.IServerClaimsManagerAPI
 *  xaero.pac.common.server.parties.system.api.IPlayerPartySystemAPI
 *  xaero.pac.common.server.player.config.api.v2.IPlayerConfigAPI
 *  xaero.pac.common.server.player.config.api.v2.PlayerConfigOptions
 */
package dev.moth.nationwars;

import dev.moth.nationwars.ClaimKey;
import dev.moth.nationwars.NationStore;
import dev.moth.nationwars.NationWars;
import dev.moth.nationwars.NationWarsConfig;
import dev.moth.nationwars.integration.opac.OpacConfigSynchronizer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.loading.FMLPaths;
import xaero.pac.common.claims.player.api.IPlayerChunkClaimAPI;
import xaero.pac.common.event.api.OPACServerAddonRegisterEvent;
import xaero.pac.common.server.api.OpenPACServerAPI;
import xaero.pac.common.server.claims.api.IServerClaimsManagerAPI;
import xaero.pac.common.server.config.ServerConfig;
import xaero.pac.common.server.parties.system.api.v2.IPlayerPartySystemAPI;
import xaero.pac.common.server.player.config.IPlayerConfigManager;
import xaero.pac.common.server.player.config.api.v2.IPlayerConfigAPI;
import xaero.pac.common.server.player.config.api.v2.PlayerConfigOptions;

public final class OpacClaimsBridge {
    private static final String PARTY_SYSTEM_ID = "nationwars";

    private OpacClaimsBridge() {
    }

    public static void forceMaxPlayerClaimsZero() {
        if (!TechnicalConfig.synchronizeOpacConfiguration() || !TechnicalConfig.mayEditOpacConfiguration()) {
            NationWars.LOGGER.info("Nation Wars OPAC file synchronization is disabled by technical configuration.");
            return;
        }
        Path config = FMLPaths.CONFIGDIR.get().resolve("openpartiesandclaims-server.toml");
        try {
            OpacConfigSynchronizer.Result result = new OpacConfigSynchronizer().synchronize(config,
                NationWarsConfig.get().setOpenPacPrimaryPartySystem);
            if (result.changed()) {
                NationWars.LOGGER.info("Updated OPAC configuration {}: {}", config, String.join(", ", result.changes()));
            }
        }
        catch (IOException exception) {
            NationWars.LOGGER.warn("Could not update Open Parties and Claims server settings.", (Throwable)exception);
        }
    }

    @SubscribeEvent
    public static void registerPartySystem(OPACServerAddonRegisterEvent event) {
        event.getPartySystemManagerAPI().register(PARTY_SYSTEM_ID, (IPlayerPartySystemAPI)NationWarsPartySystem.INSTANCE);
        NationWars.LOGGER.info("Registered Nation Wars Open Parties and Claims party system.");
    }

    public static void activatePrimaryPartySystem(MinecraftServer server) {
        if (!TechnicalConfig.synchronizeOpacConfiguration() || !NationWarsConfig.get().setOpenPacPrimaryPartySystem) {
            NationWars.LOGGER.info("Nation Wars OPAC primary party-system activation is disabled by configuration.");
            return;
        }
        try {
            ServerConfig.CONFIG.maxPlayerClaims.set(0);
            ServerConfig.CONFIG.partyOwnedClaims.set(true);
            Object manager = OpenPACServerAPI.get((MinecraftServer)server).getPlayerConfigManager();
            if (manager instanceof IPlayerConfigManager) {
                IPlayerConfigManager internalManager = (IPlayerConfigManager)manager;
                internalManager.getPartySystemManager().updatePrimarySystem(PARTY_SYSTEM_ID);
                NationWars.LOGGER.info("Activated Nation Wars as the Open Parties and Claims primary party system.");
            } else {
                NationWars.LOGGER.warn("Could not activate the Nation Wars OPAC party system without a restart.");
            }
        }
        catch (RuntimeException exception) {
            NationWars.LOGGER.warn("Could not activate the Nation Wars OPAC party system.", (Throwable)exception);
        }
    }

    public static boolean canMirrorClaim(MinecraftServer server, ClaimKey claim, UUID opacOwner) {
        IServerClaimsManagerAPI claims = OpacClaimsBridge.claims(server);
        IPlayerChunkClaimAPI existing = claims.get(OpacClaimsBridge.dimension(claim), claim.x(), claim.z());
        return existing == null || existing.getPlayerId().equals(opacOwner);
    }

    public static Optional<UUID> ownerOf(MinecraftServer server, ClaimKey claim) {
        IPlayerChunkClaimAPI existing = OpacClaimsBridge.claims(server).get(OpacClaimsBridge.dimension(claim), claim.x(), claim.z());
        return existing == null ? Optional.empty() : Optional.of(existing.getPlayerId());
    }

    public static void mirrorClaim(MinecraftServer server, NationStore.Nation nation, ClaimKey claim) {
        UUID opacOwner = OpacClaimsBridge.nationOwner(nation);
        if (!OpacClaimsBridge.canMirrorClaim(server, claim, opacOwner)) {
            OpacClaimsBridge.ownerOf(server, claim).ifPresent(owner -> NationWars.LOGGER.warn("Skipping OPAC mirror for {} because it is already claimed by {}", (Object)claim.id(), owner));
            return;
        }
        OpacClaimsBridge.syncClaimDisplayName(server, nation);
        OpacClaimsBridge.claims(server).claim(OpacClaimsBridge.dimension(claim), opacOwner, 0, claim.x(), claim.z(), false);
    }

    public static void unmirrorClaim(MinecraftServer server, NationStore.Nation nation, ClaimKey claim) {
        IServerClaimsManagerAPI claims = OpacClaimsBridge.claims(server);
        IPlayerChunkClaimAPI existing = claims.get(OpacClaimsBridge.dimension(claim), claim.x(), claim.z());
        if (existing != null && existing.getPlayerId().equals(OpacClaimsBridge.nationOwner(nation))) {
            claims.unclaim(OpacClaimsBridge.dimension(claim), claim.x(), claim.z());
        }
    }

    public static void replaceClaim(MinecraftServer server, NationStore.Nation oldOwner, NationStore.Nation newOwner, ClaimKey claim) {
        IServerClaimsManagerAPI claims = OpacClaimsBridge.claims(server);
        IPlayerChunkClaimAPI existing = claims.get(OpacClaimsBridge.dimension(claim), claim.x(), claim.z());
        if (existing == null || existing.getPlayerId().equals(OpacClaimsBridge.nationOwner(oldOwner))) {
            claims.unclaim(OpacClaimsBridge.dimension(claim), claim.x(), claim.z());
            OpacClaimsBridge.syncClaimDisplayName(server, newOwner);
            claims.claim(OpacClaimsBridge.dimension(claim), OpacClaimsBridge.nationOwner(newOwner), 0, claim.x(), claim.z(), false);
        } else {
            if (existing.getPlayerId().equals(OpacClaimsBridge.nationOwner(newOwner))) {
                OpacClaimsBridge.syncClaimDisplayName(server, newOwner);
                return;
            }
            NationWars.LOGGER.warn("Skipping OPAC transfer for {} because it is already claimed by {}", (Object)claim.id(), (Object)existing.getPlayerId());
        }
    }

    public static void syncAll(MinecraftServer server, NationStore store) {
        for (NationStore.Nation nation : store.nations()) {
            OpacClaimsBridge.syncClaimDisplayName(server, nation);
        }
        for (Map.Entry<String, String> entry : store.claimOwnerEntries()) {
            try {
                NationStore.Nation nation = store.nationById(entry.getValue()).orElse(null);
                if (nation == null) continue;
                ClaimKey claim = ClaimKey.parse(entry.getKey());
                OpacClaimsBridge.removeLegacyMisplacedMirror(server, store, nation, claim);
                OpacClaimsBridge.mirrorClaim(server, nation, claim);
            }
            catch (RuntimeException exception) {
                NationWars.LOGGER.warn("Skipping OPAC sync for NationWars claim {}.", (Object)entry.getKey(), (Object)exception);
            }
        }
    }

    public static void syncClaimDisplayName(MinecraftServer server, NationStore.Nation nation) {
        try {
            IPlayerConfigAPI config = OpenPACServerAPI.get((MinecraftServer)server).getPlayerConfigManager().getLoadedConfig(OpacClaimsBridge.nationOwner(nation));
            if (config != null) {
                IPlayerConfigAPI.SetResult result = config.tryToSet(PlayerConfigOptions.CLAIMS_NAME, nation.name);
                if (result != IPlayerConfigAPI.SetResult.SUCCESS && result != IPlayerConfigAPI.SetResult.DEFAULTED) {
                    NationWars.LOGGER.warn("Open Parties and Claims rejected display name '{}' for {}: {}", (Object)nation.name, (Object)nation.id, (Object)result);
                }
            }
        }
        catch (RuntimeException exception) {
            NationWars.LOGGER.warn("Could not set Open Parties and Claims display name for {}.", (Object)nation.name, (Object)exception);
        }
    }

    private static void removeLegacyMisplacedMirror(MinecraftServer server, NationStore store, NationStore.Nation nation, ClaimKey claim) {
        ClaimKey legacy = new ClaimKey(claim.dimension(), claim.z(), 0);
        if (legacy.id().equals(claim.id()) || store.nationOwning(legacy).isPresent()) {
            return;
        }
        IServerClaimsManagerAPI claims = OpacClaimsBridge.claims(server);
        IPlayerChunkClaimAPI existing = claims.get(OpacClaimsBridge.dimension(legacy), legacy.x(), legacy.z());
        if (existing != null && existing.getPlayerId().equals(OpacClaimsBridge.nationOwner(nation)) && existing.getSubConfigIndex() == claim.x()) {
            claims.unclaim(OpacClaimsBridge.dimension(legacy), legacy.x(), legacy.z());
            NationWars.LOGGER.info("Removed legacy misplaced OPAC mirror {} for NationWars claim {}", (Object)legacy.id(), (Object)claim.id());
        }
    }

    private static IServerClaimsManagerAPI claims(MinecraftServer server) {
        return OpenPACServerAPI.get((MinecraftServer)server).getServerClaimsManager();
    }

    private static ResourceLocation dimension(ClaimKey claim) {
        return ResourceLocation.parse((String)claim.dimension());
    }

    private static UUID nationOwner(NationStore.Nation nation) {
        return UUID.fromString(nation.owner);
    }

    private static final class NationWarsPartySystem
    implements IPlayerPartySystemAPI<NationStore.Nation> {
        private static final NationWarsPartySystem INSTANCE = new NationWarsPartySystem();

        private NationWarsPartySystem() {
        }

        public NationStore.Nation getPartyByOwner(UUID playerId) {
            return NationWarsPartySystem.store().flatMap(store -> store.nations().stream().filter(nation -> nation.owner.equals(playerId.toString())).findFirst()).orElse(null);
        }

        public NationStore.Nation getPartyByMember(UUID playerId) {
            return NationWarsPartySystem.store().flatMap(store -> store.nationOf(playerId)).orElse(null);
        }

        public boolean isPlayerAllying(UUID firstPlayerId, UUID secondPlayerId) {
            Optional<NationStore> store = NationWarsPartySystem.store();
            if (store.isEmpty()) {
                return false;
            }
            Optional<NationStore.Nation> first = store.get().nationOf(firstPlayerId);
            Optional<NationStore.Nation> second = store.get().nationOf(secondPlayerId);
            return first.isPresent() && second.isPresent() && (first.get().id.equals(second.get().id) || store.get().sameAlliance(first.get(), second.get()));
        }

        public boolean isPermittedToPartyClaim(UUID playerId) {
            return NationWarsPartySystem.store().flatMap(store -> store.nationOf(playerId)).isPresent();
        }

        public UUID getOwner(NationStore.Nation nation) {
            return nation == null ? null : UUID.fromString(nation.owner);
        }

        public Component getName(NationStore.Nation nation) {
            return nation == null ? Component.empty() : Component.literal((String)nation.name);
        }

        public int getMemberCount(NationStore.Nation nation) {
            return nation == null || nation.members == null ? 0 : nation.members.size();
        }

        public boolean canEditPartyConfig(UUID playerId) {
            return NationWarsPartySystem.store().flatMap(store -> store.nationOf(playerId)).map(nation -> nation.owner.equals(playerId.toString())).orElse(false);
        }

        public boolean canCreatePartyConfigGroups(UUID playerId) {
            return this.canEditPartyConfig(playerId);
        }

        public boolean canIncludeGroupsInPartyConfigGroups(UUID playerId) {
            return this.canEditPartyConfig(playerId);
        }

        public boolean canIncludePlayersInPartyConfigGroups(UUID playerId) {
            return this.canEditPartyConfig(playerId);
        }

        private static Optional<NationStore> store() {
            try {
                return Optional.of(NationStore.get());
            }
            catch (IllegalStateException exception) {
                return Optional.empty();
            }
        }
    }
}
