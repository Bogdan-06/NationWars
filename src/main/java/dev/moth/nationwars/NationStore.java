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

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.moth.nationwars.ClaimKey;
import dev.moth.nationwars.Doctrine;
import dev.moth.nationwars.NationWars;
import dev.moth.nationwars.NationWarsConfig;
import dev.moth.nationwars.OpacClaimsBridge;
import java.io.IOException;
import java.nio.file.Path;
import dev.moth.nationwars.persistence.NationRepository;
import dev.moth.nationwars.persistence.DataIntegrityService;
import dev.moth.nationwars.service.PuppetRules;
import dev.moth.nationwars.service.PuppetService;
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
    public static final int NATION_INVITATION_SECONDS = 600;
    private static final Random RANDOM = new Random();
    private static final Set<String> SPY_STATUSES = Set.of("idle", "traveling", "stationed", "mission", "counterspy", "recovering");
    private static final Map<String, Integer> SPY_MISSION_CHUNK_COUNTS = Map.ofEntries(
        Map.entry("counterspy", 1),
        Map.entry("doctrine", 0),
        Map.entry("treasury", 0),
        Map.entry("members", 0),
        Map.entry("faction", 0),
        Map.entry("size", 0),
        Map.entry("scout", 3),
        Map.entry("infiltrate", 1),
        Map.entry("paralyze", 1),
        Map.entry("steal", 1),
        Map.entry("raid", 1)
    );
    private static NationStore current;
    private final MinecraftServer server;
    private final NationRepository repository;
    private final State state;

    private NationStore(MinecraftServer server, NationRepository repository, State state) {
        this.server = server;
        this.repository = repository;
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
        NationRepository repository = new NationRepository(file);
        NationRepository.LoadResult result;
        try {
            result = repository.load();
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("Refusing to overwrite unreadable or unsupported Nation Wars data at " + file, exception);
        }
        result.diagnostics().forEach(message -> NationWars.LOGGER.warn("{}", message));
        if (result.migrated()) {
            NationWars.LOGGER.info("Migrated Nation Wars data from version {} to version {}",
                result.originalVersion(), dev.moth.nationwars.persistence.DataMigrationService.CURRENT_DATA_VERSION);
        }
        current = new NationStore(server, repository, result.state());
        current.save();
        OpacClaimsBridge.syncAll(server, current);
    }

    public synchronized void save() {
        try {
            this.repository.save(this.state);
        }
        catch (IOException exception) {
            NationWars.LOGGER.error("Failed to save Nation Wars data", exception);
        }
    }

    public static long persistentNow() {
        return PersistentTime.now();
    }

    public Collection<Nation> nations() {
        return this.state.nations.values();
    }

    public MinecraftServer server() {
        return this.server;
    }

    public void updatePlayerIdentity(ServerPlayer player) {
        String playerId = player.getUUID().toString();
        String currentName = player.getGameProfile().getName();
        boolean changed = false;
        if (!Objects.equals(this.state.playerNames.get(playerId), currentName)) {
            this.state.playerNames.put(playerId, currentName);
            changed = true;
        }
        for (Nation nation : this.state.nations.values()) {
            if (!playerId.equals(nation.owner) || Objects.equals(currentName, nation.ownerName)) continue;
            nation.ownerName = currentName;
            changed = true;
        }
        if (changed) {
            this.save();
        }
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
        NationWarsConfig config = NationWarsConfig.get();
        if (config.isDoctrineDisabled(doctrine)) {
            return true;
        }
        if (!config.enforceDoctrineLimits) {
            return false;
        }
        return this.doctrineUseCount(doctrine) >= config.doctrineLimit(doctrine);
    }

    public int doctrineUseCount(Doctrine doctrine) {
        return (int)this.state.nations.values().stream().filter(nation -> nation.doctrine() == doctrine).count();
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

    public synchronized boolean purchaseMarketListing(int id, UUID buyerId, double buyerPrice, double sellerPayout) {
        if (buyerId == null || !Double.isFinite(buyerPrice) || buyerPrice < 0.0
            || !Double.isFinite(sellerPayout) || sellerPayout < 0.0 || sellerPayout > buyerPrice + 1.0E-4) {
            return false;
        }
        MarketListing listing = this.marketListing(id).orElse(null);
        if (listing == null || buyerId.toString().equals(listing.seller)) {
            return false;
        }
        UUID sellerId;
        try {
            sellerId = UUID.fromString(listing.seller);
        } catch (IllegalArgumentException exception) {
            return false;
        }
        this.ensurePlayer(buyerId);
        this.ensurePlayer(sellerId);
        String buyerKey = buyerId.toString();
        String sellerKey = sellerId.toString();
        double buyerBalance = this.state.players.get(buyerKey);
        if (buyerBalance + 1.0E-4 < buyerPrice) {
            return false;
        }
        this.state.players.put(buyerKey, NationStore.roundMoney(buyerBalance - buyerPrice));
        this.state.players.put(sellerKey, NationStore.roundMoney(this.state.players.get(sellerKey) + sellerPayout));
        this.state.marketListings.remove(listing);
        this.save();
        return true;
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
        nation.joinPolicy = TechnicalConfig.defaultJoinPolicy().name();
        nation.balance = 250.0;
        nation.freeClaimsRemaining = Math.max(0, doctrine.freeClaims - 1);
        nation.capitalClaim = capital.id();
        nation.members.add(nation.owner);
        nation.coreClaims.add(capital.id());
        this.state.nations.put(key, nation);
        this.state.playerNation.put(nation.owner, key);
        this.state.playerNames.put(nation.owner, nation.ownerName);
        this.state.claims.put(capital.id(), key);
        this.ensurePlayer(owner.getUUID());
        OpacClaimsBridge.mirrorClaim(this.server, nation, capital);
        this.save();
        return nation;
    }

    public Optional<Nation> nationByName(String name) {
        return Optional.ofNullable(this.state.nations.get(NationStore.nationKey(name)));
    }

    public Optional<TradeOffer> tradeOfferBetween(Nation first, Nation second) {
        if (first == null || second == null) {
            return Optional.empty();
        }
        return this.state.tradeOffers.stream()
            .filter(offer -> (first.id.equals(offer.proposer) && second.id.equals(offer.receiver))
                || (second.id.equals(offer.proposer) && first.id.equals(offer.receiver)))
            .findFirst();
    }

    public TradeOffer setTradeOffer(TradeOffer offer) {
        if (offer == null || offer.proposer == null || offer.receiver == null || offer.proposer.equals(offer.receiver)
            || !this.state.nations.containsKey(offer.proposer) || !this.state.nations.containsKey(offer.receiver)) {
            return null;
        }
        NationStore.normalizeTradeOffer(offer);
        offer.id = offer.id <= 0 ? this.state.nextTradeOfferId++ : offer.id;
        this.state.tradeOffers.removeIf(existing -> (offer.proposer.equals(existing.proposer) && offer.receiver.equals(existing.receiver))
            || (offer.proposer.equals(existing.receiver) && offer.receiver.equals(existing.proposer)));
        this.state.tradeOffers.add(offer);
        this.save();
        return offer;
    }

    public void removeTradeOffer(TradeOffer offer) {
        if (offer == null) {
            return;
        }
        this.state.tradeOffers.removeIf(existing -> existing.id == offer.id);
        this.save();
    }

    public boolean applyTradeOffer(TradeOffer offer) {
        if (offer == null || !this.state.tradeOffers.contains(offer) || !NationWarsConfig.get().allowTrade) {
            return false;
        }
        NationStore.normalizeTradeOffer(offer);
        Nation proposer = this.nationById(offer.proposer).orElse(null);
        Nation receiver = this.nationById(offer.receiver).orElse(null);
        if (proposer == null || receiver == null || this.activeWarForCapture(proposer, receiver).isPresent() || this.activeWarForCapture(receiver, proposer).isPresent()) {
            return false;
        }
        if (proposer.balance + 1.0E-4 < offer.offeredMoney || receiver.balance + 1.0E-4 < offer.requestedMoney) {
            return false;
        }
        long tick = NationStore.persistentNow();
        if ((offer.offeredMoney > 0.0 && this.isSpendingBlocked(proposer, tick)) || (offer.requestedMoney > 0.0 && this.isSpendingBlocked(receiver, tick))) {
            return false;
        }
        if (!this.claimsStillOwnedBy(offer.offeredClaims, proposer) || !this.claimsStillOwnedBy(offer.requestedClaims, receiver)) {
            return false;
        }
        if (this.containsCapital(offer.offeredClaims, proposer) || this.containsCapital(offer.requestedClaims, receiver)) {
            return false;
        }
        if (offer.offeredClaims.stream().anyMatch(this::isClaimCapturedInActiveWar)
            || offer.requestedClaims.stream().anyMatch(this::isClaimCapturedInActiveWar)) {
            return false;
        }
        ArrayList<ClaimTransfer> transfers = new ArrayList<>();
        if (offer.offeredMoney > 0.0) {
            proposer.balance = NationStore.roundMoney(proposer.balance - offer.offeredMoney);
            receiver.balance = NationStore.roundMoney(receiver.balance + offer.offeredMoney);
        }
        if (offer.requestedMoney > 0.0) {
            receiver.balance = NationStore.roundMoney(receiver.balance - offer.requestedMoney);
            proposer.balance = NationStore.roundMoney(proposer.balance + offer.requestedMoney);
        }
        for (String claimId : offer.offeredClaims) {
            if (!proposer.id.equals(this.state.claims.get(claimId))) continue;
            ClaimTransfer transfer = this.transferClaimState(claimId, receiver);
            if (transfer != null) {
                transfers.add(transfer);
            }
            receiver.coreClaims.add(claimId);
        }
        for (String claimId : offer.requestedClaims) {
            if (!receiver.id.equals(this.state.claims.get(claimId))) continue;
            ClaimTransfer transfer = this.transferClaimState(claimId, proposer);
            if (transfer != null) {
                transfers.add(transfer);
            }
            proposer.coreClaims.add(claimId);
        }
        if (offer.incomeTermsSpecified) {
            this.setIncomeAgreement(proposer, receiver, offer.offeredIncomePerMinute, offer.requestedIncomePerMinute);
        }
        this.recordAcceptedPuppetTrade(offer);
        this.state.tradeOffers.removeIf(existing -> existing.id == offer.id);
        this.save();
        this.syncClaimTransfers(transfers);
        return true;
    }

    public Optional<Nation> nationOf(UUID playerId) {
        return Optional.ofNullable(this.state.playerNation.get(playerId.toString())).map(this.state.nations::get);
    }

    public boolean isCurrentNation(Nation nation) {
        return nation != null && this.state.nations.get(nation.id) == nation;
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
        Set<String> invited = this.state.nationInvites.get(nation.id);
        if (invited != null) {
            invited.remove(id);
            if (invited.isEmpty()) {
                this.state.nationInvites.remove(nation.id);
            }
        }
        Map<String, Long> expirations = this.state.nationInviteExpirations.get(nation.id);
        if (expirations != null) {
            expirations.remove(id);
            if (expirations.isEmpty()) {
                this.state.nationInviteExpirations.remove(nation.id);
            }
        }
        Set<String> formerMembers = this.state.formerNationMembers.get(nation.id);
        if (formerMembers != null) {
            formerMembers.remove(id);
            if (formerMembers.isEmpty()) {
                this.state.formerNationMembers.remove(nation.id);
            }
        }
        this.ensurePlayer(playerId);
        this.save();
    }

    public boolean inviteMember(Nation nation, UUID playerId) {
        if (nation == null || playerId == null || this.hasNation(playerId) || nation.joinPolicy() != JoinPolicy.INVITE_ONLY) {
            return false;
        }
        String player = playerId.toString();
        this.removeExpiredNationInvites(nation, NationStore.persistentNow());
        boolean added = this.state.nationInvites.computeIfAbsent(nation.id, ignored -> new LinkedHashSet<>()).add(player);
        if (added) {
            this.state.nationInviteExpirations.computeIfAbsent(nation.id, ignored -> new LinkedHashMap<>())
                .put(player, NationStore.persistentNow() + NATION_INVITATION_SECONDS * 20L);
            this.save();
        }
        return added;
    }

    public boolean hasNationInvite(Nation nation, UUID playerId) {
        if (nation == null || playerId == null) {
            return false;
        }
        this.removeExpiredNationInvites(nation, NationStore.persistentNow());
        return this.state.nationInvites.getOrDefault(nation.id, Set.of()).contains(playerId.toString());
    }

    public boolean removeNationInvite(Nation nation, UUID playerId) {
        if (nation == null || playerId == null) {
            return false;
        }
        String player = playerId.toString();
        Set<String> invites = this.state.nationInvites.get(nation.id);
        boolean removed = invites != null && invites.remove(player);
        Map<String, Long> expirations = this.state.nationInviteExpirations.get(nation.id);
        if (expirations != null) {
            expirations.remove(player);
        }
        if (removed) {
            this.save();
        }
        return removed;
    }

    public void setJoinPolicy(Nation nation, JoinPolicy policy) {
        if (nation == null || policy == null) {
            return;
        }
        nation.joinPolicy = policy.name();
        if (policy != JoinPolicy.INVITE_ONLY) {
            this.state.nationInvites.remove(nation.id);
            this.state.nationInviteExpirations.remove(nation.id);
        }
        this.save();
    }

    private void removeExpiredNationInvites(Nation nation, long now) {
        Set<String> invites = this.state.nationInvites.get(nation.id);
        Map<String, Long> expirations = this.state.nationInviteExpirations.get(nation.id);
        if (invites == null || expirations == null) {
            return;
        }
        invites.removeIf(player -> expirations.getOrDefault(player, 0L) <= now);
        expirations.keySet().removeIf(player -> !invites.contains(player));
        if (invites.isEmpty()) {
            this.state.nationInvites.remove(nation.id);
            this.state.nationInviteExpirations.remove(nation.id);
        }
    }

    public boolean purchaseUpgrade(Nation nation, double cost) {
        if (nation == null || nation.doctrine() == Doctrine.AMERICAN || nation.upgradeLevel >= 4
            || !Double.isFinite(cost) || cost <= 0.0 || nation.balance + 1.0E-4 < cost
            || this.isSpendingBlocked(nation, NationStore.persistentNow())) {
            return false;
        }
        nation.balance = NationStore.roundMoney(nation.balance - cost);
        nation.upgradeLevel++;
        nation.freeClaimsRemaining += 5;
        this.save();
        return true;
    }

    public boolean resolveBuildingRewardAttempt(UUID playerId, String positionKey, double amount, boolean award) {
        if (playerId == null || positionKey == null || positionKey.isBlank() || !Double.isFinite(amount) || amount <= 0.0
            || !this.state.rewardedBuildPositions.add(positionKey)) {
            return false;
        }
        if (award) {
            this.ensurePlayer(playerId);
            this.state.players.compute(playerId.toString(),
                (id, balance) -> NationStore.roundMoney((balance == null ? 0.0 : balance) + amount));
        }
        this.save();
        return award;
    }

    public boolean hasRewardedBuildPosition(String positionKey) {
        return positionKey != null && this.state.rewardedBuildPositions.contains(positionKey);
    }

    public void recordPlayerName(ServerPlayer player) {
        this.state.playerNames.put(player.getUUID().toString(), player.getGameProfile().getName());
        this.save();
    }

    public Optional<String> playerName(UUID playerId) {
        return Optional.ofNullable(this.state.playerNames.get(playerId.toString()));
    }

    public Optional<UUID> memberIdByName(Nation nation, String playerNameOrUuid) {
        if (nation == null || playerNameOrUuid == null || playerNameOrUuid.isBlank()) {
            return Optional.empty();
        }
        try {
            UUID uuid = UUID.fromString(playerNameOrUuid);
            return nation.members.contains(uuid.toString()) ? Optional.of(uuid) : Optional.empty();
        }
        catch (IllegalArgumentException ignored) {
            // Try recorded player names below.
        }
        String requested = playerNameOrUuid.trim();
        return nation.members.stream()
            .filter(member -> requested.equalsIgnoreCase(this.state.playerNames.getOrDefault(member, "")))
            .findFirst()
            .map(UUID::fromString);
    }

    public boolean hasFormerMembership(UUID playerId, Nation nation) {
        return nation != null && this.state.formerNationMembers.getOrDefault(nation.id, Set.of()).contains(playerId.toString());
    }

    public boolean removeMember(UUID playerId, Nation nation, boolean blockRejoin) {
        if (nation == null || playerId == null || this.isOwner(playerId, nation)) {
            return false;
        }
        String id = playerId.toString();
        boolean removed = nation.members.remove(id);
        this.state.playerNation.remove(id);
        if (blockRejoin) {
            this.state.formerNationMembers.computeIfAbsent(nation.id, ignored -> new LinkedHashSet<String>()).add(id);
        }
        if (removed || blockRejoin) {
            this.save();
        }
        return removed;
    }

    public boolean addCityClaim(Nation nation, String claimId, double cost) {
        if (nation == null || !Double.isFinite(cost) || cost <= 0.0 || !nation.id.equals(this.state.claims.get(claimId))
            || nation.cityClaims.contains(claimId) || nation.balance + 1.0E-4 < cost
            || this.isSpendingBlocked(nation, NationStore.persistentNow())) {
            return false;
        }
        nation.balance = NationStore.roundMoney(nation.balance - cost);
        nation.cityClaims.add(claimId);
        this.save();
        return true;
    }

    public boolean sameAlliance(Nation first, Nation second) {
        if (first == null || second == null || !NationWarsConfig.get().factions) {
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
        if (playerId == null || !Double.isFinite(amount) || amount == 0.0) {
            return;
        }
        this.ensurePlayer(playerId);
        this.state.players.compute(playerId.toString(),
            (id, balance) -> NationStore.roundMoney(Math.max(0.0, (balance == null ? 0.0 : balance) + amount)));
        this.save();
    }

    public boolean withdrawPlayerMoney(UUID playerId, double amount) {
        if (playerId == null || !Double.isFinite(amount) || amount <= 0.0) {
            return false;
        }
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

    public double confiscatePlayerMoneyForTransaction(UUID playerId) {
        this.ensurePlayer(playerId);
        String id = playerId.toString();
        double balance = this.state.players.get(id);
        this.state.players.put(id, 0.0);
        return NationStore.roundMoney(balance);
    }

    public boolean depositToNation(UUID playerId, Nation nation, double amount) {
        if (playerId == null || nation == null || !Double.isFinite(amount) || amount <= 0.0) {
            return false;
        }
        this.ensurePlayer(playerId);
        String playerKey = playerId.toString();
        double playerBalance = this.state.players.get(playerKey);
        if (playerBalance + 1.0E-4 < amount) {
            return false;
        }
        this.state.players.put(playerKey, NationStore.roundMoney(playerBalance - amount));
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
        nation.coreClaims.add(claim.id());
        OpacClaimsBridge.mirrorClaim(this.server, nation, claim);
        this.save();
        return true;
    }

    public boolean isCoastClaim(String claimId) {
        return claimId != null && this.state.coastClaims.contains(claimId);
    }

    public void setCoastClaim(String claimId, boolean coast) {
        if (claimId == null || claimId.isBlank()) {
            return;
        }
        if (coast) {
            this.state.coastClaims.add(claimId);
        } else {
            this.state.coastClaims.remove(claimId);
        }
        this.save();
    }

    public boolean coastClaimsMigrated() {
        return this.state.coastClaimsMigrated;
    }

    public void finishCoastClaimsMigration(Set<String> coastClaims) {
        this.state.coastClaims.clear();
        if (coastClaims != null) {
            this.state.coastClaims.addAll(coastClaims);
        }
        this.state.coastClaims.removeIf(claimId -> !this.state.claims.containsKey(claimId));
        this.state.coastClaimsMigrated = true;
        this.save();
    }

    public boolean hasClaimInDimension(Nation nation, String dimension) {
        return nation != null && dimension != null && this.claimsOf(nation).stream()
            .filter(claimId -> !this.isCapturedClaimHeldBy(nation, claimId)).map(ClaimKey::parse)
            .anyMatch(claim -> dimension.equals(claim.dimension()));
    }

    public boolean unclaim(Nation nation, ClaimKey claim) {
        if (!nation.id.equals(this.state.claims.get(claim.id()))) {
            return false;
        }
        if (claim.id().equals(nation.capitalClaim) || this.isClaimCapturedInActiveWar(claim.id())) {
            return false;
        }
        this.state.claims.remove(claim.id());
        this.state.landPurchaseOffers.removeIf(offer -> claim.id().equals(offer.claimId));
        this.state.coastClaims.remove(claim.id());
        nation.cityClaims.remove(claim.id());
        nation.coreClaims.remove(claim.id());
        OpacClaimsBridge.unmirrorClaim(this.server, nation, claim);
        this.save();
        return true;
    }

    public void transferClaim(String claimId, Nation newOwner) {
        ClaimTransfer transfer = this.transferClaimState(claimId, newOwner);
        if (transfer == null) {
            return;
        }
        this.save();
        this.syncClaimTransfer(transfer);
    }

    private ClaimTransfer transferClaimState(String claimId, Nation newOwner) {
        if (claimId == null || newOwner == null || !this.state.claims.containsKey(claimId)) {
            return null;
        }
        Nation oldOwner = Optional.ofNullable(this.state.claims.get(claimId)).map(this.state.nations::get).orElse(null);
        if (oldOwner != null && oldOwner.id.equals(newOwner.id)) {
            return null;
        }
        this.state.claims.put(claimId, newOwner.id);
        this.state.landPurchaseOffers.removeIf(offer -> claimId.equals(offer.claimId));
        if (oldOwner != null) {
            oldOwner.cityClaims.remove(claimId);
        }
        if (oldOwner != null && claimId.equals(oldOwner.capitalClaim)) {
            oldOwner.capitalClaim = this.claimsOf(oldOwner).stream().filter(id -> !id.equals(claimId)).findFirst().orElse("");
        }
        if (newOwner.capitalClaim == null || newOwner.capitalClaim.isBlank()) {
            newOwner.capitalClaim = claimId;
        }
        return new ClaimTransfer(claimId, oldOwner, newOwner);
    }

    private void syncClaimTransfer(ClaimTransfer transfer) {
        try {
            ClaimKey claim = ClaimKey.parse(transfer.claimId);
            if (transfer.oldOwner != null) {
                OpacClaimsBridge.replaceClaim(this.server, transfer.oldOwner, transfer.newOwner, claim);
            } else {
                OpacClaimsBridge.mirrorClaim(this.server, transfer.newOwner, claim);
            }
        }
        catch (RuntimeException exception) {
            NationWars.LOGGER.warn("Could not synchronize transferred claim {} with Open Parties and Claims; the next sync will retry it.", transfer.claimId, exception);
        }
    }

    private void syncClaimTransfers(List<ClaimTransfer> transfers) {
        transfers.forEach(this::syncClaimTransfer);
    }

    private record ClaimTransfer(String claimId, Nation oldOwner, Nation newOwner) {
    }

    public boolean captureClaim(War war, Nation capturingNation, Nation defendingNation, String claimId) {
        if (war == null || capturingNation == null || defendingNation == null || claimId == null || claimId.isBlank()
            || !defendingNation.id.equals(this.state.claims.get(claimId)) || this.isClaimCapturedInOtherActiveWar(war, claimId)) {
            return false;
        }
        ClaimTransfer transfer = this.transferClaimState(claimId, capturingNation);
        if (transfer == null) {
            return false;
        }
        this.recordCapturedClaimState(war, capturingNation, defendingNation, claimId);
        if (defendingNation.doctrine() == Doctrine.ROMANIAN && this.isCoreClaimForWar(war, defendingNation, claimId)) {
            this.recordLostCoreClaim(defendingNation, claimId);
        }
        this.save();
        this.syncClaimTransfer(transfer);
        return true;
    }

    private void recordCapturedClaimState(War war, Nation capturingNation, Nation defendingNation, String claimId) {
        NationStore.ensureWarState(war);
        String originalOwner = war.originalClaimOwners.getOrDefault(claimId, defendingNation.id);
        for (Set<String> claims : war.capturedClaimsByNation.values()) {
            claims.remove(claimId);
        }
        if (capturingNation.id.equals(originalOwner)) {
            war.originalClaimOwners.remove(claimId);
        } else {
            war.originalClaimOwners.putIfAbsent(claimId, originalOwner);
            war.capturedClaimsByNation.computeIfAbsent(capturingNation.id, ignored -> new LinkedHashSet()).add(claimId);
        }
        war.capturedClaimsByNation.entrySet().removeIf(entry -> ((Set)entry.getValue()).isEmpty());
        if (this.sideOf(war, capturingNation) > 0 && !capturingNation.id.equals(originalOwner)) {
            war.attackerCapturedClaims.add(claimId);
        } else {
            war.attackerCapturedClaims.remove(claimId);
        }
    }

    public boolean isCoreClaimForWar(War war, Nation nation, String claimId) {
        if (war == null || nation == null || claimId == null) {
            return false;
        }
        NationStore.ensureWarState(war);
        return war.coreClaimsByNation.getOrDefault(nation.id, Set.of()).contains(claimId);
    }

    public int coreClaimCountForWar(War war, Nation nation) {
        if (war == null || nation == null) {
            return 0;
        }
        NationStore.ensureWarState(war);
        return war.coreClaimsByNation.getOrDefault(nation.id, Set.of()).size();
    }

    public void snapshotWarCores(War war, Nation nation) {
        if (war == null || nation == null) {
            return;
        }
        NationStore.ensureWarState(war);
        LinkedHashSet<String> cores = new LinkedHashSet<>(this.claimsOf(nation));
        cores.removeIf(claimId -> this.isClaimCapturedInActiveWar(claimId));
        war.coreClaimsByNation.put(nation.id, cores);
    }

    public void removeCapturedClaim(War war, String claimId) {
        if (war == null || claimId == null || claimId.isBlank()) {
            return;
        }
        this.removeCapturedClaimState(war, claimId);
        this.save();
    }

    private void removeCapturedClaimState(War war, String claimId) {
        NationStore.ensureWarState(war);
        for (Set<String> claims : war.capturedClaimsByNation.values()) {
            claims.remove(claimId);
        }
        war.originalClaimOwners.remove(claimId);
        war.capturedClaimsByNation.entrySet().removeIf(entry -> ((Set)entry.getValue()).isEmpty());
        war.attackerCapturedClaims.remove(claimId);
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

    public boolean isClaimCapturedInActiveWar(String claimId) {
        if (claimId == null) {
            return false;
        }
        return this.state.wars.values().stream().filter(war -> war.active).anyMatch(war -> {
            NationStore.ensureWarState(war);
            return war.originalClaimOwners.containsKey(claimId) || war.capturedClaimsByNation.values().stream().anyMatch(claims -> claims.contains(claimId));
        });
    }

    public boolean isClaimCapturedInOtherActiveWar(War currentWar, String claimId) {
        if (claimId == null) {
            return false;
        }
        return this.state.wars.values().stream().filter(war -> war != currentWar && war.active).anyMatch(war -> {
            NationStore.ensureWarState(war);
            return war.originalClaimOwners.containsKey(claimId)
                || war.capturedClaimsByNation.values().stream().anyMatch(claims -> claims.contains(claimId));
        });
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
                if (!captor.id.equals(this.state.claims.get(claimId)) || !defeated.id.equals(war.originalClaimOwners.get(claimId))) continue;
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
        for (War war : new ArrayList<>(this.state.wars.values())) {
            if (this.isIndependenceWar(war)
                && (nation.id.equals(war.attacker) || nation.id.equals(war.defender))) {
                this.returnAllCapturedClaimsToOriginalOwners(war);
            }
        }
        List<Nation> releasedPuppets = PuppetService.releaseAll(this.state, nation.id).stream()
            .map(this.state.nations::get).filter(Objects::nonNull).toList();
        PuppetRelation ownPuppetRelation = this.state.puppetRelations.get(nation.id);
        if (ownPuppetRelation != null) {
            PuppetService.release(this.state, ownPuppetRelation.master, nation.id);
        }
        this.state.puppetProposals.entrySet().removeIf(entry -> {
            PuppetProposal proposal = entry.getValue();
            return proposal == null || nation.id.equals(proposal.master) || nation.id.equals(proposal.puppet);
        });
        for (String claimId : new ArrayList<String>(this.claimsOf(nation))) {
            this.state.claims.remove(claimId);
            this.state.landPurchaseOffers.removeIf(offer -> claimId.equals(offer.claimId));
            this.state.coastClaims.remove(claimId);
            this.state.paralyzedClaims.remove(claimId);
            this.state.raidedClaims.remove(claimId);
            this.state.disabledCounterspyClaims.remove(claimId);
            OpacClaimsBridge.unmirrorClaim(this.server, nation, ClaimKey.parse(claimId));
        }
        this.state.nations.remove(nation.id);
        this.state.playerNation.entrySet().removeIf(entry -> nation.id.equals(entry.getValue()));
        this.state.formerNationMembers.remove(nation.id);
        this.state.nationInvites.remove(nation.id);
        this.state.nationInviteExpirations.remove(nation.id);
        this.state.landPurchaseOffers.removeIf(offer -> nation.id.equals(offer.buyer) || nation.id.equals(offer.seller));
        this.state.tradeOffers.removeIf(offer -> nation.id.equals(offer.proposer) || nation.id.equals(offer.receiver));
        this.state.truces.entrySet().removeIf(entry -> nation.id.equals(entry.getValue().first) || nation.id.equals(entry.getValue().second));
        this.state.truceOffers.entrySet().removeIf(entry -> nation.id.equals(entry.getValue().proposer) || nation.id.equals(entry.getValue().receiver));
        this.state.incomeTransfers.entrySet().removeIf(entry -> nation.id.equals(entry.getValue().payer) || nation.id.equals(entry.getValue().receiver));
        this.state.peaceCooldowns.keySet().removeIf(key -> key.startsWith(nation.id + "->") || key.endsWith("->" + nation.id));
        this.state.warDeclarationRejections.removeIf(key -> key.startsWith(nation.id + "->") || key.endsWith("->" + nation.id));
        this.state.alliances.values().removeIf(alliance -> Objects.equals(alliance.leader, nation.id));
        this.state.guarantees.remove(nation.id);
        this.state.guarantees.values().forEach(guarantors -> guarantors.remove(nation.id));
        this.state.guarantees.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        for (SpyMission mission : new ArrayList<>(this.state.spyMissions)) {
            if (!nation.id.equals(mission.spyNation) && !nation.id.equals(mission.target)) {
                continue;
            }
            if (!nation.id.equals(mission.spyNation)) {
                this.resetSpyAfterCancelledMission(mission);
            }
            this.state.spyMissions.remove(mission);
        }
        this.state.spendingBlocks.remove(nation.id);
        for (Alliance alliance2 : this.state.alliances.values()) {
            alliance2.members.remove(nation.id);
            alliance2.invites.remove(nation.id);
        }
        for (War war : new ArrayList<>(this.state.wars.values())) {
            NationStore.ensureWarSides(war);
            NationStore.ensureWarState(war);
            if (nation.id.equals(war.attacker) || nation.id.equals(war.defender)) {
                NationStore.deactivateWarState(war);
                this.state.wars.remove(war.id, war);
                continue;
            }
            if (this.sideOf(war, nation) != 0) {
                war.attackerSide.remove(nation.id);
                war.defenderSide.remove(nation.id);
                this.clearCapturedTrackingFor(war, nation);
                war.coreClaimsByNation.remove(nation.id);
            }
            war.joinRequests.entrySet().removeIf(entry -> nation.id.equals(entry.getKey()) || nation.id.equals(entry.getValue()));
            war.defenseCalls.entrySet().removeIf(entry -> nation.id.equals(entry.getKey()) || nation.id.equals(entry.getValue().caller));
        }
        this.save();
        if (this.server != null) {
            for (Nation released : releasedPuppets) {
                this.notifyNation(this.server, released,
                    NationText.message("nationwars.puppet.released.master_deleted", nation.name));
            }
        }
    }

    public boolean removeBorderClaim(Nation nation) {
        List<String> candidates = this.borderClaimsOf(nation).stream()
            .filter(claim -> !claim.equals(nation.capitalClaim) && !this.isClaimCapturedInActiveWar(claim)).toList();
        if (candidates.isEmpty()) {
            return false;
        }
        String removed = candidates.get(RANDOM.nextInt(candidates.size()));
        this.state.claims.remove(removed);
        this.state.landPurchaseOffers.removeIf(offer -> removed.equals(offer.claimId));
        this.state.coastClaims.remove(removed);
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
        NationStore.ensureWarState(war);
        return war;
    }

    public void endWar(War war) {
        if (war == null) {
            return;
        }
        NationStore.deactivateWarState(war);
        this.state.wars.remove(war.id, war);
        this.save();
    }

    public boolean isCurrentActiveWar(War war) {
        return war != null && war.active && this.state.wars.get(war.id) == war;
    }

    public boolean applyPeaceDeal(War war, PeaceDeal deal) {
        if (!this.isCurrentActiveWar(war) || deal == null || war.peaceDeal != deal || NationWarsConfig.get().noMercy) {
            return false;
        }
        Nation proposer = this.nationById(deal.proposer).orElse(null);
        Nation receiver = this.nationById(deal.receiver).orElse(null);
        if (proposer == null || receiver == null || !this.areOpposingWarSides(war, proposer, receiver)) {
            return false;
        }
        NationStore.normalizePeaceDeal(deal);
        if (this.isIndependenceWar(war)) {
            Nation master = this.masterOf(this.state.nations.get(war.independencePuppet)).orElse(null);
            boolean puppetWon = master != null && war.independencePuppet.equals(proposer.id)
                && master.id.equals(receiver.id);
            return this.resolveIndependenceWar(war, puppetWon).resolved();
        }
        if (deal.puppetReceiver && (!NationWarsConfig.get().puppets || !this.canEstablishPuppet(proposer, receiver))) {
            return false;
        }
        double demandedMoney = NationStore.roundMoney(Math.max(0.0, deal.demandedMoney));
        double offeredMoney = NationStore.roundMoney(Math.max(0.0, deal.offeredMoney));
        if (receiver.balance + 1.0E-4 < demandedMoney || proposer.balance + 1.0E-4 < offeredMoney) {
            return false;
        }
        long tick = NationStore.persistentNow();
        if ((demandedMoney > 0.0 && this.isSpendingBlocked(receiver, tick)) || (offeredMoney > 0.0 && this.isSpendingBlocked(proposer, tick))) {
            return false;
        }
        if (!this.claimsStillOwnedBy(deal.demandedClaims, receiver) || !this.claimsStillOwnedBy(deal.offeredClaims, proposer)) {
            return false;
        }
        if (deal.demandedClaims.stream().anyMatch(claimId -> this.isClaimCapturedInOtherActiveWar(war, claimId))
            || deal.offeredClaims.stream().anyMatch(claimId -> this.isClaimCapturedInOtherActiveWar(war, claimId))) {
            return false;
        }
        if (this.containsCapital(deal.demandedClaims, receiver) || this.containsCapital(deal.offeredClaims, proposer)) {
            return false;
        }
        ArrayList<ClaimTransfer> transfers = new ArrayList<>();
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
            ClaimTransfer transfer = this.transferClaimState(claimId, proposer);
            if (transfer != null) {
                transfers.add(transfer);
                if (receiver.doctrine() == Doctrine.ROMANIAN && this.isCoreClaimForWar(war, receiver, claimId)) {
                    this.recordLostCoreClaim(receiver, claimId);
                }
                this.removeCapturedClaimState(war, claimId);
            }
        }
        for (String claimId : deal.offeredClaims) {
            if (!proposer.id.equals(this.state.claims.get(claimId))) continue;
            ClaimTransfer transfer = this.transferClaimState(claimId, receiver);
            if (transfer != null) {
                transfers.add(transfer);
                if (proposer.doctrine() == Doctrine.ROMANIAN && this.isCoreClaimForWar(war, proposer, claimId)) {
                    this.recordLostCoreClaim(proposer, claimId);
                }
                this.removeCapturedClaimState(war, claimId);
            }
        }
        if (deal.returnCapturedClaims) {
            java.util.function.Predicate<String> selector = this.isPrimaryPeacePair(war, proposer, receiver)
                ? claimId -> true
                : claimId -> {
                    Nation captor = this.captorOfClaim(war, claimId);
                    String original = war.originalClaimOwners.get(claimId);
                    return proposer.id.equals(original) || receiver.id.equals(original)
                        || captor != null && (proposer.id.equals(captor.id) || receiver.id.equals(captor.id));
                };
            this.returnCapturedClaimsToOriginalOwnersState(war, selector, transfers);
        }
        PuppetService.EstablishResult puppetResult = null;
        if (deal.puppetReceiver) {
            puppetResult = PuppetService.establish(this.state, proposer.id, receiver.id);
            if (!puppetResult.established()) {
                return false;
            }
        }
        if (this.isPrimaryPeacePair(war, proposer, receiver)) {
            NationStore.deactivateWarState(war);
            this.state.wars.remove(war.id, war);
        } else {
            war.peaceDeal = null;
            if (!this.isPrimaryWarParticipant(war, proposer)) {
                this.removeWarParticipantState(war, proposer);
            }
            if (!this.isPrimaryWarParticipant(war, receiver)) {
                this.removeWarParticipantState(war, receiver);
            }
        }
        this.save();
        this.syncClaimTransfers(transfers);
        if (puppetResult != null) {
            for (String releasedId : puppetResult.releasedPuppets()) {
                Nation released = this.state.nations.get(releasedId);
                if (released != null) {
                    this.notifyNation(this.server, released,
                        NationText.message("nationwars.puppet.released.master_subjugated", receiver.name));
                }
            }
        }
        return true;
    }

    public boolean setPeaceDeal(War war, PeaceDeal deal) {
        if (!this.isCurrentActiveWar(war) || deal == null || NationWarsConfig.get().noMercy) {
            return false;
        }
        Nation proposer = this.state.nations.get(deal.proposer);
        Nation receiver = this.state.nations.get(deal.receiver);
        if (proposer == null || receiver == null || !this.areOpposingWarSides(war, proposer, receiver)) {
            return false;
        }
        if (deal.puppetReceiver && (this.isIndependenceWar(war) || !NationWarsConfig.get().puppets
            || !this.canEstablishPuppet(proposer, receiver))) {
            return false;
        }
        PeaceDeal existing = war.peaceDeal;
        if (existing != null && !((deal.proposer.equals(existing.proposer) && deal.receiver.equals(existing.receiver))
            || (deal.proposer.equals(existing.receiver) && deal.receiver.equals(existing.proposer)))) {
            return false;
        }
        NationStore.normalizePeaceDeal(deal);
        war.peaceDeal = deal;
        war.peaceOffers.clear();
        this.save();
        return true;
    }

    public void clearPeaceDeal(War war) {
        if (!this.isCurrentActiveWar(war)) {
            return;
        }
        war.peaceDeal = null;
        this.save();
    }

    public boolean clearAllPeaceDeals() {
        boolean changed = false;
        for (War war : this.state.wars.values()) {
            if (war.peaceDeal != null) {
                war.peaceDeal = null;
                changed = true;
            }
        }
        if (changed) {
            this.save();
        }
        return changed;
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
        return this.state.wars.values().stream().filter(war -> war.active)
            .filter(war -> this.areOpposingWarSides((War)war, attacker, defender))
            .sorted(Comparator.comparing(war -> war.id)).findFirst();
    }

    public Optional<War> activeWarRelationship(Nation first, Nation second) {
        if (first == null || second == null) {
            return Optional.empty();
        }
        return this.state.wars.values().stream().filter(war -> war.active && this.isWarParticipant(war, first)
            && this.isWarParticipant(war, second)).sorted(Comparator.comparing(war -> war.id)).findFirst();
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
        this.removeWarParticipantState(war, nation);
        this.save();
        return true;
    }

    private void removeWarParticipantState(War war, Nation nation) {
        war.attackerSide.remove(nation.id);
        war.defenderSide.remove(nation.id);
        war.joinRequests.entrySet().removeIf(entry -> nation.id.equals(entry.getKey()) || nation.id.equals(entry.getValue()));
        NationStore.ensureWarState(war);
        war.defenseCalls.entrySet().removeIf(entry -> nation.id.equals(entry.getKey()) || nation.id.equals(entry.getValue().caller));
        this.clearCapturedTrackingFor(war, nation);
        war.coreClaimsByNation.remove(nation.id);
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
        NationStore.ensureWarState(war);
        int count = 0;
        for (String claimId : war.originalClaimOwners.keySet()) {
            Nation captor = this.captorOfClaim(war, claimId);
            String original = war.originalClaimOwners.get(claimId);
            if (first.id.equals(original) || second.id.equals(original)
                || captor != null && (first.id.equals(captor.id) || second.id.equals(captor.id))) {
                count++;
            }
        }
        return count;
    }

    public int capturedClaimsHeldBySide(War war, int side) {
        if (war == null || side == 0) {
            return 0;
        }
        NationStore.ensureWarCaptureMap(war);
        int count = 0;
        for (Map.Entry<String, Set<String>> entry : war.capturedClaimsByNation.entrySet()) {
            Nation captor = this.nationById(entry.getKey()).orElse(null);
            if (captor == null || this.sideOf(war, captor) != side) {
                continue;
            }
            for (String claimId : entry.getValue()) {
                if (captor.id.equals(this.state.claims.get(claimId))) {
                    ++count;
                }
            }
        }
        return count;
    }

    public int capturedClaimsHeldBySideAgainst(War war, int side, Nation originalOwner) {
        if (war == null || side == 0 || originalOwner == null) {
            return 0;
        }
        NationStore.ensureWarState(war);
        int count = 0;
        for (Map.Entry<String, Set<String>> entry : war.capturedClaimsByNation.entrySet()) {
            Nation captor = this.state.nations.get(entry.getKey());
            if (captor == null || this.sideOf(war, captor) != side) {
                continue;
            }
            for (String claimId : entry.getValue()) {
                if (captor.id.equals(this.state.claims.get(claimId)) && originalOwner.id.equals(war.originalClaimOwners.get(claimId))) {
                    count++;
                }
            }
        }
        return count;
    }

    public int returnCapturedClaimsHeldBySide(War war, int side, Nation ignoredRecipient) {
        return this.returnCapturedClaimsToOriginalOwners(war, claimId -> {
            Nation captor = this.captorOfClaim(war, claimId);
            return captor != null && this.sideOf(war, captor) == side;
        });
    }

    public int returnAllCapturedClaimsToOriginalOwners(War war) {
        return this.returnCapturedClaimsToOriginalOwners(war, claimId -> true);
    }

    public int returnCapturedClaimsInvolving(War war, Nation nation) {
        if (nation == null) {
            return 0;
        }
        return this.returnCapturedClaimsToOriginalOwners(war, claimId -> {
            Nation captor = this.captorOfClaim(war, claimId);
            return nation.id.equals(war.originalClaimOwners.get(claimId)) || captor != null && nation.id.equals(captor.id);
        });
    }

    private void returnCapturedClaimsForDeal(War war, Nation proposer, Nation receiver) {
        if (war == null || proposer == null || receiver == null || !this.areOpposingWarSides(war, proposer, receiver)) {
            return;
        }
        if (this.isPrimaryPeacePair(war, proposer, receiver)) {
            this.returnAllCapturedClaimsToOriginalOwners(war);
            return;
        }
        this.returnCapturedClaimsInvolving(war, proposer);
        this.returnCapturedClaimsInvolving(war, receiver);
    }

    private int returnCapturedClaimsToOriginalOwners(War war, java.util.function.Predicate<String> selector) {
        if (war == null) {
            return 0;
        }
        NationStore.ensureWarState(war);
        ArrayList<ClaimTransfer> transfers = new ArrayList<>();
        int returned = this.returnCapturedClaimsToOriginalOwnersState(war, selector, transfers);
        if (returned > 0) {
            this.save();
            this.syncClaimTransfers(transfers);
        }
        return returned;
    }

    private int returnCapturedClaimsToOriginalOwnersState(War war, java.util.function.Predicate<String> selector, List<ClaimTransfer> transfers) {
        int returned = 0;
        for (String claimId : new ArrayList<>(war.originalClaimOwners.keySet())) {
            if (!selector.test(claimId)) {
                continue;
            }
            Nation original = this.state.nations.get(war.originalClaimOwners.get(claimId));
            Nation current = Optional.ofNullable(this.state.claims.get(claimId)).map(this.state.nations::get).orElse(null);
            if (original != null && current != null && !original.id.equals(current.id)) {
                ClaimTransfer transfer = this.transferClaimState(claimId, original);
                if (transfer != null) {
                    transfers.add(transfer);
                    returned++;
                }
            }
            this.removeCapturedClaimState(war, claimId);
        }
        return returned;
    }

    private Nation captorOfClaim(War war, String claimId) {
        if (war == null || claimId == null) {
            return null;
        }
        NationStore.ensureWarState(war);
        for (Map.Entry<String, Set<String>> entry : war.capturedClaimsByNation.entrySet()) {
            if (entry.getValue().contains(claimId)) {
                return this.state.nations.get(entry.getKey());
            }
        }
        return null;
    }

    private void clearCapturedTrackingFor(War war, Nation nation) {
        NationStore.ensureWarState(war);
        LinkedHashSet<String> involving = new LinkedHashSet<>();
        war.capturedClaimsByNation.getOrDefault(nation.id, Set.of()).forEach(involving::add);
        war.originalClaimOwners.forEach((claimId, ownerId) -> {
            if (nation.id.equals(ownerId)) {
                involving.add(claimId);
            }
        });
        Set<String> removedClaims = war.capturedClaimsByNation.remove(nation.id);
        if (removedClaims != null) {
            war.attackerCapturedClaims.removeAll(removedClaims);
        }
        war.capturedClaimsByNation.values().forEach(claims -> claims.removeIf(claimId -> nation.id.equals(this.state.claims.get(claimId))));
        involving.forEach(war.originalClaimOwners::remove);
        war.capturedClaimsByNation.entrySet().removeIf(entry -> ((Set)entry.getValue()).isEmpty());
        LinkedHashSet stillTracked = new LinkedHashSet();
        war.capturedClaimsByNation.values().forEach(stillTracked::addAll);
        war.attackerCapturedClaims.removeIf(claimId -> !stillTracked.contains(claimId));
    }

    public boolean addWarJoinRequest(War war, Nation requester, Nation sponsor) {
        if (war == null || requester == null || sponsor == null || !war.active || war.independenceWar
            || this.sideOf(war, requester) != 0 || this.sideOf(war, sponsor) == 0) {
            return false;
        }
        NationStore.ensureWarState(war);
        if (war.defenseCalls.containsKey(requester.id)
            || this.wouldJoinAgainstProtectedRelationship(war, requester, this.sideOf(war, sponsor))
            || this.hasConflictingWarRelationship(war, requester)) {
            return false;
        }
        war.joinRequests.put(requester.id, sponsor.id);
        this.save();
        return true;
    }

    public boolean acceptWarJoinRequest(War war, Nation requester, Nation acceptor) {
        if (war == null || requester == null || acceptor == null || !war.active || war.independenceWar) {
            return false;
        }
        String sponsorId = war.joinRequests.get(requester.id);
        int acceptingSide = this.sideOf(war, acceptor);
        if (sponsorId == null || acceptingSide == 0 || acceptingSide != this.sideOf(war, this.nationById(sponsorId).orElse(null))
            || this.wouldJoinAgainstProtectedRelationship(war, requester, acceptingSide)
            || this.hasConflictingWarRelationship(war, requester)) {
            return false;
        }
        if (this.sideOf(war, acceptor) > 0) {
            war.attackerSide.add(requester.id);
        } else {
            war.defenderSide.add(requester.id);
        }
        war.joinRequests.remove(requester.id);
        this.snapshotWarCores(war, requester);
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

    public boolean addWarDefenseCall(War war, Nation ally, Nation caller, String kind) {
        int callerSide = this.sideOf(war, caller);
        if (war == null || ally == null || caller == null || !war.active || war.independenceWar
            || this.sideOf(war, ally) != 0 || callerSide == 0
            || this.wouldJoinAgainstProtectedRelationship(war, ally, callerSide)
            || this.hasConflictingWarRelationship(war, ally)) {
            return false;
        }
        NationStore.ensureWarState(war);
        DefenseCall call = new DefenseCall();
        call.caller = caller.id;
        call.kind = "guarantee".equals(kind) ? "guarantee" : "alliance";
        if (war.defenseCalls.putIfAbsent(ally.id, call) != null) {
            return false;
        }
        this.save();
        return true;
    }

    public boolean acceptWarDefenseCall(War war, Nation ally, Nation caller) {
        if (war == null || ally == null || caller == null || !war.active || war.independenceWar) {
            return false;
        }
        NationStore.ensureWarState(war);
        DefenseCall call = war.defenseCalls.get(ally.id);
        int callerSide = this.sideOf(war, caller);
        if (call == null || !caller.id.equals(call.caller) || callerSide == 0 || !this.defenseCallEnabled(call)
            || this.wouldJoinAgainstProtectedRelationship(war, ally, callerSide)
            || this.hasConflictingWarRelationship(war, ally)) {
            return false;
        }
        if (callerSide > 0) {
            war.attackerSide.add(ally.id);
        } else {
            war.defenderSide.add(ally.id);
        }
        war.defenseCalls.remove(ally.id);
        this.snapshotWarCores(war, ally);
        this.save();
        return true;
    }

    public boolean rejectWarDefenseCall(War war, Nation ally, Nation caller) {
        if (war == null || ally == null || caller == null || !war.active) {
            return false;
        }
        NationStore.ensureWarState(war);
        DefenseCall call = war.defenseCalls.get(ally.id);
        if (call == null || !caller.id.equals(call.caller) || !this.defenseCallEnabled(call)) {
            return false;
        }
        war.defenseCalls.remove(ally.id);
        this.save();
        return true;
    }

    public boolean clearDisabledDefenseCalls() {
        boolean changed = false;
        for (War war : this.state.wars.values()) {
            NationStore.ensureWarState(war);
            changed |= war.defenseCalls.entrySet().removeIf(entry -> !this.defenseCallEnabled(entry.getValue()));
        }
        if (changed) {
            this.save();
        }
        return changed;
    }

    private boolean defenseCallEnabled(DefenseCall call) {
        return call != null && ("guarantee".equals(call.kind) ? NationWarsConfig.get().guarantees : NationWarsConfig.get().factions);
    }

    private boolean wouldJoinAgainstProtectedRelationship(War war, Nation joiner, int joiningSide) {
        if (war == null || joiner == null || joiningSide == 0) {
            return true;
        }
        Set<String> opponents = joiningSide > 0 ? war.defenderSide : war.attackerSide;
        return opponents.stream().map(this.state.nations::get).filter(Objects::nonNull).anyMatch(opponent ->
            this.activeTruce(joiner, opponent).isPresent() || this.sameAlliance(joiner, opponent)
                || this.guarantees(joiner, opponent) || this.guarantees(opponent, joiner));
    }

    private boolean hasConflictingWarRelationship(War targetWar, Nation joiner) {
        if (targetWar == null || joiner == null) {
            return true;
        }
        LinkedHashSet<String> targetParticipants = new LinkedHashSet<>(targetWar.attackerSide);
        targetParticipants.addAll(targetWar.defenderSide);
        for (War otherWar : this.state.wars.values()) {
            if (otherWar == targetWar || !otherWar.active || !this.isWarParticipant(otherWar, joiner)) {
                continue;
            }
            boolean sharedParticipant = targetParticipants.stream().map(this.state.nations::get).filter(Objects::nonNull)
                .anyMatch(participant -> this.isWarParticipant(otherWar, participant));
            if (sharedParticipant) {
                return true;
            }
        }
        return false;
    }

    public boolean leaveWarSafely(War war, Nation nation) {
        if (war == null || nation == null || !war.active || this.sideOf(war, nation) == 0) {
            return false;
        }
        if (nation.id.equals(war.attacker) || nation.id.equals(war.defender)) {
            this.returnAllCapturedClaimsToOriginalOwners(war);
            this.endWar(war);
            return true;
        }
        this.returnCapturedClaimsInvolving(war, nation);
        return this.removeWarParticipant(war, nation);
    }

    public Collection<Alliance> alliances() {
        return this.state.alliances.values();
    }

    public Set<String> guarantorIdsOf(Nation target) {
        if (target == null) {
            return Set.of();
        }
        return Set.copyOf(this.state.guarantees.getOrDefault(target.id, Set.of()));
    }

    public List<Nation> guarantorsOf(Nation target) {
        return this.guarantorIdsOf(target).stream().map(this.state.nations::get).filter(Objects::nonNull).sorted(Comparator.comparing(nation -> nation.name.toLowerCase(Locale.ROOT))).toList();
    }

    public boolean guarantees(Nation guarantor, Nation target) {
        return NationWarsConfig.get().guarantees && guarantor != null && target != null
            && this.state.guarantees.getOrDefault(target.id, Set.of()).contains(guarantor.id);
    }

    public boolean addGuarantee(Nation guarantor, Nation target) {
        if (guarantor == null || target == null || guarantor.id.equals(target.id) || this.sameAlliance(guarantor, target)
            || this.activeWarForCapture(guarantor, target).isPresent() || this.activeWarForCapture(target, guarantor).isPresent()) {
            return false;
        }
        boolean added = this.state.guarantees.computeIfAbsent(target.id, ignored -> new LinkedHashSet<>()).add(guarantor.id);
        if (added) {
            this.save();
        }
        return added;
    }

    public boolean removeGuarantee(Nation guarantor, Nation target) {
        if (guarantor == null || target == null) {
            return false;
        }
        Set<String> guarantors = this.state.guarantees.get(target.id);
        boolean removed = guarantors != null && guarantors.remove(guarantor.id);
        if (guarantors != null && guarantors.isEmpty()) {
            this.state.guarantees.remove(target.id);
        }
        if (removed) {
            this.save();
        }
        return removed;
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
        if (alliance == null || inviter == null || invited == null || !alliance.leader.equals(inviter.id)
            || this.allianceOf(invited).isPresent() || this.hasAllianceWarConflict(alliance, invited)) {
            return false;
        }
        alliance.invites.add(invited.id);
        this.save();
        return true;
    }

    public boolean acceptAllianceInvite(Alliance alliance, Nation nation) {
        if (alliance == null || nation == null || this.allianceOf(nation).isPresent() || this.hasAllianceWarConflict(alliance, nation)
            || !alliance.invites.remove(nation.id)) {
            return false;
        }
        alliance.members.add(nation.id);
        this.save();
        return true;
    }

    private boolean hasAllianceWarConflict(Alliance alliance, Nation candidate) {
        return alliance != null && candidate != null && alliance.members.stream().map(this.state.nations::get).filter(Objects::nonNull)
            .anyMatch(member -> this.activeWarForCapture(candidate, member).isPresent()
                || this.activeWarForCapture(member, candidate).isPresent());
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

    public Optional<Truce> activeTruce(Nation first, Nation second) {
        if (first == null || second == null) {
            return Optional.empty();
        }
        Truce truce = this.state.truces.get(NationStore.pairKey(first.id, second.id));
        return truce != null && truce.expiresTick > NationStore.persistentNow() ? Optional.of(truce) : Optional.empty();
    }

    public Optional<TruceOffer> truceOfferBetween(Nation first, Nation second) {
        if (first == null || second == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(this.state.truceOffers.get(NationStore.pairKey(first.id, second.id)));
    }

    public boolean offerTruce(Nation proposer, Nation receiver, boolean renewal) {
        if (proposer == null || receiver == null || proposer.id.equals(receiver.id)
            || this.activeWarForCapture(proposer, receiver).isPresent() || this.activeWarForCapture(receiver, proposer).isPresent()
            || this.warBetween(proposer, receiver).isPresent() || this.hasDefenseCallAgainst(proposer, receiver)) {
            return false;
        }
        Optional<Truce> active = this.activeTruce(proposer, receiver);
        if (renewal) {
            if (active.isEmpty() || active.get().expiresTick - NationStore.persistentNow() > 60L * 20L) {
                return false;
            }
        } else if (active.isPresent()) {
            return false;
        }
        String key = NationStore.pairKey(proposer.id, receiver.id);
        if (this.state.truceOffers.containsKey(key)) {
            return false;
        }
        TruceOffer offer = new TruceOffer();
        offer.proposer = proposer.id;
        offer.receiver = receiver.id;
        offer.renewal = renewal;
        offer.createdTick = NationStore.persistentNow();
        this.state.truceOffers.put(key, offer);
        this.save();
        return true;
    }

    public Optional<Truce> acceptTruce(Nation receiver, Nation proposer) {
        if (receiver == null || proposer == null || receiver.id.equals(proposer.id)
            || this.activeWarForCapture(receiver, proposer).isPresent() || this.activeWarForCapture(proposer, receiver).isPresent()
            || this.warBetween(receiver, proposer).isPresent() || this.hasDefenseCallAgainst(receiver, proposer)) {
            return Optional.empty();
        }
        String key = NationStore.pairKey(receiver.id, proposer.id);
        TruceOffer offer = this.state.truceOffers.get(key);
        long now = NationStore.persistentNow();
        if (offer == null || !proposer.id.equals(offer.proposer) || !receiver.id.equals(offer.receiver)
            || now - offer.createdTick > 300L * 20L) {
            return Optional.empty();
        }
        Truce existing = this.state.truces.get(key);
        if (!offer.renewal && existing != null && existing.expiresTick > now) {
            return Optional.empty();
        }
        if (offer.renewal && (existing == null || existing.expiresTick <= now || existing.expiresTick - now > 60L * 20L)) {
            return Optional.empty();
        }
        Truce truce = existing == null ? new Truce() : existing;
        String[] pair = key.split("->", 2);
        truce.first = pair[0];
        truce.second = pair[1];
        truce.expiresTick = offer.renewal ? Math.max(now, truce.expiresTick) + 1200L * 20L : now + 1200L * 20L;
        truce.expiryWarningSent = false;
        this.state.truces.put(key, truce);
        this.state.truceOffers.remove(key);
        this.save();
        return Optional.of(truce);
    }

    private boolean hasDefenseCallAgainst(Nation first, Nation second) {
        if (first == null || second == null) {
            return false;
        }
        for (War war : this.state.wars.values()) {
            if (!war.active) {
                continue;
            }
            NationStore.ensureWarState(war);
            DefenseCall firstCall = war.defenseCalls.get(first.id);
            if (firstCall != null) {
                Nation caller = this.state.nations.get(firstCall.caller);
                int joiningSide = this.sideOf(war, caller);
                if (joiningSide != 0 && this.sideOf(war, second) == -joiningSide) {
                    return true;
                }
            }
            DefenseCall secondCall = war.defenseCalls.get(second.id);
            if (secondCall != null) {
                Nation caller = this.state.nations.get(secondCall.caller);
                int joiningSide = this.sideOf(war, caller);
                if (joiningSide != 0 && this.sideOf(war, first) == -joiningSide) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean rejectTruce(Nation receiver, Nation proposer) {
        if (receiver == null || proposer == null) {
            return false;
        }
        String key = NationStore.pairKey(receiver.id, proposer.id);
        TruceOffer offer = this.state.truceOffers.get(key);
        if (offer == null || !proposer.id.equals(offer.proposer) || !receiver.id.equals(offer.receiver)) {
            return false;
        }
        this.state.truceOffers.remove(key);
        this.save();
        return true;
    }

    public List<Truce> trucesOf(Nation nation) {
        if (nation == null) {
            return List.of();
        }
        return this.state.truces.values().stream().filter(truce -> nation.id.equals(truce.first) || nation.id.equals(truce.second))
            .filter(truce -> truce.expiresTick > NationStore.persistentNow()).sorted(Comparator.comparingLong(truce -> truce.expiresTick)).toList();
    }

    public boolean tickTruces(MinecraftServer server, long now) {
        boolean changed = false;
        for (Map.Entry<String, Truce> entry : new ArrayList<>(this.state.truces.entrySet())) {
            Truce truce = entry.getValue();
            Nation first = this.state.nations.get(truce.first);
            Nation second = this.state.nations.get(truce.second);
            if (first == null || second == null || truce.expiresTick <= now) {
                this.state.truces.remove(entry.getKey());
                this.state.truceOffers.remove(entry.getKey());
                if (first != null && second != null) {
                    this.notifyNation(server, first, NationText.message("nationwars.truce.expired", second.name));
                    this.notifyNation(server, second, NationText.message("nationwars.truce.expired", first.name));
                }
                changed = true;
                continue;
            }
            if (!truce.expiryWarningSent && truce.expiresTick - now <= 60L * 20L) {
                truce.expiryWarningSent = true;
                this.notifyNation(server, first, NationText.message("nationwars.truce.warning", second.name, 60, second.id));
                this.notifyNation(server, second, NationText.message("nationwars.truce.warning", first.name, 60, first.id));
                changed = true;
            }
        }
        for (Map.Entry<String, TruceOffer> entry : new ArrayList<>(this.state.truceOffers.entrySet())) {
            TruceOffer offer = entry.getValue();
            Truce active = this.state.truces.get(entry.getKey());
            long expires = offer.renewal && active != null ? Math.min(active.expiresTick, offer.createdTick + 300L * 20L) : offer.createdTick + 300L * 20L;
            if (expires > now) {
                continue;
            }
            this.state.truceOffers.remove(entry.getKey());
            changed = true;
        }
        if (changed) {
            this.save();
        }
        return changed;
    }

    public List<IncomeTransfer> incomeTransfers() {
        return List.copyOf(this.state.incomeTransfers.values());
    }

    public double incomeAgreementFrom(Nation payer, Nation receiver) {
        if (payer == null || receiver == null) {
            return 0.0;
        }
        IncomeTransfer transfer = this.state.incomeTransfers.get(NationStore.incomeTransferKey(payer.id, receiver.id));
        return transfer == null ? 0.0 : transfer.amountPerMinute;
    }

    private void setIncomeAgreement(Nation proposer, Nation receiver, double offered, double requested) {
        double next = NationStore.incomeAgreementNet(offered, requested);
        this.state.incomeTransfers.remove(NationStore.incomeTransferKey(proposer.id, receiver.id));
        this.state.incomeTransfers.remove(NationStore.incomeTransferKey(receiver.id, proposer.id));
        if (next == 0.0) {
            return;
        }
        IncomeTransfer transfer = new IncomeTransfer();
        transfer.payer = next > 0.0 ? proposer.id : receiver.id;
        transfer.receiver = next > 0.0 ? receiver.id : proposer.id;
        transfer.amountPerMinute = Math.abs(next);
        this.state.incomeTransfers.put(NationStore.incomeTransferKey(transfer.payer, transfer.receiver), transfer);
    }

    static double incomeAgreementNet(double offered, double requested) {
        return NationRules.incomeAgreementNet(offered, requested);
    }

    static Nation peaceDemandSource(boolean viewingIncoming, Nation ownNation, Nation otherNation) {
        return NationRules.incomingDemandUsesOwnNation(viewingIncoming) ? ownNation : otherNation;
    }

    static Nation peaceOfferSource(boolean viewingIncoming, Nation ownNation, Nation otherNation) {
        return viewingIncoming ? otherNation : ownNation;
    }

    static boolean isEmptyTradeOffer(TradeOffer offer) {
        return NationRules.isTradeEmpty(offer.requestedClaims.size(), offer.offeredClaims.size(), offer.requestedMoney,
            offer.offeredMoney, offer.requestedIncomePerMinute, offer.offeredIncomePerMinute, offer.incomeTermsSpecified);
    }

    static TradeOffer flipTradeOfferForReceiver(TradeOffer source) {
        NationRules.TradeTerms terms = NationRules.flipTradeTerms(new NationRules.TradeTerms(source.requestedMoney,
            source.offeredMoney, source.requestedIncomePerMinute, source.offeredIncomePerMinute, source.incomeTermsSpecified));
        TradeOffer copy = new TradeOffer();
        copy.id = source.id;
        copy.proposer = source.receiver;
        copy.receiver = source.proposer;
        copy.requestedClaims.addAll(source.offeredClaims);
        copy.offeredClaims.addAll(source.requestedClaims);
        copy.requestedMoney = terms.requestedMoney();
        copy.offeredMoney = terms.offeredMoney();
        copy.requestedIncomePerMinute = terms.requestedIncome();
        copy.offeredIncomePerMinute = terms.offeredIncome();
        copy.incomeTermsSpecified = terms.incomeTermsSpecified();
        copy.createdTick = source.createdTick;
        return copy;
    }

    public Optional<PuppetRelation> puppetRelation(Nation puppet) {
        return puppet == null ? Optional.empty() : PuppetService.relation(this.state, puppet.id);
    }

    public Optional<Nation> masterOf(Nation puppet) {
        return this.puppetRelation(puppet).flatMap(relation -> this.nationById(relation.master));
    }

    public List<Nation> puppetsOf(Nation master) {
        if (master == null) {
            return List.of();
        }
        return PuppetService.puppets(this.state, master.id).stream()
            .map(this.state.nations::get)
            .filter(Objects::nonNull)
            .sorted(Comparator.comparing(nation -> nation.name, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    public boolean isPuppet(Nation nation) {
        return this.puppetRelation(nation).isPresent();
    }

    public boolean isMasterOf(Nation master, Nation puppet) {
        return master != null && puppet != null
            && this.puppetRelation(puppet).map(relation -> master.id.equals(relation.master)).orElse(false);
    }

    public boolean canEstablishPuppet(Nation master, Nation puppet) {
        return NationWarsConfig.get().puppets && master != null && puppet != null
            && PuppetService.canEstablish(this.state, master.id, puppet.id);
    }

    public boolean proposePuppet(Nation master, Nation puppet) {
        if (!this.canEstablishPuppet(master, puppet)
            || !this.activeWarsOf(master).isEmpty() || !this.activeWarsOf(puppet).isEmpty()) {
            return false;
        }
        boolean added = PuppetService.addProposal(this.state, master.id, puppet.id, NationStore.persistentNow());
        if (added) {
            this.save();
        }
        return added;
    }

    public boolean hasPuppetProposal(Nation master, Nation puppet) {
        return master != null && puppet != null && PuppetService.hasProposal(this.state, master.id, puppet.id);
    }

    public PuppetEstablishResult acceptPuppetProposal(Nation master, Nation puppet) {
        if (!this.hasPuppetProposal(master, puppet) || !this.canEstablishPuppet(master, puppet)
            || !this.activeWarsOf(master).isEmpty() || !this.activeWarsOf(puppet).isEmpty()) {
            return PuppetEstablishResult.failed();
        }
        PuppetService.removeProposal(this.state, master.id, puppet.id);
        return this.establishPuppet(master, puppet);
    }

    public boolean rejectPuppetProposal(Nation master, Nation puppet) {
        if (master == null || puppet == null) {
            return false;
        }
        boolean removed = PuppetService.removeProposal(this.state, master.id, puppet.id);
        if (removed) {
            this.save();
        }
        return removed;
    }

    public PuppetEstablishResult establishPuppet(Nation master, Nation puppet) {
        if (!this.canEstablishPuppet(master, puppet)) {
            return PuppetEstablishResult.failed();
        }
        PuppetService.EstablishResult result = PuppetService.establish(this.state, master.id, puppet.id);
        if (!result.established()) {
            return PuppetEstablishResult.failed();
        }
        List<Nation> released = result.releasedPuppets().stream()
            .map(this.state.nations::get)
            .filter(Objects::nonNull)
            .toList();
        this.save();
        for (Nation releasedNation : released) {
            this.notifyNation(this.server, releasedNation,
                NationText.message("nationwars.puppet.released.master_subjugated", puppet.name));
        }
        return new PuppetEstablishResult(true, result.relation(), released);
    }

    public boolean releasePuppet(Nation master, Nation puppet) {
        PuppetRelation relation = this.puppetRelation(puppet).orElse(null);
        if (!NationWarsConfig.get().puppets || master == null || puppet == null || relation == null
            || this.pointsFrozen(relation)
            || !PuppetService.release(this.state, master.id, puppet.id)) {
            return false;
        }
        this.save();
        return true;
    }

    public PuppetActionResult peacefullyLiberatePuppet(Nation puppet) {
        PuppetRelation relation = this.puppetRelation(puppet).orElse(null);
        if (!NationWarsConfig.get().puppets || relation == null) {
            return PuppetActionResult.failure(PuppetActionStatus.NOT_A_PUPPET, relation);
        }
        if (this.pointsFrozen(relation)) {
            return PuppetActionResult.failure(PuppetActionStatus.FROZEN, relation);
        }
        if (!PuppetRules.canPeacefullyLiberate(relation.independencePoints)) {
            return PuppetActionResult.failure(PuppetActionStatus.POINTS_TOO_LOW, relation);
        }
        PuppetService.release(this.state, relation.master, relation.puppet);
        this.save();
        return PuppetActionResult.success(relation);
    }

    public PuppetActionResult agitatePuppet(Nation puppet) {
        PuppetRelation relation = this.puppetRelation(puppet).orElse(null);
        if (!NationWarsConfig.get().puppets || relation == null) {
            return PuppetActionResult.failure(PuppetActionStatus.NOT_A_PUPPET, relation);
        }
        if (this.pointsFrozen(relation)) {
            return PuppetActionResult.failure(PuppetActionStatus.FROZEN, relation);
        }
        long now = NationStore.persistentNow();
        if (!PuppetRules.cooldownReady(now, relation.agitateCooldownUntil)) {
            return new PuppetActionResult(PuppetActionStatus.COOLDOWN, relation.independencePoints,
                relation.lostIndependenceWars, relation.agitateCooldownUntil);
        }
        relation.independencePoints = PuppetRules.agitate(relation.independencePoints, false);
        relation.agitateCooldownUntil = PuppetRules.agitatePacifyCooldownUntil(now);
        this.save();
        return PuppetActionResult.success(relation);
    }

    public PuppetActionResult pacifyPuppet(Nation master, Nation puppet) {
        PuppetRelation relation = this.puppetRelation(puppet).orElse(null);
        if (!NationWarsConfig.get().puppets || relation == null || master == null || !master.id.equals(relation.master)) {
            return PuppetActionResult.failure(PuppetActionStatus.NOT_MASTER, relation);
        }
        if (this.pointsFrozen(relation)) {
            return PuppetActionResult.failure(PuppetActionStatus.FROZEN, relation);
        }
        long now = NationStore.persistentNow();
        if (!PuppetRules.cooldownReady(now, relation.pacifyCooldownUntil)) {
            return new PuppetActionResult(PuppetActionStatus.COOLDOWN, relation.independencePoints,
                relation.lostIndependenceWars, relation.pacifyCooldownUntil);
        }
        relation.independencePoints = PuppetRules.pacify(relation.independencePoints, false);
        relation.pacifyCooldownUntil = PuppetRules.agitatePacifyCooldownUntil(now);
        this.save();
        return PuppetActionResult.success(relation);
    }

    public boolean canPuppetClaim(Nation nation) {
        if (!NationWarsConfig.get().puppets) {
            return true;
        }
        return this.puppetRelation(nation)
            .map(relation -> PuppetRules.canClaim(relation.independencePoints))
            .orElse(true);
    }

    public boolean pointsFrozen(PuppetRelation relation) {
        if (relation == null) {
            return false;
        }
        return this.state.wars.values().stream().anyMatch(war -> war.active && war.independenceWar
            && relation.puppet.equals(war.independencePuppet));
    }

    public PuppetWarResult startIndependenceWar(Nation puppet) {
        PuppetRelation relation = this.puppetRelation(puppet).orElse(null);
        Nation master = relation == null ? null : this.state.nations.get(relation.master);
        if (!NationWarsConfig.get().puppets || relation == null || master == null) {
            return new PuppetWarResult(PuppetActionStatus.NOT_A_PUPPET, null);
        }
        if (relation.lostIndependenceWars >= PuppetRules.ANNEX_AFTER_LOST_WARS) {
            return new PuppetWarResult(PuppetActionStatus.MAXIMUM_WARS_LOST, null);
        }
        if (!PuppetRules.canStartIndependenceWar(relation.independencePoints)) {
            return new PuppetWarResult(PuppetActionStatus.POINTS_TOO_LOW, null);
        }
        if (!this.activeWarsOf(puppet).isEmpty() || !this.activeWarsOf(master).isEmpty()) {
            return new PuppetWarResult(PuppetActionStatus.ACTIVE_WAR, null);
        }
        String key = NationStore.warKey(puppet, master);
        War war = new War();
        war.id = key;
        war.attacker = puppet.id;
        war.defender = master.id;
        war.active = true;
        war.justificationCompleteTick = NationStore.persistentNow();
        war.defenderStartingClaims = this.claimsOf(master).size();
        war.independenceWar = true;
        war.independencePuppet = puppet.id;
        NationStore.ensureWarSides(war);
        NationStore.ensureWarState(war);
        this.snapshotWarCores(war, puppet);
        this.snapshotWarCores(war, master);
        this.state.wars.put(key, war);
        this.save();
        return new PuppetWarResult(PuppetActionStatus.SUCCESS, war);
    }

    public boolean isIndependenceWar(War war) {
        return war != null && war.active && war.independenceWar && war.independencePuppet != null
            && !war.independencePuppet.isBlank();
    }

    public IndependenceResolution resolveIndependenceWar(War war, boolean puppetWon) {
        if (!this.isCurrentActiveWar(war) || !this.isIndependenceWar(war)) {
            return IndependenceResolution.failed();
        }
        Nation puppet = this.state.nations.get(war.independencePuppet);
        PuppetRelation relation = puppet == null ? null : this.state.puppetRelations.get(puppet.id);
        if (puppet == null || relation == null) {
            return IndependenceResolution.failed();
        }
        int returnedClaims = this.returnAllCapturedClaimsToOriginalOwners(war);
        if (puppetWon) {
            PuppetService.release(this.state, relation.master, relation.puppet);
        } else {
            relation.independencePoints = PuppetRules.adjustPoints(relation.independencePoints, -50, false);
            relation.lostIndependenceWars = Math.max(0, relation.lostIndependenceWars) + 1;
        }
        int points = relation.independencePoints;
        int losses = relation.lostIndependenceWars;
        NationStore.deactivateWarState(war);
        this.state.wars.remove(war.id, war);
        this.save();
        return new IndependenceResolution(true, puppetWon, points, losses, returnedClaims);
    }

    public PuppetAnnexResult annexPuppet(Nation master, Nation puppet) {
        PuppetRelation relation = this.puppetRelation(puppet).orElse(null);
        if (!NationWarsConfig.get().puppets || master == null || puppet == null || relation == null
            || !master.id.equals(relation.master)) {
            return PuppetAnnexResult.failure(PuppetActionStatus.NOT_MASTER);
        }
        if (this.pointsFrozen(relation) || !this.activeWarsOf(puppet).isEmpty() || !this.activeWarsOf(master).isEmpty()) {
            return PuppetAnnexResult.failure(PuppetActionStatus.ACTIVE_WAR);
        }
        if (!PuppetRules.canAnnex(relation.independencePoints, relation.lostIndependenceWars)) {
            return PuppetAnnexResult.failure(PuppetActionStatus.NOT_ANNEXABLE);
        }
        List<Nation> released = PuppetService.releaseAll(this.state, puppet.id).stream()
            .map(this.state.nations::get).filter(Objects::nonNull).toList();
        ArrayList<ClaimTransfer> transfers = new ArrayList<>();
        int transferredClaims = 0;
        for (String claimId : new ArrayList<>(this.claimsOf(puppet))) {
            ClaimTransfer transfer = this.transferClaimState(claimId, master);
            if (transfer != null) {
                transfers.add(transfer);
                master.coreClaims.add(claimId);
                transferredClaims++;
            }
        }
        double treasury = NationStore.roundMoney(Math.max(0.0, puppet.balance));
        master.balance = NationStore.roundMoney(master.balance + treasury);
        int transferredMembers = 0;
        for (String member : new ArrayList<>(puppet.members)) {
            if (master.members.add(member)) {
                transferredMembers++;
            }
            this.state.playerNation.put(member, master.id);
        }
        PuppetService.release(this.state, master.id, puppet.id);
        this.deleteNation(puppet);
        this.syncClaimTransfers(transfers);
        OpacClaimsBridge.syncAll(this.server, this);
        for (Nation releasedNation : released) {
            this.notifyNation(this.server, releasedNation,
                NationText.message("nationwars.puppet.released.master_annexed", puppet.name));
        }
        return new PuppetAnnexResult(PuppetActionStatus.SUCCESS, transferredClaims, transferredMembers, treasury, released);
    }

    public void recordAcceptedPuppetTrade(TradeOffer offer) {
        if (!NationWarsConfig.get().puppets || offer == null) {
            return;
        }
        Nation proposer = this.state.nations.get(offer.proposer);
        Nation receiver = this.state.nations.get(offer.receiver);
        if (proposer == null || receiver == null) {
            return;
        }
        this.recordAcceptedPuppetTradeFor(proposer, receiver, false, offer);
        this.recordAcceptedPuppetTradeFor(receiver, proposer, true, offer);
    }

    private void recordAcceptedPuppetTradeFor(Nation puppet, Nation other, boolean puppetIsReceiver,
                                               TradeOffer offer) {
        PuppetRelation relation = this.state.puppetRelations.get(puppet.id);
        if (relation == null || this.pointsFrozen(relation)) {
            return;
        }
        long now = NationStore.persistentNow();
        if (!PuppetRules.cooldownReady(now, relation.tradePointCooldownUntil)) {
            return;
        }
        int delta;
        if (!other.id.equals(relation.master)) {
            delta = PuppetRules.FOREIGN_TRADE_DELTA;
        } else {
            double receivedMoney = puppetIsReceiver ? offer.offeredMoney : offer.requestedMoney;
            int receivedClaims = puppetIsReceiver ? offer.offeredClaims.size() : offer.requestedClaims.size();
            double receivedIncome = puppetIsReceiver ? offer.offeredIncomePerMinute : offer.requestedIncomePerMinute;
            double givenMoney = puppetIsReceiver ? offer.requestedMoney : offer.offeredMoney;
            int givenClaims = puppetIsReceiver ? offer.requestedClaims.size() : offer.offeredClaims.size();
            double givenIncome = puppetIsReceiver ? offer.requestedIncomePerMinute : offer.offeredIncomePerMinute;
            if (!PuppetRules.isPuppetFavouredMasterTrade(receivedMoney, receivedClaims, receivedIncome,
                givenMoney, givenClaims, givenIncome)) {
                return;
            }
            delta = PuppetRules.MASTER_FAVOURED_TRADE_DELTA;
        }
        relation.independencePoints = PuppetRules.adjustPoints(relation.independencePoints, delta, false);
        relation.tradePointCooldownUntil = PuppetRules.tradePointCooldownUntil(now);
    }

    public boolean recordRejectedMasterTrade(Nation puppet, Nation master) {
        PuppetRelation relation = this.puppetRelation(puppet).orElse(null);
        if (!NationWarsConfig.get().puppets || relation == null || master == null
            || !master.id.equals(relation.master) || this.pointsFrozen(relation)) {
            return false;
        }
        long now = NationStore.persistentNow();
        if (!PuppetRules.cooldownReady(now, relation.tradePointCooldownUntil)) {
            return false;
        }
        relation.independencePoints = PuppetRules.applyForeignTrade(relation.independencePoints, false);
        relation.tradePointCooldownUntil = PuppetRules.tradePointCooldownUntil(now);
        this.save();
        return true;
    }

    public Optional<SpyAgency> spyAgency(Nation nation) {
        return nation == null ? Optional.empty() : Optional.ofNullable(nation.spyAgency);
    }

    public SpyAgency createSpyAgency(Nation nation) {
        if (nation.spyAgency == null) {
            nation.spyAgency = new SpyAgency();
            this.save();
        }
        return nation.spyAgency;
    }

    public SpyUnit hireSpy(Nation nation) {
        SpyAgency agency = this.createSpyAgency(nation);
        SpyUnit spy = new SpyUnit();
        spy.id = agency.spies.stream().map(unit -> unit.id).max(Integer::compareTo).orElse(0) + 1;
        agency.spies.add(spy);
        this.save();
        return spy;
    }

    public Optional<SpyUnit> spyUnit(Nation nation, int id) {
        return this.spyAgency(nation).flatMap(agency -> agency.spies.stream().filter(spy -> spy.id == id).findFirst());
    }

    public List<SpyMission> spyMissionsFor(Nation nation) {
        if (nation == null) {
            return List.of();
        }
        return this.state.spyMissions.stream().filter(mission -> nation.id.equals(mission.spyNation)).sorted(Comparator.comparingInt(mission -> mission.id)).toList();
    }

    public SpyMission createSpyMission(Nation spyNation, Nation target, SpyUnit spy, String type, List<String> chunks, long completeTick) {
        SpyMission mission = new SpyMission();
        mission.id = this.state.nextSpyMissionId++;
        mission.spyNation = spyNation.id;
        mission.target = target.id;
        mission.spyId = spy.id;
        mission.type = type;
        mission.chunks = new ArrayList<>(chunks);
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

    public SpyIntel spyIntel(Nation viewer, Nation target, boolean create) {
        if (viewer == null || target == null || viewer.spyAgency == null) {
            return null;
        }
        SpyIntel intel = viewer.spyAgency.intel.get(target.id);
        if (intel == null && create) {
            intel = new SpyIntel();
            intel.target = target.id;
            intel.name = target.name;
            intel.known.add("name");
            viewer.spyAgency.intel.put(target.id, intel);
        }
        return intel;
    }

    public boolean isClaimParalyzed(String claimId, long tick) {
        return this.state.paralyzedClaims.getOrDefault(claimId, 0L) > tick;
    }

    // Spy mission effects are deliberately persisted together by removeSpyMission's final save.
    public void paralyzeClaim(String claimId, long untilTick) {
        this.state.paralyzedClaims.put(claimId, untilTick);
    }

    public boolean isRaidActive(String claimId, long tick) {
        return this.state.raidedClaims.getOrDefault(claimId, 0L) > tick;
    }

    public void raidClaim(String claimId, long untilTick) {
        this.state.raidedClaims.put(claimId, untilTick);
    }

    public boolean isCounterspyDisabled(String claimId, long tick) {
        return this.state.disabledCounterspyClaims.getOrDefault(claimId, 0L) > tick;
    }

    public void disableCounterspy(String claimId, long untilTick) {
        this.state.disabledCounterspyClaims.put(claimId, untilTick);
    }

    public boolean isSpendingBlocked(Nation nation, long tick) {
        return nation != null && this.state.spendingBlocks.getOrDefault(nation.id, 0L) > tick;
    }

    public long spendingBlockedUntil(Nation nation) {
        return nation == null ? 0L : this.state.spendingBlocks.getOrDefault(nation.id, 0L);
    }

    public void blockSpending(Nation nation, long untilTick) {
        this.state.spendingBlocks.put(nation.id, untilTick);
    }

    public boolean clearExpiredSpyEffects(long tick) {
        boolean changed = this.state.paralyzedClaims.entrySet().removeIf(entry -> entry.getValue() <= tick);
        changed |= this.state.raidedClaims.entrySet().removeIf(entry -> entry.getValue() <= tick);
        changed |= this.state.disabledCounterspyClaims.entrySet().removeIf(entry -> entry.getValue() <= tick);
        changed |= this.state.spendingBlocks.entrySet().removeIf(entry -> entry.getValue() <= tick);
        return changed;
    }

    public void notifyNation(MinecraftServer server, Nation nation, Component message) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!this.isMember(player.getUUID(), nation)) continue;
            player.sendSystemMessage(message);
        }
    }

    private void recordLostCoreClaim(Nation nation, String claimId) {
        if (nation == null || claimId == null || claimId.isBlank()) {
            return;
        }
        if (nation.lostCoreClaims == null) {
            nation.lostCoreClaims = new LinkedHashSet<String>();
        }
        boolean added = nation.lostCoreClaims.add(claimId);
        nation.lostCoreTerritory = true;
        if (added) {
            this.notifyNation(this.server, nation, NationText.message("nationwars.doctrine.romania.iron_guard_penalty"));
        }
    }

    public static String nationKey(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]", "");
    }

    public static String warKey(Nation first, Nation second) {
        return NationStore.pairKey(first.id, second.id);
    }

    public static String pairKey(String first, String second) {
        String a = first.compareTo(second) <= 0 ? first : second;
        String b = first.compareTo(second) <= 0 ? second : first;
        return a + "->" + b;
    }

    private static String incomeTransferKey(String payer, String receiver) {
        return payer + "->" + receiver;
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
        if (this.state.playerNames == null) {
            this.state.playerNames = new LinkedHashMap<String, String>();
        }
        if (this.state.players == null) {
            this.state.players = new LinkedHashMap<String, Double>();
        }
        if (this.state.formerNationMembers == null) {
            this.state.formerNationMembers = new LinkedHashMap<String, Set<String>>();
        }
        if (this.state.nationInvites == null) {
            this.state.nationInvites = new LinkedHashMap<String, Set<String>>();
        }
        if (this.state.nationInviteExpirations == null) {
            this.state.nationInviteExpirations = new LinkedHashMap<String, Map<String, Long>>();
        }
        if (this.state.claims == null) {
            this.state.claims = new LinkedHashMap<String, String>();
        }
        if (this.state.coastClaims == null) {
            this.state.coastClaims = new LinkedHashSet<String>();
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
        if (this.state.tradeOffers == null) {
            this.state.tradeOffers = new ArrayList<TradeOffer>();
        }
        if (this.state.alliances == null) {
            this.state.alliances = new LinkedHashMap<String, Alliance>();
        }
        if (this.state.truces == null) {
            this.state.truces = new LinkedHashMap<String, Truce>();
        }
        if (this.state.truceOffers == null) {
            this.state.truceOffers = new LinkedHashMap<String, TruceOffer>();
        }
        if (this.state.incomeTransfers == null) {
            this.state.incomeTransfers = new LinkedHashMap<String, IncomeTransfer>();
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
        if (this.state.puppetRelations == null) {
            this.state.puppetRelations = new LinkedHashMap<String, PuppetRelation>();
        }
        if (this.state.puppetProposals == null) {
            this.state.puppetProposals = new LinkedHashMap<String, PuppetProposal>();
        }
        if (this.state.warDeclarationRejections == null) {
            this.state.warDeclarationRejections = new LinkedHashSet<String>();
        }
        if (this.state.guarantees == null) {
            this.state.guarantees = new LinkedHashMap<String, Set<String>>();
        }
        if (this.state.paralyzedClaims == null) {
            this.state.paralyzedClaims = new LinkedHashMap<String, Long>();
        }
        if (this.state.raidedClaims == null) {
            this.state.raidedClaims = new LinkedHashMap<String, Long>();
        }
        if (this.state.disabledCounterspyClaims == null) {
            this.state.disabledCounterspyClaims = new LinkedHashMap<String, Long>();
        }
        if (this.state.spendingBlocks == null) {
            this.state.spendingBlocks = new LinkedHashMap<String, Long>();
        }
        if (this.state.rewardedBuildPositions == null) {
            this.state.rewardedBuildPositions = new LinkedHashSet<String>();
        }
        DataIntegrityService.RepairReport repairs = DataIntegrityService.repairReferences(this.state);
        if (repairs.repairedReferences() > 0) {
            NationWars.LOGGER.warn("Repaired {} invalid Nation Wars save reference(s): {}",
                repairs.repairedReferences(), String.join("; ", repairs.details()));
        }
        PuppetService.NormalizeResult puppetRepairs = PuppetService.normalize(this.state);
        if (puppetRepairs.removedRelations() > 0 || puppetRepairs.removedProposals() > 0) {
            NationWars.LOGGER.warn("Removed {} invalid puppet relation(s) and {} invalid puppet proposal(s).",
                puppetRepairs.removedRelations(), puppetRepairs.removedProposals());
        }
        for (PuppetRelation relation : this.state.puppetRelations.values()) {
            relation.agitateCooldownUntil = this.migrateDeadline(relation.agitateCooldownUntil,
                PuppetRules.AGITATE_PACIFY_COOLDOWN_TICKS);
            relation.pacifyCooldownUntil = this.migrateDeadline(relation.pacifyCooldownUntil,
                PuppetRules.AGITATE_PACIFY_COOLDOWN_TICKS);
            relation.tradePointCooldownUntil = this.migrateDeadline(relation.tradePointCooldownUntil,
                PuppetRules.TRADE_POINT_COOLDOWN_TICKS);
        }
        this.state.nations.entrySet().removeIf(entry -> entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null);
        this.state.playerNames.entrySet().removeIf(entry -> entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null || entry.getValue().isBlank());
        this.state.players.entrySet().removeIf(entry -> entry.getKey() == null || entry.getKey().isBlank());
        this.state.players.replaceAll((id, balance) -> balance == null || !Double.isFinite(balance) ? 0.0 : NationStore.roundMoney(Math.max(0.0, balance)));
        this.state.wars.entrySet().removeIf(entry -> entry.getValue() == null
            || !this.state.nations.containsKey(entry.getValue().attacker)
            || !this.state.nations.containsKey(entry.getValue().defender)
            || Objects.equals(entry.getValue().attacker, entry.getValue().defender));
        this.state.alliances.entrySet().removeIf(entry -> entry.getValue() == null || !this.state.nations.containsKey(entry.getValue().leader));
        this.state.marketListings.removeIf(listing -> listing == null || listing.id <= 0 || listing.seller == null || listing.itemTag == null || listing.itemTag.isBlank()
            || !Double.isFinite(listing.price) || listing.price <= 0.0);
        this.state.paralyzedClaims.replaceAll((id, tick) -> tick == null ? 0L : tick);
        this.state.raidedClaims.replaceAll((id, tick) -> tick == null ? 0L : tick);
        this.state.disabledCounterspyClaims.replaceAll((id, tick) -> tick == null ? 0L : tick);
        this.state.spendingBlocks.replaceAll((id, tick) -> tick == null ? 0L : tick);
        this.state.paralyzedClaims.replaceAll((id, tick) -> this.migrateDeadline(tick, 600L * 20L));
        this.state.raidedClaims.replaceAll((id, tick) -> this.migrateDeadline(tick, 300L * 20L));
        this.state.disabledCounterspyClaims.replaceAll((id, tick) -> this.migrateDeadline(tick, 600L * 20L));
        this.state.spendingBlocks.replaceAll((id, tick) -> this.migrateDeadline(tick, 600L * 20L));
        this.state.peaceCooldowns.replaceAll((id, tick) -> this.migrateDeadline(tick == null ? 0L : tick, 300L * 20L));
        this.state.spyCooldowns.replaceAll((id, tick) -> this.migrateDeadline(tick == null ? 0L : tick, 300L * 20L));
        this.state.paralyzedClaims.keySet().removeIf(id -> !NationStore.isValidClaimId(id));
        this.state.raidedClaims.keySet().removeIf(id -> !NationStore.isValidClaimId(id));
        this.state.disabledCounterspyClaims.keySet().removeIf(id -> !NationStore.isValidClaimId(id));
        this.state.spendingBlocks.keySet().removeIf(id -> !this.state.nations.containsKey(id));
        this.state.claims.entrySet().removeIf(entry -> !this.state.nations.containsKey(entry.getValue()) || !NationStore.isValidClaimId((String)entry.getKey()));
        this.state.coastClaims.removeIf(claimId -> !this.state.claims.containsKey(claimId) || !NationStore.isValidClaimId(claimId));
        this.state.playerNation.entrySet().removeIf(entry -> !this.state.nations.containsKey(entry.getValue()));
        this.state.formerNationMembers.entrySet().removeIf(entry -> !this.state.nations.containsKey(entry.getKey()));
        this.state.formerNationMembers.replaceAll((nationId, members) -> members == null ? new LinkedHashSet<>() : members);
        this.state.formerNationMembers.values().forEach(members -> members.removeIf(member -> member == null || member.isBlank()));
        this.state.formerNationMembers.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        this.state.nationInvites.entrySet().removeIf(entry -> !this.state.nations.containsKey(entry.getKey()));
        this.state.nationInvites.replaceAll((nationId, players) -> players == null ? new LinkedHashSet<>() : players);
        this.state.nationInvites.values().forEach(players -> players.removeIf(player -> player == null || player.isBlank() || this.state.playerNation.containsKey(player)));
        this.state.nationInvites.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        long invitationNow = NationStore.persistentNow();
        this.state.nationInviteExpirations.entrySet().removeIf(entry -> !this.state.nations.containsKey(entry.getKey()));
        this.state.nationInviteExpirations.replaceAll((nationId, expirations) -> expirations == null ? new LinkedHashMap<>() : expirations);
        this.state.nationInvites.forEach((nationId, players) -> {
            Map<String, Long> expirations = this.state.nationInviteExpirations.computeIfAbsent(nationId, ignored -> new LinkedHashMap<>());
            players.forEach(player -> expirations.putIfAbsent(player, invitationNow + NATION_INVITATION_SECONDS * 20L));
        });
        this.state.nationInviteExpirations.forEach((nationId, expirations) ->
            expirations.keySet().removeIf(player -> !this.state.nationInvites.getOrDefault(nationId, Set.of()).contains(player)));
        this.state.nationInviteExpirations.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        this.state.guarantees.entrySet().removeIf(entry -> !this.state.nations.containsKey(entry.getKey()));
        this.state.guarantees.replaceAll((id, guarantors) -> guarantors == null ? new LinkedHashSet<>() : guarantors);
        this.state.guarantees.values().forEach(guarantors -> guarantors.removeIf(id -> !this.state.nations.containsKey(id)));
        this.state.guarantees.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        this.state.nextSpyMissionId = Math.max(this.state.nextSpyMissionId,
            this.state.spyMissions.stream().filter(Objects::nonNull).map(mission -> mission.id).max(Integer::compareTo).orElse(0) + 1);
        this.state.wars.forEach((id, war) -> war.id = id);
        HashSet<String> assignedCaptureProvenance = new HashSet<>();
        for (War war : this.state.wars.values().stream().sorted(Comparator.comparing(value -> value.id)).toList()) {
            NationStore.ensureWarSides(war);
            NationStore.ensureWarState(war);
            if (war.independenceWar) {
                PuppetRelation relation = this.state.puppetRelations.get(war.independencePuppet);
                boolean validIndependenceWar = relation != null
                    && war.independencePuppet.equals(war.attacker)
                    && relation.master.equals(war.defender);
                if (!validIndependenceWar) {
                    NationWars.LOGGER.warn("Cleared an invalid independence-war marker from war {}.", war.id);
                    war.independenceWar = false;
                    war.independencePuppet = "";
                } else {
                    war.attackerSide.clear();
                    war.attackerSide.add(war.attacker);
                    war.defenderSide.clear();
                    war.defenderSide.add(war.defender);
                    war.joinRequests.clear();
                    war.defenseCalls.clear();
                }
            }
            war.attackerSide.removeIf(id -> !this.state.nations.containsKey(id));
            war.defenderSide.removeIf(id -> !this.state.nations.containsKey(id) || war.attackerSide.contains(id));
            war.joinRequests.entrySet().removeIf(entry -> !this.state.nations.containsKey(entry.getKey())
                || !this.state.nations.containsKey(entry.getValue()) || this.sideOf(war, this.state.nations.get(entry.getKey())) != 0
                || this.sideOf(war, this.state.nations.get(entry.getValue())) == 0);
            war.defenseCalls.entrySet().removeIf(entry -> !this.state.nations.containsKey(entry.getKey())
                || !this.state.nations.containsKey(entry.getValue().caller) || this.sideOf(war, this.state.nations.get(entry.getKey())) != 0);
            war.originalClaimOwners.entrySet().removeIf(entry -> !this.state.nations.containsKey(entry.getValue()));
            war.coreClaimsByNation.entrySet().removeIf(entry -> !this.state.nations.containsKey(entry.getKey()));
            war.justificationCompleteTick = this.migrateDeadline(war.justificationCompleteTick, 120L * 20L);
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
            for (Map.Entry<String, Set<String>> entry : war.capturedClaimsByNation.entrySet()) {
                Nation captor = this.state.nations.get(entry.getKey());
                if (captor == null) {
                    entry.getValue().clear();
                    continue;
                }
                entry.getValue().removeIf(claimId -> !captor.id.equals(this.state.claims.get(claimId))
                    || this.sideOf(war, captor) == 0 || !assignedCaptureProvenance.add(claimId));
                int captorSide = this.sideOf(war, captor);
                String inferredOriginal = captorSide > 0 ? war.defender : captorSide < 0 ? war.attacker : null;
                if (inferredOriginal != null && this.state.nations.containsKey(inferredOriginal)) {
                    entry.getValue().forEach(claimId -> war.originalClaimOwners.putIfAbsent(claimId, inferredOriginal));
                }
            }
            war.capturedClaimsByNation.entrySet().removeIf(entry -> entry.getValue().isEmpty());
            LinkedHashSet<String> trackedCapturedClaims = new LinkedHashSet<>();
            war.capturedClaimsByNation.values().forEach(trackedCapturedClaims::addAll);
            war.originalClaimOwners.entrySet().removeIf(entry -> !this.state.claims.containsKey(entry.getKey())
                || !trackedCapturedClaims.contains(entry.getKey()));
            war.attackerCapturedClaims.clear();
            war.capturedClaimsByNation.forEach((captorId, claims) -> {
                Nation captor = this.state.nations.get(captorId);
                if (captor != null && this.sideOf(war, captor) > 0) {
                    war.attackerCapturedClaims.addAll(claims);
                }
            });
            if (war.active) {
                LinkedHashSet<String> participants = new LinkedHashSet<>(war.attackerSide);
                participants.addAll(war.defenderSide);
                for (String participantId : participants) {
                    if (war.coreClaimsByNation.containsKey(participantId)) {
                        continue;
                    }
                    Nation participant = this.state.nations.get(participantId);
                    if (participant == null) {
                        continue;
                    }
                    LinkedHashSet<String> originalClaims = new LinkedHashSet<>(this.claimsOf(participant));
                    originalClaims.removeIf(claimId -> this.isClaimCapturedInOtherActiveWar(war, claimId));
                    war.originalClaimOwners.forEach((claimId, ownerId) -> {
                        if (participantId.equals(ownerId)) {
                            originalClaims.add(claimId);
                        }
                    });
                    war.coreClaimsByNation.put(participantId, originalClaims);
                }
            }
            if (war.peaceDeal == null) continue;
            NationStore.normalizePeaceDeal(war.peaceDeal);
        }
        this.state.nextListingId = Math.max(this.state.nextListingId,
            this.state.marketListings.stream().map(listing -> listing.id).max(Integer::compareTo).orElse(0) + 1);
        this.state.landPurchaseOffers.removeIf(offer -> offer == null || !this.state.nations.containsKey(offer.buyer) || !this.state.nations.containsKey(offer.seller) || !NationStore.isValidClaimId(offer.claimId) || !offer.seller.equals(this.state.claims.get(offer.claimId)) || offer.price <= 0.0);
        int nextLandPurchaseOfferId = this.state.landPurchaseOffers.stream().map(offer -> offer.id).max(Integer::compareTo).orElse(0) + 1;
        this.state.nextLandPurchaseOfferId = Math.max(this.state.nextLandPurchaseOfferId, nextLandPurchaseOfferId);
        this.state.tradeOffers.removeIf(offer -> offer == null || !this.state.nations.containsKey(offer.proposer) || !this.state.nations.containsKey(offer.receiver) || offer.proposer.equals(offer.receiver));
        for (TradeOffer offer : this.state.tradeOffers) {
            NationStore.normalizeTradeOffer(offer);
        }
        int nextTradeOfferId = this.state.tradeOffers.stream().map(offer -> offer.id).max(Integer::compareTo).orElse(0) + 1;
        this.state.nextTradeOfferId = Math.max(this.state.nextTradeOfferId, nextTradeOfferId);
        for (Nation nation : this.state.nations.values()) {
            nation.doctrine = Doctrine.byId(nation.doctrine).orElse(Doctrine.AMERICAN).id;
            nation.joinPolicy = JoinPolicy.parse(nation.joinPolicy).orElse(JoinPolicy.INVITE_ONLY).name();
            if (nation.members == null) {
                nation.members = new LinkedHashSet<String>();
            }
            if (nation.cityClaims == null) {
                nation.cityClaims = new LinkedHashSet<String>();
            }
            if (nation.usedSpecialWarLeaveIdeologies == null) {
                nation.usedSpecialWarLeaveIdeologies = new LinkedHashSet<String>();
            }
            nation.balance = Double.isFinite(nation.balance) ? NationStore.roundMoney(nation.balance) : 0.0;
            nation.freeClaimsRemaining = Math.max(0, nation.freeClaimsRemaining);
            nation.upgradeLevel = nation.doctrine() == Doctrine.AMERICAN ? 0 : Math.max(0, Math.min(4, nation.upgradeLevel));
            if (nation.coreClaims == null) {
                nation.coreClaims = new LinkedHashSet<String>();
                for (String claimId : this.claimsOf(nation)) {
                    if (!this.isCapturedClaimHeldBy(nation, claimId)) {
                        nation.coreClaims.add(claimId);
                    }
                }
            }
            if (nation.lostCoreClaims == null) {
                nation.lostCoreClaims = new LinkedHashSet<String>();
            }
            nation.coreClaims.removeIf(claimId -> !NationStore.isValidClaimId(claimId));
            nation.lostCoreClaims.removeIf(claimId -> !NationStore.isValidClaimId(claimId));
            if (nation.spyAgency != null) {
                NationStore.normalizeSpyAgency(nation.spyAgency, this.state.nations);
                for (SpyUnit spy : nation.spyAgency.spies) {
                    long maximum = "counterspy".equals(spy.status) ? 900L * 20L
                        : "traveling".equals(spy.status) || "recovering".equals(spy.status) ? 60L * 20L : 180L * 20L;
                    spy.availableTick = this.normalizeSpyDeadline(spy.availableTick, maximum);
                }
                for (SpyIntel intel : nation.spyAgency.intel.values()) {
                    if (intel.updatedTick > 0L && !PersistentTime.isPersistent(intel.updatedTick)) {
                        intel.updatedTick = NationStore.persistentNow();
                    }
                }
            }
            if (nation.lastSpecialWarLeaveTick > 0L && !PersistentTime.isPersistent(nation.lastSpecialWarLeaveTick)) {
                nation.lastSpecialWarLeaveTick = NationStore.persistentNow();
            }
            if (nation.ownerName == null || nation.ownerName.isBlank()) {
                String string = nation.ownerName = nation.owner == null || nation.owner.length() < 8 ? "unknown" : nation.owner.substring(0, 8);
            }
            if (nation.owner != null) {
                nation.members.add(nation.owner);
            }
            this.state.playerNation.forEach((playerId, nationId) -> {
                if (nation.id.equals(nationId)) {
                    nation.members.add(playerId);
                }
            });
            nation.members.removeIf(member -> member == null || member.isBlank());
            nation.cityClaims.removeIf(claimId -> !nation.id.equals(this.state.claims.get(claimId)));
            if (nation.capitalClaim != null && nation.id.equals(this.state.claims.get(nation.capitalClaim))) continue;
            nation.capitalClaim = this.claimsOf(nation).stream().findFirst().orElse("");
        }
        LinkedHashMap<String, String> normalizedPlayerNation = new LinkedHashMap<>();
        for (Nation nation : this.state.nations.values()) {
            if (nation.owner != null && !nation.owner.isBlank()) {
                normalizedPlayerNation.putIfAbsent(nation.owner, nation.id);
            }
        }
        for (Nation nation : this.state.nations.values()) {
            nation.members.removeIf(member -> {
                String assigned = normalizedPlayerNation.putIfAbsent(member, nation.id);
                return assigned != null && !assigned.equals(nation.id) && !member.equals(nation.owner);
            });
        }
        this.state.playerNation = normalizedPlayerNation;

        this.state.alliances.forEach((id, alliance) -> alliance.id = id);
        Set<String> allianceLeaders = this.state.alliances.values().stream().map(alliance -> alliance.leader).collect(java.util.stream.Collectors.toSet());
        LinkedHashSet<String> assignedAllianceMembers = new LinkedHashSet<>();
        for (Alliance alliance : this.state.alliances.values().stream().sorted(Comparator.comparing(value -> value.id)).toList()) {
            if (alliance.members == null) {
                alliance.members = new LinkedHashSet<String>();
            }
            if (alliance.invites == null) {
                alliance.invites = new LinkedHashSet<String>();
            }
            if (alliance.name == null || alliance.name.isBlank()) {
                alliance.name = alliance.id;
            }
            alliance.members.removeIf(member -> !this.state.nations.containsKey(member)
                || allianceLeaders.contains(member) && !member.equals(alliance.leader)
                || assignedAllianceMembers.contains(member));
            alliance.members.add(alliance.leader);
            assignedAllianceMembers.addAll(alliance.members);
        }
        this.state.alliances.values().forEach(alliance -> alliance.invites.removeIf(invited ->
            !this.state.nations.containsKey(invited) || assignedAllianceMembers.contains(invited)));
        this.normalizeTrucesAndIncomeTransfers();
        this.reconcileSpyMissions();
    }

    private long migrateDeadline(long value, long maximumRemainingTicks) {
        return PersistentTime.migrateDeadline(value, this.server.getTickCount(), maximumRemainingTicks, NationStore.persistentNow());
    }

    private long normalizeSpyDeadline(long value, long maximumRemainingTicks) {
        long now = NationStore.persistentNow();
        long migrated = PersistentTime.migrateDeadline(value, this.server.getTickCount(), maximumRemainingTicks, now);
        return migrated <= 0L ? migrated : Math.min(migrated, now + maximumRemainingTicks);
    }

    private void resetSpyAfterCancelledMission(SpyMission mission) {
        Nation spyNation = this.state.nations.get(mission.spyNation);
        if (spyNation == null || spyNation.spyAgency == null) {
            return;
        }
        spyNation.spyAgency.spies.stream().filter(spy -> spy.id == mission.spyId).findFirst().ifPresent(spy -> {
            spy.status = "recovering";
            spy.mission = "";
            spy.targetChunk = "";
            spy.availableTick = SpyRecovery.deadline(NationStore.persistentNow());
        });
    }

    private void reconcileSpyMissions() {
        List<SpyMission> retained = new ArrayList<SpyMission>();
        Map<String, SpyMission> missionsBySpy = new LinkedHashMap<String, SpyMission>();
        Set<Integer> missionIds = new HashSet<Integer>();
        int nextMissionId = Math.max(1, this.state.nextSpyMissionId);
        int cancelled = 0;
        for (SpyMission mission : new ArrayList<SpyMission>(this.state.spyMissions)) {
            if (mission == null) {
                cancelled++;
                continue;
            }
            mission.spyPlayer = mission.spyPlayer == null ? "" : mission.spyPlayer;
            mission.spyName = mission.spyName == null ? "" : mission.spyName;
            mission.spyNation = mission.spyNation == null ? "" : mission.spyNation;
            mission.target = mission.target == null ? "" : mission.target;
            mission.type = mission.type == null ? "" : mission.type.trim().toLowerCase(Locale.ROOT);
            mission.chunks = mission.chunks == null ? new ArrayList<String>() : new ArrayList<String>(mission.chunks);
            Nation spyNation = this.state.nations.get(mission.spyNation);
            Nation target = this.state.nations.get(mission.target);
            SpyUnit spy = spyNation == null || spyNation.spyAgency == null ? null
                : spyNation.spyAgency.spies.stream().filter(unit -> unit.id == mission.spyId).findFirst().orElse(null);
            Integer expectedChunks = SPY_MISSION_CHUNK_COUNTS.get(mission.type);
            boolean duplicateChunks = new HashSet<String>(mission.chunks).size() != mission.chunks.size();
            boolean invalidChunks = mission.chunks.stream().anyMatch(chunk -> chunk == null || !NationStore.isValidClaimId(chunk));
            boolean invalidTarget = "counterspy".equals(mission.type)
                ? !Objects.equals(mission.spyNation, mission.target)
                : Objects.equals(mission.spyNation, mission.target);
            String spyKey = NationStore.spyMissionKey(mission.spyNation, mission.spyId);
            boolean invalid = spy == null || target == null || expectedChunks == null || invalidTarget
                || mission.chunks.size() != (expectedChunks == null ? -1 : expectedChunks)
                || duplicateChunks || invalidChunks || missionsBySpy.containsKey(spyKey);
            if (invalid) {
                if (!missionsBySpy.containsKey(spyKey)) {
                    this.resetSpyAfterCancelledMission(mission);
                }
                cancelled++;
                continue;
            }
            if (mission.id <= 0 || !missionIds.add(mission.id)) {
                while (missionIds.contains(nextMissionId)) {
                    nextMissionId++;
                }
                mission.id = nextMissionId++;
                missionIds.add(mission.id);
            }
            mission.completeTick = this.normalizeSpyDeadline(mission.completeTick, 180L * 20L);
            missionsBySpy.put(spyKey, mission);
            retained.add(mission);
        }
        for (Map.Entry<String, SpyMission> entry : missionsBySpy.entrySet()) {
            SpyMission mission = entry.getValue();
            Nation nation = this.state.nations.get(mission.spyNation);
            SpyUnit spy = nation.spyAgency.spies.stream().filter(unit -> unit.id == mission.spyId).findFirst().orElseThrow();
            spy.country = mission.target;
            spy.status = "mission";
            spy.mission = mission.type;
            spy.targetChunk = mission.chunks.isEmpty() ? "" : mission.chunks.get(0);
            spy.availableTick = mission.completeTick;
        }
        int recovered = 0;
        for (Nation nation : this.state.nations.values()) {
            if (nation.spyAgency == null) {
                continue;
            }
            for (SpyUnit spy : nation.spyAgency.spies) {
                if (!"mission".equals(spy.status) || missionsBySpy.containsKey(NationStore.spyMissionKey(nation.id, spy.id))) {
                    continue;
                }
                spy.status = "recovering";
                spy.mission = "";
                spy.targetChunk = "";
                spy.availableTick = SpyRecovery.deadline(NationStore.persistentNow());
                recovered++;
            }
        }
        this.state.spyMissions.clear();
        this.state.spyMissions.addAll(retained);
        this.state.nextSpyMissionId = Math.max(nextMissionId,
            retained.stream().map(mission -> mission.id).max(Integer::compareTo).orElse(0) + 1);
        if (cancelled > 0 || recovered > 0) {
            NationWars.LOGGER.warn("Repaired spy state: cancelled {} invalid mission(s) and recovered {} orphaned spy assignment(s).", cancelled, recovered);
        }
    }

    private static String spyMissionKey(String nationId, int spyId) {
        return String.valueOf(nationId) + "#" + spyId;
    }

    private static void normalizeSpyAgency(SpyAgency agency, Map<String, Nation> nations) {
        if (agency.spies == null) {
            agency.spies = new ArrayList<SpyUnit>();
        }
        if (agency.intel == null) {
            agency.intel = new LinkedHashMap<String, SpyIntel>();
        }
        agency.spies.removeIf(Objects::isNull);
        int nextSpyId = agency.spies.stream().map(spy -> spy.id).filter(id -> id > 0).max(Integer::compareTo).orElse(0) + 1;
        Set<Integer> usedSpyIds = new HashSet<Integer>();
        for (SpyUnit spy : agency.spies) {
            if (spy.id <= 0 || !usedSpyIds.add(spy.id)) {
                while (usedSpyIds.contains(nextSpyId)) {
                    nextSpyId++;
                }
                spy.id = nextSpyId++;
                usedSpyIds.add(spy.id);
            }
            spy.status = spy.status == null ? "idle" : spy.status.trim().toLowerCase(Locale.ROOT);
            spy.country = spy.country == null ? "" : spy.country;
            spy.mission = spy.mission == null ? "" : spy.mission;
            spy.targetChunk = spy.targetChunk == null ? "" : spy.targetChunk;
            boolean validCountry = !spy.country.isBlank() && nations.containsKey(spy.country);
            if (!SPY_STATUSES.contains(spy.status)) {
                spy.status = validCountry ? "stationed" : "idle";
                spy.mission = "";
                spy.targetChunk = "";
                spy.availableTick = 0L;
            }
            if ("idle".equals(spy.status) && validCountry) {
                spy.status = "stationed";
                spy.availableTick = 0L;
            }
            if (("stationed".equals(spy.status) || "traveling".equals(spy.status)) && !validCountry) {
                spy.status = "idle";
                spy.country = "";
                spy.availableTick = 0L;
            }
            if ("counterspy".equals(spy.status) && (!validCountry || !NationStore.isValidClaimId(spy.targetChunk))) {
                spy.status = "recovering";
                spy.country = validCountry ? spy.country : "";
                spy.targetChunk = "";
                spy.availableTick = 0L;
            }
            if ("recovering".equals(spy.status) && !validCountry) {
                spy.country = "";
            }
            if ("counterspy".equals(spy.status)) {
                spy.mission = "counterspy";
            } else if (!"mission".equals(spy.status)) {
                spy.mission = "";
                spy.targetChunk = "";
            }
            if ("idle".equals(spy.status) || "stationed".equals(spy.status)) {
                spy.availableTick = 0L;
            }
        }
        agency.intel.entrySet().removeIf(entry -> !nations.containsKey(entry.getKey()) || entry.getValue() == null);
        for (Map.Entry<String, SpyIntel> entry : agency.intel.entrySet()) {
            SpyIntel intel = entry.getValue();
            intel.target = entry.getKey();
            if (intel.known == null) {
                intel.known = new LinkedHashSet<String>();
            }
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

    private static void ensureWarState(War war) {
        ensureWarCaptureMap(war);
        if (war.defenseCalls == null) {
            war.defenseCalls = new LinkedHashMap<String, DefenseCall>();
        }
        war.defenseCalls.entrySet().removeIf(entry -> entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null);
        for (DefenseCall call : war.defenseCalls.values()) {
            if (call.caller == null) {
                call.caller = "";
            }
            if (!"guarantee".equals(call.kind)) {
                call.kind = "alliance";
            }
        }
        if (war.originalClaimOwners == null) {
            war.originalClaimOwners = new LinkedHashMap<String, String>();
        }
        war.originalClaimOwners.entrySet().removeIf(entry -> !isValidClaimId(entry.getKey()) || entry.getValue() == null || entry.getValue().isBlank());
        if (war.coreClaimsByNation == null) {
            war.coreClaimsByNation = new LinkedHashMap<String, Set<String>>();
        }
        war.coreClaimsByNation.replaceAll((id, claims) -> claims == null ? new LinkedHashSet<>() : claims);
        war.coreClaimsByNation.values().forEach(claims -> claims.removeIf(claimId -> !isValidClaimId(claimId)));
        if (war.independencePuppet == null) {
            war.independencePuppet = "";
        }
        if (!war.independenceWar) {
            war.independencePuppet = "";
        }
    }

    private static void deactivateWarState(War war) {
        if (war == null) {
            return;
        }
        NationStore.ensureWarState(war);
        war.active = false;
        war.pendingDefenderResponse = false;
        war.peaceDeal = null;
        war.peaceOffers.clear();
        war.joinRequests.clear();
        war.defenseCalls.clear();
        war.independenceWar = false;
        war.independencePuppet = "";
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

    private void normalizeTrucesAndIncomeTransfers() {
        long now = NationStore.persistentNow();
        LinkedHashMap<String, Truce> normalizedTruces = new LinkedHashMap<>();
        for (Truce truce : this.state.truces.values()) {
            if (truce == null || !this.state.nations.containsKey(truce.first) || !this.state.nations.containsKey(truce.second) || truce.first.equals(truce.second)) {
                continue;
            }
            String key = NationStore.pairKey(truce.first, truce.second);
            String[] pair = key.split("->", 2);
            truce.first = pair[0];
            truce.second = pair[1];
            truce.expiresTick = this.migrateDeadline(truce.expiresTick, 1200L * 20L);
            if (truce.expiresTick > now) {
                normalizedTruces.put(key, truce);
            }
        }
        this.state.truces = normalizedTruces;

        LinkedHashMap<String, TruceOffer> normalizedOffers = new LinkedHashMap<>();
        for (TruceOffer offer : this.state.truceOffers.values()) {
            if (offer == null || !this.state.nations.containsKey(offer.proposer) || !this.state.nations.containsKey(offer.receiver) || offer.proposer.equals(offer.receiver)) {
                continue;
            }
            if (offer.createdTick <= 0L || !PersistentTime.isPersistent(offer.createdTick)) {
                offer.createdTick = now;
            }
            normalizedOffers.put(NationStore.pairKey(offer.proposer, offer.receiver), offer);
        }
        this.state.truceOffers = normalizedOffers;

        LinkedHashMap<String, IncomeTransfer> normalizedIncome = new LinkedHashMap<>();
        for (IncomeTransfer transfer : this.state.incomeTransfers.values()) {
            if (transfer == null || !this.state.nations.containsKey(transfer.payer) || !this.state.nations.containsKey(transfer.receiver)
                || transfer.payer.equals(transfer.receiver) || !Double.isFinite(transfer.amountPerMinute)) {
                continue;
            }
            transfer.amountPerMinute = NationStore.roundMoney(Math.max(0.0, transfer.amountPerMinute));
            if (transfer.amountPerMinute > 0.0) {
                normalizedIncome.put(NationStore.incomeTransferKey(transfer.payer, transfer.receiver), transfer);
            }
        }
        this.state.incomeTransfers = normalizedIncome;
        this.state.rewardedBuildPositions.removeIf(position -> position == null || position.isBlank());
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
        deal.demandedMoney = Double.isFinite(deal.demandedMoney) ? NationStore.roundMoney(Math.max(0.0, deal.demandedMoney)) : 0.0;
        deal.offeredMoney = Double.isFinite(deal.offeredMoney) ? NationStore.roundMoney(Math.max(0.0, deal.offeredMoney)) : 0.0;
    }

    private static void normalizeTradeOffer(TradeOffer offer) {
        if (offer.requestedClaims == null) {
            offer.requestedClaims = new LinkedHashSet<String>();
        }
        if (offer.offeredClaims == null) {
            offer.offeredClaims = new LinkedHashSet<String>();
        }
        offer.requestedClaims.removeIf(claimId -> !NationStore.isValidClaimId(claimId));
        offer.offeredClaims.removeIf(claimId -> !NationStore.isValidClaimId(claimId));
        offer.requestedMoney = Double.isFinite(offer.requestedMoney) ? NationStore.roundMoney(Math.max(0.0, offer.requestedMoney)) : 0.0;
        offer.offeredMoney = Double.isFinite(offer.offeredMoney) ? NationStore.roundMoney(Math.max(0.0, offer.offeredMoney)) : 0.0;
        offer.requestedIncomePerMinute = Double.isFinite(offer.requestedIncomePerMinute)
            ? NationStore.roundMoney(Math.max(0.0, offer.requestedIncomePerMinute)) : 0.0;
        offer.offeredIncomePerMinute = Double.isFinite(offer.offeredIncomePerMinute)
            ? NationStore.roundMoney(Math.max(0.0, offer.offeredIncomePerMinute)) : 0.0;
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

    public enum PuppetActionStatus {
        SUCCESS,
        NOT_A_PUPPET,
        NOT_MASTER,
        FROZEN,
        COOLDOWN,
        POINTS_TOO_LOW,
        MAXIMUM_WARS_LOST,
        ACTIVE_WAR,
        NOT_ANNEXABLE
    }

    public record PuppetActionResult(PuppetActionStatus status, int points, int lostWars, long cooldownUntil) {
        public static PuppetActionResult success(PuppetRelation relation) {
            return new PuppetActionResult(PuppetActionStatus.SUCCESS,
                relation == null ? 0 : relation.independencePoints,
                relation == null ? 0 : relation.lostIndependenceWars, 0L);
        }

        public static PuppetActionResult failure(PuppetActionStatus status, PuppetRelation relation) {
            return new PuppetActionResult(status,
                relation == null ? 0 : relation.independencePoints,
                relation == null ? 0 : relation.lostIndependenceWars, 0L);
        }

        public boolean successful() {
            return this.status == PuppetActionStatus.SUCCESS;
        }
    }

    public record PuppetEstablishResult(boolean established, PuppetRelation relation, List<Nation> releasedPuppets) {
        public PuppetEstablishResult {
            releasedPuppets = List.copyOf(releasedPuppets);
        }

        public static PuppetEstablishResult failed() {
            return new PuppetEstablishResult(false, null, List.of());
        }
    }

    public record PuppetWarResult(PuppetActionStatus status, War war) {
        public boolean started() {
            return this.status == PuppetActionStatus.SUCCESS && this.war != null;
        }
    }

    public record IndependenceResolution(boolean resolved, boolean puppetWon, int points, int lostWars,
                                         int returnedClaims) {
        public static IndependenceResolution failed() {
            return new IndependenceResolution(false, false, 0, 0, 0);
        }
    }

    public record PuppetAnnexResult(PuppetActionStatus status, int transferredClaims, int transferredMembers,
                                    double transferredTreasury, List<Nation> releasedPuppets) {
        public PuppetAnnexResult {
            releasedPuppets = List.copyOf(releasedPuppets);
        }

        public static PuppetAnnexResult failure(PuppetActionStatus status) {
            return new PuppetAnnexResult(status, 0, 0, 0.0, List.of());
        }

        public boolean annexed() {
            return this.status == PuppetActionStatus.SUCCESS;
        }
    }

    public static final class State {
        public int dataVersion = 0;
        public Map<String, Nation> nations = new LinkedHashMap<String, Nation>();
        public Map<String, String> playerNation = new LinkedHashMap<String, String>();
        public Map<String, String> playerNames = new LinkedHashMap<String, String>();
        public Map<String, Double> players = new LinkedHashMap<String, Double>();
        public Map<String, Set<String>> formerNationMembers = new LinkedHashMap<String, Set<String>>();
        public Map<String, Set<String>> nationInvites = new LinkedHashMap<String, Set<String>>();
        public Map<String, Map<String, Long>> nationInviteExpirations = new LinkedHashMap<String, Map<String, Long>>();
        public Map<String, String> claims = new LinkedHashMap<String, String>();
        public Set<String> coastClaims = new LinkedHashSet<String>();
        public boolean coastClaimsMigrated = false;
        public Map<String, War> wars = new LinkedHashMap<String, War>();
        public Map<String, Alliance> alliances = new LinkedHashMap<String, Alliance>();
        public Map<String, Truce> truces = new LinkedHashMap<String, Truce>();
        public Map<String, TruceOffer> truceOffers = new LinkedHashMap<String, TruceOffer>();
        public Map<String, IncomeTransfer> incomeTransfers = new LinkedHashMap<String, IncomeTransfer>();
        public Map<String, Long> spyCooldowns = new LinkedHashMap<String, Long>();
        public Map<String, Long> peaceCooldowns = new LinkedHashMap<String, Long>();
        public Map<String, PuppetRelation> puppetRelations = new LinkedHashMap<String, PuppetRelation>();
        public Map<String, PuppetProposal> puppetProposals = new LinkedHashMap<String, PuppetProposal>();
        public Map<String, Set<String>> guarantees = new LinkedHashMap<String, Set<String>>();
        public Map<String, Long> paralyzedClaims = new LinkedHashMap<String, Long>();
        public Map<String, Long> raidedClaims = new LinkedHashMap<String, Long>();
        public Map<String, Long> disabledCounterspyClaims = new LinkedHashMap<String, Long>();
        public Map<String, Long> spendingBlocks = new LinkedHashMap<String, Long>();
        public Set<String> warDeclarationRejections = new LinkedHashSet<String>();
        public List<SpyMission> spyMissions = new ArrayList<SpyMission>();
        public List<MarketListing> marketListings = new ArrayList<MarketListing>();
        public List<LandPurchaseOffer> landPurchaseOffers = new ArrayList<LandPurchaseOffer>();
        public List<TradeOffer> tradeOffers = new ArrayList<TradeOffer>();
        public Set<String> rewardedBuildPositions = new LinkedHashSet<String>();
        public int nextListingId = 1;
        public int nextSpyMissionId = 1;
        public int nextLandPurchaseOfferId = 1;
        public int nextTradeOfferId = 1;
    }

    public static final class Nation {
        public String id = "";
        public String name = "";
        public String owner = "";
        public String ownerName = "";
        public String doctrine;
        public String joinPolicy;
        public double balance;
        public int freeClaimsRemaining;
        public int upgradeLevel;
        public String capitalClaim;
        public Set<String> members;
        public Set<String> cityClaims;
        public Set<String> coreClaims;
        public Set<String> lostCoreClaims;
        public long lastSpecialWarLeaveTick;
        public Set<String> usedSpecialWarLeaveIdeologies;
        public boolean lostCoreTerritory;
        public SpyAgency spyAgency;

        public Nation() {
            this.doctrine = Doctrine.AMERICAN.id;
            this.joinPolicy = JoinPolicy.INVITE_ONLY.name();
            this.balance = 0.0;
            this.freeClaimsRemaining = 0;
            this.upgradeLevel = 0;
            this.capitalClaim = "";
            this.members = new LinkedHashSet<String>();
            this.cityClaims = new LinkedHashSet<String>();
            this.coreClaims = new LinkedHashSet<String>();
            this.lostCoreClaims = new LinkedHashSet<String>();
            this.lastSpecialWarLeaveTick = -1L;
            this.usedSpecialWarLeaveIdeologies = new LinkedHashSet<String>();
            this.lostCoreTerritory = false;
            this.spyAgency = null;
        }

        public Doctrine doctrine() {
            return Doctrine.byId(this.doctrine).orElse(Doctrine.AMERICAN);
        }

        public JoinPolicy joinPolicy() {
            return JoinPolicy.parse(this.joinPolicy).orElse(JoinPolicy.INVITE_ONLY);
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
        public Map<String, DefenseCall> defenseCalls = new LinkedHashMap<String, DefenseCall>();
        public Map<String, Set<String>> capturedClaimsByNation = new LinkedHashMap<String, Set<String>>();
        public Map<String, String> originalClaimOwners = new LinkedHashMap<String, String>();
        public Map<String, Set<String>> coreClaimsByNation = new LinkedHashMap<String, Set<String>>();
        public boolean pendingDefenderResponse = false;
        public boolean independenceWar = false;
        public String independencePuppet = "";
    }

    public static final class DefenseCall {
        public String caller = "";
        public String kind = "alliance";
    }

    public static final class PeaceDeal {
        public String proposer = "";
        public String receiver = "";
        public Set<String> demandedClaims = new LinkedHashSet<String>();
        public Set<String> offeredClaims = new LinkedHashSet<String>();
        public double demandedMoney = 0.0;
        public double offeredMoney = 0.0;
        public boolean returnCapturedClaims = false;
        public boolean puppetReceiver = false;
    }

    public static final class PuppetRelation {
        public String master = "";
        public String puppet = "";
        public int independencePoints = PuppetRules.INITIAL_INDEPENDENCE_POINTS;
        public int lostIndependenceWars = 0;
        public long agitateCooldownUntil = 0L;
        public long pacifyCooldownUntil = 0L;
        public long tradePointCooldownUntil = 0L;
    }

    public static final class PuppetProposal {
        public String master = "";
        public String puppet = "";
        public long createdTick = 0L;
    }

    public static final class TradeOffer {
        public int id = 0;
        public String proposer = "";
        public String receiver = "";
        public Set<String> requestedClaims = new LinkedHashSet<String>();
        public Set<String> offeredClaims = new LinkedHashSet<String>();
        public double requestedMoney = 0.0;
        public double offeredMoney = 0.0;
        public double requestedIncomePerMinute = 0.0;
        public double offeredIncomePerMinute = 0.0;
        public boolean incomeTermsSpecified = false;
        public long createdTick = 0L;
    }

    public static final class Truce {
        public String first = "";
        public String second = "";
        public long expiresTick = 0L;
        public boolean expiryWarningSent = false;
    }

    public static final class TruceOffer {
        public String proposer = "";
        public String receiver = "";
        public boolean renewal = false;
        public long createdTick = 0L;
    }

    public static final class IncomeTransfer {
        public String payer = "";
        public String receiver = "";
        public double amountPerMinute = 0.0;
    }

    public static final class SpyMission {
        public int id = 0;
        public String spyPlayer = "";
        public String spyName = "";
        public String spyNation = "";
        public String target = "";
        public int spyId = 0;
        public String type = "";
        public List<String> chunks = new ArrayList<String>();
        public long completeTick = 0L;
    }

    public static final class SpyAgency {
        public List<SpyUnit> spies = new ArrayList<SpyUnit>();
        public Map<String, SpyIntel> intel = new LinkedHashMap<String, SpyIntel>();
    }

    public static final class SpyUnit {
        public int id = 0;
        public String country = "";
        public String status = "idle";
        public String mission = "";
        public String targetChunk = "";
        public long availableTick = 0L;
    }

    public static final class SpyIntel {
        public String target = "";
        public Set<String> known = new LinkedHashSet<String>();
        public String name = "";
        public String doctrine = "";
        public String ideology = "";
        public String balance = "";
        public String capital = "";
        public String size = "";
        public String members = "";
        public String faction = "";
        public String guarantees = "";
        public long updatedTick = 0L;
    }
}
