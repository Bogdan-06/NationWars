package dev.moth.nationwars.persistence;

import dev.moth.nationwars.NationStore;
import dev.moth.nationwars.JoinPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NationRepositoryTest {
    @TempDir
    Path directory;

    @Test
    void loadsAndMigratesExistingUnversionedSaveWithoutChangingValues() throws Exception {
        Path file = directory.resolve("nationwars.json");
        Files.writeString(file, """
            {"players":{"00000000-0000-0000-0000-000000000001":123.45},"nextListingId":7}
            """, StandardCharsets.UTF_8);

        NationRepository repository = new NationRepository(file);
        NationRepository.LoadResult result = repository.load();

        assertTrue(result.migrated());
        assertEquals(0, result.originalVersion());
        assertEquals(2, result.state().dataVersion);
        assertEquals(123.45, result.state().players.get("00000000-0000-0000-0000-000000000001"));
        assertEquals(7, result.state().nextListingId);
        assertTrue(Files.exists(directory.resolve("nationwars.json.pre-migration-v0.bak")));
    }

    @Test
    void legacyNationWithoutPolicyMigratesToInviteOnly() throws Exception {
        Path file = directory.resolve("nationwars.json");
        Files.writeString(file, """
            {"nations":{"alpha":{"id":"alpha","name":"Alpha"}}}
            """, StandardCharsets.UTF_8);

        NationRepository.LoadResult result = new NationRepository(file).load();

        assertTrue(result.migrated());
        assertEquals(JoinPolicy.INVITE_ONLY, result.state().nations.get("alpha").joinPolicy());
    }

    @Test
    void explicitlyOpenNationRemainsOpenAfterLoading() throws Exception {
        Path file = directory.resolve("nationwars.json");
        Files.writeString(file, """
            {"nations":{"alpha":{"id":"alpha","joinPolicy":"OPEN"}}}
            """, StandardCharsets.UTF_8);

        NationRepository.LoadResult result = new NationRepository(file).load();

        assertTrue(result.migrated());
        assertEquals(JoinPolicy.OPEN, result.state().nations.get("alpha").joinPolicy());
    }

    @Test
    void explicitlyClosedNationRemainsClosedAfterLoading() throws Exception {
        Path file = directory.resolve("nationwars.json");
        Files.writeString(file, """
            {"nations":{"alpha":{"id":"alpha","joinPolicy":"CLOSED"}}}
            """, StandardCharsets.UTF_8);

        NationRepository.LoadResult result = new NationRepository(file).load();

        assertTrue(result.migrated());
        assertEquals(JoinPolicy.CLOSED, result.state().nations.get("alpha").joinPolicy());
    }

    @Test
    void versionOneMigratesToVersionTwoWithoutChangingExistingValues() throws Exception {
        Path file = directory.resolve("nationwars.json");
        Files.writeString(file, """
            {"dataVersion":1,"nextTradeOfferId":27,"nations":{"alpha":{"id":"alpha","name":"Alpha","joinPolicy":"OPEN","balance":321.45}}}
            """, StandardCharsets.UTF_8);

        NationRepository.LoadResult result = new NationRepository(file).load();

        assertTrue(result.migrated());
        assertEquals(1, result.originalVersion());
        assertEquals(2, result.state().dataVersion);
        assertEquals(27, result.state().nextTradeOfferId);
        assertEquals("Alpha", result.state().nations.get("alpha").name);
        assertEquals(321.45, result.state().nations.get("alpha").balance);
        assertEquals(JoinPolicy.OPEN, result.state().nations.get("alpha").joinPolicy());
        assertTrue(Files.exists(directory.resolve("nationwars.json.pre-migration-v1.bak")));
    }

    @Test
    void loadsCurrentSaveVersionWithoutMigration() throws Exception {
        Path file = directory.resolve("nationwars.json");
        Files.writeString(file, "{\"dataVersion\":2,\"nextTradeOfferId\":12}", StandardCharsets.UTF_8);

        NationRepository.LoadResult result = new NationRepository(file).load();

        assertFalse(result.migrated());
        assertEquals(2, result.state().dataVersion);
        assertEquals(12, result.state().nextTradeOfferId);
    }

    @Test
    void recoversCorruptedMainDataFromBackup() throws Exception {
        Path file = directory.resolve("nationwars.json");
        Files.writeString(file, "{broken", StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("nationwars.json.bak"),
            "{\"dataVersion\":2,\"nextSpyMissionId\":42}", StandardCharsets.UTF_8);

        NationRepository.LoadResult result = new NationRepository(file).load();

        assertEquals(NationRepository.LoadSource.BACKUP, result.source());
        assertEquals(42, result.state().nextSpyMissionId);
        assertTrue(Files.list(directory).anyMatch(path -> path.getFileName().toString().startsWith("nationwars.json.corrupt-")));
    }

    @Test
    void promotesACompleteInterruptedTemporaryWriteWhenMainIsMissing() throws Exception {
        Path file = directory.resolve("nationwars.json");
        Files.writeString(directory.resolve("nationwars.json.tmp"),
            "{\"dataVersion\":2,\"nextLandPurchaseOfferId\":19}", StandardCharsets.UTF_8);

        NationRepository.LoadResult result = new NationRepository(file).load();

        assertEquals(NationRepository.LoadSource.TEMPORARY, result.source());
        assertEquals(19, result.state().nextLandPurchaseOfferId);
        assertTrue(Files.exists(file));
        assertFalse(Files.exists(directory.resolve("nationwars.json.tmp")));
    }

    @Test
    void missingOptionalFieldsReceiveSchemaDefaultsAndUnknownRootsSurviveSave() throws Exception {
        Path file = directory.resolve("nationwars.json");
        Files.writeString(file, "{\"dataVersion\":2,\"nations\":{\"alpha\":{\"id\":\"alpha\",\"futureNationField\":17}},\"futureFeature\":{\"kept\":true}}", StandardCharsets.UTF_8);
        NationRepository repository = new NationRepository(file);

        NationRepository.LoadResult result = repository.load();
        assertNotNull(result.state().players);
        assertNotNull(result.state().claims);
        repository.save(result.state());

        String saved = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(saved.contains("\"futureFeature\""));
        assertTrue(saved.contains("\"kept\": true"));
        assertTrue(saved.contains("\"futureNationField\": 17"));
    }

    @Test
    void puppetRelationsAndProposalsRoundTripEveryPersistedField() throws Exception {
        Path file = directory.resolve("nationwars.json");
        Files.writeString(file, """
            {
              "dataVersion": 2,
              "nations": {
                "master": {"id":"master","name":"Master"},
                "puppet": {"id":"puppet","name":"Puppet"},
                "candidate": {"id":"candidate","name":"Candidate"}
              },
              "puppetRelations": {
                "puppet": {
                  "master":"master",
                  "puppet":"puppet",
                  "independencePoints":167,
                  "lostIndependenceWars":2,
                  "agitateCooldownUntil":1111,
                  "pacifyCooldownUntil":2222,
                  "tradePointCooldownUntil":3333
                }
              },
              "puppetProposals": {
                "master->candidate": {
                  "master":"master",
                  "puppet":"candidate",
                  "createdTick":4444
                }
              }
            }
            """, StandardCharsets.UTF_8);

        NationRepository repository = new NationRepository(file);
        NationRepository.LoadResult firstLoad = repository.load();
        NationStore.PuppetRelation relation = firstLoad.state().puppetRelations.get("puppet");
        assertEquals("master", relation.master);
        assertEquals("puppet", relation.puppet);
        assertEquals(167, relation.independencePoints);
        assertEquals(2, relation.lostIndependenceWars);
        assertEquals(1111L, relation.agitateCooldownUntil);
        assertEquals(2222L, relation.pacifyCooldownUntil);
        assertEquals(3333L, relation.tradePointCooldownUntil);
        NationStore.PuppetProposal proposal = firstLoad.state().puppetProposals.get("master->candidate");
        assertEquals("master", proposal.master);
        assertEquals("candidate", proposal.puppet);
        assertEquals(4444L, proposal.createdTick);

        repository.save(firstLoad.state());
        NationRepository.LoadResult secondLoad = new NationRepository(file).load();
        NationStore.PuppetRelation roundTripped = secondLoad.state().puppetRelations.get("puppet");
        assertEquals(167, roundTripped.independencePoints);
        assertEquals(2, roundTripped.lostIndependenceWars);
        assertEquals(1111L, roundTripped.agitateCooldownUntil);
        assertEquals(2222L, roundTripped.pacifyCooldownUntil);
        assertEquals(3333L, roundTripped.tradePointCooldownUntil);
        assertEquals(4444L, secondLoad.state().puppetProposals.get("master->candidate").createdTick);
    }

    @Test
    void invalidReferencesToDeletedNationsAreReportedAndRemoved() {
        NationStore.State state = new NationStore.State();
        NationStore.Nation existing = new NationStore.Nation();
        existing.id = "existing";
        state.nations.put(existing.id, existing);
        state.playerNation.put("valid-player", "deleted");
        state.claims.put("minecraft:overworld:0:0", "deleted");
        state.claims.put("minecraft:overworld:1:0", "existing");
        state.guarantees.put("existing", new java.util.LinkedHashSet<>(java.util.List.of("deleted")));

        DataIntegrityService.RepairReport report = DataIntegrityService.repairReferences(state);

        assertEquals(3, report.repairedReferences());
        assertTrue(state.playerNation.isEmpty());
        assertEquals("existing", state.claims.get("minecraft:overworld:1:0"));
        assertFalse(state.claims.containsKey("minecraft:overworld:0:0"));
        assertTrue(state.guarantees.get("existing").isEmpty());
    }
}
