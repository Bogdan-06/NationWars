package dev.moth.nationwars.persistence;

import com.google.gson.JsonElement;
import dev.moth.nationwars.JoinPolicy;
import dev.moth.nationwars.NationStore;

import java.util.Map;

/** Explicit, ordered save-schema migrations. Version 0 is the pre-0.4.0 schema. */
public final class DataMigrationService {
    public static final int CURRENT_DATA_VERSION = 2;

    public int migrate(NationDataSerializer.Document document) {
        int original = document.state().dataVersion;
        if (original < 0) {
            throw new IllegalArgumentException("Negative Nation Wars data version: " + original);
        }
        if (original > CURRENT_DATA_VERSION) {
            throw new IllegalStateException("Nation Wars data version " + original
                + " is newer than supported version " + CURRENT_DATA_VERSION);
        }
        int version = original;
        while (version < CURRENT_DATA_VERSION) {
            version = switch (version) {
                case 0 -> migrateVersionZeroToOne(document);
                case 1 -> migrateVersionOneToTwo(document);
                default -> throw new IllegalStateException("No Nation Wars migration from version " + version);
            };
        }
        return original;
    }

    private static int migrateVersionZeroToOne(NationDataSerializer.Document document) {
        restoreLegacyJoinPolicies(document);
        document.state().dataVersion = 1;
        return 1;
    }

    /** Version 2 introduces persisted puppet relations, proposals, and independence-war markers. */
    private static int migrateVersionOneToTwo(NationDataSerializer.Document document) {
        document.state().dataVersion = 2;
        return 2;
    }

    private static void restoreLegacyJoinPolicies(NationDataSerializer.Document document) {
        JsonElement savedNations = document.originalRoot().get("nations");
        if (savedNations == null || !savedNations.isJsonObject()) {
            return;
        }
        for (Map.Entry<String, JsonElement> entry : savedNations.getAsJsonObject().entrySet()) {
            JsonElement savedNation = entry.getValue();
            NationStore.Nation nation = document.state().nations.get(entry.getKey());
            if (nation != null && savedNation.isJsonObject() && !savedNation.getAsJsonObject().has("joinPolicy")) {
                nation.joinPolicy = JoinPolicy.INVITE_ONLY.name();
            }
        }
    }
}
