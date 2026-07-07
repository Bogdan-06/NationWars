package dev.moth.nationwars;

final class SpyRecovery {
    static final int SECONDS = 60;

    private SpyRecovery() {
    }

    static long deadline(long now) {
        return now + SECONDS * 20L;
    }
}
