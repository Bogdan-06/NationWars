/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.gson.Gson
 *  com.google.gson.GsonBuilder
 *  com.mojang.brigadier.exceptions.CommandSyntaxException
 *  net.minecraft.core.HolderLookup$Provider
 *  net.minecraft.nbt.CompoundTag
 *  net.minecraft.nbt.TagParser
 *  net.minecraft.network.chat.Component
 *  net.minecraft.server.MinecraftServer
 *  net.minecraft.server.level.ServerPlayer
 *  net.minecraft.world.item.ItemStack
 *  net.minecraft.world.level.storage.LevelResource
 */
package dev.moth.nationwars;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.moth.nationwars.ClaimKey;
import dev.moth.nationwars.Doctrine;
import dev.moth.nationwars.NationWars;
import dev.moth.nationwars.NationWarsConfig;
import dev.moth.nationwars.OpacClaimsBridge;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
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
import net.minecraft.core.HolderLookup;
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
    private final State state;

    private NationStore(MinecraftServer server, Path file, State state) {
        this.server = server;
        this.file = file;
        this.state = state;
        this.normalize();
    }

    public static NationStore get() {
        if (current == null) {
            throw new IllegalStateException("Nation store has not loaded yet");
        }
        return current;
    }

    public static void load(MinecraftServer server) {
        Path file = server.getWorldPath(LevelResource.ROOT).resolve("data").resolve("nationwars.json");
        State state = new State();
        if (Files.exists(file, new LinkOption[0])) {
            try (BufferedReader reader = Files.newBufferedReader(file);){
                State loaded = (State)GSON.fromJson((Reader)reader, State.class);
                if (loaded != null) {
                    state = loaded;
                }
            }
            catch (IOException | RuntimeException exception) {
                NationWars.LOGGER.error("Failed to load Nation Wars data from {}", (Object)file, (Object)exception);
            }
        }
        current = new NationStore(server, file, state);
        current.save();
        OpacClaimsBridge.syncAll(server, current);
    }

    public void save() {
        try {
            Files.createDirectories(this.file.getParent(), new FileAttribute[0]);
            try (BufferedWriter writer = Files.newBufferedWriter(this.file, new OpenOption[0]);){
                GSON.toJson((Object)this.state, (Appendable)writer);
            }
        }
        catch (IOException exception) {
            NationWars.LOGGER.error("Failed to save Nation Wars data to {}", (Object)this.file, (Object)exception);
        }
    }

    public Collection<Nation> nations() {
        return this.state.nations.values();
    }

    public MinecraftServer server() {
        return this.server;
    }

    public Optional<Nation> nationById(String id) {
        return Optional.ofNullable(this.state.nations.get(id));
    }

    public Set<Map.Entry<String, String>> claimOwnerEntries() {
        return Set.copyOf(this.state.claims.entrySet());
    }

    public Collection<War> wars() {
        return this.state.wars.values();
    }

    public boolean isDoctrineTaken(Doctrine doctrine) {
        return this.doctrineUseCount(doctrine) >= NationWarsConfig.get().doctrineLimit(doctrine);
    }

    public int doctrineUseCount(Doctrine doctrine) {
        return (int)this.state.nations.values().stream().filter(nation -> doctrine.id.equals(nation.doctrine)).count();
    }

    public List<Doctrine> availableDoctrines() {
        return Arrays.stream(Doctrine.values()).filter(doctrine -> !this.isDoctrineTaken((Doctrine)((Object)doctrine))).toList();
    }

    public List<MarketListing> marketListings() {
        return this.state.marketListings.stream().sorted(Comparator.comparingInt(listing -> listing.id)).toList();
    }

    public List<LandPurchaseOffer> landPurchaseOffersFor(Nation nation) {
        return this.state.landPurchaseOffers.stream().filter(offer -> nation.id.equals(offer.buyer) || nation.id.equals(offer.seller)).sorted(Comparator.comparingInt(offer -> offer.id)).toList();
    }

    public Optional<LandPurchaseOffer> landPurchaseOffer(int id) {
        return this.state.landPurchaseOffers.stream().filter(offer -> offer.id == id).findFirst();
    }

    public Optional<LandPurchaseOffer> landPurchaseOfferForClaim(String buyerId, String sellerId, String claimId) {
        return this.state.landPurchaseOffers.stream().filter(offer -> buyerId.equals(offer.buyer) && sellerId.equals(offer.seller) && claimId.equals(offer.claimId)).findFirst();
    }

    public LandPurchaseOffer createLandPurchaseOffer(Nation buyer, Nation seller, String claimId, double price, ServerPlayer requester, long tick) {
        LandPurchaseOffer offer = new LandPurchaseOffer();
        offer.id = this.state.nextLandPurchaseOfferId++;
        offer.buyer = buyer.id;
        offer.seller = seller.id;
        offer.claimId = claimId;
        offer.price = NationStore.roundMoney(price);
        offer.requester = requester.getUUID().toString();
        offer.requesterName = requester.getGameProfile().getName();
        offer.createdTick = tick;
        this.state.landPurchaseOffers.add(offer);
        this.save();
        return offer;
    }

    public void removeLandPurchaseOffer(LandPurchaseOffer offer) {
        this.state.landPurchaseOffers.removeIf(existing -> existing.id == offer.id);
        this.save();
    }

    public Optional<MarketListing> marketListing(int id) {
        return this.state.marketListings.stream().filter(listing -> listing.id == id).findFirst();
    }

    public MarketListing createMarketListing(ServerPlayer seller, ItemStack stack, double price) {
        MarketListing listing = new MarketListing();
        listing.id = this.state.nextListingId++;
        listing.seller = seller.getUUID().toString();
        listing.sellerName = seller.getGameProfile().getName();
        listing.price = NationStore.roundMoney(price);
        listing.itemTag = stack.save((HolderLookup.Provider)seller.registryAccess()).getAsString();
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

    public ItemStack listingStack(MarketListing listing, HolderLookup.Provider registries) {
        try {
            CompoundTag tag = TagParser.parseTag((String)listing.itemTag);
            return ItemStack.parseOptional((HolderLookup.Provider)registries, (CompoundTag)tag);
        }
        catch (CommandSyntaxException | RuntimeException exception) {
            NationWars.LOGGER.warn("Failed to parse market listing {}", (Object)listing.id, (Object)exception);
            return ItemStack.EMPTY;
        }
    }

    public Nation createNation(ServerPlayer owner, String name, Doctrine doctrine, ClaimKey capital) {
        String key = NationStore.nationKey(name);
        Nation nation = new Nation();
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

    public Optional<Nation> nationByName(String name) {
        return Optional.ofNullable(this.state.nations.get(NationStore.nationKey(name)));
    }

    public Optional<Nation> nationOf(UUID playerId) {
        return Optional.ofNullable(this.state.playerNation.get(playerId.toString())).map(this.state.nations::get);
    }

    public List<Nation> nationsSorted() {
        return this.state.nations.values().stream().sorted(Comparator.comparing(nation -> nation.name.toLowerCase(Locale.ROOT))).toList();
    }

    public Optional<Nation> nationOwning(ClaimKey claim) {
        return Optional.ofNullable(this.state.claims.get(claim.id())).map(this.state.nations::get);
    }

    public boolean hasNation(UUID playerId) {
        return this.state.playerNation.containsKey(playerId.toString());
    }

    public boolean isMember(UUID playerId, Nation nation) {
        return nation != null && nation.members.contains(playerId.toString());
    }

    public boolean isOwner(UUID playerId, Nation nation) {
        return nation != null && Objects.equals(nation.owner, playerId.toString());
    }

    public void addMember(UUID playerId, Nation nation) {
        String id = playerId.toString();
        nation.members.add(id);
        this.state.playerNation.put(id, nation.id);
        this.ensurePlayer(playerId);
        this.save();
    }

    public boolean addCityClaim(Nation nation, String claimId, double cost) {
        if (!nation.id.equals(this.state.claims.get(claimId)) || nation.cityClaims.contains(claimId) || nation.balance + 1.0E-4 < cost) {
            return false;
        }
        nation.balance = NationStore.roundMoney(nation.balance - cost);
        nation.cityClaims.add(claimId);
        this.save();
        return true;
    }

    public boolean sameAlliance(Nation first, Nation second) {
        if (first == null || second == null) {
            return false;
        }
        Optional<Alliance> firstAlliance = this.allianceOf(first);
        return firstAlliance.isPresent() && firstAlliance.get().members.contains(second.id);
    }

    public double playerBalance(UUID playerId) {
        this.ensurePlayer(playerId);
        return this.state.players.get(playerId.toString());
    }

    public void addPlayerMoney(UUID playerId, double amount) {
        this.ensurePlayer(playerId);
        this.state.players.compute(playerId.toString(), (id, balance) -> NationStore.roundMoney((balance == null ? 0.0 : balance) + amount));
        this.save();
    }

    public boolean withdrawPlayerMoney(UUID playerId, double amount) {
        this.ensurePlayer(playerId);
        String id = playerId.toString();
        double balance = this.state.players.get(id);
        if (balance + 1.0E-4 < amount) {
            return false;
        }
        this.state.players.put(id, NationStore.roundMoney(balance - amount));
        this.save();
        return true;
    }

    public double confiscatePlayerMoney(UUID playerId) {
        this.ensurePlayer(playerId);
        String id = playerId.toString();
        double balance = this.state.players.get(id);
        this.state.players.put(id, 0.0);
        this.save();
        return NationStore.roundMoney(balance);
    }

    public boolean depositToNation(UUID playerId, Nation nation, double amount) {
        if (!this.withdrawPlayerMoney(playerId, amount)) {
            return false;
        }
        nation.balance = NationStore.roundMoney(nation.balance + amount);
        this.save();
        return true;
    }

    public int claimCount(Nation nation) {
        int count = 0;
        for (String owner : this.state.claims.values()) {
            if (!nation.id.equals(owner)) continue;
            ++count;
        }
        return count;
    }

    public List<String> claimsOf(Nation nation) {
        return this.state.claims.entrySet().stream().filter(entry -> nation.id.equals(entry.getValue())).map(Map.Entry::getKey).sorted().toList();
    }

    public boolean claim(Nation nation, ClaimKey claim) {
        if (this.state.claims.containsKey(claim.id())) {
            return false;
        }
        this.state.claims.put(claim.id(), nation.id);
        OpacClaimsBridge.mirrorClaim(this.server, nation, claim);
        this.save();
        return true;
    }

    public boolean unclaim(Nation nation, ClaimKey claim) {
        if (!nation.id.equals(this.state.claims.get(claim.id()))) {
            return false;
        }
        if (claim.id().equals(nation.capitalClaim)) {
            return false;
        }
        this.state.claims.remove(claim.id());
        this.state.landPurchaseOffers.removeIf(offer -> claim.id().equals(offer.claimId));
        nation.cityClaims.remove(claim.id());
        OpacClaimsBridge.unmirrorClaim(this.server, nation, claim);
        this.save();
        return true;
    }

    public void transferClaim(String claimId, Nation newOwner) {
        Nation oldOwner = Optional.ofNullable(this.state.claims.get(claimId)).map(this.state.nations::get).orElse(null);
        this.state.claims.put(claimId, newOwner.id);
        this.state.landPurchaseOffers.removeIf(offer -> claimId.equals(offer.claimId));
        if (oldOwner != null) {
            oldOwner.cityClaims.remove(claimId);
        }
        if (oldOwner != null) {
            OpacClaimsBridge.replaceClaim(this.server, oldOwner, newOwner, ClaimKey.parse(claimId));
        } else {
            OpacClaimsBridge.mirrorClaim(this.server, newOwner, ClaimKey.parse(claimId));
        }
        if (oldOwner != null && claimId.equals(oldOwner.capitalClaim)) {
            if (oldOwner.doctrine() == Doctrine.ROMANIAN) {
                oldOwner.lostCoreTerritory = true;
                this.notifyNation(this.server, oldOwner, (Component)Component.literal((String)"[NationWars] Iron Guard penalty is active: losing your core territory raised maintenance costs."));
            }
            oldOwner.capitalClaim = this.claimsOf(oldOwner).stream().filter(id -> !id.equals(claimId)).findFirst().orElse("");
        }
        if (newOwner.capitalClaim == null || newOwner.capitalClaim.isBlank()) {
            newOwner.capitalClaim = claimId;
        }
        this.save();
    }

    public void recordCapturedClaim(War war, Nation capturingNation, String claimId) {
        if (war == null || capturingNation == null || claimId == null || claimId.isBlank()) {
            return;
        }
        NationStore.ensureWarCaptureMap(war);
        for (Set<String> claims : war.capturedClaimsByNation.values()) {
            claims.remove(claimId);
        }
        war.capturedClaimsByNation.computeIfAbsent(capturingNation.id, ignored -> new LinkedHashSet()).add(claimId);
        war.capturedClaimsByNation.entrySet().removeIf(entry -> ((Set)entry.getValue()).isEmpty());
        this.save();
    }

    public void removeCapturedClaim(War war, String claimId) {
        if (war == null || claimId == null || claimId.isBlank()) {
            return;
        }
        NationStore.ensureWarCaptureMap(war);
        for (Set<String> claims : war.capturedClaimsByNation.values()) {
            claims.remove(claimId);
        }
        war.capturedClaimsByNation.entrySet().removeIf(entry -> ((Set)entry.getValue()).isEmpty());
        war.attackerCapturedClaims.remove(claimId);
        this.save();
    }

    public Set<String> capturedClaimsBy(War war, Nation nation) {
        if (war == null || nation == null) {
            return Set.of();
        }
        NationStore.ensureWarCaptureMap(war);
        Set<String> claims = war.capturedClaimsByNation.get(nation.id);
        if (claims == null || claims.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<String> held = new LinkedHashSet<String>();
        for (String claimId : claims) {
            if (!nation.id.equals(this.state.claims.get(claimId))) continue;
            held.add(claimId);
        }
        return Set.copyOf(held);
    }

    public int capturedClaimsHeldBy(Nation nation) {
        if (nation == null) {
            return 0;
        }
        int count = 0;
        for (War war : this.state.wars.values()) {
            if (!war.active) continue;
            NationStore.ensureWarCaptureMap(war);
            Set<String> claims = war.capturedClaimsByNation.get(nation.id);
            if (claims == null) continue;
            for (String claimId : claims) {
                if (!nation.id.equals(this.state.claims.get(claimId))) continue;
                ++count;
            }
        }
        return count;
    }

    public boolean isCapturedClaimHeldBy(Nation nation, String claimId) {
        if (nation == null || claimId == null || claimId.isBlank()) {
            return false;
        }
        for (War war : this.state.wars.values()) {
            if (!war.active) continue;
            NationStore.ensureWarCaptureMap(war);
            Set<String> claims = war.capturedClaimsByNation.get(nation.id);
            if (claims == null || !claims.contains(claimId) || !nation.id.equals(this.state.claims.get(claimId))) continue;
            return true;
        }
        return false;
    }

    public boolean isCapturedClaimTracked(War war, String claimId) {
        if (war == null || claimId == null || claimId.isBlank()) {
            return false;
        }
        NationStore.ensureWarCaptureMap(war);
        return war.capturedClaimsByNation.values().stream().anyMatch(claims -> claims.contains(claimId));
    }

    public Map<Nation, Integer> capturedClaimWeightsAgainst(War war, Nation defeated, Nation fallbackRecipient) {
        LinkedHashMap<Nation, Integer> weights = new LinkedHashMap<Nation, Integer>();
        if (war == null || defeated == null) {
            if (fallbackRecipient != null) {
                weights.put(fallbackRecipient, 1);
            }
            return weights;
        }
        NationStore.ensureWarCaptureMap(war);
        int defeatedSide = this.sideOf(war, defeated);
        for (Map.Entry<String, Set<String>> entry : war.capturedClaimsByNation.entrySet()) {
            Nation captor = this.nationById(entry.getKey()).orElse(null);
            if (captor == null || this.sideOf(war, captor) == 0 || this.sideOf(war, captor) == defeatedSide) continue;
            int held = 0;
            for (String claimId : entry.getValue()) {
                if (!captor.id.equals(this.state.claims.get(claimId))) continue;
                ++held;
            }
            if (held <= 0) continue;
            weights.put(captor, held);
        }
        if (weights.isEmpty() && fallbackRecipient != null) {
            weights.put(fallbackRecipient, 1);
        }
        return weights;
    }

    public void deleteNation(Nation nation) {
        if (nation == null) {
            return;
        }
        for (String claimId : new ArrayList<String>(this.claimsOf(nation))) {
            this.state.claims.remove(claimId);
            this.state.landPurchaseOffers.removeIf(offer -> claimId.equals(offer.claimId));
            OpacClaimsBridge.unmirrorClaim(this.server, nation, ClaimKey.parse(claimId));
        }
        this.state.nations.remove(nation.id);
        this.state.playerNation.entrySet().removeIf(entry -> nation.id.equals(entry.getValue()));
        this.state.alliances.values().removeIf(alliance -> Objects.equals(alliance.leader, nation.id));
        for (Alliance alliance2 : this.state.alliances.values()) {
            alliance2.members.remove(nation.id);
            alliance2.invites.remove(nation.id);
        }
        this.state.wars.values().removeIf(war -> {
            NationStore.ensureWarSides(war);
            return war.attackerSide.contains(nation.id) || war.defenderSide.contains(nation.id);
        });
        this.state.wars.values().forEach(war -> {
            war.joinRequests.remove(nation.id);
            NationStore.ensureWarCaptureMap(war);
            war.capturedClaimsByNation.remove(nation.id);
            war.capturedClaimsByNation.values().forEach(claims -> claims.removeIf(claimId -> nation.id.equals(this.state.claims.get(claimId))));
        });
        this.save();
    }

    public boolean removeBorderClaim(Nation nation) {
        List<String> candidates = this.borderClaimsOf(nation).stream().filter(claim -> !claim.equals(nation.capitalClaim)).toList();
        if (candidates.isEmpty()) {
            return false;
        }
        String removed = candidates.get(RANDOM.nextInt(candidates.size()));
        this.state.claims.remove(removed);
        this.state.landPurchaseOffers.removeIf(offer -> removed.equals(offer.claimId));
        nation.cityClaims.remove(removed);
        OpacClaimsBridge.unmirrorClaim(this.server, nation, ClaimKey.parse(removed));
        this.save();
        return true;
    }

    public List<String> borderClaimsOf(Nation nation) {
        HashSet<String> owned = new HashSet<String>(this.claimsOf(nation));
        ArrayList<String> borders = new ArrayList<String>();
        for (String claimId : owned) {
            ClaimKey claim = ClaimKey.parse(claimId);
            boolean border = List.of(new ClaimKey(claim.dimension(), claim.x() + 1, claim.z()), new ClaimKey(claim.dimension(), claim.x() - 1, claim.z()), new ClaimKey(claim.dimension(), claim.x(), claim.z() + 1), new ClaimKey(claim.dimension(), claim.x(), claim.z() - 1)).stream().anyMatch(neighbor -> !owned.contains(neighbor.id()));
            if (!border) continue;
            borders.add(claimId);
        }
        borders.sort(Comparator.naturalOrder());
        return borders;
    }

    public Optional<War> warBetween(Nation first, Nation second) {
        return Optional.ofNullable(this.state.wars.get(NationStore.warKey(first, second)));
    }

    public War getOrCreateWar(Nation attacker, Nation defender) {
        String key = NationStore.warKey(attacker, defender);
        War war = this.state.wars.computeIfAbsent(key, ignored -> {
            War created = new War();
            created.id = key;
            created.attacker = attacker.id;
            created.defender = defender.id;
            return created;
        });
        NationStore.ensureWarSides(war);
        return war;
    }

    public void endWar(War war) {
        this.state.wars.remove(war.id);
        this.save();
    }

    public boolean applyPeaceDeal(War war, PeaceDeal deal) {
        if (war == null || deal == null || !war.active) {
            return false;
        }
        Nation proposer = this.nationById(deal.proposer).orElse(null);
        Nation receiver = this.nationById(deal.receiver).orElse(null);
        if (proposer == null || receiver == null || !this.isWarParticipant(war, proposer) || !this.isWarParticipant(war, receiver)) {
            return false;
        }
        NationStore.normalizePeaceDeal(deal);
        double demandedMoney = NationStore.roundMoney(Math.max(0.0, deal.demandedMoney));
        double offeredMoney = NationStore.roundMoney(Math.max(0.0, deal.offeredMoney));
        if (receiver.balance + 1.0E-4 < demandedMoney || proposer.balance + 1.0E-4 < offeredMoney) {
            return false;
        }
        if (!this.claimsStillOwnedBy(deal.demandedClaims, receiver) || !this.claimsStillOwnedBy(deal.offeredClaims, proposer)) {
            return false;
        }
        if (this.containsCapital(deal.demandedClaims, receiver) || this.containsCapital(deal.offeredClaims, proposer)) {
            return false;
        }
        if (demandedMoney > 0.0) {
            receiver.balance = NationStore.roundMoney(receiver.balance - demandedMoney);
            proposer.balance = NationStore.roundMoney(proposer.balance + demandedMoney);
        }
        if (offeredMoney > 0.0) {
            proposer.balance = NationStore.roundMoney(proposer.balance - offeredMoney);
            receiver.balance = NationStore.roundMoney(receiver.balance + offeredMoney);
        }
        for (String claimId : deal.demandedClaims) {
            if (!receiver.id.equals(this.state.claims.get(claimId))) continue;
            this.transferClaim(claimId, proposer);
        }
        for (String claimId : deal.offeredClaims) {
            if (!proposer.id.equals(this.state.claims.get(claimId))) continue;
            this.transferClaim(claimId, receiver);
        }
        if (deal.returnCapturedClaims) {
            this.returnCapturedClaimsForDeal(war, proposer, receiver);
        }
        if (this.isPrimaryPeacePair(war, proposer, receiver)) {
            this.endWar(war);
        } else {
            war.peaceDeal = null;
            if (!this.isPrimaryWarParticipant(war, proposer)) {
                this.removeWarParticipant(war, proposer);
            }
            if (!this.isPrimaryWarParticipant(war, receiver)) {
                this.removeWarParticipant(war, receiver);
            }
            this.save();
        }
        return true;
    }

    public void setPeaceDeal(War war, PeaceDeal deal) {
        NationStore.normalizePeaceDeal(deal);
        war.peaceDeal = deal;
        war.peaceOffers.clear();
        this.save();
    }

    public void clearPeaceDeal(War war) {
        war.peaceDeal = null;
        this.save();
    }

    public long peaceCooldownUntil(Nation proposer, Nation receiver) {
        if (proposer == null || receiver == null) {
            return 0L;
        }
        return this.state.peaceCooldowns.getOrDefault(NationStore.peaceCooldownKey(proposer, receiver), 0L);
    }

    public void setPeaceCooldown(Nation proposer, Nation receiver, long tick) {
        if (proposer == null || receiver == null) {
            return;
        }
        this.state.peaceCooldowns.put(NationStore.peaceCooldownKey(proposer, receiver), tick);
        this.save();
    }

    public boolean hasRejectedWarDeclaration(Nation attacker, Nation defender) {
        if (attacker == null || defender == null) {
            return false;
        }
        return this.state.warDeclarationRejections.contains(NationStore.warDeclarationRejectionKey(attacker, defender));
    }

    public void recordWarDeclarationRejection(Nation attacker, Nation defender) {
        if (attacker == null || defender == null) {
            return;
        }
        this.state.warDeclarationRejections.add(NationStore.warDeclarationRejectionKey(attacker, defender));
        this.save();
    }

    public Optional<Nation> attacker(War war) {
        return Optional.ofNullable(this.state.nations.get(war.attacker));
    }

    public Optional<Nation> defender(War war) {
        return Optional.ofNullable(this.state.nations.get(war.defender));
    }

    public Optional<War> activeWarForCapture(Nation attacker, Nation defender) {
        return this.state.wars.values().stream().filter(war -> war.active).filter(war -> this.areOpposingWarSides((War)war, attacker, defender)).findFirst();
    }

    public boolean isWarParticipant(War war, Nation nation) {
        return war != null && nation != null && this.sideOf(war, nation) != 0;
    }

    public boolean isPrimaryWarParticipant(War war, Nation nation) {
        return war != null && nation != null && (nation.id.equals(war.attacker) || nation.id.equals(war.defender));
    }

    public boolean isPrimaryPeacePair(War war, Nation first, Nation second) {
        return this.isPrimaryWarParticipant(war, first) && this.isPrimaryWarParticipant(war, second) && this.areOpposingWarSides(war, first, second);
    }

    public List<War> activeWarsOf(Nation nation) {
        return this.state.wars.values().stream().filter(war -> war.active).filter(war -> this.isWarParticipant((War)war, nation)).sorted(Comparator.comparing(war -> war.id)).toList();
    }

    public Optional<War> firstActiveWarOf(Nation nation) {
        return this.activeWarsOf(nation).stream().findFirst();
    }

    public int sideOf(War war, Nation nation) {
        if (war == null || nation == null) {
            return 0;
        }
        NationStore.ensureWarSides(war);
        if (war.attackerSide.contains(nation.id)) {
            return 1;
        }
        if (war.defenderSide.contains(nation.id)) {
            return -1;
        }
        return 0;
    }

    public boolean areOpposingWarSides(War war, Nation first, Nation second) {
        int firstSide = this.sideOf(war, first);
        int secondSide = this.sideOf(war, second);
        return firstSide != 0 && secondSide != 0 && firstSide != secondSide;
    }

    public boolean removeWarParticipant(War war, Nation nation) {
        if (war == null || nation == null || !war.active || this.sideOf(war, nation) == 0 || this.isPrimaryWarParticipant(war, nation)) {
            return false;
        }
        war.attackerSide.remove(nation.id);
        war.defenderSide.remove(nation.id);
        war.joinRequests.entrySet().removeIf(entry -> nation.id.equals(entry.getKey()) || nation.id.equals(entry.getValue()));
        this.clearCapturedTrackingFor(war, nation);
        this.save();
        return true;
    }

    public int returnableCapturedClaimsForDeal(War war, Nation first, Nation second) {
        if (war == null || first == null || second == null || !war.active || !this.areOpposingWarSides(war, first, second)) {
            return 0;
        }
        NationStore.ensureWarCaptureMap(war);
        if (this.isPrimaryPeacePair(war, first, second)) {
            int count = 0;
            for (Map.Entry<String, Set<String>> entry : war.capturedClaimsByNation.entrySet()) {
                Nation captor = this.nationById(entry.getKey()).orElse(null);
                if (captor == null || this.sideOf(war, captor) == 0) continue;
                for (String claimId : entry.getValue()) {
                    if (!captor.id.equals(this.state.claims.get(claimId))) continue;
                    ++count;
                }
            }
            return count;
        }
        return this.capturedClaimsBy(war, first).size() + this.capturedClaimsBy(war, second).size();
    }

    private void returnCapturedClaimsForDeal(War war, Nation proposer, Nation receiver) {
        if (war == null || proposer == null || receiver == null || !this.areOpposingWarSides(war, proposer, receiver)) {
            return;
        }
        if (this.isPrimaryPeacePair(war, proposer, receiver)) {
            Nation attacker = this.attacker(war).orElse(null);
            Nation defender = this.defender(war).orElse(null);
            if (attacker == null || defender == null) {
                return;
            }
            this.returnCapturedClaimsFromSide(war, 1, defender);
            this.returnCapturedClaimsFromSide(war, -1, attacker);
            return;
        }
        this.returnCapturedClaimsOwnedBy(war, proposer, receiver);
        this.returnCapturedClaimsOwnedBy(war, receiver, proposer);
    }

    private void returnCapturedClaimsFromSide(War war, int captorSide, Nation recipient) {
        if (recipient == null) {
            return;
        }
        NationStore.ensureWarCaptureMap(war);
        for (String captorId : new ArrayList<String>(war.capturedClaimsByNation.keySet())) {
            Nation captor = this.nationById(captorId).orElse(null);
            if (captor == null || this.sideOf(war, captor) != captorSide) continue;
            this.returnCapturedClaimsOwnedBy(war, captor, recipient);
        }
    }

    private void returnCapturedClaimsOwnedBy(War war, Nation captor, Nation recipient) {
        if (war == null || captor == null || recipient == null) {
            return;
        }
        for (String claimId : new ArrayList<String>(this.capturedClaimsBy(war, captor))) {
            if (captor.id.equals(this.state.claims.get(claimId))) {
                this.transferClaim(claimId, recipient);
            }
            this.removeCapturedClaim(war, claimId);
        }
    }

    private void clearCapturedTrackingFor(War war, Nation nation) {
        NationStore.ensureWarCaptureMap(war);
        Set<String> removedClaims = war.capturedClaimsByNation.remove(nation.id);
        if (removedClaims != null) {
            war.attackerCapturedClaims.removeAll(removedClaims);
        }
        war.capturedClaimsByNation.values().forEach(claims -> claims.removeIf(claimId -> nation.id.equals(this.state.claims.get(claimId))));
        war.capturedClaimsByNation.entrySet().removeIf(entry -> ((Set)entry.getValue()).isEmpty());
        LinkedHashSet stillTracked = new LinkedHashSet();
        war.capturedClaimsByNation.values().forEach(stillTracked::addAll);
        war.attackerCapturedClaims.removeIf(claimId -> !stillTracked.contains(claimId));
    }

    public boolean addWarJoinRequest(War war, Nation requester, Nation sponsor) {
        if (war == null || requester == null || sponsor == null || !war.active || this.sideOf(war, requester) != 0 || this.sideOf(war, sponsor) == 0) {
            return false;
        }
        war.joinRequests.put(requester.id, sponsor.id);
        this.save();
        return true;
    }

    public boolean acceptWarJoinRequest(War war, Nation requester, Nation acceptor) {
        if (war == null || requester == null || acceptor == null || !war.active) {
            return false;
        }
        String sponsorId = war.joinRequests.get(requester.id);
        if (sponsorId == null || this.sideOf(war, acceptor) == 0 || this.sideOf(war, acceptor) != this.sideOf(war, this.nationById(sponsorId).orElse(null))) {
            return false;
        }
        if (this.sideOf(war, acceptor) > 0) {
            war.attackerSide.add(requester.id);
        } else {
            war.defenderSide.add(requester.id);
        }
        war.joinRequests.remove(requester.id);
        this.save();
        return true;
    }

    public boolean rejectWarJoinRequest(War war, Nation requester, Nation rejector) {
        if (war == null || requester == null || rejector == null || this.sideOf(war, rejector) == 0) {
            return false;
        }
        String sponsorId = war.joinRequests.get(requester.id);
        if (sponsorId == null || this.sideOf(war, rejector) != this.sideOf(war, this.nationById(sponsorId).orElse(null))) {
            return false;
        }
        war.joinRequests.remove(requester.id);
        this.save();
        return true;
    }

    public boolean addWarDefenseCall(War war, Nation ally, Nation caller) {
        if (war == null || ally == null || caller == null || !war.active || this.sideOf(war, ally) != 0 || this.sideOf(war, caller) == 0) {
            return false;
        }
        war.joinRequests.putIfAbsent(ally.id, caller.id);
        this.save();
        return true;
    }

    public boolean acceptWarDefenseCall(War war, Nation ally, Nation caller) {
        return this.acceptWarJoinRequest(war, ally, caller);
    }

    public boolean rejectWarDefenseCall(War war, Nation ally, Nation caller) {
        if (war == null || ally == null || caller == null || !war.active) {
            return false;
        }
        String callerId = war.joinRequests.get(ally.id);
        if (!caller.id.equals(callerId)) {
            return false;
        }
        war.joinRequests.remove(ally.id);
        this.save();
        return true;
    }

    public boolean leaveWarSafely(War war, Nation nation) {
        if (war == null || nation == null || !war.active || this.sideOf(war, nation) == 0) {
            return false;
        }
        if (nation.id.equals(war.attacker) || nation.id.equals(war.defender)) {
            this.endWar(war);
            return true;
        }
        return this.removeWarParticipant(war, nation);
    }

    public Collection<Alliance> alliances() {
        return this.state.alliances.values();
    }

    public Optional<Alliance> allianceByName(String name) {
        return Optional.ofNullable(this.state.alliances.get(NationStore.nationKey(name)));
    }

    public Optional<Alliance> allianceOf(Nation nation) {
        return this.state.alliances.values().stream().filter(alliance -> alliance.members.contains(nation.id)).findFirst();
    }

    public boolean createAlliance(Nation leader, String name) {
        String id = NationStore.nationKey(name);
        if (id.length() < 3 || this.state.alliances.containsKey(id) || this.allianceOf(leader).isPresent()) {
            return false;
        }
        Alliance alliance = new Alliance();
        alliance.id = id;
        alliance.name = name;
        alliance.leader = leader.id;
        alliance.members.add(leader.id);
        this.state.alliances.put(id, alliance);
        this.save();
        return true;
    }

    public boolean inviteToAlliance(Alliance alliance, Nation inviter, Nation invited) {
        if (alliance == null || invited == null || !alliance.members.contains(inviter.id) || this.allianceOf(invited).isPresent()) {
            return false;
        }
        alliance.invites.add(invited.id);
        this.save();
        return true;
    }

    public boolean acceptAllianceInvite(Alliance alliance, Nation nation) {
        if (alliance == null || nation == null || this.allianceOf(nation).isPresent() || !alliance.invites.remove(nation.id)) {
            return false;
        }
        alliance.members.add(nation.id);
        this.save();
        return true;
    }

    public boolean kickFromAlliance(Alliance alliance, Nation actor, Nation kicked) {
        if (alliance == null || actor == null || kicked == null || !alliance.leader.equals(actor.id) || alliance.leader.equals(kicked.id)) {
            return false;
        }
        boolean removed = alliance.members.remove(kicked.id);
        alliance.invites.remove(kicked.id);
        if (removed) {
            this.save();
        }
        return removed;
    }

    public SpyMission createSpyMission(ServerPlayer spy, Nation spyNation, Nation target, long completeTick) {
        SpyMission mission = new SpyMission();
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

    public long spyCooldownUntil(UUID spyPlayer) {
        return this.state.spyCooldowns.getOrDefault(spyPlayer.toString(), 0L);
    }

    public void setSpyCooldown(UUID spyPlayer, long tick) {
        this.state.spyCooldowns.put(spyPlayer.toString(), tick);
        this.save();
    }

    public Optional<SpyMission> activeSpyMission(UUID spyPlayer) {
        String id = spyPlayer.toString();
        return this.state.spyMissions.stream().filter(mission -> id.equals(mission.spyPlayer)).findFirst();
    }

    public List<SpyMission> dueSpyMissions(long tick) {
        return this.state.spyMissions.stream().filter(mission -> mission.completeTick <= tick).sorted(Comparator.comparingInt(mission -> mission.id)).toList();
    }

    public void removeSpyMission(SpyMission mission) {
        this.state.spyMissions.removeIf(existing -> existing.id == mission.id);
        this.save();
    }

    public void notifyNation(MinecraftServer server, Nation nation, Component message) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!this.isMember(player.getUUID(), nation)) continue;
            player.sendSystemMessage(message);
        }
    }

    public static String nationKey(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]", "");
    }

    public static String warKey(Nation first, Nation second) {
        String a = first.id.compareTo(second.id) <= 0 ? first.id : second.id;
        String b = first.id.compareTo(second.id) <= 0 ? second.id : first.id;
        return a + "->" + b;
    }

    private static String peaceCooldownKey(Nation proposer, Nation receiver) {
        return proposer.id + "->" + receiver.id;
    }

    private static String warDeclarationRejectionKey(Nation attacker, Nation defender) {
        return attacker.id + "->" + defender.id;
    }

    public static double roundMoney(double value) {
        return (double)Math.round(value * 100.0) / 100.0;
    }

    private void ensurePlayer(UUID playerId) {
        this.state.players.putIfAbsent(playerId.toString(), 50.0);
    }

    private void normalize() {
        if (this.state.nations == null) {
            this.state.nations = new LinkedHashMap<String, Nation>();
        }
        if (this.state.playerNation == null) {
            this.state.playerNation = new LinkedHashMap<String, String>();
        }
        if (this.state.players == null) {
            this.state.players = new LinkedHashMap<String, Double>();
        }
        if (this.state.claims == null) {
            this.state.claims = new LinkedHashMap<String, String>();
        }
        if (this.state.wars == null) {
            this.state.wars = new LinkedHashMap<String, War>();
        }
        if (this.state.marketListings == null) {
            this.state.marketListings = new ArrayList<MarketListing>();
        }
        if (this.state.landPurchaseOffers == null) {
            this.state.landPurchaseOffers = new ArrayList<LandPurchaseOffer>();
        }
        if (this.state.alliances == null) {
            this.state.alliances = new LinkedHashMap<String, Alliance>();
        }
        if (this.state.spyMissions == null) {
            this.state.spyMissions = new ArrayList<SpyMission>();
        }
        if (this.state.spyCooldowns == null) {
            this.state.spyCooldowns = new LinkedHashMap<String, Long>();
        }
        if (this.state.peaceCooldowns == null) {
            this.state.peaceCooldowns = new LinkedHashMap<String, Long>();
        }
        if (this.state.warDeclarationRejections == null) {
            this.state.warDeclarationRejections = new LinkedHashSet<String>();
        }
        this.state.claims.entrySet().removeIf(entry -> !this.state.nations.containsKey(entry.getValue()) || !NationStore.isValidClaimId((String)entry.getKey()));
        this.state.playerNation.entrySet().removeIf(entry -> !this.state.nations.containsKey(entry.getValue()));
        if (this.state.nextSpyMissionId <= 0) {
            this.state.nextSpyMissionId = this.state.spyMissions.stream().map(mission -> mission.id).max(Integer::compareTo).orElse(0) + 1;
        }
        for (War war : this.state.wars.values()) {
            NationStore.ensureWarSides(war);
            if (war.attackerCapturedClaims == null) {
                war.attackerCapturedClaims = new LinkedHashSet<String>();
            }
            if (war.peaceOffers == null) {
                war.peaceOffers = new LinkedHashSet<String>();
            }
            NationStore.ensureWarCaptureMap(war);
            if (war.capturedClaimsByNation.isEmpty() && !war.attackerCapturedClaims.isEmpty() && war.attacker != null && !war.attacker.isBlank()) {
                war.capturedClaimsByNation.put(war.attacker, new LinkedHashSet<String>(war.attackerCapturedClaims));
            }
            if (war.peaceDeal == null) continue;
            NationStore.normalizePeaceDeal(war.peaceDeal);
        }
        if (this.state.nextListingId <= 0) {
            this.state.nextListingId = this.state.marketListings.stream().map(listing -> listing.id).max(Integer::compareTo).orElse(0) + 1;
        }
        this.state.landPurchaseOffers.removeIf(offer -> offer == null || !this.state.nations.containsKey(offer.buyer) || !this.state.nations.containsKey(offer.seller) || !NationStore.isValidClaimId(offer.claimId) || !offer.seller.equals(this.state.claims.get(offer.claimId)) || offer.price <= 0.0);
        int nextLandPurchaseOfferId = this.state.landPurchaseOffers.stream().map(offer -> offer.id).max(Integer::compareTo).orElse(0) + 1;
        this.state.nextLandPurchaseOfferId = Math.max(this.state.nextLandPurchaseOfferId, nextLandPurchaseOfferId);
        for (Nation nation : this.state.nations.values()) {
            if (nation.members == null) {
                nation.members = new LinkedHashSet<String>();
            }
            if (nation.cityClaims == null) {
                nation.cityClaims = new LinkedHashSet<String>();
            }
            if (nation.usedSpecialWarLeaveIdeologies == null) {
                nation.usedSpecialWarLeaveIdeologies = new LinkedHashSet<String>();
            }
            if (nation.ownerName == null || nation.ownerName.isBlank()) {
                String string = nation.ownerName = nation.owner == null || nation.owner.length() < 8 ? "unknown" : nation.owner.substring(0, 8);
            }
            if (nation.owner != null) {
                nation.members.add(nation.owner);
            }
            nation.members.removeIf(member -> member == null || member.isBlank());
            for (String member2 : nation.members) {
                this.state.playerNation.putIfAbsent(member2, nation.id);
            }
            nation.cityClaims.removeIf(claimId -> !nation.id.equals(this.state.claims.get(claimId)));
            if (nation.capitalClaim != null && nation.id.equals(this.state.claims.get(nation.capitalClaim))) continue;
            nation.capitalClaim = this.claimsOf(nation).stream().findFirst().orElse("");
        }
        for (Alliance alliance : this.state.alliances.values()) {
            if (alliance.members == null) {
                alliance.members = new LinkedHashSet<String>();
            }
            if (alliance.invites == null) {
                alliance.invites = new LinkedHashSet<String>();
            }
            if (alliance.leader == null || alliance.leader.isBlank()) continue;
            alliance.members.add(alliance.leader);
        }
    }

    private static void ensureWarCaptureMap(War war) {
        if (war.capturedClaimsByNation == null) {
            war.capturedClaimsByNation = new LinkedHashMap<String, Set<String>>();
        }
        war.capturedClaimsByNation.replaceAll((id, claims) -> claims == null ? new LinkedHashSet() : claims);
        war.capturedClaimsByNation.values().forEach(claims -> claims.removeIf(claimId -> !NationStore.isValidClaimId(claimId)));
        war.capturedClaimsByNation.entrySet().removeIf(entry -> ((Set)entry.getValue()).isEmpty());
    }

    private static void ensureWarSides(War war) {
        if (war.attackerSide == null) {
            war.attackerSide = new LinkedHashSet<String>();
        }
        if (war.defenderSide == null) {
            war.defenderSide = new LinkedHashSet<String>();
        }
        if (war.joinRequests == null) {
            war.joinRequests = new LinkedHashMap<String, String>();
        }
        if (war.attacker != null && !war.attacker.isBlank()) {
            war.attackerSide.add(war.attacker);
        }
        if (war.defender != null && !war.defender.isBlank()) {
            war.defenderSide.add(war.defender);
        }
    }

    private boolean claimsStillOwnedBy(Set<String> claimIds, Nation nation) {
        for (String claimId : claimIds) {
            if (nation.id.equals(this.state.claims.get(claimId))) continue;
            return false;
        }
        return true;
    }

    private boolean containsCapital(Set<String> claimIds, Nation nation) {
        return nation.capitalClaim != null && claimIds.contains(nation.capitalClaim);
    }

    private static void normalizePeaceDeal(PeaceDeal deal) {
        if (deal.demandedClaims == null) {
            deal.demandedClaims = new LinkedHashSet<String>();
        }
        if (deal.offeredClaims == null) {
            deal.offeredClaims = new LinkedHashSet<String>();
        }
        deal.demandedClaims.removeIf(claimId -> !NationStore.isValidClaimId(claimId));
        deal.offeredClaims.removeIf(claimId -> !NationStore.isValidClaimId(claimId));
        deal.demandedMoney = NationStore.roundMoney(Math.max(0.0, deal.demandedMoney));
        deal.offeredMoney = NationStore.roundMoney(Math.max(0.0, deal.offeredMoney));
    }

    private static boolean isValidClaimId(String claimId) {
        try {
            ClaimKey.parse(claimId);
            return true;
        }
        catch (RuntimeException exception) {
            return false;
        }
    }

    public static final class State {
        public Map<String, Nation> nations = new LinkedHashMap<String, Nation>();
        public Map<String, String> playerNation = new LinkedHashMap<String, String>();
        public Map<String, Double> players = new LinkedHashMap<String, Double>();
        public Map<String, String> claims = new LinkedHashMap<String, String>();
        public Map<String, War> wars = new LinkedHashMap<String, War>();
        public Map<String, Alliance> alliances = new LinkedHashMap<String, Alliance>();
        public Map<String, Long> spyCooldowns = new LinkedHashMap<String, Long>();
        public Map<String, Long> peaceCooldowns = new LinkedHashMap<String, Long>();
        public Set<String> warDeclarationRejections = new LinkedHashSet<String>();
        public List<SpyMission> spyMissions = new ArrayList<SpyMission>();
        public List<MarketListing> marketListings = new ArrayList<MarketListing>();
        public List<LandPurchaseOffer> landPurchaseOffers = new ArrayList<LandPurchaseOffer>();
        public int nextListingId = 1;
        public int nextSpyMissionId = 1;
        public int nextLandPurchaseOfferId = 1;
    }

    public static final class Nation {
        public String id = "";
        public String name = "";
        public String owner = "";
        public String ownerName = "";
        public String doctrine;
        public double balance;
        public int freeClaimsRemaining;
        public String capitalClaim;
        public Set<String> members;
        public Set<String> cityClaims;
        public long lastSpecialWarLeaveTick;
        public Set<String> usedSpecialWarLeaveIdeologies;
        public boolean lostCoreTerritory;

        public Nation() {
            this.doctrine = Doctrine.AMERICAN.id;
            this.balance = 0.0;
            this.freeClaimsRemaining = 0;
            this.capitalClaim = "";
            this.members = new LinkedHashSet<String>();
            this.cityClaims = new LinkedHashSet<String>();
            this.lastSpecialWarLeaveTick = -1L;
            this.usedSpecialWarLeaveIdeologies = new LinkedHashSet<String>();
            this.lostCoreTerritory = false;
        }

        public Doctrine doctrine() {
            return Doctrine.byId(this.doctrine).orElse(Doctrine.AMERICAN);
        }
    }

    public static final class LandPurchaseOffer {
        public int id = 0;
        public String buyer = "";
        public String seller = "";
        public String claimId = "";
        public double price = 0.0;
        public String requester = "";
        public String requesterName = "";
        public long createdTick = 0L;
    }

    public static final class MarketListing {
        public int id = 0;
        public String seller = "";
        public String sellerName = "";
        public double price = 0.0;
        public String itemTag = "";
    }

    public static final class Alliance {
        public String id = "";
        public String name = "";
        public String leader = "";
        public Set<String> members = new LinkedHashSet<String>();
        public Set<String> invites = new LinkedHashSet<String>();
    }

    public static final class War {
        public String id = "";
        public String attacker = "";
        public String defender = "";
        public boolean active = false;
        public long justificationCompleteTick = 0L;
        public int defenderStartingClaims = 0;
        public Set<String> attackerCapturedClaims = new LinkedHashSet<String>();
        public Set<String> peaceOffers = new LinkedHashSet<String>();
        public PeaceDeal peaceDeal = null;
        public Set<String> attackerSide = new LinkedHashSet<String>();
        public Set<String> defenderSide = new LinkedHashSet<String>();
        public Map<String, String> joinRequests = new LinkedHashMap<String, String>();
        public Map<String, Set<String>> capturedClaimsByNation = new LinkedHashMap<String, Set<String>>();
        public boolean pendingDefenderResponse = false;
    }

    public static final class PeaceDeal {
        public String proposer = "";
        public String receiver = "";
        public Set<String> demandedClaims = new LinkedHashSet<String>();
        public Set<String> offeredClaims = new LinkedHashSet<String>();
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
}

