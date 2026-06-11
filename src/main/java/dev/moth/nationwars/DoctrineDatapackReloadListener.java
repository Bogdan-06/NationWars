/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.gson.Gson
 *  com.google.gson.GsonBuilder
 *  com.google.gson.JsonElement
 *  com.google.gson.JsonObject
 *  net.minecraft.resources.ResourceLocation
 *  net.minecraft.server.packs.resources.PreparableReloadListener
 *  net.minecraft.server.packs.resources.ResourceManager
 *  net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener
 *  net.minecraft.util.profiling.ProfilerFiller
 *  net.neoforged.bus.api.SubscribeEvent
 *  net.neoforged.neoforge.event.AddReloadListenerEvent
 */
package dev.moth.nationwars;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.moth.nationwars.Doctrine;
import dev.moth.nationwars.NationWars;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

public final class DoctrineDatapackReloadListener
extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String DIRECTORY = "nationwars/doctrines";

    public DoctrineDatapackReloadListener() {
        super(GSON, DIRECTORY);
    }

    @SubscribeEvent
    public static void addReloadListeners(AddReloadListenerEvent event) {
        event.addListener((PreparableReloadListener)new DoctrineDatapackReloadListener());
    }

    protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager resourceManager, ProfilerFiller profiler) {
        Doctrine.resetAllToDefaults();
        int applied = 0;
        for (Map.Entry<ResourceLocation, JsonElement> entry : entries.entrySet()) {
            ResourceLocation location = entry.getKey();
            JsonElement element = entry.getValue();
            if (!element.isJsonObject()) {
                NationWars.LOGGER.warn("Ignoring doctrine override {} because it is not a JSON object.", (Object)location);
                continue;
            }
            try {
                JsonObject json = element.getAsJsonObject();
                String doctrineId = DoctrineDatapackReloadListener.doctrineId(location, json);
                Optional<Doctrine> doctrine = Doctrine.byId(doctrineId);
                if (doctrine.isEmpty()) {
                    NationWars.LOGGER.warn("Ignoring doctrine override {} because '{}' is not an existing doctrine id.", (Object)location, (Object)doctrineId);
                    continue;
                }
                doctrine.get().applyOverride(json);
                ++applied;
            }
            catch (RuntimeException exception) {
                NationWars.LOGGER.warn("Ignoring invalid doctrine override {}.", (Object)location, (Object)exception);
            }
        }
        if (!entries.isEmpty()) {
            NationWars.LOGGER.info("Loaded {} Nation Wars doctrine override(s).", (Object)applied);
        }
    }

    private static String doctrineId(ResourceLocation location, JsonObject json) {
        JsonElement id = json.get("id");
        if (id != null && !id.isJsonNull()) {
            return id.getAsString().trim().toLowerCase(Locale.ROOT);
        }
        String path = location.getPath();
        int slash = path.lastIndexOf(47);
        if (slash >= 0) {
            path = path.substring(slash + 1);
        }
        return path.toLowerCase(Locale.ROOT);
    }
}

