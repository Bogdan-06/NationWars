package dev.moth.nationwars;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

public enum Doctrine {
   GERMAN(
      "german",
      "Germany",
      Ideology.FASCIST,
      1.05,
      1.0,
      1.35,
      0.65,
      1.0,
      3,
      300,
      35,
      1.0,
      1.5,
      1.0,
      1.0,
      1.0,
      false,
      false,
      true,
      false,
      false,
      false,
      false,
      false
   ),
   SOVIET(
      "soviet",
      "Soviet Union",
      Ideology.COMMUNIST,
      0.8,
      1.0,
      0.75,
      1.0,
      1.0,
      5,
      540,
      65,
      1.15,
      1.0,
      1.25,
      1.0,
      1.0,
      false,
      false,
      false,
      false,
      false,
      false,
      false,
      false
   ),
   AMERICAN(
      "american",
      "United States",
      Ideology.DEMOCRATIC,
      0.95,
      1.05,
      1.0,
      1.2,
      1.0,
      4,
      480,
      55,
      1.0,
      1.0,
      0.8,
      1.15,
      1.0,
      true,
      true,
      true,
      true,
      false,
      false,
      false,
      false
   ),
   FRENCH(
      "french",
      "France",
      Ideology.DEMOCRATIC,
      1.0,
      1.0,
      0.95,
      1.1,
      1.0,
      4,
      540,
      65,
      1.25,
      0.6,
      1.0,
      1.0,
      1.0,
      false,
      false,
      true,
      false,
      false,
      false,
      false,
      false
   ),
   BRITISH(
      "british",
      "United Kingdom",
      Ideology.DEMOCRATIC,
      1.05,
      1.05,
      0.9,
      1.15,
      1.0,
      6,
      600,
      65,
      1.0,
      1.0,
      1.0,
      1.0,
      3.0,
      false,
      false,
      true,
      false,
      false,
      false,
      false,
      false
   ),
   ITALIAN(
      "italian", "Italy", Ideology.FASCIST, 0.95, 1.0, 1.0, 0.9, 1.0, 4, 420, 60, 1.0, 1.0, 1.0, 1.0, 1.0, false, false, true, false, true, true, false, false
   ),
   ROMANIAN(
      "romanian",
      "Romania",
      Ideology.NON_ALIGNED,
      0.95,
      1.1,
      1.1,
      0.9,
      1.75,
      4,
      420,
      50,
      1.0,
      1.0,
      1.0,
      1.0,
      1.0,
      false,
      false,
      true,
      false,
      false,
      false,
      true,
      true
   );

   public final String id;
   public final String displayName;
   public final Ideology ideology;
   public final double claimCostMultiplier;
   public final double incomeMultiplier;
   public final double maintenanceMultiplier;
   public final double justificationMultiplier;
   public final double incomingJustificationMultiplier;
   public final int freeClaims;
   public final int justificationSeconds;
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
      double justificationMultiplier,
      double incomingJustificationMultiplier,
      int freeClaims,
      int justificationSeconds,
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
      this.justificationMultiplier = justificationMultiplier;
      this.incomingJustificationMultiplier = incomingJustificationMultiplier;
      this.freeClaims = freeClaims;
      this.justificationSeconds = justificationSeconds;
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
}
