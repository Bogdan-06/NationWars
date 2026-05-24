package dev.moth.nationwars;

import java.util.Optional;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import xaero.pac.common.claims.player.api.IPlayerChunkClaimAPI;
import xaero.pac.common.server.api.OpenPACServerAPI;
import xaero.pac.common.server.claims.api.IServerClaimsManagerAPI;

public final class OpacClaimsBridge {
   private OpacClaimsBridge() {
   }

   public static boolean canMirrorClaim(MinecraftServer server, ClaimKey claim, UUID opacOwner) {
      IServerClaimsManagerAPI claims = claims(server);
      IPlayerChunkClaimAPI existing = claims.get(dimension(claim), claim.x(), claim.z());
      return existing == null || existing.getPlayerId().equals(opacOwner);
   }

   public static Optional<UUID> ownerOf(MinecraftServer server, ClaimKey claim) {
      IPlayerChunkClaimAPI existing = claims(server).get(dimension(claim), claim.x(), claim.z());
      return existing == null ? Optional.empty() : Optional.of(existing.getPlayerId());
   }

   public static void mirrorClaim(MinecraftServer server, NationStore.Nation nation, ClaimKey claim) {
      UUID opacOwner = nationOwner(nation);
      if (!canMirrorClaim(server, claim, opacOwner)) {
         ownerOf(server, claim)
            .ifPresent(owner -> NationWars.LOGGER.warn("Skipping OPAC mirror for {} because it is already claimed by {}", claim.id(), owner));
      } else {
         claims(server).claim(dimension(claim), opacOwner, 0, claim.x(), claim.z(), false);
      }
   }

   public static void unmirrorClaim(MinecraftServer server, NationStore.Nation nation, ClaimKey claim) {
      IServerClaimsManagerAPI claims = claims(server);
      IPlayerChunkClaimAPI existing = claims.get(dimension(claim), claim.x(), claim.z());
      if (existing != null && existing.getPlayerId().equals(nationOwner(nation))) {
         claims.unclaim(dimension(claim), claim.x(), claim.z());
      }
   }

   public static void replaceClaim(MinecraftServer server, NationStore.Nation oldOwner, NationStore.Nation newOwner, ClaimKey claim) {
      IServerClaimsManagerAPI claims = claims(server);
      IPlayerChunkClaimAPI existing = claims.get(dimension(claim), claim.x(), claim.z());
      if (existing != null && !existing.getPlayerId().equals(nationOwner(oldOwner))) {
         if (existing.getPlayerId().equals(nationOwner(newOwner))) {
            return;
         }

         NationWars.LOGGER.warn("Skipping OPAC transfer for {} because it is already claimed by {}", claim.id(), existing.getPlayerId());
      } else {
         claims.unclaim(dimension(claim), claim.x(), claim.z());
         claims.claim(dimension(claim), nationOwner(newOwner), 0, claim.x(), claim.z(), false);
      }
   }

   public static void syncAll(MinecraftServer server, NationStore store) {
      for (Entry<String, String> entry : store.claimOwnerEntries()) {
         NationStore.Nation nation = store.nationById(entry.getValue()).orElse(null);
         if (nation != null) {
            ClaimKey claim = ClaimKey.parse(entry.getKey());
            removeLegacyMisplacedMirror(server, store, nation, claim);
            mirrorClaim(server, nation, claim);
         }
      }
   }

   private static void removeLegacyMisplacedMirror(MinecraftServer server, NationStore store, NationStore.Nation nation, ClaimKey claim) {
      ClaimKey legacy = new ClaimKey(claim.dimension(), claim.z(), 0);
      if (!legacy.id().equals(claim.id()) && !store.nationOwning(legacy).isPresent()) {
         IServerClaimsManagerAPI claims = claims(server);
         IPlayerChunkClaimAPI existing = claims.get(dimension(legacy), legacy.x(), legacy.z());
         if (existing != null && existing.getPlayerId().equals(nationOwner(nation)) && existing.getSubConfigIndex() == claim.x()) {
            claims.unclaim(dimension(legacy), legacy.x(), legacy.z());
            NationWars.LOGGER.info("Removed legacy misplaced OPAC mirror {} for NationWars claim {}", legacy.id(), claim.id());
         }
      }
   }

   private static IServerClaimsManagerAPI claims(MinecraftServer server) {
      return OpenPACServerAPI.get(server).getServerClaimsManager();
   }

   private static ResourceLocation dimension(ClaimKey claim) {
      return ResourceLocation.parse(claim.dimension());
   }

   private static UUID nationOwner(NationStore.Nation nation) {
      return UUID.fromString(nation.owner);
   }
}
