package dev.moth.nationwars;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public final class NationIcons {
   private NationIcons() {
   }

   public static Item countryBlock(Doctrine doctrine) {
      return switch (doctrine) {
         case GERMAN -> Items.NETHERITE_BLOCK;
         case SOVIET -> Items.REDSTONE_BLOCK;
         case AMERICAN -> Items.DIAMOND_BLOCK;
         case FRENCH -> Items.QUARTZ_BLOCK;
         case BRITISH -> Items.LAPIS_BLOCK;
         case ITALIAN -> Items.EMERALD_BLOCK;
         case ROMANIAN -> Items.COPPER_BLOCK;
      };
   }
}
