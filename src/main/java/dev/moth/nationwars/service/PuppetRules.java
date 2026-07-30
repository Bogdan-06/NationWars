package dev.moth.nationwars.service;

/** Pure, deterministic rules for puppet independence and income taxation. */
public final class PuppetRules {
    public static final int INITIAL_INDEPENDENCE_POINTS = 100;
    public static final int MIN_INDEPENDENCE_POINTS = 0;
    public static final int MAX_INDEPENDENCE_POINTS = 200;

    public static final int AGITATE_DELTA = 10;
    public static final int PACIFY_DELTA = -10;
    public static final int FOREIGN_TRADE_DELTA = 1;
    public static final int MASTER_FAVOURED_TRADE_DELTA = -1;

    public static final int AGITATE_PACIFY_COOLDOWN_SECONDS = 600;
    public static final int TRADE_POINT_COOLDOWN_SECONDS = 120;
    public static final int TICKS_PER_SECOND = 20;
    public static final long AGITATE_PACIFY_COOLDOWN_TICKS =
        (long)AGITATE_PACIFY_COOLDOWN_SECONDS * TICKS_PER_SECOND;
    public static final long TRADE_POINT_COOLDOWN_TICKS =
        (long)TRADE_POINT_COOLDOWN_SECONDS * TICKS_PER_SECOND;

    public static final int ANNEX_AFTER_LOST_WARS = 3;
    public static final double PUPPET_TAX_RATE = 0.20;

    private PuppetRules() {
    }

    public static int clampPoints(int points) {
        return Math.max(MIN_INDEPENDENCE_POINTS, Math.min(MAX_INDEPENDENCE_POINTS, points));
    }

    public static boolean canClaim(int points) {
        return clampPoints(points) >= 50;
    }

    public static boolean canStartIndependenceWar(int points) {
        return clampPoints(points) > 150;
    }

    public static boolean canPeacefullyLiberate(int points) {
        return clampPoints(points) >= MAX_INDEPENDENCE_POINTS;
    }

    public static boolean canAnnex(int points, int lostIndependenceWars) {
        return clampPoints(points) == MIN_INDEPENDENCE_POINTS
            || Math.max(0, lostIndependenceWars) >= ANNEX_AFTER_LOST_WARS;
    }

    /**
     * Applies an independence-point delta unless an independence war has frozen the points.
     * The returned value is always kept inside the persisted 0-200 range.
     */
    public static int adjustPoints(int currentPoints, int delta, boolean frozen) {
        int current = clampPoints(currentPoints);
        if (frozen) {
            return current;
        }
        long adjusted = (long)current + delta;
        if (adjusted <= MIN_INDEPENDENCE_POINTS) {
            return MIN_INDEPENDENCE_POINTS;
        }
        if (adjusted >= MAX_INDEPENDENCE_POINTS) {
            return MAX_INDEPENDENCE_POINTS;
        }
        return (int)adjusted;
    }

    public static int agitate(int currentPoints, boolean frozen) {
        return adjustPoints(currentPoints, AGITATE_DELTA, frozen);
    }

    public static int pacify(int currentPoints, boolean frozen) {
        return adjustPoints(currentPoints, PACIFY_DELTA, frozen);
    }

    public static int applyForeignTrade(int currentPoints, boolean frozen) {
        return adjustPoints(currentPoints, FOREIGN_TRADE_DELTA, frozen);
    }

    public static int applyMasterFavouredTrade(int currentPoints, boolean frozen) {
        return adjustPoints(currentPoints, MASTER_FAVOURED_TRADE_DELTA, frozen);
    }

    public static long agitatePacifyCooldownUntil(long nowTick) {
        return safeAdd(nowTick, AGITATE_PACIFY_COOLDOWN_TICKS);
    }

    public static long tradePointCooldownUntil(long nowTick) {
        return safeAdd(nowTick, TRADE_POINT_COOLDOWN_TICKS);
    }

    /** A cooldown becomes usable at its exact deadline, not one tick later. */
    public static boolean cooldownReady(long nowTick, long cooldownUntilTick) {
        return nowTick >= cooldownUntilTick;
    }

    /**
     * Classifies a trade from the puppet's perspective. A master-favoured trade is a
     * one-sided gift: the puppet receives at least one positive asset and gives none.
     */
    public static boolean isPuppetFavouredMasterTrade(
        double moneyReceived,
        int claimsReceived,
        double incomeReceived,
        double moneyGiven,
        int claimsGiven,
        double incomeGiven
    ) {
        boolean receivesSomething = positiveFinite(moneyReceived)
            || claimsReceived > 0
            || positiveFinite(incomeReceived);
        boolean givesNothing = moneyGiven == 0.0
            && claimsGiven == 0
            && incomeGiven == 0.0;
        return receivesSomething && givesNothing;
    }

    /** Returns 20% of generated puppet income, rounded to the nearest cent. */
    public static double puppetTax(double generatedIncome) {
        if (!Double.isFinite(generatedIncome) || generatedIncome <= 0.0) {
            return 0.0;
        }
        return Math.round(generatedIncome * PUPPET_TAX_RATE * 100.0) / 100.0;
    }

    private static boolean positiveFinite(double value) {
        return Double.isFinite(value) && value > 0.0;
    }

    private static long safeAdd(long value, long amount) {
        if (amount > 0L && value > Long.MAX_VALUE - amount) {
            return Long.MAX_VALUE;
        }
        return value + amount;
    }
}
