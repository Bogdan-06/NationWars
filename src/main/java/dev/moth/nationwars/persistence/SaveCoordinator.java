package dev.moth.nationwars.persistence;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Owns the immediate temp-file, backup, atomic-replace, and recovery mechanics. */
public final class SaveCoordinator {
    private final Path file;
    private final Path temporary;
    private final Path backup;

    public SaveCoordinator(Path file) {
        this.file = file;
        this.temporary = file.resolveSibling(file.getFileName() + ".tmp");
        this.backup = file.resolveSibling(file.getFileName() + ".bak");
    }

    public Path file() {
        return file;
    }

    public Path temporary() {
        return temporary;
    }

    public Path backup() {
        return backup;
    }

    public NationDataSerializer.Document read(Path source, NationDataSerializer serializer) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(source, StandardCharsets.UTF_8)) {
            return serializer.read(reader);
        }
    }

    public synchronized void save(NationDataSerializer serializer, NationDataSerializer.Document document) throws IOException {
        Files.createDirectories(file.getParent());
        try {
            try (BufferedWriter writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                serializer.write(writer, document);
            }
            if (Files.exists(file)) {
                Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
            }
            moveReplacing(temporary, file);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public void promoteTemporary() throws IOException {
        Files.createDirectories(file.getParent());
        moveReplacing(temporary, file);
    }

    public void removeTemporary() throws IOException {
        Files.deleteIfExists(temporary);
    }

    public Path createMigrationBackup(Path source, int version) throws IOException {
        Path migrationBackup = file.resolveSibling(file.getFileName() + ".pre-migration-v" + version + ".bak");
        Files.copy(source, migrationBackup, StandardCopyOption.REPLACE_EXISTING);
        return migrationBackup;
    }

    public Path preserveCorrupt(Path source) throws IOException {
        Path preserved = source.resolveSibling(source.getFileName() + ".corrupt-" + System.currentTimeMillis());
        Files.move(source, preserved, StandardCopyOption.REPLACE_EXISTING);
        return preserved;
    }

    private static void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
