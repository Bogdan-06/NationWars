package dev.moth.nationwars;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class NationStoreTest {
    @Test
    void legacyDeadlineKeepsRemainingTime() {
        assertEquals(50_000_001_200L, PersistentTime.migrateDeadline(2_200L, 1_000L, 3_600L, 50_000_000_000L));
    }

    @Test
    void legacyDeadlineIsClampedToMechanicMaximum() {
        assertEquals(50_000_003_600L, PersistentTime.migrateDeadline(100_000L, 1_000L, 3_600L, 50_000_000_000L));
    }

    @Test
    void persistentDeadlineIsNotMigratedAgain() {
        long deadline = 50_000_001_200L;
        assertEquals(deadline, PersistentTime.migrateDeadline(deadline, 1_000L, 3_600L, 60_000_000_000L));
    }

    @Test
    void spyRecoveryLastsSixtySeconds() {
        assertEquals(61_200L, SpyRecovery.deadline(60_000L));
        assertEquals(60, SpyRecovery.SECONDS);
    }

    @Test
    void claimKeysRoundTripAndOnlyTouchInTheSameDimension() {
        ClaimKey claim = ClaimKey.parse("minecraft:overworld:-4:9");
        assertEquals("minecraft:overworld:-4:9", claim.id());
        assertTrue(claim.touches(new ClaimKey("minecraft:overworld", -3, 9)));
        assertEquals(false, claim.touches(new ClaimKey("minecraft:the_nether", -3, 9)));
    }

    @Test
    void legacyDoctrineNamesStillMigrateToShortIds() {
        assertEquals(Doctrine.GERMAN, Doctrine.byId("germany").orElseThrow());
        assertEquals(Doctrine.BRITISH, Doctrine.byId("UK").orElseThrow());
        assertEquals("ROM", Doctrine.byId("romanian").orElseThrow().id);
    }
}
