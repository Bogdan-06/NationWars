package dev.moth.nationwars;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import net.minecraft.resources.ResourceLocation;

public enum Doctrine {
   GERMAN("german", "Germany", Ideology.FASCIST, 1.0, 1.0, 1.35, 4, 35, 1.0, 1.6, 1.0, 1.0, 1.0, false, false, true, false, false, false, false, false),
   SOVIET("soviet", "Soviet Union", Ideology.COMMUNIST, 0.8, 1.0, 0.75, 4, 50, 1.0, 1.0, 1.25, 1.0, 1.0, false, false, false, false, false, false, false, false),
   AMERICAN("american", "United States", Ideology.DEMOCRATIC, 1.0, 1.0, 1.0, 4, 50, 1.0, 1.0, 0.8, 1.0, 1.0, true, true, true, true, false, false, false, false),
   FRENCH("french", "France", Ideology.DEMOCRATIC, 1.0, 1.0, 1.0, 4, 50, 1.0, 0.6, 1.0, 1.0, 1.0, false, false, true, false, false, false, false, false),
   BRITISH(
      "british", "United Kingdom", Ideology.DEMOCRATIC, 1.0, 1.0, 1.0, 6, 50, 1.0, 1.0, 1.0, 1.0, 3.0, false, false, true, false, false, false, false, false
   ),
   ITALIAN("italian", "Italy", Ideology.FASCIST, 1.0, 1.0, 1.0, 4, 50, 1.0, 1.0, 1.0, 1.0, 1.0, false, false, true, false, true, true, false, false),
   ROMANIAN("romanian", "Romania", Ideology.NON_ALIGNED, 1.0, 1.0, 1.0, 4, 50, 1.0, 1.0, 1.0, 1.0, 1.0, false, false, true, false, false, false, true, true);

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

   private Doctrine(
      String id,
      String displayName,
      Ideology ideology,
      double claimCostMultiplier,
      double incomeMultiplier,
      double maintenanceMultiplier,
      int freeClaims,
      int captureSeconds,
      double defenseCaptureMultiplier,
      double surrenderLandMultiplier,
      double marketBuyMultiplier,
      double marketSellMultiplier,
      double peaceOfferCostMultiplier,
      boolean pacifist,
      boolean distanceClaimScaling,
      boolean capitalProducesIncome,
      boolean canBuyCities,
      boolean claimedLandSpeed,
      boolean canRejectWarDeclarations,
      boolean canLeaveWarSafely,
      boolean randomTreasuryDrain
   ) {
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
      this.defaultIconItemId = defaultIconItemId(id);
      this.defaultPerkLore = defaultPerkLore(id);
      this.resetToDefaults();
   }

   public static Optional<Doctrine> byId(String id) {
      if (id == null) {
         return Optional.empty();
      } else {
         String normalized = id.trim().toLowerCase(Locale.ROOT);
         return Arrays.stream(values()).filter(doctrine -> doctrine.id.equals(normalized)).findFirst();
      }
   }

   public static void resetAllToDefaults() {
      Arrays.stream(values()).forEach(Doctrine::resetToDefaults);
   }

   public static String choices() {
      return Arrays.stream(values()).map(doctrine -> doctrine.id).collect(Collectors.joining(", "));
   }

   public List<String> perkLore() {
      return this.perkLore;
   }

   public void applyOverride(JsonObject json) {
      String nextDisplayName = readString(json, this.displayName, "display_name", "displayName", "name");
      String nextIconItemId = readString(json, this.iconItemId, "icon", "icon_item", "iconItem", "icon_block", "iconBlock");
      Ideology nextIdeology = readIdeology(json, this.ideology, "ideology");
      double nextClaimCostMultiplier = readDouble(json, this.claimCostMultiplier, 0.0, "claim_cost_multiplier", "claimCostMultiplier");
      double nextIncomeMultiplier = readDouble(json, this.incomeMultiplier, 0.0, "income_multiplier", "incomeMultiplier");
      double nextMaintenanceMultiplier = readDouble(json, this.maintenanceMultiplier, 0.0, "maintenance_multiplier", "maintenanceMultiplier");
      int nextFreeClaims = readInt(json, this.freeClaims, 0, "free_claims", "freeClaims");
      int nextCaptureSeconds = readInt(json, this.captureSeconds, 1, "capture_seconds", "captureSeconds");
      double nextDefenseCaptureMultiplier = readDouble(json, this.defenseCaptureMultiplier, 0.0, "defense_capture_multiplier", "defenseCaptureMultiplier");
      double nextSurrenderLandMultiplier = readDouble(json, this.surrenderLandMultiplier, 0.0, "surrender_land_multiplier", "surrenderLandMultiplier");
      double nextMarketBuyMultiplier = readDouble(json, this.marketBuyMultiplier, 0.0, "market_buy_multiplier", "marketBuyMultiplier");
      double nextMarketSellMultiplier = readDouble(json, this.marketSellMultiplier, 0.0, "market_sell_multiplier", "marketSellMultiplier");
      double nextPeaceOfferCostMultiplier = readDouble(json, this.peaceOfferCostMultiplier, 0.0, "peace_offer_cost_multiplier", "peaceOfferCostMultiplier");
      boolean nextPacifist = readBoolean(json, this.pacifist, "pacifist");
      boolean nextDistanceClaimScaling = readBoolean(json, this.distanceClaimScaling, "distance_claim_scaling", "distanceClaimScaling");
      boolean nextCapitalProducesIncome = readBoolean(json, this.capitalProducesIncome, "capital_produces_income", "capitalProducesIncome");
      boolean nextCanBuyCities = readBoolean(json, this.canBuyCities, "can_buy_cities", "canBuyCities");
      boolean nextClaimedLandSpeed = readBoolean(json, this.claimedLandSpeed, "claimed_land_speed", "claimedLandSpeed");
      boolean nextCanRejectWarDeclarations = readBoolean(json, this.canRejectWarDeclarations, "can_reject_war_declarations", "canRejectWarDeclarations");
      boolean nextCanLeaveWarSafely = readBoolean(json, this.canLeaveWarSafely, "can_leave_war_safely", "canLeaveWarSafely");
      boolean nextRandomTreasuryDrain = readBoolean(json, this.randomTreasuryDrain, "random_treasury_drain", "randomTreasuryDrain");
      List<String> nextPerkLore = readStringList(json, this.perkLore, "perk_lore", "perkLore", "lore");
      if (ResourceLocation.tryParse(nextIconItemId) == null) {
         throw new IllegalArgumentException("Invalid doctrine icon item id: " + nextIconItemId);
      } else {
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
         case "german" -> "minecraft:netherite_block";
         case "soviet" -> "minecraft:redstone_block";
         case "american" -> "minecraft:diamond_block";
         case "french" -> "minecraft:quartz_block";
         case "british" -> "minecraft:lapis_block";
         case "italian" -> "minecraft:emerald_block";
         case "romanian" -> "minecraft:copper_block";
         default -> "minecraft:paper";
      };
   }

   private static List<String> defaultPerkLore(String id) {
      return switch (id) {
         case "german" -> List.of(
         "+ Blitzkrieg: capture time reduced by 15s",
         "+ Gleiwitz Incident: justification takes 40s less",
         "- War Reparations: higher maintenance",
         "- 1945: loses 40% of land after surrender"
      );
         case "soviet" -> List.of(
         "+ Mother Russia: cheaper claims and maintenance",
         "+ Great Patriotic War: land takes twice as long",
         "  to conquer unless 2 attackers are present",
         "- Collectivity: capital has no passive income",
         "- Yellow Curtains: higher market buy prices"
      );
         case "american" -> List.of(
         "+ Capitalism: pay claim cost for passive income",
         "+ Worldwide Economy: lower market prices",
         "- Pacifism: cannot declare wars",
         "- Isolation: distance raises claim cost"
      );
         case "french" -> List.of(
         "+ White Flag: loses 15% of land after surrender",
         "+ The Maginot Line: your land takes 25s longer",
         "- Casus Foederis: declining allies costs money",
         "- No War Support: attacking triples maintenance"
      );
         case "british" -> List.of(
         "+ Ports: coast and river claims produce income",
         "+ Colonies: more starter claims",
         "- Sea Lion: coast and river claims fall 10s faster",
         "- Neville Chamberlain: peace offers cost more"
      );
         case "italian" -> List.of(
         "+ Developed Infrastructure: Speed II on core claims",
         "  and random $5 building bonuses",
         "+ Alpes: hill and mountain claims take 15s longer",
         "- Push-over: bigger nations can reject once",
         "- Civil War: recaptures take 10s longer"
      );
         case "romanian" -> List.of(
         "+ King Michael's Coup: leave war once per ideology",
         "+ Flexible Foreign Policy: enemy justification +30s",
         "- Iron Guard: lost core raises maintenance by 0.1x",
         "- Carol II Lifestyle: treasury randomly drains"
      );
         default -> List.of();
      };
   }

   private static Optional<JsonElement> find(JsonObject json, String... keys) {
      for (String key : keys) {
         JsonElement element = json.get(key);
         if (element != null && !element.isJsonNull()) {
            return Optional.of(element);
         }
      }

      return Optional.empty();
   }

   private static String readString(JsonObject json, String current, String... keys) {
      return find(json, keys).<String>map(JsonElement::getAsString).orElse(current);
   }

   private static int readInt(JsonObject json, int current, int minimum, String... keys) {
      return Math.max(minimum, find(json, keys).<Integer>map(JsonElement::getAsInt).orElse(current));
   }

   private static double readDouble(JsonObject json, double current, double minimum, String... keys) {
      return Math.max(minimum, find(json, keys).<Double>map(JsonElement::getAsDouble).orElse(current));
   }

   private static boolean readBoolean(JsonObject json, boolean current, String... keys) {
      return find(json, keys).<Boolean>map(JsonElement::getAsBoolean).orElse(current);
   }

   private static Ideology readIdeology(JsonObject json, Ideology current, String... keys) {
      Optional<JsonElement> element = find(json, keys);
      if (element.isEmpty()) {
         return current;
      } else {
         String normalized = element.get().getAsString().trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
         return Ideology.valueOf(normalized);
      }
   }

   private static List<String> readStringList(JsonObject json, List<String> current, String... keys) {
      Optional<JsonElement> element = find(json, keys);
      if (element.isEmpty()) {
         return current;
      } else if (!element.get().isJsonArray()) {
         throw new IllegalArgumentException(keys[0] + " must be an array of strings");
      } else {
         JsonArray array = element.get().getAsJsonArray();
         List<String> values = new ArrayList<>();

         for (JsonElement entry : array) {
            values.add(entry.getAsString());
         }

         return List.copyOf(values);
      }
   }
}
