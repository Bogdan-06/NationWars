package dev.moth.nationwars;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Test
    void checklistTenDoctrineDefaultsMatchTheReleaseRules() {
        assertEquals(0.5, Doctrine.SOVIET.maintenanceMultiplier);
        assertEquals(1.25, Doctrine.SOVIET.marketBuyMultiplier);
        assertEquals(0.8, Doctrine.AMERICAN.marketBuyMultiplier);
        // American Dream is passive-capital-only; resource rewards retain the
        // existing doctrine income multiplier.
        assertEquals(1.0, Doctrine.AMERICAN.incomeMultiplier);
        assertFalse(Doctrine.AMERICAN.distanceClaimScaling);
        assertFalse(Doctrine.AMERICAN.canBuyCities);
    }

    @Test
    void checklistTenServerConfigurationDefaultsPreserveNormalGameplay() {
        NationWarsConfig config = NationWarsConfig.get();
        assertTrue(config.stealing);
        assertEquals(1.0, config.maintenanceMultiplier);
        assertEquals(1.0, config.claimCostMultiplier);
        assertEquals(1.0, config.incomeMultiplier);
        assertEquals(10, config.memberIncome);
    }

    @Test
    void upgradeIncomeHandlesNormalSovietAndAmericanNations() {
        assertEquals(144.0, NationRules.capitalIncome("GER", 4));
        assertEquals(24.0, NationRules.capitalIncome("SOV", 4));
        assertEquals(120.0, NationRules.capitalIncome("USA", 4));
    }

    @Test
    void incomingPeaceColumnsAreOrientedForTheReceiver() {
        assertTrue(NationRules.incomingDemandUsesOwnNation(true));
        assertFalse(NationRules.incomingDemandUsesOwnNation(false));
    }

    @Test
    void tradeFlipPreservesReceiverPerspectiveAndIncomeTerms() {
        NationRules.TradeTerms source = new NationRules.TradeTerms(200.0, 500.0, 2.0, 7.0, true);
        NationRules.TradeTerms flipped = NationRules.flipTradeTerms(source);
        assertEquals(500.0, flipped.requestedMoney());
        assertEquals(200.0, flipped.offeredMoney());
        assertEquals(7.0, flipped.requestedIncome());
        assertEquals(2.0, flipped.offeredIncome());
        assertTrue(flipped.incomeTermsSpecified());
    }

    @Test
    void zeroIncomeTermsCanEndAnExistingAgreement() {
        assertTrue(NationRules.isTradeEmpty(0, 0, 0.0, 0.0, 0.0, 0.0, false));
        assertFalse(NationRules.isTradeEmpty(0, 0, 0.0, 0.0, 0.0, 0.0, true));
        assertEquals(0.0, NationRules.incomeAgreementNet(0.0, 0.0));
        assertEquals(4.0, NationRules.incomeAgreementNet(7.0, 3.0));
        assertEquals(-4.0, NationRules.incomeAgreementNet(3.0, 7.0));
    }

    @Test
    void doctrineOverridesRejectNonFiniteNumbers() {
        assertThrows(IllegalArgumentException.class, () -> Doctrine.finiteAtLeast(Double.NaN, 0.0, "income_multiplier"));
        assertThrows(IllegalArgumentException.class, () -> Doctrine.finiteAtLeast(Double.POSITIVE_INFINITY, 0.0, "income_multiplier"));
        assertEquals(0.0, Doctrine.finiteAtLeast(-3.0, 0.0, "income_multiplier"));
    }

    @Test
    void newlyCreatedNationDefaultsToInviteOnly() {
        assertEquals(JoinPolicy.INVITE_ONLY, new NationStore.Nation().joinPolicy());
    }

    @Test
    void nationJoinPoliciesAcceptFriendlySpellings() {
        assertEquals(JoinPolicy.INVITE_ONLY, JoinPolicy.parse("invite-only").orElseThrow());
        assertEquals(JoinPolicy.CLOSED, JoinPolicy.parse("closed").orElseThrow());
        assertTrue(JoinPolicy.parse("unknown").isEmpty());
    }

    @Test
    void joiningInviteOnlyNationWithoutInvitationFails() {
        assertFalse(JoinPolicy.INVITE_ONLY.allowsJoin(false));
    }

    @Test
    void joiningOpenNationWithoutInvitationSucceeds() {
        assertTrue(JoinPolicy.OPEN.allowsJoin(false));
    }
}
