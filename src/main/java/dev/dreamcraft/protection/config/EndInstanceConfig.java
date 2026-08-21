package dev.dreamcraft.protection.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Configuration for Estate adventure instances (End / Trial Chamber).
 *
 * <p>Loaded from the {@code estate-instances} section of config.yml.
 */
public record EndInstanceConfig(
        boolean enabled,
        String worldPrefix,
        int defaultAreaRadius,
        int frameScanRadius,
        int resetDelaySeconds,
        boolean resetPortalOnFirstEnter,
        int portalResetDelaySeconds
) {

    public static EndInstanceConfig load(FileConfiguration config) {
        ConfigurationSection s = config.getConfigurationSection("estate-instances");
        if (s == null) {
            return new EndInstanceConfig(true, "dc_end_", 32, 24, 10, true, 30);
        }
        return new EndInstanceConfig(
                s.getBoolean("enabled", true),
                s.getString("world-prefix", "dc_end_"),
                Math.max(4, s.getInt("default-area-radius", 32)),
                Math.max(8, s.getInt("frame-scan-radius", 24)),
                Math.max(0, s.getInt("reset-delay-seconds", 10)),
                s.getBoolean("reset-portal-on-first-enter", true),
                Math.max(0, s.getInt("portal-reset-delay-seconds", 30))
        );
    }
}
