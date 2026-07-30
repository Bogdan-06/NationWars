package dev.moth.nationwars;

import dev.moth.nationwars.service.PuppetRules;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PuppetRulesTest {
    @Test
    void constantsAndClampingMatchTheIndependenceScale() {
        assertEquals(100, PuppetRules.INITIAL_INDEPENDENCE_POINTS);
        assertEquals(0, PuppetRules.clampPoints(-1));
        assertEquals(0, PuppetRules.clampPoints(0));
        assertEquals(100, PuppetRules.clampPoints(100));
        assertEquals(200, PuppetRules.clampPoints(200));
        assertEquals(200, PuppetRules.clampPoints(201));
    }

    @Test
    void claimThresholdIsInclusiveAtFifty() {
        assertFalse(PuppetRules.canClaim(0));
        assertFalse(PuppetRules.canClaim(49));
        assertTrue(PuppetRules.canClaim(50));
        assertTrue(PuppetRules.canClaim(200));
    }

    @Test
    void independenceWarThresholdIsStrictlyAboveOneHundredFifty() {
        assertFalse(PuppetRules.canStartIndependenceWar(0));
        assertFalse(PuppetRules.canStartIndependenceWar(150));
        assertTrue(PuppetRules.canStartIndependenceWar(151));
        assertTrue(PuppetRules.canStartIndependenceWar(200));
    }

    @Test
    void peacefulLiberationRequiresTwoHundredPoints() {
        assertFalse(PuppetRules.canPeacefullyLiberate(199));
        assertTrue(PuppetRules.canPeacefullyLiberate(200));
        assertTrue(PuppetRules.canPeacefullyLiberate(250));
    }

    @Test
    void annexationRequiresZeroPointsOrThreeLostWars() {
        assertTrue(PuppetRules.canAnnex(0, 0));
        assertFalse(PuppetRules.canAnnex(1, 0));
        assertFalse(PuppetRules.canAnnex(200, 2));
        assertTrue(PuppetRules.canAnnex(200, 3));
        assertTrue(PuppetRules.canAnnex(100, 4));
    }

    @Test
    void commandAndTradeDeltasClampAtBothEnds() {
        assertEquals(110, PuppetRules.agitate(100, false));
        assertEquals(200, PuppetRules.agitate(195, false));
        assertEquals(90, PuppetRules.pacify(100, false));
        assertEquals(0, PuppetRules.pacify(5, false));
        assertEquals(101, PuppetRules.applyForeignTrade(100, false));
        assertEquals(200, PuppetRules.applyForeignTrade(200, false));
        assertEquals(99, PuppetRules.applyMasterFavouredTrade(100, false));
        assertEquals(0, PuppetRules.applyMasterFavouredTrade(0, false));
    }

    @Test
    void frozenPointsRejectEveryAdjustment() {
        assertEquals(100, PuppetRules.agitate(100, true));
        assertEquals(100, PuppetRules.pacify(100, true));
        assertEquals(100, PuppetRules.applyForeignTrade(100, true));
        assertEquals(100, PuppetRules.applyMasterFavouredTrade(100, true));
        assertEquals(100, PuppetRules.adjustPoints(100, Integer.MAX_VALUE, true));
    }

    @Test
    void cooldownsUseRequiredDurationsAndOpenAtExactDeadline() {
        long now = 1_000_000L;
        long commandDeadline = PuppetRules.agitatePacifyCooldownUntil(now);
        long tradeDeadline = PuppetRules.tradePointCooldownUntil(now);

        assertEquals(now + 600L * 20L, commandDeadline);
        assertEquals(now + 120L * 20L, tradeDeadline);
        assertFalse(PuppetRules.cooldownReady(commandDeadline - 1L, commandDeadline));
        assertTrue(PuppetRules.cooldownReady(commandDeadline, commandDeadline));
        assertFalse(PuppetRules.cooldownReady(tradeDeadline - 1L, tradeDeadline));
        assertTrue(PuppetRules.cooldownReady(tradeDeadline, tradeDeadline));
    }

    @Test
    void cooldownDeadlineSaturatesInsteadOfOverflowing() {
        assertEquals(Long.MAX_VALUE, PuppetRules.agitatePacifyCooldownUntil(Long.MAX_VALUE - 1L));
        assertEquals(Long.MAX_VALUE, PuppetRules.tradePointCooldownUntil(Long.MAX_VALUE - 1L));
    }

    @Test
    void puppetFavouredTradeRequiresAOneSidedGift() {
        assertTrue(PuppetRules.isPuppetFavouredMasterTrade(10.0, 0, 0.0, 0.0, 0, 0.0));
        assertTrue(PuppetRules.isPuppetFavouredMasterTrade(0.0, 1, 0.0, 0.0, 0, 0.0));
        assertTrue(PuppetRules.isPuppetFavouredMasterTrade(0.0, 0, 2.5, 0.0, 0, 0.0));
        assertTrue(PuppetRules.isPuppetFavouredMasterTrade(10.0, 1, 2.5, 0.0, 0, 0.0));
        assertTrue(PuppetRules.isPuppetFavouredMasterTrade(10.0, 0, 0.0, -0.0, 0, -0.0));

        assertFalse(PuppetRules.isPuppetFavouredMasterTrade(0.0, 0, 0.0, 0.0, 0, 0.0));
        assertFalse(PuppetRules.isPuppetFavouredMasterTrade(10.0, 0, 0.0, 1.0, 0, 0.0));
        assertFalse(PuppetRules.isPuppetFavouredMasterTrade(10.0, 0, 0.0, 0.0, 1, 0.0));
        assertFalse(PuppetRules.isPuppetFavouredMasterTrade(10.0, 0, 0.0, 0.0, 0, 1.0));
        assertFalse(PuppetRules.isPuppetFavouredMasterTrade(Double.NaN, 0, 0.0, 0.0, 0, 0.0));
    }

    @Test
    void puppetTaxIsTwentyPercentRoundedToCents() {
        assertEquals(0.0, PuppetRules.puppetTax(0.0));
        assertEquals(0.0, PuppetRules.puppetTax(-10.0));
        assertEquals(0.0, PuppetRules.puppetTax(Double.NaN));
        assertEquals(20.0, PuppetRules.puppetTax(100.0));
        assertEquals(2.01, PuppetRules.puppetTax(10.03));
        assertEquals(24.69, PuppetRules.puppetTax(123.456));
    }
}
