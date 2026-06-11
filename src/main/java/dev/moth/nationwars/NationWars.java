/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.logging.LogUtils
 *  net.neoforged.bus.api.IEventBus
 *  net.neoforged.fml.ModContainer
 *  net.neoforged.fml.common.Mod
 *  net.neoforged.neoforge.common.NeoForge
 *  org.slf4j.Logger
 */
package dev.moth.nationwars;

import com.mojang.logging.LogUtils;
import dev.moth.nationwars.DoctrineDatapackReloadListener;
import dev.moth.nationwars.NationCommands;
import dev.moth.nationwars.NationEvents;
import dev.moth.nationwars.OpacClaimsBridge;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(value="nationwars")
public final class NationWars {
    public static final String MODID = "nationwars";
    public static final Logger LOGGER = LogUtils.getLogger();

    public NationWars(IEventBus modBus, ModContainer container) {
        NeoForge.EVENT_BUS.register(NationCommands.class);
        NeoForge.EVENT_BUS.register(NationEvents.class);
        NeoForge.EVENT_BUS.register(DoctrineDatapackReloadListener.class);
        NeoForge.EVENT_BUS.register(OpacClaimsBridge.class);
    }
}

