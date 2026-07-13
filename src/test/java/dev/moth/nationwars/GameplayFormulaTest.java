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
    void passiveIncomePreservesCapitalPortCityAndUpgradeRules() {
        assertEquals(12.0, EconomyService.passiveIncomePerMinute(Doctrine.GERMAN, 0, true, 0, 0));
        assertEquals(18.0, EconomyService.passiveIncomePerMinute(Doctrine.BRITISH, 0, true, 1, 1));
        assertEquals(0.0, EconomyService.passiveIncomePerMinute(Doctrine.SOVIET, 0, true, 3, 0));
        assertEquals(12.0, EconomyService.capitalIncomePerMinute(Doctrine.AMERICAN, 4));
        assertEquals(36.0, EconomyService.capitalIncomePerMinute(Doctrine.GERMAN, 4));
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
