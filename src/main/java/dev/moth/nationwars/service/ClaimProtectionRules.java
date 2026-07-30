package dev.moth.nationwars.service;

/** Pure policy for deciding when OPAC claim protection is bypassed during war. */
public final class ClaimProtectionRules {
    private ClaimProtectionRules() {
    }

    public static boolean disableForEnemyWarClaim(boolean scorchedEarth, boolean activeWar,
                                                   boolean peacePending, boolean respawnLocked) {
        return scorchedEarth && activeWar && !peacePending && !respawnLocked;
    }

    public static boolean paidAccessAllowed(boolean stealing, boolean visitorHasNation) {
        return stealing || !visitorHasNation;
    }
}
