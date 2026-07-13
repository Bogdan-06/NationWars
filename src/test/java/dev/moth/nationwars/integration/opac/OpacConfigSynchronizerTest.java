package dev.moth.nationwars.integration.opac;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpacConfigSynchronizerTest {
    @TempDir
    Path directory;

    @Test
    void changesOnlyOwnedSettingsAndCreatesBackup() throws Exception {
        Path config = directory.resolve("openpartiesandclaims-server.toml");
        String original = """
            maxPlayerClaims = 500
            primaryPartySystem = "openpartiesandclaims"
            partyOwnedClaims = false # keep this explanation
            unrelatedSetting = 73
            """;
        Files.writeString(config, original, StandardCharsets.UTF_8);

        OpacConfigSynchronizer.Result result = new OpacConfigSynchronizer().synchronize(config, true);

        String updated = Files.readString(config, StandardCharsets.UTF_8);
        assertTrue(result.changed());
        assertTrue(updated.contains("maxPlayerClaims = 0"));
        assertTrue(updated.contains("primaryPartySystem = \"nationwars\""));
        assertTrue(updated.contains("partyOwnedClaims = true # keep this explanation"));
        assertTrue(updated.contains("unrelatedSetting = 73"));
        assertEquals(original, Files.readString(directory.resolve("openpartiesandclaims-server.toml.nationwars.bak"), StandardCharsets.UTF_8));
    }

    @Test
    void leavesPrimaryPartySettingsUntouchedWhenThatFeatureIsDisabled() throws Exception {
        Path config = directory.resolve("openpartiesandclaims-server.toml");
        Files.writeString(config, "maxPlayerClaims=9\nprimaryPartySystem=\"other\"\npartyOwnedClaims=false\n", StandardCharsets.UTF_8);

        new OpacConfigSynchronizer().synchronize(config, false);

        String updated = Files.readString(config, StandardCharsets.UTF_8);
        assertTrue(updated.contains("maxPlayerClaims= 0"));
        assertTrue(updated.contains("primaryPartySystem=\"other\""));
        assertTrue(updated.contains("partyOwnedClaims=false"));
    }
}
