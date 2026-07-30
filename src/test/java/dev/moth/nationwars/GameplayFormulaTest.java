package dev.moth.nationwars;

import dev.moth.nationwars.service.EconomyService;
import dev.moth.nationwars.service.WarService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GameplayFormulaTest {
    @Test
    void claimCostsApplyExpansionDoctrineAndGlobalMultipliersWithoutIsolation() {
        assertEquals(100.0, EconomyService.claimCost(1, 0, Doctrine.GERMAN, 1.0));
        assertEquals(110.0, EconomyService.claimCost(2, 0, Doctrine.GERMAN, 1.0));
        assertEquals(80.0, EconomyService.claimCost(1, 0, Doctrine.SOVIET, 1.0));
        assertEquals(100.0, EconomyService.claimCost(1, 10, Doctrine.AMERICAN, 1.0));
        assertEquals(50.0, EconomyService.claimCost(1, 0, Doctrine.GERMAN, 0.5));
    }

    @Test
    void maintenanceExcludesOwnedCapitalAndComposesAllMultipliers() {
        assertEquals(0.0, EconomyService.maintenanceDue(1, 0, true, 1, Doctrine.GERMAN, false, false, 0, false, 1.0));
        assertEquals(10.8, EconomyService.maintenanceDue(2, 0, true, 1, Doctrine.GERMAN, false, false, 0, false, 1.0));
        assertEquals(36.0, EconomyService.maintenanceDue(3, 1, true, 1, Doctrine.FRENCH, true, true, 0, false, 1.0));
        assertEquals(9.6, EconomyService.maintenanceDue(2, 0, true, 1, Doctrine.ROMANIAN, false, false, 2, false, 1.0));
        assertEquals(8.8, EconomyService.maintenanceDue(2, 0, true, 1, Doctrine.ROMANIAN, false, false, 0, true, 1.0));
        assertEquals(9.72, EconomyService.maintenanceDue(2, 0, true, 1, Doctrine.GERMAN, false, false, 0, false, 0.9));
        assertEquals(21.6, EconomyService.maintenanceDue(2, 0, false, 1, Doctrine.GERMAN, false, false, 0, false, 1.0));
        assertEquals(6.4, EconomyService.maintenanceDue(2, 0, true, 1, Doctrine.ITALIAN, false, true, 0, false, 1.0));
        assertEquals(48.0, EconomyService.maintenanceDue(5, 0, true, 1, Doctrine.BRITISH, false, false, 0, false, 1.0));
        assertEquals(24.0, EconomyService.maintenanceDue(4, 0, true, 1, Doctrine.BRITISH, false, false, 0, false, 1.0));
    }

    @Test
    void currentIncomeCycleUsesCapitalStructureAndAdditionalMemberRules() {
        assertEquals(12000, NationEvents.INCOME_INTERVAL_TICKS);
        assertEquals(120.0, EconomyService.currentIncomePerCycle(Doctrine.GERMAN, 0, true, 1, 10, 1.0));
        assertEquals(144.0, EconomyService.currentIncomePerCycle(Doctrine.GERMAN, 0, true, 4, 10, 1.0));
        assertEquals(192.0, EconomyService.currentIncomePerCycle(Doctrine.GERMAN, 0, true, 20, 10, 1.0));
        assertEquals(24.0, EconomyService.currentIncomePerCycle(Doctrine.SOVIET, 0, true, 4, 10, 1.0));
        assertEquals(48.0, EconomyService.currentIncomePerCycle(Doctrine.SOVIET, 4, true, 4, 10, 1.0));
        assertEquals(24.0, EconomyService.currentIncomePerCycle(Doctrine.GERMAN, 0, false, 4, 10, 1.0));
        assertEquals(168.0, EconomyService.currentIncomePerCycle(Doctrine.BRITISH, 0, true, 4, 10, 1.0));
        assertEquals(180.0, EconomyService.currentIncomePerCycle(Doctrine.AMERICAN, 0, true, 1, 10, 1.0));
        assertEquals(84.0, EconomyService.currentIncomePerCycle(Doctrine.GERMAN, 0, true, 4, 10, 0.5));
        assertEquals(120.0, EconomyService.capitalIncomePerCycle(Doctrine.AMERICAN, 4));
        assertEquals(144.0, EconomyService.capitalIncomePerCycle(Doctrine.GERMAN, 4));
        assertEquals(24.0, EconomyService.capitalIncomePerCycle(Doctrine.SOVIET, 4));
        assertEquals(200.0, EconomyService.warClaimLossPenalty(Doctrine.AMERICAN));
        assertEquals(0.0, EconomyService.warClaimLossPenalty(Doctrine.BRITISH));
    }

    @Test
    void warTimersPreserveModifierOrderAndValues() {
        assertEquals(90, WarService.justificationSeconds(Doctrine.FRENCH, Doctrine.ITALIAN));
        assertEquals(80, WarService.justificationSeconds(Doctrine.GERMAN, Doctrine.ROMANIAN));
        assertEquals(60, WarService.captureSeconds(Doctrine.GERMAN, Doctrine.FRENCH, false, 1));
        assertEquals(35, WarService.captureSeconds(Doctrine.GERMAN, Doctrine.BRITISH, false, 1));
        assertEquals(35, WarService.captureSeconds(Doctrine.GERMAN, Doctrine.ITALIAN, false, 1));
        assertEquals(60, WarService.captureSeconds(Doctrine.ITALIAN, Doctrine.GERMAN, true, 1));
        assertEquals(70, WarService.captureSeconds(Doctrine.GERMAN, Doctrine.SOVIET, false, 1));
        assertEquals(35, WarService.captureSeconds(Doctrine.GERMAN, Doctrine.SOVIET, false, 2));
    }
}
