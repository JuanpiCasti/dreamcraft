package dev.dreamcraft.protection.config;

import java.time.Duration;

public final class DurationParser {
    private DurationParser() {
    }

    public static Duration parse(String raw) {
        String value = raw.trim().toLowerCase();
        // Only treat as a simple suffix if the prefix before the suffix is a pure integer.
        // This avoids misidentifying ISO-8601 strings like "PT2H" as suffix-notation.
        if (value.endsWith("ms")) {
            String prefix = value.substring(0, value.length() - 2);
            if (isLong(prefix)) {
                return Duration.ofMillis(Long.parseLong(prefix));
            }
        }
        if (value.endsWith("h")) {
            String prefix = value.substring(0, value.length() - 1);
            if (isLong(prefix)) {
                return Duration.ofHours(Long.parseLong(prefix));
            }
        }
        if (value.endsWith("d")) {
            String prefix = value.substring(0, value.length() - 1);
            if (isLong(prefix)) {
                return Duration.ofDays(Long.parseLong(prefix));
            }
        }
        if (value.endsWith("m")) {
            String prefix = value.substring(0, value.length() - 1);
            if (isLong(prefix)) {
                return Duration.ofMinutes(Long.parseLong(prefix));
            }
        }
        // Fall back to ISO-8601 (e.g. "PT2H", "P7D")
        return Duration.parse(raw);
    }

    /** Returns true if s is a non-empty string that can be parsed as a long integer. */
    private static boolean isLong(String s) {
        if (s.isEmpty()) return false;
        int start = (s.charAt(0) == '-') ? 1 : 0;
        if (start >= s.length()) return false;
        for (int i = start; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) return false;
        }
        return true;
    }
}
