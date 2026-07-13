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
import com.google.gson.annotations.SerializedName;
import dev.moth.nationwars.Doctrine;
import dev.moth.nationwars.NationWars;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.neoforged.fml.loading.FMLPaths;

public final class NationWarsConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static NationWarsConfig current = new NationWarsConfig();
    private static Path currentFile;
    public boolean enforceDoctrineLimits = true;
    public int defaultDoctrineLimit = 1;
    public Map<String, Integer> doctrineLimits = new LinkedHashMap<String, Integer>();
    public boolean setOpenPacPrimaryPartySystem = true;
    public boolean disableEspionage = false;
    public boolean scorchedEarth = true;
    public boolean instantWar = false;
    public boolean noMercy = false;
    public boolean factions = true;
    public boolean guarantees = true;
    public boolean leaveNation = false;
    public boolean rejoinNation = false;
    @SerializedName(value="satellites", alternate={"satelites"})
    public boolean satellites = false;
    public boolean claimNether = false;
    public boolean claimEnd = false;
    public boolean colonialism = false;
    public boolean allowTrade = true;
    public int spawnProtection = 200;
    public Set<String> disabledDoctrines = new LinkedHashSet<String>();

    private NationWarsConfig() {
    }

    public static NationWarsConfig get() {
        return current;
    }

    public static void load() {
        Path file = FMLPaths.CONFIGDIR.get().resolve("nationwars-server.json");
        currentFile = file;
        NationWarsConfig config = new NationWarsConfig();
        if (Files.exists(file, new LinkOption[0])) {
            try (BufferedReader reader = Files.newBufferedReader(file);){
                NationWarsConfig loaded = (NationWarsConfig)GSON.fromJson((Reader)reader, NationWarsConfig.class);
                if (loaded != null) {
                    config = loaded;
                } else {
                    throw new IllegalStateException("Config file contains no JSON object");
                }
            }
            catch (IOException | RuntimeException exception) {
                config.normalize();
                current = config;
                NationWars.LOGGER.warn("Could not load Nation Wars config from {}. Using in-memory defaults; the malformed file was left untouched.", (Object)file, (Object)exception);
                return;
            }
        }
        config.normalize();
        current = config;
        current.save();
    }

    public synchronized boolean save() {
        this.normalize();
        return this.save(currentFile != null ? currentFile : FMLPaths.CONFIGDIR.get().resolve("nationwars-server.json"));
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
        if (this.disabledDoctrines == null) {
            this.disabledDoctrines = new LinkedHashSet<String>();
        }
        LinkedHashSet<String> normalizedDisabled = new LinkedHashSet<String>();
        this.disabledDoctrines.forEach(id -> Doctrine.byId(id).ifPresent(doctrine -> normalizedDisabled.add(doctrine.id)));
        this.disabledDoctrines = normalizedDisabled;
        Map<String, Integer> normalizedLimits = new LinkedHashMap<String, Integer>();
        this.doctrineLimits.forEach((id, limit) -> Doctrine.byId(id).ifPresent(doctrine -> normalizedLimits.putIfAbsent(doctrine.id, limit)));
        this.doctrineLimits = normalizedLimits;
        this.defaultDoctrineLimit = Math.max(0, this.defaultDoctrineLimit);
        this.spawnProtection = Math.max(0, this.spawnProtection);
        for (Doctrine doctrine : Doctrine.values()) {
            this.doctrineLimits.putIfAbsent(doctrine.id, this.defaultDoctrineLimit);
        }
        this.doctrineLimits.replaceAll((id, limit) -> limit == null ? this.defaultDoctrineLimit : Math.max(0, limit));
    }

    public boolean isDoctrineDisabled(Doctrine doctrine) {
        return doctrine != null && this.disabledDoctrines.contains(doctrine.id);
    }

    public boolean setDoctrineDisabled(Doctrine doctrine, boolean disabled) {
        if (doctrine == null) {
            return false;
        }
        if (this.disabledDoctrines == null) {
            this.disabledDoctrines = new LinkedHashSet<String>();
        }
        boolean previouslyDisabled = this.disabledDoctrines.contains(doctrine.id);
        if (disabled) {
            this.disabledDoctrines.add(doctrine.id);
        } else {
            this.disabledDoctrines.remove(doctrine.id);
        }
        if (this.save()) {
            return true;
        }
        if (previouslyDisabled) {
            this.disabledDoctrines.add(doctrine.id);
        } else {
            this.disabledDoctrines.remove(doctrine.id);
        }
        return false;
    }

    private boolean save(Path file) {
        Path tempFile = null;
        Path backupTempFile = null;
        try {
            Path directory = file.toAbsolutePath().getParent();
            if (directory == null) {
                throw new IOException("Config path has no parent directory: " + file);
            }
            Files.createDirectories(directory);

            String fileName = file.getFileName().toString();
            tempFile = Files.createTempFile(directory, fileName + ".", ".tmp");
            try (var writer = Files.newBufferedWriter(tempFile, StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                GSON.toJson((Object)this, (Appendable)writer);
            }

            if (Files.exists(file, new LinkOption[0])) {
                Path backupFile = file.resolveSibling(fileName + ".bak");
                backupTempFile = Files.createTempFile(directory, fileName + ".backup.", ".tmp");
                Files.copy(file, backupTempFile, StandardCopyOption.REPLACE_EXISTING);
                replaceAtomically(backupTempFile, backupFile);
                backupTempFile = null;
            }

            replaceAtomically(tempFile, file);
            tempFile = null;
            return true;
        }
        catch (IOException | RuntimeException exception) {
            NationWars.LOGGER.warn("Could not save Nation Wars config to {}.", (Object)file, (Object)exception);
            return false;
        }
        finally {
            deleteTemporaryFile(tempFile);
            deleteTemporaryFile(backupTempFile);
        }
    }

    private static void replaceAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        }
        catch (AtomicMoveNotSupportedException exception) {
            NationWars.LOGGER.warn("Atomic file replacement is unavailable for {}. Falling back to a regular replacement.", (Object)target);
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteTemporaryFile(Path file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        }
        catch (IOException exception) {
            NationWars.LOGGER.warn("Could not remove temporary Nation Wars config file {}.", (Object)file, (Object)exception);
        }
    }
}
