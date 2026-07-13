/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.gson.JsonArray
 *  com.google.gson.JsonElement
 *  com.google.gson.JsonObject
 *  net.minecraft.resources.ResourceLocation
 */
package dev.moth.nationwars;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.moth.nationwars.Ideology;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import net.minecraft.resources.ResourceLocation;

public enum Doctrine {
    GERMAN("GER", "Germany", Ideology.FASCIST, 1.0, 1.0, 1.35, 4, 35, 1.0, 1.0, 1.0, 1.0, 1.0, false, false, true, false, false, false, false, false),
    SOVIET("SOV", "Soviet Union", Ideology.COMMUNIST, 0.8, 1.0, 0.5, 4, 50, 1.0, 1.0, 1.25, 1.0, 1.0, false, false, false, false, false, false, false, false),
    AMERICAN("USA", "United States", Ideology.DEMOCRATIC, 1.0, 1.0, 1.0, 4, 50, 1.0, 1.0, 0.8, 1.0, 1.0, true, true, true, true, false, false, false, false),
    FRENCH("FRA", "France", Ideology.DEMOCRATIC, 1.0, 1.0, 1.0, 4, 50, 1.0, 1.0, 1.0, 1.0, 1.0, false, false, true, false, false, false, false, false),
    BRITISH("ENG", "United Kingdom", Ideology.DEMOCRATIC, 1.0, 1.0, 1.0, 6, 50, 1.0, 1.0, 1.0, 1.0, 3.0, false, false, true, false, false, false, false, false),
    ITALIAN("ITA", "Italy", Ideology.FASCIST, 1.0, 1.0, 1.0, 4, 50, 1.0, 1.0, 1.0, 1.0, 1.0, false, false, true, false, true, true, false, false),
    ROMANIAN("ROM", "Romania", Ideology.NON_ALIGNED, 1.0, 1.0, 1.0, 4, 50, 1.0, 1.0, 1.0, 1.0, 1.0, false, false, true, false, false, false, true, true);

    public final String id;
    public String displayName;
    public Ideology ideology;
    public double claimCostMultiplier;
    public double incomeMultiplier;
    public double maintenanceMultiplier;
    public int freeClaims;
    public int captureSeconds;
    public double defenseCaptureMultiplier;
    public double surrenderLandMultiplier;
    public double marketBuyMultiplier;
    public double marketSellMultiplier;
    public double peaceOfferCostMultiplier;
    public boolean pacifist;
    public boolean distanceClaimScaling;
    public boolean capitalProducesIncome;
    public boolean canBuyCities;
    public boolean claimedLandSpeed;
    public boolean canRejectWarDeclarations;
    public boolean canLeaveWarSafely;
    public boolean randomTreasuryDrain;
    public String iconItemId;
    private final String defaultDisplayName;
    private final Ideology defaultIdeology;
    private final double defaultClaimCostMultiplier;
    private final double defaultIncomeMultiplier;
    private final double defaultMaintenanceMultiplier;
    private final int defaultFreeClaims;
    private final int defaultCaptureSeconds;
    private final double defaultDefenseCaptureMultiplier;
    private final double defaultSurrenderLandMultiplier;
    private final double defaultMarketBuyMultiplier;
    private final double defaultMarketSellMultiplier;
    private final double defaultPeaceOfferCostMultiplier;
    private final boolean defaultPacifist;
    private final boolean defaultDistanceClaimScaling;
    private final boolean defaultCapitalProducesIncome;
    private final boolean defaultCanBuyCities;
    private final boolean defaultClaimedLandSpeed;
    private final boolean defaultCanRejectWarDeclarations;
    private final boolean defaultCanLeaveWarSafely;
    private final boolean defaultRandomTreasuryDrain;
    private final String defaultIconItemId;
    private final List<String> defaultPerkLore;
    private List<String> perkLore;

    private Doctrine(String id, String displayName, Ideology ideology, double claimCostMultiplier, double incomeMultiplier, double maintenanceMultiplier, int freeClaims, int captureSeconds, double defenseCaptureMultiplier, double surrenderLandMultiplier, double marketBuyMultiplier, double marketSellMultiplier, double peaceOfferCostMultiplier, boolean pacifist, boolean distanceClaimScaling, boolean capitalProducesIncome, boolean canBuyCities, boolean claimedLandSpeed, boolean canRejectWarDeclarations, boolean canLeaveWarSafely, boolean randomTreasuryDrain) {
        this.id = id;
        this.defaultDisplayName = displayName;
        this.defaultIdeology = ideology;
        this.defaultClaimCostMultiplier = claimCostMultiplier;
        this.defaultIncomeMultiplier = incomeMultiplier;
        this.defaultMaintenanceMultiplier = maintenanceMultiplier;
        this.defaultFreeClaims = freeClaims;
        this.defaultCaptureSeconds = captureSeconds;
        this.defaultDefenseCaptureMultiplier = defenseCaptureMultiplier;
        this.defaultSurrenderLandMultiplier = surrenderLandMultiplier;
        this.defaultMarketBuyMultiplier = marketBuyMultiplier;
        this.defaultMarketSellMultiplier = marketSellMultiplier;
        this.defaultPeaceOfferCostMultiplier = peaceOfferCostMultiplier;
        this.defaultPacifist = pacifist;
        this.defaultDistanceClaimScaling = distanceClaimScaling;
        this.defaultCapitalProducesIncome = capitalProducesIncome;
        this.defaultCanBuyCities = canBuyCities;
        this.defaultClaimedLandSpeed = claimedLandSpeed;
        this.defaultCanRejectWarDeclarations = canRejectWarDeclarations;
        this.defaultCanLeaveWarSafely = canLeaveWarSafely;
        this.defaultRandomTreasuryDrain = randomTreasuryDrain;
        this.defaultIconItemId = Doctrine.defaultIconItemId(id);
        this.defaultPerkLore = Doctrine.defaultPerkLore(id);
        this.resetToDefaults();
    }

    public static Optional<Doctrine> byId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        String normalized = id.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "GER", "GERMAN", "GERMANY" -> Optional.of(GERMAN);
            case "SOV", "SOVIET", "SOVIET_UNION", "USSR" -> Optional.of(SOVIET);
            case "USA", "AMERICAN", "UNITED_STATES", "US" -> Optional.of(AMERICAN);
            case "FRA", "FRENCH", "FRANCE" -> Optional.of(FRENCH);
            case "ENG", "BRITISH", "UNITED_KINGDOM", "UK" -> Optional.of(BRITISH);
            case "ITA", "ITALIAN", "ITALY" -> Optional.of(ITALIAN);
            case "ROM", "ROMANIAN", "ROMANIA" -> Optional.of(ROMANIAN);
            default -> Optional.empty();
        };
    }

    public static void resetAllToDefaults() {
        Arrays.stream(Doctrine.values()).forEach(Doctrine::resetToDefaults);
    }

    public static String choices() {
        return Arrays.stream(Doctrine.values()).map(doctrine -> doctrine.id).collect(Collectors.joining(", "));
    }

    public List<String> perkLore() {
        return this.perkLore;
    }

    boolean usesDefaultDisplayName() {
        return this.displayName.equals(this.defaultDisplayName);
    }

    boolean usesDefaultPerkLore() {
        return this.perkLore.equals(this.defaultPerkLore);
    }

    String translationStem() {
        return switch (this.id) {
            case "GER" -> "germany";
            case "SOV" -> "soviet_union";
            case "USA" -> "united_states";
            case "FRA" -> "france";
            case "ENG" -> "united_kingdom";
            case "ITA" -> "italy";
            case "ROM" -> "romania";
            default -> this.id.toLowerCase(Locale.ROOT);
        };
    }

    List<String> defaultPerkTranslationKeys() {
        String prefix = "nationwars.doctrine." + this.translationStem() + ".perk.";
        return java.util.stream.IntStream.range(0, this.defaultPerkLore.size())
            .mapToObj(index -> prefix + index).toList();
    }

    public void applyOverride(JsonObject json) {
        String nextDisplayName = Doctrine.readString(json, this.displayName, "display_name", "displayName", "name");
        String nextIconItemId = Doctrine.readString(json, this.iconItemId, "icon", "icon_item", "iconItem", "icon_block", "iconBlock");
        Ideology nextIdeology = Doctrine.readIdeology(json, this.ideology, "ideology");
        double nextClaimCostMultiplier = Doctrine.readDouble(json, this.claimCostMultiplier, 0.0, "claim_cost_multiplier", "claimCostMultiplier");
        double nextIncomeMultiplier = Doctrine.readDouble(json, this.incomeMultiplier, 0.0, "income_multiplier", "incomeMultiplier");
        double nextMaintenanceMultiplier = Doctrine.readDouble(json, this.maintenanceMultiplier, 0.0, "maintenance_multiplier", "maintenanceMultiplier");
        int nextFreeClaims = Doctrine.readInt(json, this.freeClaims, 0, "free_claims", "freeClaims");
        int nextCaptureSeconds = Doctrine.readInt(json, this.captureSeconds, 1, "capture_seconds", "captureSeconds");
        double nextDefenseCaptureMultiplier = Doctrine.readDouble(json, this.defenseCaptureMultiplier, 0.0, "defense_capture_multiplier", "defenseCaptureMultiplier");
        double nextSurrenderLandMultiplier = Doctrine.readDouble(json, this.surrenderLandMultiplier, 0.0, "surrender_land_multiplier", "surrenderLandMultiplier");
        double nextMarketBuyMultiplier = Doctrine.readDouble(json, this.marketBuyMultiplier, 0.0, "market_buy_multiplier", "marketBuyMultiplier");
        double nextMarketSellMultiplier = Doctrine.readDouble(json, this.marketSellMultiplier, 0.0, "market_sell_multiplier", "marketSellMultiplier");
        double nextPeaceOfferCostMultiplier = Doctrine.readDouble(json, this.peaceOfferCostMultiplier, 0.0, "peace_offer_cost_multiplier", "peaceOfferCostMultiplier");
        boolean nextPacifist = Doctrine.readBoolean(json, this.pacifist, "pacifist");
        boolean nextDistanceClaimScaling = Doctrine.readBoolean(json, this.distanceClaimScaling, "distance_claim_scaling", "distanceClaimScaling");
        boolean nextCapitalProducesIncome = Doctrine.readBoolean(json, this.capitalProducesIncome, "capital_produces_income", "capitalProducesIncome");
        boolean nextCanBuyCities = Doctrine.readBoolean(json, this.canBuyCities, "can_buy_cities", "canBuyCities");
        boolean nextClaimedLandSpeed = Doctrine.readBoolean(json, this.claimedLandSpeed, "claimed_land_speed", "claimedLandSpeed");
        boolean nextCanRejectWarDeclarations = Doctrine.readBoolean(json, this.canRejectWarDeclarations, "can_reject_war_declarations", "canRejectWarDeclarations");
        boolean nextCanLeaveWarSafely = Doctrine.readBoolean(json, this.canLeaveWarSafely, "can_leave_war_safely", "canLeaveWarSafely");
        boolean nextRandomTreasuryDrain = Doctrine.readBoolean(json, this.randomTreasuryDrain, "random_treasury_drain", "randomTreasuryDrain");
        List<String> nextPerkLore = Doctrine.readStringList(json, this.perkLore, "perk_lore", "perkLore", "lore");
        if (ResourceLocation.tryParse((String)nextIconItemId) == null) {
            throw new IllegalArgumentException("Invalid doctrine icon item id: " + nextIconItemId);
        }
        this.displayName = nextDisplayName;
        this.iconItemId = nextIconItemId;
        this.ideology = nextIdeology;
        this.claimCostMultiplier = nextClaimCostMultiplier;
        this.incomeMultiplier = nextIncomeMultiplier;
        this.maintenanceMultiplier = nextMaintenanceMultiplier;
        this.freeClaims = nextFreeClaims;
        this.captureSeconds = nextCaptureSeconds;
        this.defenseCaptureMultiplier = nextDefenseCaptureMultiplier;
        this.surrenderLandMultiplier = nextSurrenderLandMultiplier;
        this.marketBuyMultiplier = nextMarketBuyMultiplier;
        this.marketSellMultiplier = nextMarketSellMultiplier;
        this.peaceOfferCostMultiplier = nextPeaceOfferCostMultiplier;
        this.pacifist = nextPacifist;
        this.distanceClaimScaling = nextDistanceClaimScaling;
        this.capitalProducesIncome = nextCapitalProducesIncome;
        this.canBuyCities = nextCanBuyCities;
        this.claimedLandSpeed = nextClaimedLandSpeed;
        this.canRejectWarDeclarations = nextCanRejectWarDeclarations;
        this.canLeaveWarSafely = nextCanLeaveWarSafely;
        this.randomTreasuryDrain = nextRandomTreasuryDrain;
        this.perkLore = nextPerkLore;
    }

    private void resetToDefaults() {
        this.displayName = this.defaultDisplayName;
        this.ideology = this.defaultIdeology;
        this.claimCostMultiplier = this.defaultClaimCostMultiplier;
        this.incomeMultiplier = this.defaultIncomeMultiplier;
        this.maintenanceMultiplier = this.defaultMaintenanceMultiplier;
        this.freeClaims = this.defaultFreeClaims;
        this.captureSeconds = this.defaultCaptureSeconds;
        this.defenseCaptureMultiplier = this.defaultDefenseCaptureMultiplier;
        this.surrenderLandMultiplier = this.defaultSurrenderLandMultiplier;
        this.marketBuyMultiplier = this.defaultMarketBuyMultiplier;
        this.marketSellMultiplier = this.defaultMarketSellMultiplier;
        this.peaceOfferCostMultiplier = this.defaultPeaceOfferCostMultiplier;
        this.pacifist = this.defaultPacifist;
        this.distanceClaimScaling = this.defaultDistanceClaimScaling;
        this.capitalProducesIncome = this.defaultCapitalProducesIncome;
        this.canBuyCities = this.defaultCanBuyCities;
        this.claimedLandSpeed = this.defaultClaimedLandSpeed;
        this.canRejectWarDeclarations = this.defaultCanRejectWarDeclarations;
        this.canLeaveWarSafely = this.defaultCanLeaveWarSafely;
        this.randomTreasuryDrain = this.defaultRandomTreasuryDrain;
        this.iconItemId = this.defaultIconItemId;
        this.perkLore = this.defaultPerkLore;
    }

    private static String defaultIconItemId(String id) {
        return switch (id) {
            case "GER" -> "minecraft:netherite_block";
            case "SOV" -> "minecraft:redstone_block";
            case "USA" -> "minecraft:diamond_block";
            case "FRA" -> "minecraft:quartz_block";
            case "ENG" -> "minecraft:lapis_block";
            case "ITA" -> "minecraft:emerald_block";
            case "ROM" -> "minecraft:copper_block";
            default -> "minecraft:paper";
        };
    }

    private static List<String> defaultPerkLore(String id) {
        return switch (id) {
            case "GER" -> List.of("+ Blitzkrieg: enemy claims take 35s to capture", "+ Gleiwitz Incident: war justification is 40s faster", "- War Reparations: claim maintenance costs 1.35x", "- Turing's Bombe: counterspies block only 50% of attacks");
            case "SOV" -> List.of("+ Mother Russia: claims and maintenance cost less", "+ Great Patriotic War: land takes twice as long", "  to conquer unless 2 attackers are present", "- Collectivity: capital produces no passive treasury money", "- Yellow Curtains: buying from market costs more");
            case "USA" -> List.of("+ Capitalism: pay claim cost for city income", "  worth 0.25x capital income", "+ Worldwide Economy: market prices are 0.8x", "- Pacifism: cannot declare wars", "- Isolation: distance from the capital raises claim cost");
            case "FRA" -> List.of("+ Spy Master: 5 extra spies costing $300 each", "+ The Maginot Line: your land takes 25s longer to capture", "- Casus Foederis: declining allies costs money", "- No War Support: war-held land maintenance is higher");
            case "ENG" -> List.of("+ Ports: coast claims pay 0.25x capital income", "+ Colonies: more starter claims", "- Sea Lion: coast claims are captured 10s faster", "- Neville Chamberlain: peace offers cost 3x more");
            case "ITA" -> List.of("+ Developed Infrastructure: Speed II on owned claims", "  while at peace, Speed I while at war, and", "  better random building payouts", "+ Alpes: mountain and hilly claims take 15s longer", "- Push-over: nations with more claims can reject once", "- Civil War: recapturing land takes 10s longer");
            case "ROM" -> List.of("+ King Michael's Coup: one safe leave per ideology", "+ Flexible Foreign Policy: enemies justify 30s longer", "- Iron Guard: losing a core claim raises maintenance 0.1x", "- Carol II Lifestyle: drains money until all leaves are used");
            default -> List.of();
        };
    }

    private static Optional<JsonElement> find(JsonObject json, String ... keys) {
        for (String key : keys) {
            JsonElement element = json.get(key);
            if (element == null || element.isJsonNull()) continue;
            return Optional.of(element);
        }
        return Optional.empty();
    }

    private static String readString(JsonObject json, String current, String ... keys) {
        return Doctrine.find(json, keys).map(JsonElement::getAsString).orElse(current);
    }

    private static int readInt(JsonObject json, int current, int minimum, String ... keys) {
        return Math.max(minimum, Doctrine.find(json, keys).map(JsonElement::getAsInt).orElse(current));
    }

    private static double readDouble(JsonObject json, double current, double minimum, String ... keys) {
        Optional<JsonElement> element = Doctrine.find(json, keys);
        if (element.isEmpty()) {
            return current;
        }
        return Doctrine.finiteAtLeast(element.get().getAsDouble(), minimum, keys[0]);
    }

    static double finiteAtLeast(double value, double minimum, String field) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(field + " must be a finite number");
        }
        return Math.max(minimum, value);
    }

    private static boolean readBoolean(JsonObject json, boolean current, String ... keys) {
        return Doctrine.find(json, keys).map(JsonElement::getAsBoolean).orElse(current);
    }

    private static Ideology readIdeology(JsonObject json, Ideology current, String ... keys) {
        Optional<JsonElement> element = Doctrine.find(json, keys);
        if (element.isEmpty()) {
            return current;
        }
        String normalized = element.get().getAsString().trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return Ideology.valueOf(normalized);
    }

    private static List<String> readStringList(JsonObject json, List<String> current, String ... keys) {
        Optional<JsonElement> element = Doctrine.find(json, keys);
        if (element.isEmpty()) {
            return current;
        }
        if (!element.get().isJsonArray()) {
            throw new IllegalArgumentException(keys[0] + " must be an array of strings");
        }
        JsonArray array = element.get().getAsJsonArray();
        ArrayList<String> values = new ArrayList<String>();
        for (JsonElement entry : array) {
            values.add(entry.getAsString());
        }
        return List.copyOf(values);
    }
}
