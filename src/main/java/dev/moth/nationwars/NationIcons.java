/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.core.registries.BuiltInRegistries
 *  net.minecraft.resources.ResourceLocation
 *  net.minecraft.world.item.Item
 *  net.minecraft.world.item.Items
 */
package dev.moth.nationwars;

import dev.moth.nationwars.Doctrine;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public final class NationIcons {
    private NationIcons() {
    }

    public static Item countryBlock(Doctrine doctrine) {
        ResourceLocation id = ResourceLocation.tryParse((String)doctrine.iconItemId);
        if (id == null) {
            return Items.PAPER;
        }
        return BuiltInRegistries.ITEM.getOptional(id).orElse(Items.PAPER);
    }
}

