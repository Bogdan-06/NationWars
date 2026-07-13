package dev.moth.nationwars.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.moth.nationwars.NationStore;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Converts the persisted JSON document without making gameplay decisions. */
public final class NationDataSerializer {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Set<String> KNOWN_ROOT_FIELDS = java.util.Arrays.stream(NationStore.State.class.getFields())
        .map(Field::getName).collect(Collectors.toUnmodifiableSet());

    public Document read(Reader reader) throws IOException {
        JsonElement parsed;
        try {
            parsed = JsonParser.parseReader(reader);
        } catch (RuntimeException exception) {
            throw new IOException("Nation Wars data is not valid JSON", exception);
        }
        if (!parsed.isJsonObject()) {
            throw new IOException("Nation Wars data root must be a JSON object");
        }
        JsonObject root = parsed.getAsJsonObject();
        NationStore.State state;
        try {
            state = GSON.fromJson(root, NationStore.State.class);
        } catch (RuntimeException exception) {
            throw new IOException("Nation Wars data does not match the save schema", exception);
        }
        if (state == null) {
            throw new IOException("Nation Wars data file was empty");
        }
        Map<String, JsonElement> unknown = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            if (!KNOWN_ROOT_FIELDS.contains(entry.getKey())) {
                unknown.put(entry.getKey(), entry.getValue().deepCopy());
            }
        }
        return new Document(state, unknown, root.deepCopy());
    }

    public void write(Writer writer, Document document) throws IOException {
        JsonObject root = GSON.toJsonTree(document.state()).getAsJsonObject();
        mergeUnknownFields(root, document.originalRoot(), NationStore.State.class);
        document.unknownRootFields().forEach((key, value) -> {
            if (!root.has(key)) {
                root.add(key, value.deepCopy());
            }
        });
        GSON.toJson(root, writer);
    }

    private static void mergeUnknownFields(JsonObject target, JsonObject original, Class<?> schema) {
        Map<String, Field> fields = java.util.Arrays.stream(schema.getFields())
            .collect(Collectors.toMap(Field::getName, field -> field));
        original.entrySet().stream().filter(entry -> !fields.containsKey(entry.getKey()))
            .forEach(entry -> target.add(entry.getKey(), entry.getValue().deepCopy()));
        for (Map.Entry<String, Field> entry : fields.entrySet()) {
            JsonElement current = target.get(entry.getKey());
            JsonElement previous = original.get(entry.getKey());
            if (current != null && previous != null) {
                mergeKnownValue(current, previous, entry.getValue().getGenericType());
            }
        }
    }

    private static void mergeKnownValue(JsonElement target, JsonElement original, Type type) {
        if (type instanceof Class<?> valueClass) {
            if (isSaveObject(valueClass) && target.isJsonObject() && original.isJsonObject()) {
                mergeUnknownFields(target.getAsJsonObject(), original.getAsJsonObject(), valueClass);
            }
            return;
        }
        if (!(type instanceof ParameterizedType parameterized)) {
            return;
        }
        Type raw = parameterized.getRawType();
        if (raw == Map.class && target.isJsonObject() && original.isJsonObject()) {
            Type valueType = parameterized.getActualTypeArguments()[1];
            for (Map.Entry<String, JsonElement> entry : target.getAsJsonObject().entrySet()) {
                JsonElement previous = original.getAsJsonObject().get(entry.getKey());
                if (previous != null) {
                    mergeKnownValue(entry.getValue(), previous, valueType);
                }
            }
        } else if ((raw == List.class || raw == java.util.Collection.class) && target.isJsonArray() && original.isJsonArray()) {
            Type elementType = parameterized.getActualTypeArguments()[0];
            if (elementType instanceof Class<?> elementClass && isSaveObject(elementClass)) {
                Map<String, JsonElement> originalsById = new LinkedHashMap<>();
                original.getAsJsonArray().forEach(element -> originalsById.put(stableId(element), element));
                target.getAsJsonArray().forEach(element -> {
                    JsonElement previous = originalsById.get(stableId(element));
                    if (previous != null) {
                        mergeKnownValue(element, previous, elementClass);
                    }
                });
            }
        }
    }

    private static boolean isSaveObject(Class<?> type) {
        return type.getName().startsWith(NationStore.class.getName() + "$");
    }

    private static String stableId(JsonElement element) {
        if (!element.isJsonObject()) {
            return element.toString();
        }
        JsonObject object = element.getAsJsonObject();
        for (String key : List.of("id", "target", "claimId")) {
            if (object.has(key)) {
                return key + ":" + object.get(key);
            }
        }
        return object.toString();
    }

    public record Document(NationStore.State state, Map<String, JsonElement> unknownRootFields, JsonObject originalRoot) {
        public Document {
            unknownRootFields = new LinkedHashMap<>(unknownRootFields);
            originalRoot = originalRoot.deepCopy();
        }

        public static Document empty() {
            return new Document(new NationStore.State(), Map.of(), new JsonObject());
        }
    }
}
