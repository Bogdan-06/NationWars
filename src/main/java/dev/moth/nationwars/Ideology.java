package dev.moth.nationwars;

public enum Ideology {
   FASCIST("Fascist"),
   COMMUNIST("Communist"),
   DEMOCRATIC("Democratic"),
   NON_ALIGNED("Non-aligned");

   public final String displayName;

   private Ideology(String displayName) {
      this.displayName = displayName;
   }
}
