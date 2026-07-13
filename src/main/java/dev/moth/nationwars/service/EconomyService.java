package dev.moth.nationwars.service;

import dev.moth.nationwars.Doctrine;
import dev.moth.nationwars.NationRules;

/** Pure, behavior-locked Nation Wars economy calculations shared by commands and events. */
public final class EconomyService {
    public static final double BASE_CLAIM_COST = 100.0;
    public static final double MAINTENANCE_PER_CLAIM = 8.0;
    public static final double CAPTURED_CLAIM_MAINTENANCE_MULTIPLIER = 2.0;
    public static final double CITY_INCOME_PER_MINUTE = 3.0;

    private EconomyService() {
    }

    public static double claimCost(int currentClaims, int capitalDistanceChunks, Doctrine doctrine) {
        double expansion = 1.0 + Math.max(0, currentClaims - 1) * 0.1;
        double distance = doctrine.distanceClaimScaling ? 1.0 + Math.max(0, capitalDistanceChunks) * 0.03 : 1.0;
        return roundMoney(BASE_CLAIM_COST * expansion * distance * doctrine.claimCostMultiplier);
    }

    public static double capitalIncomePerMinute(Doctrine doctrine, int upgradeLevel) {
        return NationRules.capitalIncome(doctrine.id, upgradeLevel);
    }

    public static double passiveIncomePerMinute(Doctrine doctrine, int upgradeLevel, boolean capitalActive,
                                                 int activeCoastClaims, int activeCityClaims) {
        double capitalIncome = capitalIncomePerMinute(doctrine, upgradeLevel);
        double income = capitalActive ? capitalIncome : 0.0;
        if (doctrine == Doctrine.BRITISH) {
            income += Math.max(0, activeCoastClaims) * capitalIncome * 0.25;
        }
        income += Math.max(0, activeCityClaims) * CITY_INCOME_PER_MINUTE;
        return roundMoney(income * doctrine.incomeMultiplier);
    }

    public static double maintenanceDue(int claims, int capturedClaims, Doctrine doctrine,
                                        boolean hasDeclaredActiveWar, int lostCoreClaims,
                                        boolean legacyLostCoreTerritory) {
        if (claims <= 1) {
            return 0.0;
        }
        double multiplier = doctrine.maintenanceMultiplier;
        if (doctrine == Doctrine.FRENCH && hasDeclaredActiveWar) {
            multiplier *= 1.5;
        }
        if (doctrine == Doctrine.ROMANIAN) {
            int losses = Math.max(0, lostCoreClaims);
            if (losses == 0 && legacyLostCoreTerritory) {
                losses = 1;
            }
            multiplier += losses * 0.1;
        }
        double normalDue = claims * MAINTENANCE_PER_CLAIM * multiplier;
        double capturedPremium = Math.max(0, capturedClaims) * MAINTENANCE_PER_CLAIM * multiplier
            * (CAPTURED_CLAIM_MAINTENANCE_MULTIPLIER - 1.0);
        return roundMoney(normalDue + capturedPremium);
    }

    public static boolean carolLifestyleActive(Doctrine doctrine, int usedIdeologies, int ideologyCount) {
        return doctrine.randomTreasuryDrain && usedIdeologies < ideologyCount;
    }

    private static double roundMoney(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
