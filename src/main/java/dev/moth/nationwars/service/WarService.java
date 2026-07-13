package dev.moth.nationwars.service;

import dev.moth.nationwars.Doctrine;

/** Pure war timing calculations; callers provide terrain and live-player observations. */
public final class WarService {
    private WarService() {
    }

    public static int justificationSeconds(Doctrine attacker, Doctrine defender) {
        int seconds = 90;
        if (attacker == Doctrine.GERMAN) {
            seconds -= 40;
        }
        if (defender == Doctrine.ROMANIAN) {
            seconds += 30;
        }
        return Math.max(10, seconds);
    }

    public static int captureSeconds(Doctrine attacker, Doctrine defender, boolean defenderCoast,
                                     boolean defenderHillOrMountain, boolean italianRecapture,
                                     int attackersPresent) {
        // Modifier order is contractual: flat terrain/doctrine changes precede the Soviet doubling.
        double required = attacker.captureSeconds * defender.defenseCaptureMultiplier;
        if (defender == Doctrine.FRENCH) {
            required += 25.0;
        }
        if (defender == Doctrine.BRITISH && defenderCoast) {
            required -= 10.0;
        }
        if (defender == Doctrine.ITALIAN && defenderHillOrMountain) {
            required += 15.0;
        }
        if (attacker == Doctrine.ITALIAN && italianRecapture) {
            required += 10.0;
        }
        if (defender == Doctrine.SOVIET && attackersPresent < 2) {
            required *= 2.0;
        }
        return Math.max(10, (int)Math.round(required));
    }
}
