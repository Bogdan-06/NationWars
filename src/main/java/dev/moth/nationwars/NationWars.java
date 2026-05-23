package dev.moth.nationwars;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod("nationwars")
public final class NationWars {
   public static final String MODID = "nationwars";
   public static final Logger LOGGER = LogUtils.getLogger();

   public NationWars(IEventBus modBus, ModContainer container) {
      NeoForge.EVENT_BUS.register(NationCommands.class);
      NeoForge.EVENT_BUS.register(NationEvents.class);
   }
}
