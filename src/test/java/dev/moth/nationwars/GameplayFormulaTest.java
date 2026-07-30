package dev.moth.nationwars;

import dev.moth.nationwars.service.EconomyService;
import dev.moth.nationwars.service.WarService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GameplayFormulaTest {
    @Test
    void claimCostsPreserveExpansionDoctrineAndAmericanDistanceScaling() {
        assertEquals(100.0, EconomyService.claimCost(1, 0, Doctrine.GERMAN));
        assertEquals(110.0, EconomyService.claimCost(2, 0, Doctrine.GERMAN));
        assertEquals(80.0, EconomyService.claimCost(1, 0, Doctrine.SOVIET));
        assertEquals(130.0, EconomyService.claimCost(1, 10, Doctrine.AMERICAN));
    }

    @Test
    void maintenancePreservesFirstClaimCapturedFrenchAndRomanianRules() {
        assertEquals(0.0, EconomyService.maintenanceDue(1, 0, Doctrine.GERMAN, false, 0, false));
        assertEquals(21.6, EconomyService.maintenanceDue(2, 0, Doctrine.GERMAN, false, 0, false));
        assertEquals(48.0, EconomyService.maintenanceDue(3, 1, Doctrine.FRENCH, true, 0, false));
        assertEquals(19.2, EconomyService.maintenanceDue(2, 0, Doctrine.ROMANIAN, false, 2, false));
        assertEquals(17.6, EconomyService.maintenanceDue(2, 0, Doctrine.ROMANIAN, false, 0, true));
    }

    @Test
    void currentIncomeCycleUsesCapitalStructureAndAdditionalMemberRules() {
        assertEquals(12000, NationEvents.INCOME_INTERVAL_TICKS);
        assertEquals(120.0, EconomyService.currentIncomePerCycle(Doctrine.GERMAN, 0, true, 0, 0, 1));
        assertEquals(144.0, EconomyService.currentIncomePerCycle(Doctrine.GERMAN, 0, true, 0, 0, 4));
        assertEquals(24.0, EconomyService.currentIncomePerCycle(Doctrine.SOVIET, 0, true, 3, 0, 4));
        assertEquals(48.0, EconomyService.currentIncomePerCycle(Doctrine.SOVIET, 4, true, 0, 0, 4));
        assertEquals(24.0, EconomyService.currentIncomePerCycle(Doctrine.GERMAN, 0, false, 0, 0, 4));
        assertEquals(132.0, EconomyService.currentIncomePerCycle(Doctrine.BRITISH, 0, true, 1, 0, 1));
        assertEquals(144.0, EconomyService.currentIncomePerCycle(Doctrine.AMERICAN, 0, true, 0, 1, 1));
        assertEquals(120.0, EconomyService.capitalIncomePerCycle(Doctrine.AMERICAN, 4));
        assertEquals(144.0, EconomyService.capitalIncomePerCycle(Doctrine.GERMAN, 4));
        assertEquals(24.0, EconomyService.capitalIncomePerCycle(Doctrine.SOVIET, 4));
    }

    @Test
    void warTimersPreserveModifierOrderAndValues() {
        assertEquals(90, WarService.justificationSeconds(Doctrine.FRENCH, Doctrine.ITALIAN));
        assertEquals(80, WarService.justificationSeconds(Doctrine.GERMAN, Doctrine.ROMANIAN));
        assertEquals(60, WarService.captureSeconds(Doctrine.GERMAN, Doctrine.FRENCH, false, false, false, 1));
        assertEquals(25, WarService.captureSeconds(Doctrine.GERMAN, Doctrine.BRITISH, true, false, false, 1));
        assertEquals(50, WarService.captureSeconds(Doctrine.GERMAN, Doctrine.ITALIAN, false, true, false, 1));
        assertEquals(60, WarService.captureSeconds(Doctrine.ITALIAN, Doctrine.GERMAN, false, false, true, 1));
        assertEquals(70, WarService.captureSeconds(Doctrine.GERMAN, Doctrine.SOVIET, false, false, false, 1));
        assertEquals(35, WarService.captureSeconds(Doctrine.GERMAN, Doctrine.SOVIET, false, false, false, 2));
    }
}
