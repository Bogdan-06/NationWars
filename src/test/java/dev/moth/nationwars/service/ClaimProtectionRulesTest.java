package dev.moth.nationwars.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimProtectionRulesTest {
    @Test
    void scorchedEarthOnlyDisablesProtectionForAnUnlockedActiveEnemyWarClaim() {
        assertTrue(ClaimProtectionRules.disableForEnemyWarClaim(true, true, false, false));
        assertFalse(ClaimProtectionRules.disableForEnemyWarClaim(false, true, false, false));
        assertFalse(ClaimProtectionRules.disableForEnemyWarClaim(true, false, false, false));
        assertFalse(ClaimProtectionRules.disableForEnemyWarClaim(true, true, true, false));
        assertFalse(ClaimProtectionRules.disableForEnemyWarClaim(true, true, false, true));
    }

    @Test
    void stealingOnlyBlocksPaidAccessForVisitorsWhoBelongToANation() {
        assertTrue(ClaimProtectionRules.paidAccessAllowed(true, true));
        assertTrue(ClaimProtectionRules.paidAccessAllowed(true, false));
        assertTrue(ClaimProtectionRules.paidAccessAllowed(false, false));
        assertFalse(ClaimProtectionRules.paidAccessAllowed(false, true));
    }
}
