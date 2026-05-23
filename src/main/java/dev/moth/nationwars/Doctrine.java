package dev.moth.nationwars;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

public enum Doctrine {
   AMERICAN("american", "American Arsenal", 1.15, 1.3, 1.0, 1.2, 4, 480, 55),
   SOVIET("soviet", "Soviet Collective", 0.8, 0.95, 1.2, 1.0, 5, 540, 65),
   BRITISH("british", "British Defensive", 1.05, 1.05, 0.75, 1.15, 4, 600, 70),
   GERMAN("german", "German Blitz", 1.1, 1.0, 1.35, 0.65, 3, 300, 40),
   ROMANIAN("romanian", "Romanian Opportunist", 0.95, 1.15, 1.1, 0.9, 4, 420, 50);

   public final String id;
   public final String displayName;
   public final double claimCostMultiplier;
   public final double incomeMultiplier;
   public final double maintenanceMultiplier;
   public final double justificationMultiplier;
   public final int freeClaims;
   public final int justificationSeconds;
   public final int captureSeconds;

   private Doctrine(
      String id,
      String displayName,
      double claimCostMultiplier,
      double incomeMultiplier,
      double maintenanceMultiplier,
      double justificationMultiplier,
      int freeClaims,
      int justificationSeconds,
      int captureSeconds
   ) {
      this.id = id;
      this.displayName = displayName;
      this.claimCostMultiplier = claimCostMultiplier;
      this.incomeMultiplier = incomeMultiplier;
      this.maintenanceMultiplier = maintenanceMultiplier;
      this.justificationMultiplier = justificationMultiplier;
      this.freeClaims = freeClaims;
      this.justificationSeconds = justificationSeconds;
      this.captureSeconds = captureSeconds;
   }

   public static Optional<Doctrine> byId(String id) {
      String normalized = id.toLowerCase(Locale.ROOT);
      return Arrays.stream(values()).filter(doctrine -> doctrine.id.equals(normalized)).findFirst();
   }

   public static String choices() {
      return Arrays.stream(values()).map(doctrine -> doctrine.id).collect(Collectors.joining(", "));
   }
}
