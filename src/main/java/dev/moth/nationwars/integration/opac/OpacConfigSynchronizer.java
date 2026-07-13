package dev.moth.nationwars.integration.opac;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Safely edits only the explicitly owned OPAC keys and validates before keeping the result. */
public final class OpacConfigSynchronizer {
    public Result synchronize(Path config, boolean setPrimaryPartySystem) throws IOException {
        if (!Files.exists(config)) {
            return new Result(false, List.of());
        }
        Map<String, String> desired = new LinkedHashMap<>();
        desired.put("maxPlayerClaims", "0");
        if (setPrimaryPartySystem) {
            desired.put("primaryPartySystem", "\"nationwars\"");
            desired.put("partyOwnedClaims", "true");
        }

        List<String> original = Files.readAllLines(config, StandardCharsets.UTF_8);
        List<String> updated = new ArrayList<>(original);
        List<String> changed = new ArrayList<>();
        for (int index = 0; index < updated.size(); index++) {
            ParsedLine parsed = parse(updated.get(index));
            if (parsed == null || !desired.containsKey(parsed.key())) {
                continue;
            }
            String wanted = desired.get(parsed.key());
            if (!parsed.value().equals(wanted)) {
                updated.set(index, parsed.prefixThroughEquals() + " " + wanted + parsed.commentSuffix());
                changed.add(parsed.key() + ": " + parsed.value() + " -> " + wanted);
            }
        }
        if (changed.isEmpty()) {
            return new Result(false, List.of());
        }

        Path backup = config.resolveSibling(config.getFileName() + ".nationwars.bak");
        Path temporary = config.resolveSibling(config.getFileName() + ".nationwars.tmp");
        Files.copy(config, backup, StandardCopyOption.REPLACE_EXISTING);
        try {
            Files.write(temporary, updated, StandardCharsets.UTF_8);
            validate(temporary, desired, changed);
            moveReplacing(temporary, config);
            validate(config, desired, changed);
            return new Result(true, List.copyOf(changed));
        } catch (IOException | RuntimeException exception) {
            Files.copy(backup, config, StandardCopyOption.REPLACE_EXISTING);
            throw new IOException("OPAC configuration validation failed; restored " + backup, exception);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void validate(Path file, Map<String, String> desired, List<String> changed) throws IOException {
        Map<String, String> values = new LinkedHashMap<>();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            ParsedLine parsed = parse(line);
            if (parsed != null) {
                values.put(parsed.key(), parsed.value());
            }
        }
        for (String description : changed) {
            String key = description.substring(0, description.indexOf(':'));
            if (!desired.get(key).equals(values.get(key))) {
                throw new IOException("Expected " + key + " = " + desired.get(key));
            }
        }
    }

    private static ParsedLine parse(String line) {
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
            return null;
        }
        int equals = line.indexOf('=');
        if (equals < 0) {
            return null;
        }
        String key = line.substring(0, equals).trim();
        if (key.isEmpty()) {
            return null;
        }
        String remainder = line.substring(equals + 1).trim();
        int comment = remainder.indexOf(" #");
        String value = comment < 0 ? remainder : remainder.substring(0, comment).trim();
        String suffix = comment < 0 ? "" : remainder.substring(comment);
        return new ParsedLine(key, value, line.substring(0, equals + 1), suffix);
    }

    private static void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public record Result(boolean changed, List<String> changes) {
    }

    private record ParsedLine(String key, String value, String prefixThroughEquals, String commentSuffix) {
    }
}
