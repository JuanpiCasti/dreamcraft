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
        int portalResetDelaySeconds,
        /**
         * Vertical band (stealth): the estate area region and zone discovery
         * only apply between anchorY - bandBelow and anchorY + bandAbove.
         * Keeps surface players unaware of the stronghold/chamber below.
         */
        int areaBandBelow,
        int areaBandAbove,
        /**
         * Structure preservation: ores, frames, vaults and spawners inside
         * instanced adventure areas are indestructible, so no party can
         * deplete the stronghold for future groups.
         */
        boolean protectStructure,
        /**
         * Zone regeneration: player edits inside the zone are journaled and
         * rolled back when the party closes it (exit portal / reset), so the
         * next adventurers always find pristine chunks.
         */
        boolean regenerateZone
) {

    public static EndInstanceConfig load(FileConfiguration config) {
        ConfigurationSection s = config.getConfigurationSection("estate-instances");
        if (s == null) {
            return new EndInstanceConfig(true, "dc_end_", 32, 24, 10, true, 30, 16, 48, true, true);
        }
        return new EndInstanceConfig(
                s.getBoolean("enabled", true),
                s.getString("world-prefix", "dc_end_"),
                Math.max(4, s.getInt("default-area-radius", 32)),
                Math.max(8, s.getInt("frame-scan-radius", 24)),
                Math.max(0, s.getInt("reset-delay-seconds", 10)),
                s.getBoolean("reset-portal-on-first-enter", true),
                Math.max(0, s.getInt("portal-reset-delay-seconds", 30)),
                Math.max(0, s.getInt("band-below", 16)),
                Math.max(4, s.getInt("band-above", 48)),
                s.getBoolean("protect-structure", true),
                s.getBoolean("regenerate-zone", true)
        );
    }
}
