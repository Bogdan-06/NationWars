package dev.moth.nationwars;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

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
   public final String displayName;
   public final Ideology ideology;
   public final double claimCostMultiplier;
   public final double incomeMultiplier;
   public final double maintenanceMultiplier;
   public final int freeClaims;
   public final int captureSeconds;
   public final double defenseCaptureMultiplier;
   public final double surrenderLandMultiplier;
   public final double marketBuyMultiplier;
   public final double marketSellMultiplier;
   public final double peaceOfferCostMultiplier;
   public final boolean pacifist;
   public final boolean distanceClaimScaling;
   public final boolean capitalProducesIncome;
   public final boolean canBuyCities;
   public final boolean claimedLandSpeed;
   public final boolean canRejectWarDeclarations;
   public final boolean canLeaveWarSafely;
   public final boolean randomTreasuryDrain;

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
      this.displayName = displayName;
      this.ideology = ideology;
      this.claimCostMultiplier = claimCostMultiplier;
      this.incomeMultiplier = incomeMultiplier;
      this.maintenanceMultiplier = maintenanceMultiplier;
      this.freeClaims = freeClaims;
      this.captureSeconds = captureSeconds;
      this.defenseCaptureMultiplier = defenseCaptureMultiplier;
      this.surrenderLandMultiplier = surrenderLandMultiplier;
      this.marketBuyMultiplier = marketBuyMultiplier;
      this.marketSellMultiplier = marketSellMultiplier;
      this.peaceOfferCostMultiplier = peaceOfferCostMultiplier;
      this.pacifist = pacifist;
      this.distanceClaimScaling = distanceClaimScaling;
      this.capitalProducesIncome = capitalProducesIncome;
      this.canBuyCities = canBuyCities;
      this.claimedLandSpeed = claimedLandSpeed;
      this.canRejectWarDeclarations = canRejectWarDeclarations;
      this.canLeaveWarSafely = canLeaveWarSafely;
      this.randomTreasuryDrain = randomTreasuryDrain;
   }

   public static Optional<Doctrine> byId(String id) {
      String normalized = id.toLowerCase(Locale.ROOT);
      return Arrays.stream(values()).filter(doctrine -> doctrine.id.equals(normalized)).findFirst();
   }

   public static String choices() {
      return Arrays.stream(values()).map(doctrine -> doctrine.id).collect(Collectors.joining(", "));
   }

   public List<String> perkLore() {
      return switch (this) {
         case GERMAN -> List.of(
         "+ Blitzkrieg: capture time reduced by 15s",
         "+ Gleiwitz Incident: justification takes 40s less",
         "- War Reparations: higher maintenance",
         "- 1945: loses 40% of land after surrender"
      );
         case SOVIET -> List.of(
         "+ Mother Russia: cheaper claims and maintenance",
         "+ Great Patriotic War: land takes twice as long",
         "  to conquer unless 2 attackers are present",
         "- Collectivity: capital has no passive income",
         "- Yellow Curtains: higher market buy prices"
      );
         case AMERICAN -> List.of(
         "+ Capitalism: pay claim cost for passive income",
         "+ Worldwide Economy: lower market prices",
         "- Pacifism: cannot declare wars",
         "- Isolation: distance raises claim cost"
      );
         case FRENCH -> List.of(
         "+ White Flag: loses 15% of land after surrender",
         "+ The Maginot Line: your land takes 25s longer",
         "- Casus Foederis: declining allies costs money",
         "- No War Support: attacking triples maintenance"
      );
         case BRITISH -> List.of(
         "+ Ports: coast and river claims produce income",
         "+ Colonies: more starter claims",
         "- Sea Lion: coast and river claims fall 10s faster",
         "- Neville Chamberlain: peace offers cost more"
      );
         case ITALIAN -> List.of(
         "+ Developed Infrastructure: Speed II on core claims",
         "  and random $5 building bonuses",
         "+ Alpes: hill and mountain claims take 15s longer",
         "- Push-over: bigger nations can reject once",
         "- Civil War: recaptures take 10s longer"
      );
         case ROMANIAN -> List.of(
         "+ King Michael's Coup: leave war once per ideology",
         "+ Flexible Foreign Policy: enemy justification +30s",
         "- Iron Guard: lost core raises maintenance by 0.1x",
         "- Carol II Lifestyle: treasury randomly drains"
      );
      };
   }
}
