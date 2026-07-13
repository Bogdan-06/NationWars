package dev.moth.nationwars.persistence;

import dev.moth.nationwars.NationStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Loads and stores Nation Wars state while keeping recovery policy out of gameplay code. */
public final class NationRepository {
    private final NationDataSerializer serializer;
    private final DataMigrationService migrations;
    private final SaveCoordinator saves;
    private NationDataSerializer.Document document;

    public NationRepository(Path file) {
        this(new NationDataSerializer(), new DataMigrationService(), new SaveCoordinator(file));
    }

    NationRepository(NationDataSerializer serializer, DataMigrationService migrations, SaveCoordinator saves) {
        this.serializer = serializer;
        this.migrations = migrations;
        this.saves = saves;
    }

    public LoadResult load() throws IOException {
        List<String> diagnostics = new ArrayList<>();
        boolean existingDataFound = Files.exists(saves.file()) || Files.exists(saves.temporary()) || Files.exists(saves.backup());
        NationDataSerializer.Document loaded = null;
        Path source = null;
        LoadSource loadSource = LoadSource.NEW;

        if (!Files.exists(saves.file()) && Files.exists(saves.temporary())) {
            try {
                loaded = saves.read(saves.temporary(), serializer);
                saves.promoteTemporary();
                source = saves.file();
                loadSource = LoadSource.TEMPORARY;
                diagnostics.add("Recovered an interrupted Nation Wars temporary-file write.");
            } catch (IOException exception) {
                Path preserved = saves.preserveCorrupt(saves.temporary());
                diagnostics.add("Preserved an unreadable temporary Nation Wars file as " + preserved + ": " + exception.getMessage());
            }
        } else if (Files.exists(saves.file()) && Files.exists(saves.temporary())) {
            saves.removeTemporary();
            diagnostics.add("Removed a stale Nation Wars temporary file after finding the main save.");
        }

        if (loaded == null && Files.exists(saves.file())) {
            try {
                loaded = saves.read(saves.file(), serializer);
                source = saves.file();
                loadSource = LoadSource.MAIN;
            } catch (IOException exception) {
                Path preserved = saves.preserveCorrupt(saves.file());
                diagnostics.add("Preserved unreadable Nation Wars data as " + preserved + ": " + exception.getMessage());
            }
        }
        if (loaded == null && Files.exists(saves.backup())) {
            try {
                loaded = saves.read(saves.backup(), serializer);
                source = saves.backup();
                loadSource = LoadSource.BACKUP;
                diagnostics.add("Recovered Nation Wars data from backup " + saves.backup() + ".");
            } catch (IOException exception) {
                diagnostics.add("Failed to read Nation Wars backup " + saves.backup() + ": " + exception.getMessage());
            }
        }
        if (loaded == null) {
            if (existingDataFound) {
                throw new IOException("No readable Nation Wars main, temporary, or backup save remains. Existing files were not overwritten.");
            }
            loaded = NationDataSerializer.Document.empty();
        }

        int originalVersion = loaded.state().dataVersion;
        if (originalVersion < DataMigrationService.CURRENT_DATA_VERSION && source != null && Files.exists(source)) {
            Path migrationBackup = saves.createMigrationBackup(source, originalVersion);
            diagnostics.add("Created pre-migration Nation Wars backup " + migrationBackup + ".");
        }
        migrations.migrate(loaded);
        document = loaded;
        return new LoadResult(loaded.state(), loadSource, originalVersion, List.copyOf(diagnostics));
    }

    public synchronized void save(NationStore.State state) throws IOException {
        if (document == null) {
            document = new NationDataSerializer.Document(state, java.util.Map.of(), new com.google.gson.JsonObject());
        } else if (document.state() != state) {
            document = new NationDataSerializer.Document(state, document.unknownRootFields(), document.originalRoot());
        }
        saves.save(serializer, document);
    }

    public enum LoadSource {
        NEW, MAIN, BACKUP, TEMPORARY
    }

    public record LoadResult(NationStore.State state, LoadSource source, int originalVersion, List<String> diagnostics) {
        public boolean migrated() {
            return originalVersion < DataMigrationService.CURRENT_DATA_VERSION;
        }
    }
}
