package dev.moth.nationwars;

final class PersistentTime {
    private static final long EPOCH_THRESHOLD = 10_000_000_000L;

    private PersistentTime() {
    }

    static long now() {
        return System.currentTimeMillis() / 50L;
    }

    static boolean isPersistent(long value) {
        return value >= EPOCH_THRESHOLD;
    }

    static long migrateDeadline(long value, long oldCurrentTick, long maximumRemainingTicks, long now) {
        if (value <= 0L || isPersistent(value)) {
            return value;
        }
        long oldRemaining = Math.max(0L, value - oldCurrentTick);
        return now + Math.min(oldRemaining, maximumRemainingTicks);
    }
}
