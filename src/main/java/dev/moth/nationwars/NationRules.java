package dev.moth.nationwars;

public final class NationRules {
    private static final double BASE_CAPITAL_INCOME = 120.0;

    private NationRules() {
    }

    public static double capitalIncome(String doctrineId, int upgradeLevel) {
        int upgrades = Math.max(0, Math.min(4, upgradeLevel));
        if ("USA".equals(doctrineId)) {
            return BASE_CAPITAL_INCOME;
        }
        double base = "SOV".equals(doctrineId) ? 0.0 : BASE_CAPITAL_INCOME;
        return roundMoney(base + 6.0 * upgrades);
    }

    static boolean incomingDemandUsesOwnNation(boolean viewingIncoming) {
        return viewingIncoming;
    }

    static double incomeAgreementNet(double offered, double requested) {
        double safeOffered = Double.isFinite(offered) ? Math.max(0.0, offered) : 0.0;
        double safeRequested = Double.isFinite(requested) ? Math.max(0.0, requested) : 0.0;
        return roundMoney(safeOffered - safeRequested);
    }

    static boolean isTradeEmpty(int requestedClaimCount, int offeredClaimCount, double requestedMoney, double offeredMoney,
                                double requestedIncome, double offeredIncome, boolean incomeTermsSpecified) {
        return requestedClaimCount <= 0 && offeredClaimCount <= 0 && requestedMoney <= 0.0 && offeredMoney <= 0.0
            && requestedIncome <= 0.0 && offeredIncome <= 0.0 && !incomeTermsSpecified;
    }

    static TradeTerms flipTradeTerms(TradeTerms source) {
        return new TradeTerms(source.offeredMoney, source.requestedMoney, source.offeredIncome, source.requestedIncome,
            source.incomeTermsSpecified);
    }

    private static double roundMoney(double value) {
        return (double)Math.round(value * 100.0) / 100.0;
    }

    record TradeTerms(double requestedMoney, double offeredMoney, double requestedIncome, double offeredIncome,
                      boolean incomeTermsSpecified) {
    }
}
