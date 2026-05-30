package dev.moth.nationwars;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.loading.FMLPaths;
import xaero.pac.common.claims.player.api.IPlayerChunkClaimAPI;
import xaero.pac.common.server.api.OpenPACServerAPI;
import xaero.pac.common.server.claims.api.IServerClaimsManagerAPI;
import xaero.pac.common.server.player.config.api.v2.IPlayerConfigAPI;
import xaero.pac.common.server.player.config.api.v2.PlayerConfigOptions;

public final class OpacClaimsBridge {
   private static final Pattern MAX_PLAYER_CLAIMS = Pattern.compile("(?m)^(\\s*maxPlayerClaims\\s*=\\s*)\\d+\\s*$");

   private OpacClaimsBridge() {
   }

   public static void forceMaxPlayerClaimsZero() {
      Path config = FMLPaths.CONFIGDIR.get().resolve("openpartiesandclaims-server.toml");
      if (Files.exists(config)) {
         try {
            String text = Files.readString(config);
            Matcher matcher = MAX_PLAYER_CLAIMS.matcher(text);
            if (!matcher.find()) {
               return;
            }

            String updated = matcher.replaceFirst(Matcher.quoteReplacement(matcher.group(1) + "0"));
            if (!updated.equals(text)) {
               Files.writeString(config, updated);
               NationWars.LOGGER.info("Set Open Parties and Claims maxPlayerClaims to 0 in {}", config);
            }
         } catch (IOException var4) {
            NationWars.LOGGER.warn("Could not update Open Parties and Claims maxPlayerClaims setting.", var4);
         }
      }
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
         syncClaimDisplayName(server, nation);
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
            syncClaimDisplayName(server, newOwner);
            return;
         }

         NationWars.LOGGER.warn("Skipping OPAC transfer for {} because it is already claimed by {}", claim.id(), existing.getPlayerId());
      } else {
         claims.unclaim(dimension(claim), claim.x(), claim.z());
         syncClaimDisplayName(server, newOwner);
         claims.claim(dimension(claim), nationOwner(newOwner), 0, claim.x(), claim.z(), false);
      }
   }

   public static void syncAll(MinecraftServer server, NationStore store) {
      for (Entry<String, String> entry : store.claimOwnerEntries()) {
         try {
            NationStore.Nation nation = store.nationById(entry.getValue()).orElse(null);
            if (nation != null) {
               ClaimKey claim = ClaimKey.parse(entry.getKey());
               removeLegacyMisplacedMirror(server, store, nation, claim);
               syncClaimDisplayName(server, nation);
               mirrorClaim(server, nation, claim);
            }
         } catch (RuntimeException var6) {
            NationWars.LOGGER.warn("Skipping OPAC sync for NationWars claim {}.", entry.getKey(), var6);
         }
      }
   }

   public static void syncClaimDisplayName(MinecraftServer server, NationStore.Nation nation) {
      try {
         IPlayerConfigAPI config = OpenPACServerAPI.get(server).getPlayerConfigManager().getLoadedConfig(nationOwner(nation));
         if (config != null) {
            config.tryToSet(PlayerConfigOptions.CLAIMS_NAME, nation.name);
         }
      } catch (RuntimeException var3) {
         NationWars.LOGGER.warn("Could not set Open Parties and Claims display name for {}.", nation.name, var3);
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
