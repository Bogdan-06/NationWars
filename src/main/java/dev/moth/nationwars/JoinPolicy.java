package dev.moth.nationwars;

import java.util.Locale;
import java.util.Optional;

public enum JoinPolicy {
    OPEN,
    INVITE_ONLY,
    CLOSED;

    public boolean allowsJoin(boolean hasInvitation) {
        return this == OPEN || this == INVITE_ONLY && hasInvitation;
    }

    public static Optional<JoinPolicy> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(value.trim().replace('-', '_').toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
