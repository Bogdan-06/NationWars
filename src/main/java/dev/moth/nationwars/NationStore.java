package dev.moth.nationwars;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;

public final class NationStore {
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static final Random RANDOM = new Random();
   private static NationStore current;
   private final MinecraftServer server;
   private final Path file;
   private final NationStore.State state;

   private NationStore(MinecraftServer server, Path file, NationStore.State state) {
      this.server = server;
      this.file = file;
      this.state = state;
      this.normalize();
   }

   public static NationStore get() {
      if (current == null) {
         throw new IllegalStateException("Nation store has not loaded yet");
      } else {
         return current;
      }
   }

   public static void load(MinecraftServer server) {
      Path file = server.getWorldPath(LevelResource.ROOT).resolve("data").resolve("nationwars.json");
      NationStore.State state = new NationStore.State();
      if (Files.exists(file)) {
         try (Reader reader = Files.newBufferedReader(file)) {
            NationStore.State loaded = (NationStore.State)GSON.fromJson(reader, NationStore.State.class);
            if (loaded != null) {
               state = loaded;
            }
         } catch (RuntimeException | IOException var8) {
            NationWars.LOGGER.error("Failed to load Nation Wars data from {}", file, var8);
         }
      }

      current = new NationStore(server, file, state);
      current.save();
      OpacClaimsBridge.syncAll(server, current);
   }

   public void save() {
      try {
         Files.createDirectories(this.file.getParent());

         try (Writer writer = Files.newBufferedWriter(this.file)) {
            GSON.toJson(this.state, writer);
         }
      } catch (IOException var6) {
         NationWars.LOGGER.error("Failed to save Nation Wars data to {}", this.file, var6);
      }
   }

   public Collection<NationStore.Nation> nations() {
      return this.state.nations.values();
   }

   public Optional<NationStore.Nation> nationById(String id) {
      return Optional.ofNullable(this.state.nations.get(id));
   }

   public Set<Entry<String, String>> claimOwnerEntries() {
      return Set.copyOf(this.state.claims.entrySet());
   }

   public Collection<NationStore.War> wars() {
      return this.state.wars.values();
   }

   public boolean isDoctrineTaken(Doctrine doctrine) {
      return this.state.nations.values().stream().anyMatch(nation -> doctrine.id.equals(nation.doctrine));
   }

   public List<Doctrine> availableDoctrines() {
      return Arrays.stream(Doctrine.values()).filter(doctrine -> !this.isDoctrineTaken(doctrine)).toList();
   }

   public List<NationStore.MarketListing> marketListings() {
      return this.state.marketListings.stream().sorted(Comparator.comparingInt(listing -> listing.id)).toList();
   }

   public Optional<NationStore.MarketListing> marketListing(int id) {
      return this.state.marketListings.stream().filter(listing -> listing.id == id).findFirst();
   }

   public NationStore.MarketListing createMarketListing(ServerPlayer seller, ItemStack stack, double price) {
      NationStore.MarketListing listing = new NationStore.MarketListing();
      listing.id = this.state.nextListingId++;
      listing.seller = seller.getUUID().toString();
      listing.sellerName = seller.getGameProfile().getName();
      listing.price = roundMoney(price);
      listing.itemTag = stack.save(seller.registryAccess()).getAsString();
      this.state.marketListings.add(listing);
      this.save();
      return listing;
   }

   public boolean removeMarketListing(int id) {
      boolean removed = this.state.marketListings.removeIf(listing -> listing.id == id);
      if (removed) {
         this.save();
      }

      return removed;
   }

   public ItemStack listingStack(NationStore.MarketListing listing, Provider registries) {
      try {
         CompoundTag tag = TagParser.parseTag(listing.itemTag);
         return ItemStack.parseOptional(registries, tag);
      } catch (RuntimeException | CommandSyntaxException var4) {
         NationWars.LOGGER.warn("Failed to parse market listing {}", listing.id, var4);
         return ItemStack.EMPTY;
      }
   }

   public NationStore.Nation createNation(ServerPlayer owner, String name, Doctrine doctrine, ClaimKey capital) {
      String key = nationKey(name);
      NationStore.Nation nation = new NationStore.Nation();
      nation.id = key;
      nation.name = name;
      nation.owner = owner.getUUID().toString();
      nation.ownerName = owner.getGameProfile().getName();
      nation.doctrine = doctrine.id;
      nation.balance = 250.0;
      nation.freeClaimsRemaining = Math.max(0, doctrine.freeClaims - 1);
      nation.capitalClaim = capital.id();
      nation.members.add(nation.owner);
      this.state.nations.put(key, nation);
      this.state.playerNation.put(nation.owner, key);
      this.state.claims.put(capital.id(), key);
      this.ensurePlayer(owner.getUUID());
      OpacClaimsBridge.mirrorClaim(this.server, nation, capital);
      this.save();
      return nation;
   }

   public Optional<NationStore.Nation> nationByName(String name) {
      return Optional.ofNullable(this.state.nations.get(nationKey(name)));
   }

   public Optional<NationStore.Nation> nationOf(UUID playerId) {
      return Optional.ofNullable(this.state.playerNation.get(playerId.toString())).map(this.state.nations::get);
   }

   public List<NationStore.Nation> nationsSorted() {
      return this.state.nations.values().stream().sorted(Comparator.comparing(nation -> nation.name.toLowerCase(Locale.ROOT))).toList();
   }

   public Optional<NationStore.Nation> nationOwning(ClaimKey claim) {
      return Optional.ofNullable(this.state.claims.get(claim.id())).map(this.state.nations::get);
   }

   public boolean hasNation(UUID playerId) {
      return this.state.playerNation.containsKey(playerId.toString());
   }

   public boolean isMember(UUID playerId, NationStore.Nation nation) {
      return nation != null && nation.members.contains(playerId.toString());
   }

   public boolean isOwner(UUID playerId, NationStore.Nation nation) {
      return nation != null && Objects.equals(nation.owner, playerId.toString());
   }

   public void addMember(UUID playerId, NationStore.Nation nation) {
      String id = playerId.toString();
      nation.members.add(id);
      this.state.playerNation.put(id, nation.id);
      this.ensurePlayer(playerId);
      this.save();
   }

   public boolean addCityClaim(NationStore.Nation nation, String claimId, double cost) {
      if (nation.id.equals(this.state.claims.get(claimId)) && !nation.cityClaims.contains(claimId) && !(nation.balance + 1.0E-4 < cost)) {
         nation.balance = roundMoney(nation.balance - cost);
         nation.cityClaims.add(claimId);
         this.save();
         return true;
      } else {
         return false;
      }
   }

   public double playerBalance(UUID playerId) {
      this.ensurePlayer(playerId);
      return this.state.players.get(playerId.toString());
   }

   public void addPlayerMoney(UUID playerId, double amount) {
      this.ensurePlayer(playerId);
      this.state.players.compute(playerId.toString(), (id, balance) -> roundMoney((balance == null ? 0.0 : balance) + amount));
      this.save();
   }

   public boolean withdrawPlayerMoney(UUID playerId, double amount) {
      this.ensurePlayer(playerId);
      String id = playerId.toString();
      double balance = this.state.players.get(id);
      if (balance + 1.0E-4 < amount) {
         return false;
      } else {
         this.state.players.put(id, roundMoney(balance - amount));
         this.save();
         return true;
      }
   }

   public boolean depositToNation(UUID playerId, NationStore.Nation nation, double amount) {
      if (!this.withdrawPlayerMoney(playerId, amount)) {
         return false;
      } else {
         nation.balance = roundMoney(nation.balance + amount);
         this.save();
         return true;
      }
   }

   public int claimCount(NationStore.Nation nation) {
      int count = 0;

      for (String owner : this.state.claims.values()) {
         if (nation.id.equals(owner)) {
            count++;
         }
      }

      return count;
   }

   public List<String> claimsOf(NationStore.Nation nation) {
      return this.state.claims.entrySet().stream().filter(entry -> nation.id.equals(entry.getValue())).map(Entry::getKey).sorted().toList();
   }

   public boolean claim(NationStore.Nation nation, ClaimKey claim) {
      if (this.state.claims.containsKey(claim.id())) {
         return false;
      } else {
         this.state.claims.put(claim.id(), nation.id);
         OpacClaimsBridge.mirrorClaim(this.server, nation, claim);
         this.save();
         return true;
      }
   }

   public boolean unclaim(NationStore.Nation nation, ClaimKey claim) {
      if (!nation.id.equals(this.state.claims.get(claim.id()))) {
         return false;
      } else if (claim.id().equals(nation.capitalClaim)) {
         return false;
      } else {
         this.state.claims.remove(claim.id());
         OpacClaimsBridge.unmirrorClaim(this.server, nation, claim);
         this.save();
         return true;
      }
   }

   public void transferClaim(String claimId, NationStore.Nation newOwner) {
      NationStore.Nation oldOwner = Optional.ofNullable(this.state.claims.get(claimId)).map(this.state.nations::get).orElse(null);
      this.state.claims.put(claimId, newOwner.id);
      if (oldOwner != null) {
         OpacClaimsBridge.replaceClaim(this.server, oldOwner, newOwner, ClaimKey.parse(claimId));
      } else {
         OpacClaimsBridge.mirrorClaim(this.server, newOwner, ClaimKey.parse(claimId));
      }

      if (oldOwner != null && claimId.equals(oldOwner.capitalClaim)) {
         if (oldOwner.doctrine() == Doctrine.ROMANIAN) {
            oldOwner.lostCoreTerritory = true;
            this.notifyNation(
               this.server, oldOwner, Component.literal("[NationWars] The Iron Guard penalty is active: losing your core territory raised maintenance costs.")
            );
         }

         oldOwner.capitalClaim = this.claimsOf(oldOwner).stream().filter(id -> !id.equals(claimId)).findFirst().orElse("");
      }

      if (newOwner.capitalClaim == null || newOwner.capitalClaim.isBlank()) {
         newOwner.capitalClaim = claimId;
      }

      this.save();
   }

   public boolean removeBorderClaim(NationStore.Nation nation) {
      List<String> candidates = this.borderClaimsOf(nation).stream().filter(claim -> !claim.equals(nation.capitalClaim)).toList();
      if (candidates.isEmpty()) {
         return false;
      } else {
         String removed = candidates.get(RANDOM.nextInt(candidates.size()));
         this.state.claims.remove(removed);
         OpacClaimsBridge.unmirrorClaim(this.server, nation, ClaimKey.parse(removed));
         this.save();
         return true;
      }
   }

   public List<String> borderClaimsOf(NationStore.Nation nation) {
      Set<String> owned = new HashSet<>(this.claimsOf(nation));
      List<String> borders = new ArrayList<>();

      for (String claimId : owned) {
         ClaimKey claim = ClaimKey.parse(claimId);
         boolean border = List.of(
               new ClaimKey(claim.dimension(), claim.x() + 1, claim.z()),
               new ClaimKey(claim.dimension(), claim.x() - 1, claim.z()),
               new ClaimKey(claim.dimension(), claim.x(), claim.z() + 1),
               new ClaimKey(claim.dimension(), claim.x(), claim.z() - 1)
            )
            .stream()
            .anyMatch(neighbor -> !owned.contains(neighbor.id()));
         if (border) {
            borders.add(claimId);
         }
      }

      borders.sort(Comparator.naturalOrder());
      return borders;
   }

   public Optional<NationStore.War> warBetween(NationStore.Nation first, NationStore.Nation second) {
      return Optional.ofNullable(this.state.wars.get(warKey(first, second)));
   }

   public NationStore.War getOrCreateWar(NationStore.Nation attacker, NationStore.Nation defender) {
      String key = warKey(attacker, defender);
      NationStore.War war = this.state.wars.computeIfAbsent(key, ignored -> {
         NationStore.War created = new NationStore.War();
         created.id = key;
         created.attacker = attacker.id;
         created.defender = defender.id;
         return created;
      });
      ensureWarSides(war);
      return war;
   }

   public void endWar(NationStore.War war) {
      this.state.wars.remove(war.id);
      this.save();
   }

   public boolean applyPeaceDeal(NationStore.War war, NationStore.PeaceDeal deal) {
      if (war != null && deal != null && war.active) {
         NationStore.Nation proposer = this.nationById(deal.proposer).orElse(null);
         NationStore.Nation receiver = this.nationById(deal.receiver).orElse(null);
         if (proposer != null && receiver != null && this.isWarParticipant(war, proposer) && this.isWarParticipant(war, receiver)) {
            normalizePeaceDeal(deal);
            double demandedMoney = roundMoney(Math.max(0.0, deal.demandedMoney));
            double offeredMoney = roundMoney(Math.max(0.0, deal.offeredMoney));
            if (receiver.balance + 1.0E-4 < demandedMoney || proposer.balance + 1.0E-4 < offeredMoney) {
               return false;
            } else if (!this.claimsStillOwnedBy(deal.demandedClaims, receiver) || !this.claimsStillOwnedBy(deal.offeredClaims, proposer)) {
               return false;
            } else if (!this.containsCapital(deal.demandedClaims, receiver) && !this.containsCapital(deal.offeredClaims, proposer)) {
               if (demandedMoney > 0.0) {
                  receiver.balance = roundMoney(receiver.balance - demandedMoney);
                  proposer.balance = roundMoney(proposer.balance + demandedMoney);
               }

               if (offeredMoney > 0.0) {
                  proposer.balance = roundMoney(proposer.balance - offeredMoney);
                  receiver.balance = roundMoney(receiver.balance + offeredMoney);
               }

               for (String claimId : deal.demandedClaims) {
                  if (receiver.id.equals(this.state.claims.get(claimId))) {
                     this.transferClaim(claimId, proposer);
                  }
               }

               for (String claimIdx : deal.offeredClaims) {
                  if (proposer.id.equals(this.state.claims.get(claimIdx))) {
                     this.transferClaim(claimIdx, receiver);
                  }
               }

               if (deal.returnCapturedClaims) {
                  NationStore.Nation attacker = this.attacker(war).orElse(null);
                  NationStore.Nation defender = this.defender(war).orElse(null);
                  if (attacker != null && defender != null) {
                     for (String claimIdxx : new ArrayList<>(war.attackerCapturedClaims)) {
                        if (attacker.id.equals(this.state.claims.get(claimIdxx))) {
                           this.transferClaim(claimIdxx, defender);
                        }
                     }
                  }
               }

               this.endWar(war);
               return true;
            } else {
               return false;
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   public void setPeaceDeal(NationStore.War war, NationStore.PeaceDeal deal) {
      normalizePeaceDeal(deal);
      war.peaceDeal = deal;
      war.peaceOffers.clear();
      this.save();
   }

   public void clearPeaceDeal(NationStore.War war) {
      war.peaceDeal = null;
      this.save();
   }

   public Optional<NationStore.Nation> attacker(NationStore.War war) {
      return Optional.ofNullable(this.state.nations.get(war.attacker));
   }

   public Optional<NationStore.Nation> defender(NationStore.War war) {
      return Optional.ofNullable(this.state.nations.get(war.defender));
   }

   public Optional<NationStore.War> activeWarForCapture(NationStore.Nation attacker, NationStore.Nation defender) {
      return this.state.wars.values().stream().filter(war -> war.active).filter(war -> this.areOpposingWarSides(war, attacker, defender)).findFirst();
   }

   public boolean isWarParticipant(NationStore.War war, NationStore.Nation nation) {
      return war != null && nation != null && this.sideOf(war, nation) != 0;
   }

   public List<NationStore.War> activeWarsOf(NationStore.Nation nation) {
      return this.state
         .wars
         .values()
         .stream()
         .filter(war -> war.active)
         .filter(war -> this.isWarParticipant(war, nation))
         .sorted(Comparator.comparing(war -> war.id))
         .toList();
   }

   public Optional<NationStore.War> firstActiveWarOf(NationStore.Nation nation) {
      return this.activeWarsOf(nation).stream().findFirst();
   }

   public int sideOf(NationStore.War war, NationStore.Nation nation) {
      if (war != null && nation != null) {
         ensureWarSides(war);
         if (war.attackerSide.contains(nation.id)) {
            return 1;
         } else {
            return war.defenderSide.contains(nation.id) ? -1 : 0;
         }
      } else {
         return 0;
      }
   }

   public boolean areOpposingWarSides(NationStore.War war, NationStore.Nation first, NationStore.Nation second) {
      int firstSide = this.sideOf(war, first);
      int secondSide = this.sideOf(war, second);
      return firstSide != 0 && secondSide != 0 && firstSide != secondSide;
   }

   public boolean addWarJoinRequest(NationStore.War war, NationStore.Nation requester, NationStore.Nation sponsor) {
      if (war != null && requester != null && sponsor != null && war.active && this.sideOf(war, requester) == 0 && this.sideOf(war, sponsor) != 0) {
         war.joinRequests.put(requester.id, sponsor.id);
         this.save();
         return true;
      } else {
         return false;
      }
   }

   public boolean acceptWarJoinRequest(NationStore.War war, NationStore.Nation requester, NationStore.Nation acceptor) {
      if (war != null && requester != null && acceptor != null && war.active) {
         String sponsorId = war.joinRequests.get(requester.id);
         if (sponsorId != null && this.sideOf(war, acceptor) != 0 && this.sideOf(war, acceptor) == this.sideOf(war, this.nationById(sponsorId).orElse(null))) {
            if (this.sideOf(war, acceptor) > 0) {
               war.attackerSide.add(requester.id);
            } else {
               war.defenderSide.add(requester.id);
            }

            war.joinRequests.remove(requester.id);
            this.save();
            return true;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   public boolean rejectWarJoinRequest(NationStore.War war, NationStore.Nation requester, NationStore.Nation rejector) {
      if (war != null && requester != null && rejector != null && this.sideOf(war, rejector) != 0) {
         String sponsorId = war.joinRequests.get(requester.id);
         if (sponsorId != null && this.sideOf(war, rejector) == this.sideOf(war, this.nationById(sponsorId).orElse(null))) {
            war.joinRequests.remove(requester.id);
            this.save();
            return true;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   public boolean addWarDefenseCall(NationStore.War war, NationStore.Nation ally, NationStore.Nation caller) {
      if (war != null && ally != null && caller != null && war.active && this.sideOf(war, ally) == 0 && this.sideOf(war, caller) != 0) {
         war.joinRequests.putIfAbsent(ally.id, caller.id);
         this.save();
         return true;
      } else {
         return false;
      }
   }

   public boolean acceptWarDefenseCall(NationStore.War war, NationStore.Nation ally, NationStore.Nation caller) {
      return this.acceptWarJoinRequest(war, ally, caller);
   }

   public boolean rejectWarDefenseCall(NationStore.War war, NationStore.Nation ally, NationStore.Nation caller) {
      if (war != null && ally != null && caller != null && war.active) {
         String callerId = war.joinRequests.get(ally.id);
         if (!caller.id.equals(callerId)) {
            return false;
         } else {
            war.joinRequests.remove(ally.id);
            this.save();
            return true;
         }
      } else {
         return false;
      }
   }

   public boolean leaveWarSafely(NationStore.War war, NationStore.Nation nation) {
      if (war == null || nation == null || !war.active || this.sideOf(war, nation) == 0) {
         return false;
      } else if (!nation.id.equals(war.attacker) && !nation.id.equals(war.defender)) {
         war.attackerSide.remove(nation.id);
         war.defenderSide.remove(nation.id);
         war.joinRequests.remove(nation.id);
         this.save();
         return true;
      } else {
         this.endWar(war);
         return true;
      }
   }

   public Collection<NationStore.Alliance> alliances() {
      return this.state.alliances.values();
   }

   public Optional<NationStore.Alliance> allianceByName(String name) {
      return Optional.ofNullable(this.state.alliances.get(nationKey(name)));
   }

   public Optional<NationStore.Alliance> allianceOf(NationStore.Nation nation) {
      return this.state.alliances.values().stream().filter(alliance -> alliance.members.contains(nation.id)).findFirst();
   }

   public boolean createAlliance(NationStore.Nation leader, String name) {
      String id = nationKey(name);
      if (id.length() >= 3 && !this.state.alliances.containsKey(id) && !this.allianceOf(leader).isPresent()) {
         NationStore.Alliance alliance = new NationStore.Alliance();
         alliance.id = id;
         alliance.name = name;
         alliance.leader = leader.id;
         alliance.members.add(leader.id);
         this.state.alliances.put(id, alliance);
         this.save();
         return true;
      } else {
         return false;
      }
   }

   public boolean inviteToAlliance(NationStore.Alliance alliance, NationStore.Nation inviter, NationStore.Nation invited) {
      if (alliance != null && invited != null && alliance.members.contains(inviter.id) && !this.allianceOf(invited).isPresent()) {
         alliance.invites.add(invited.id);
         this.save();
         return true;
      } else {
         return false;
      }
   }

   public boolean acceptAllianceInvite(NationStore.Alliance alliance, NationStore.Nation nation) {
      if (alliance != null && nation != null && !this.allianceOf(nation).isPresent() && alliance.invites.remove(nation.id)) {
         alliance.members.add(nation.id);
         this.save();
         return true;
      } else {
         return false;
      }
   }

   public boolean kickFromAlliance(NationStore.Alliance alliance, NationStore.Nation actor, NationStore.Nation kicked) {
      if (alliance != null && actor != null && kicked != null && alliance.leader.equals(actor.id) && !alliance.leader.equals(kicked.id)) {
         boolean removed = alliance.members.remove(kicked.id);
         alliance.invites.remove(kicked.id);
         if (removed) {
            this.save();
         }

         return removed;
      } else {
         return false;
      }
   }

   public NationStore.SpyMission createSpyMission(ServerPlayer spy, NationStore.Nation spyNation, NationStore.Nation target, long completeTick) {
      NationStore.SpyMission mission = new NationStore.SpyMission();
      mission.id = this.state.nextSpyMissionId++;
      mission.spyPlayer = spy.getUUID().toString();
      mission.spyName = spy.getGameProfile().getName();
      mission.spyNation = spyNation.id;
      mission.target = target.id;
      mission.completeTick = completeTick;
      this.state.spyMissions.add(mission);
      this.save();
      return mission;
   }

   public Optional<NationStore.SpyMission> activeSpyMission(UUID spyPlayer) {
      String id = spyPlayer.toString();
      return this.state.spyMissions.stream().filter(mission -> id.equals(mission.spyPlayer)).findFirst();
   }

   public List<NationStore.SpyMission> dueSpyMissions(long tick) {
      return this.state.spyMissions.stream().filter(mission -> mission.completeTick <= tick).sorted(Comparator.comparingInt(mission -> mission.id)).toList();
   }

   public void removeSpyMission(NationStore.SpyMission mission) {
      this.state.spyMissions.removeIf(existing -> existing.id == mission.id);
      this.save();
   }

   public void notifyNation(MinecraftServer server, NationStore.Nation nation, Component message) {
      for (ServerPlayer player : server.getPlayerList().getPlayers()) {
         if (this.isMember(player.getUUID(), nation)) {
            player.sendSystemMessage(message);
         }
      }
   }

   public static String nationKey(String name) {
      return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]", "");
   }

   public static String warKey(NationStore.Nation first, NationStore.Nation second) {
      String a = first.id.compareTo(second.id) <= 0 ? first.id : second.id;
      String b = first.id.compareTo(second.id) <= 0 ? second.id : first.id;
      return a + "->" + b;
   }

   public static double roundMoney(double value) {
      return (double)Math.round(value * 100.0) / 100.0;
   }

   private void ensurePlayer(UUID playerId) {
      this.state.players.putIfAbsent(playerId.toString(), 50.0);
   }

   private void normalize() {
      if (this.state.nations == null) {
         this.state.nations = new LinkedHashMap<>();
      }

      if (this.state.playerNation == null) {
         this.state.playerNation = new LinkedHashMap<>();
      }

      if (this.state.players == null) {
         this.state.players = new LinkedHashMap<>();
      }

      if (this.state.claims == null) {
         this.state.claims = new LinkedHashMap<>();
      }

      if (this.state.wars == null) {
         this.state.wars = new LinkedHashMap<>();
      }

      if (this.state.marketListings == null) {
         this.state.marketListings = new ArrayList<>();
      }

      if (this.state.alliances == null) {
         this.state.alliances = new LinkedHashMap<>();
      }

      if (this.state.spyMissions == null) {
         this.state.spyMissions = new ArrayList<>();
      }

      if (this.state.nextSpyMissionId <= 0) {
         this.state.nextSpyMissionId = this.state.spyMissions.stream().map(mission -> mission.id).max(Integer::compareTo).orElse(0) + 1;
      }

      for (NationStore.War war : this.state.wars.values()) {
         ensureWarSides(war);
         if (war.attackerCapturedClaims == null) {
            war.attackerCapturedClaims = new LinkedHashSet<>();
         }

         if (war.peaceOffers == null) {
            war.peaceOffers = new LinkedHashSet<>();
         }

         if (war.peaceDeal != null) {
            normalizePeaceDeal(war.peaceDeal);
         }
      }

      if (this.state.nextListingId <= 0) {
         this.state.nextListingId = this.state.marketListings.stream().map(listing -> listing.id).max(Integer::compareTo).orElse(0) + 1;
      }

      for (NationStore.Nation nation : this.state.nations.values()) {
         if (nation.members == null) {
            nation.members = new LinkedHashSet<>();
         }

         if (nation.cityClaims == null) {
            nation.cityClaims = new LinkedHashSet<>();
         }

         if (nation.ownerName == null || nation.ownerName.isBlank()) {
            nation.ownerName = nation.owner != null && nation.owner.length() >= 8 ? nation.owner.substring(0, 8) : "unknown";
         }

         if (nation.owner != null) {
            nation.members.add(nation.owner);
         }
      }

      for (NationStore.Alliance alliance : this.state.alliances.values()) {
         if (alliance.members == null) {
            alliance.members = new LinkedHashSet<>();
         }

         if (alliance.invites == null) {
            alliance.invites = new LinkedHashSet<>();
         }

         if (alliance.leader != null && !alliance.leader.isBlank()) {
            alliance.members.add(alliance.leader);
         }
      }
   }

   private static void ensureWarSides(NationStore.War war) {
      if (war.attackerSide == null) {
         war.attackerSide = new LinkedHashSet<>();
      }

      if (war.defenderSide == null) {
         war.defenderSide = new LinkedHashSet<>();
      }

      if (war.joinRequests == null) {
         war.joinRequests = new LinkedHashMap<>();
      }

      if (war.attacker != null && !war.attacker.isBlank()) {
         war.attackerSide.add(war.attacker);
      }

      if (war.defender != null && !war.defender.isBlank()) {
         war.defenderSide.add(war.defender);
      }
   }

   private boolean claimsStillOwnedBy(Set<String> claimIds, NationStore.Nation nation) {
      for (String claimId : claimIds) {
         if (!nation.id.equals(this.state.claims.get(claimId))) {
            return false;
         }
      }

      return true;
   }

   private boolean containsCapital(Set<String> claimIds, NationStore.Nation nation) {
      return nation.capitalClaim != null && claimIds.contains(nation.capitalClaim);
   }

   private static void normalizePeaceDeal(NationStore.PeaceDeal deal) {
      if (deal.demandedClaims == null) {
         deal.demandedClaims = new LinkedHashSet<>();
      }

      if (deal.offeredClaims == null) {
         deal.offeredClaims = new LinkedHashSet<>();
      }

      deal.demandedMoney = roundMoney(Math.max(0.0, deal.demandedMoney));
      deal.offeredMoney = roundMoney(Math.max(0.0, deal.offeredMoney));
   }

   public static final class Alliance {
      public String id = "";
      public String name = "";
      public String leader = "";
      public Set<String> members = new LinkedHashSet<>();
      public Set<String> invites = new LinkedHashSet<>();
   }

   public static final class MarketListing {
      public int id = 0;
      public String seller = "";
      public String sellerName = "";
      public double price = 0.0;
      public String itemTag = "";
   }

   public static final class Nation {
      public String id = "";
      public String name = "";
      public String owner = "";
      public String ownerName = "";
      public String doctrine = Doctrine.AMERICAN.id;
      public double balance = 0.0;
      public int freeClaimsRemaining = 0;
      public String capitalClaim = "";
      public Set<String> members = new LinkedHashSet<>();
      public Set<String> cityClaims = new LinkedHashSet<>();
      public long lastSpecialWarLeaveTick = -1L;
      public boolean lostCoreTerritory = false;

      public Doctrine doctrine() {
         return Doctrine.byId(this.doctrine).orElse(Doctrine.AMERICAN);
      }
   }

   public static final class PeaceDeal {
      public String proposer = "";
      public String receiver = "";
      public Set<String> demandedClaims = new LinkedHashSet<>();
      public Set<String> offeredClaims = new LinkedHashSet<>();
      public double demandedMoney = 0.0;
      public double offeredMoney = 0.0;
      public boolean returnCapturedClaims = false;
   }

   public static final class SpyMission {
      public int id = 0;
      public String spyPlayer = "";
      public String spyName = "";
      public String spyNation = "";
      public String target = "";
      public long completeTick = 0L;
   }

   public static final class State {
      public Map<String, NationStore.Nation> nations = new LinkedHashMap<>();
      public Map<String, String> playerNation = new LinkedHashMap<>();
      public Map<String, Double> players = new LinkedHashMap<>();
      public Map<String, String> claims = new LinkedHashMap<>();
      public Map<String, NationStore.War> wars = new LinkedHashMap<>();
      public Map<String, NationStore.Alliance> alliances = new LinkedHashMap<>();
      public List<NationStore.SpyMission> spyMissions = new ArrayList<>();
      public List<NationStore.MarketListing> marketListings = new ArrayList<>();
      public int nextListingId = 1;
      public int nextSpyMissionId = 1;
   }

   public static final class War {
      public String id = "";
      public String attacker = "";
      public String defender = "";
      public boolean active = false;
      public long justificationCompleteTick = 0L;
      public int defenderStartingClaims = 0;
      public Set<String> attackerCapturedClaims = new LinkedHashSet<>();
      public Set<String> peaceOffers = new LinkedHashSet<>();
      public NationStore.PeaceDeal peaceDeal = null;
      public Set<String> attackerSide = new LinkedHashSet<>();
      public Set<String> defenderSide = new LinkedHashSet<>();
      public Map<String, String> joinRequests = new LinkedHashMap<>();
      public boolean pendingDefenderResponse = false;
   }
}
