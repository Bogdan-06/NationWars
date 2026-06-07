package dev.moth.nationwars;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

public final class DoctrineDatapackReloadListener extends SimpleJsonResourceReloadListener {
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
   private static final String DIRECTORY = "nationwars/doctrines";

   public DoctrineDatapackReloadListener() {
      super(GSON, "nationwars/doctrines");
   }

   @SubscribeEvent
   public static void addReloadListeners(AddReloadListenerEvent event) {
      event.addListener(new DoctrineDatapackReloadListener());
   }

   protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager resourceManager, ProfilerFiller profiler) {
      Doctrine.resetAllToDefaults();
      int applied = 0;

      for (Entry<ResourceLocation, JsonElement> entry : entries.entrySet()) {
         ResourceLocation location = entry.getKey();
         JsonElement element = entry.getValue();
         if (!element.isJsonObject()) {
            NationWars.LOGGER.warn("Ignoring doctrine override {} because it is not a JSON object.", location);
         } else {
            try {
               JsonObject json = element.getAsJsonObject();
               String doctrineId = doctrineId(location, json);
               Optional<Doctrine> doctrine = Doctrine.byId(doctrineId);
               if (doctrine.isEmpty()) {
                  NationWars.LOGGER.warn("Ignoring doctrine override {} because '{}' is not an existing doctrine id.", location, doctrineId);
               } else {
                  doctrine.get().applyOverride(json);
                  applied++;
               }
            } catch (RuntimeException var12) {
               NationWars.LOGGER.warn("Ignoring invalid doctrine override {}.", location, var12);
            }
         }
      }

      if (!entries.isEmpty()) {
         NationWars.LOGGER.info("Loaded {} Nation Wars doctrine override(s).", applied);
      }
   }

   private static String doctrineId(ResourceLocation location, JsonObject json) {
      JsonElement id = json.get("id");
      if (id != null && !id.isJsonNull()) {
         return id.getAsString().trim().toLowerCase(Locale.ROOT);
      } else {
         String path = location.getPath();
         int slash = path.lastIndexOf(47);
         if (slash >= 0) {
            path = path.substring(slash + 1);
         }

         return path.toLowerCase(Locale.ROOT);
      }
   }
}
