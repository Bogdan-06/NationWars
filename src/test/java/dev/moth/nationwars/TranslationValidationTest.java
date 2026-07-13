package dev.moth.nationwars;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TranslationValidationTest {
    private static final Path LANG = Path.of("src/main/resources/assets/nationwars/lang");
    private static final Path JAVA = Path.of("src/main/java/dev/moth/nationwars");
    private static final Pattern KEY_LITERAL = Pattern.compile("\\\"(nationwars\\.[a-z0-9_.]+)\\\"");
    private static final Pattern PLACEHOLDER = Pattern.compile("%(?:(\\d+)\\$)?s");
    private static final Pattern FIXED_COMPONENT_LITERAL = Pattern.compile(
        "Component\\.literal\\(\\s*(?:\\(String\\)\\s*)?\\\"([^\\\"]*[A-Za-z][^\\\"]*)\\\"");
    private static final Pattern RAW_COMMAND_MESSAGE = Pattern.compile(
        "(?:NationCommands\\.)?(?:ok|fail)\\(\\s*[^,\\n]+,\\s*\\\"((?!nationwars\\.)[^\\\"]*[A-Za-z][^\\\"]*)\\\"");
    private static final Pattern SERVER_SIDE_TRANSLATION_RESOLUTION = Pattern.compile(
        "(getHoverName\\(\\)\\.getString\\(\\)|\\bI18n\\.get\\s*\\(|\\bLanguage\\.getInstance\\s*\\()");

    @Test
    void languageCatalogsAreValidCompleteAndCompatible() throws IOException {
        Map<String, String> english = readCatalog(LANG.resolve("en_us.json"));
        for (String catalogName : List.of("ro_ro.json", "es_es.json")) {
            Map<String, String> translated = readCatalog(LANG.resolve(catalogName));
            assertEquals(english.keySet(), translated.keySet(),
                () -> "English and " + catalogName + " translation keys differ");
            for (String key : english.keySet()) {
                assertEquals(argumentIndexes(english.get(key)), argumentIndexes(translated.get(key)),
                    () -> "Translation arguments differ for " + key + " in " + catalogName);
            }
        }
    }

    @Test
    void javaHasNoSuspiciousFixedPlayerFacingLiterals() throws IOException {
        List<String> suspicious = new ArrayList<>();
        for (Path path : javaFiles()) {
            String source = Files.readString(path, StandardCharsets.UTF_8);
            collectMatches(path, source, FIXED_COMPONENT_LITERAL, suspicious);
            collectMatches(path, source, RAW_COMMAND_MESSAGE, suspicious);
            collectMatches(path, source, SERVER_SIDE_TRANSLATION_RESOLUTION, suspicious);
        }
        assertTrue(suspicious.isEmpty(), () -> "Suspicious player-facing English literals remain:\n"
            + String.join("\n", suspicious));
    }

    @Test
    void reportsUnusedKeysWherePractical() throws IOException {
        Map<String, String> english = readCatalog(LANG.resolve("en_us.json"));
        Set<String> referenced = new HashSet<>();
        for (Path path : javaFiles()) {
            Matcher matcher = KEY_LITERAL.matcher(Files.readString(path, StandardCharsets.UTF_8));
            while (matcher.find()) {
                referenced.add(matcher.group(1));
            }
        }
        List<String> unused = english.keySet().stream().filter(key -> !referenced.contains(key)).sorted().toList();
        System.out.println("Translation validation: " + english.size() + " keys; " + unused.size()
            + " not statically referenced (dynamic doctrine, ideology, mission, and status keys are expected)." );
        unused.stream().limit(30).forEach(key -> System.out.println("  dynamic-or-unused: " + key));
    }

    private static Map<String, String> readCatalog(Path path) throws IOException {
        // Stream the object first because Gson's normal tree parser keeps the last duplicate silently.
        Map<String, String> values = new LinkedHashMap<>();
        try (Reader input = Files.newBufferedReader(path, StandardCharsets.UTF_8);
             JsonReader reader = new JsonReader(input)) {
            reader.beginObject();
            while (reader.hasNext()) {
                String key = reader.nextName();
                assertTrue(!values.containsKey(key), () -> "Duplicate translation key in " + path + ": " + key);
                JsonElement value = JsonParser.parseReader(reader);
                assertTrue(value.isJsonPrimitive() && value.getAsJsonPrimitive().isString(),
                    () -> "Translation value must be a string in " + path + ": " + key);
                values.put(key, value.getAsString());
            }
            reader.endObject();
        }
        return values;
    }

    private static Set<Integer> argumentIndexes(String value) {
        Set<Integer> indexes = new HashSet<>();
        int ordinary = 1;
        Matcher matcher = PLACEHOLDER.matcher(value);
        while (matcher.find()) {
            if (matcher.group(1) == null) {
                indexes.add(ordinary++);
            } else {
                indexes.add(Integer.parseInt(matcher.group(1)));
            }
        }
        return indexes;
    }

    private static List<Path> javaFiles() throws IOException {
        try (Stream<Path> stream = Files.walk(JAVA)) {
            return stream.filter(path -> path.toString().endsWith(".java")).sorted().toList();
        }
    }

    private static void collectMatches(Path path, String source, Pattern pattern, List<String> output) {
        Matcher matcher = pattern.matcher(source);
        while (matcher.find()) {
            int line = 1;
            for (int index = 0; index < matcher.start(); index++) {
                if (source.charAt(index) == '\n') {
                    line++;
                }
            }
            output.add(path + ":" + line + ": " + matcher.group(1));
        }
    }
}
