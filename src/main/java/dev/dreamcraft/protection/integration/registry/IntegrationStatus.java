package dev.dreamcraft.protection.integration.registry;

/**
 * Snapshot of a single integration's detected state at startup.
 */
public record IntegrationStatus(
        IntegrationKey key,
        boolean present,
        boolean compatible,
        String detectedVersion,
        String requiredMinVersion,
        String unavailableReason
) {
    /** Convenience: integration is usable (present and compatible). */
    public boolean available() {
        return present && compatible;
    }

    /** Create an available status. */
    public static IntegrationStatus available(IntegrationKey key, String version) {
        return new IntegrationStatus(key, true, true, version, null, null);
    }

    /** Create a missing status (plugin not installed). */
    public static IntegrationStatus missing(IntegrationKey key) {
        return new IntegrationStatus(key, false, false, null, null, "Plugin not installed");
    }

    /** Create an incompatible status (plugin present but wrong version). */
    public static IntegrationStatus incompatible(IntegrationKey key, String detectedVersion, String requiredMin) {
        return new IntegrationStatus(key, true, false, detectedVersion, requiredMin,
                "Version " + detectedVersion + " < required " + requiredMin);
    }

    /** Create a disabled status (present but disabled by config). */
    public static IntegrationStatus disabled(IntegrationKey key) {
        return new IntegrationStatus(key, false, false, null, null, "Disabled by configuration");
    }
}
