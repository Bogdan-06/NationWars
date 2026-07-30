package dev.moth.nationwars.service;

import dev.moth.nationwars.Doctrine;
import dev.moth.nationwars.NationRules;

/** Pure, behavior-locked Nation Wars economy calculations shared by commands and events. */
public final class EconomyService {
    public static final double BASE_CLAIM_COST = 100.0;
    public static final double MAINTENANCE_PER_CLAIM = 8.0;
    public static final double CAPTURED_CLAIM_MAINTENANCE_MULTIPLIER = 2.0;
    public static final double AMERICAN_CITY_INCOME_MULTIPLIER = 0.2;
    public static final double BRITISH_PORT_INCOME_MULTIPLIER = 0.1;
    public static final double ADDITIONAL_MEMBER_INCOME_PER_CYCLE = 8.0;

    private EconomyService() {
    }

    public static double claimCost(int currentClaims, int capitalDistanceChunks, Doctrine doctrine) {
        double expansion = 1.0 + Math.max(0, currentClaims - 1) * 0.1;
        double distance = doctrine.distanceClaimScaling ? 1.0 + Math.max(0, capitalDistanceChunks) * 0.03 : 1.0;
        return roundMoney(BASE_CLAIM_COST * expansion * distance * doctrine.claimCostMultiplier);
    }

    public static double capitalIncomePerCycle(Doctrine doctrine, int upgradeLevel) {
        return NationRules.capitalIncome(doctrine.id, upgradeLevel);
    }

    public static double currentIncomePerCycle(Doctrine doctrine, int upgradeLevel, boolean capitalActive,
                                                int activeCoastClaims, int activeCityClaims, int memberCount) {
        double capitalIncome = capitalIncomePerCycle(doctrine, upgradeLevel);
        double income = capitalActive ? capitalIncome : 0.0;
        if (doctrine == Doctrine.BRITISH) {
            income += Math.max(0, activeCoastClaims) * capitalIncome * BRITISH_PORT_INCOME_MULTIPLIER;
        }
        if (doctrine == Doctrine.AMERICAN) {
            income += Math.max(0, activeCityClaims) * capitalIncome * AMERICAN_CITY_INCOME_MULTIPLIER;
        }
        double memberIncome = Math.max(0, memberCount - 1) * ADDITIONAL_MEMBER_INCOME_PER_CYCLE;
        return roundMoney(income * doctrine.incomeMultiplier + memberIncome);
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
