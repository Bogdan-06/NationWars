package dev.moth.nationwars;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public final class NationIcons {
   private NationIcons() {
   }

   public static Item countryBlock(Doctrine doctrine) {
      ResourceLocation id = ResourceLocation.tryParse(doctrine.iconItemId);
      return id == null ? Items.PAPER : BuiltInRegistries.ITEM.getOptional(id).orElse(Items.PAPER);
   }
}
