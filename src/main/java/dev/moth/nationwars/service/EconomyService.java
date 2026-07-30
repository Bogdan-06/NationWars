package dev.moth.nationwars.service;

import dev.moth.nationwars.Doctrine;
import dev.moth.nationwars.NationRules;

/** Pure, behavior-locked Nation Wars economy calculations shared by commands and events. */
public final class EconomyService {
    public static final double BASE_CLAIM_COST = 100.0;
    public static final double MAINTENANCE_PER_CLAIM = 8.0;
    public static final double CAPTURED_CLAIM_MAINTENANCE_MULTIPLIER = 2.0;
    public static final double ADDITIONAL_MEMBER_INCOME_PER_CYCLE = 8.0;
    public static final double AMERICAN_WAR_CLAIM_LOSS = 200.0;
    public static final double AMERICAN_PASSIVE_INCOME_MULTIPLIER = 1.5;

    private EconomyService() {
    }

    public static double claimCost(int currentClaims, int capitalDistanceChunks, Doctrine doctrine,
                                   double globalClaimCostMultiplier) {
        double expansion = 1.0 + Math.max(0, currentClaims - 1) * 0.1;
        double distance = doctrine.distanceClaimScaling ? 1.0 + Math.max(0, capitalDistanceChunks) * 0.03 : 1.0;
        return roundMoney(BASE_CLAIM_COST * expansion * distance * doctrine.claimCostMultiplier
            * finiteNonNegative(globalClaimCostMultiplier, 1.0));
    }

    public static double capitalIncomePerCycle(Doctrine doctrine, int upgradeLevel) {
        return NationRules.capitalIncome(doctrine.id, upgradeLevel);
    }

    public static double currentIncomePerCycle(Doctrine doctrine, int upgradeLevel, boolean capitalActive,
                                                int memberCount, int memberLimit, double globalIncomeMultiplier) {
        double capitalIncome = capitalIncomePerCycle(doctrine, upgradeLevel);
        double income = capitalActive ? capitalIncome : 0.0;
        int countedMembers = Math.min(Math.max(0, memberCount), Math.max(0, memberLimit));
        double memberMultiplier = doctrine == Doctrine.BRITISH ? 2.0 : 1.0;
        double memberIncome = Math.max(0, countedMembers - 1) * ADDITIONAL_MEMBER_INCOME_PER_CYCLE * memberMultiplier;
        double passiveDoctrineMultiplier = doctrine.incomeMultiplier
            * (doctrine == Doctrine.AMERICAN ? AMERICAN_PASSIVE_INCOME_MULTIPLIER : 1.0);
        return roundMoney(income * passiveDoctrineMultiplier * finiteNonNegative(globalIncomeMultiplier, 1.0)
            + memberIncome);
    }

    public static double maintenanceDue(int claims, int capturedClaims, boolean ownsCapital, int memberCount,
                                        Doctrine doctrine, boolean hasDeclaredActiveWar, boolean hasAnyActiveWar,
                                        int lostCoreClaims, boolean legacyLostCoreTerritory,
                                        double globalMaintenanceMultiplier) {
        double multiplier = doctrine.maintenanceMultiplier;
        if (doctrine == Doctrine.ROMANIAN) {
            int losses = Math.max(0, lostCoreClaims);
            if (losses == 0 && legacyLostCoreTerritory) {
                losses = 1;
            }
            multiplier += losses * 0.1;
        }
        if (doctrine == Doctrine.FRENCH && hasDeclaredActiveWar) {
            multiplier *= 1.5;
        }
        if (doctrine == Doctrine.ITALIAN && hasAnyActiveWar) {
            multiplier *= 0.8;
        }
        if (urbanSprawlActive(doctrine, memberCount, claims)) {
            multiplier *= 1.5;
        }
        multiplier *= finiteNonNegative(globalMaintenanceMultiplier, 1.0);
        int billableClaims = Math.max(0, claims - (ownsCapital ? 1 : 0));
        double normalDue = billableClaims * MAINTENANCE_PER_CLAIM * multiplier;
        double capturedPremium = Math.max(0, capturedClaims) * MAINTENANCE_PER_CLAIM * multiplier
            * (CAPTURED_CLAIM_MAINTENANCE_MULTIPLIER - 1.0);
        return roundMoney(normalDue + capturedPremium);
    }

    public static boolean urbanSprawlActive(Doctrine doctrine, int memberCount, int claimCount) {
        return doctrine == Doctrine.BRITISH && claimCount >= Math.max(1, memberCount) * 5;
    }

    public static double warClaimLossPenalty(Doctrine doctrine) {
        return doctrine == Doctrine.AMERICAN ? AMERICAN_WAR_CLAIM_LOSS : 0.0;
    }

    public static boolean carolLifestyleActive(Doctrine doctrine, int usedIdeologies, int ideologyCount) {
        return doctrine.randomTreasuryDrain && usedIdeologies < ideologyCount;
    }

    private static double roundMoney(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static double finiteNonNegative(double value, double fallback) {
        return Double.isFinite(value) ? Math.max(0.0, value) : fallback;
    }
}
