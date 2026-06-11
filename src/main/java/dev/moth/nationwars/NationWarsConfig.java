/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.gson.Gson
 *  com.google.gson.GsonBuilder
 *  net.neoforged.fml.loading.FMLPaths
 */
package dev.moth.nationwars;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.moth.nationwars.Doctrine;
import dev.moth.nationwars.NationWars;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.LinkedHashMap;
import java.util.Map;
import net.neoforged.fml.loading.FMLPaths;

public final class NationWarsConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static NationWarsConfig current = new NationWarsConfig();
    public boolean enforceDoctrineLimits = true;
    public int defaultDoctrineLimit = 1;
    public Map<String, Integer> doctrineLimits = new LinkedHashMap<String, Integer>();
    public boolean setOpenPacPrimaryPartySystem = true;

    private NationWarsConfig() {
    }

    public static NationWarsConfig get() {
        return current;
    }

    public static void load() {
        Path file = FMLPaths.CONFIGDIR.get().resolve("nationwars-server.json");
        NationWarsConfig config = new NationWarsConfig();
        if (Files.exists(file, new LinkOption[0])) {
            try (BufferedReader reader = Files.newBufferedReader(file);){
                NationWarsConfig loaded = (NationWarsConfig)GSON.fromJson((Reader)reader, NationWarsConfig.class);
                if (loaded != null) {
                    config = loaded;
                }
            }
            catch (IOException | RuntimeException exception) {
                NationWars.LOGGER.warn("Could not load Nation Wars config from {}. Using defaults.", (Object)file, (Object)exception);
            }
        }
        config.normalize();
        current = config;
        current.save(file);
    }

    public int doctrineLimit(Doctrine doctrine) {
        if (!this.enforceDoctrineLimits) {
            return Integer.MAX_VALUE;
        }
        int limit = this.doctrineLimits.getOrDefault(doctrine.id, this.defaultDoctrineLimit);
        return limit <= 0 ? Integer.MAX_VALUE : limit;
    }

    private void normalize() {
        if (this.doctrineLimits == null) {
            this.doctrineLimits = new LinkedHashMap<String, Integer>();
        }
        this.defaultDoctrineLimit = Math.max(0, this.defaultDoctrineLimit);
        for (Doctrine doctrine : Doctrine.values()) {
            this.doctrineLimits.putIfAbsent(doctrine.id, this.defaultDoctrineLimit);
        }
        this.doctrineLimits.replaceAll((id, limit) -> limit == null ? this.defaultDoctrineLimit : Math.max(0, limit));
    }

    private void save(Path file) {
        try {
            Files.createDirectories(file.getParent(), new FileAttribute[0]);
            try (BufferedWriter writer = Files.newBufferedWriter(file, new OpenOption[0]);){
                GSON.toJson((Object)this, (Appendable)writer);
            }
        }
        catch (IOException exception) {
            NationWars.LOGGER.warn("Could not save Nation Wars config to {}.", (Object)file, (Object)exception);
        }
    }
}

