package dev.dreamcraft.protection.integration.registry;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Detects, validates and registers all optional integrations at plugin startup.
 *
 * <p>An integration being absent or incompatible <b>never</b> prevents the
 * DreamCraft domain from functioning. Adapters check {@link #isAvailable(IntegrationKey)}
 * before making calls to external plugin APIs.
 *
 * <p>Usage:
 * <pre>
 *   CapabilityRegistry registry = new CapabilityRegistry(getServer().getPluginManager(), getLogger());
 *   registry.detect();
 *   if (registry.isAvailable(IntegrationKey.WORLD_GUARD)) { ... }
 * </pre>
 */
public final class CapabilityRegistry {

    private final PluginManager pluginManager;
    private final Logger logger;
    private final Map<IntegrationKey, IntegrationStatus> statuses = new EnumMap<>(IntegrationKey.class);

    public CapabilityRegistry(PluginManager pluginManager, Logger logger) {
        this.pluginManager = pluginManager;
        this.logger = logger;
    }

    /**
     * Detects and validates all integrations.
     * Must be called during {@code onEnable}, after all plugins have been loaded.
     */
    public void detect() {
        statuses.put(IntegrationKey.WORLD_GUARD,   detectPlugin("WorldGuard",      "7.0.0"));
        statuses.put(IntegrationKey.LUCK_PERMS,    detectPlugin("LuckPerms",        "5.0.0"));
        statuses.put(IntegrationKey.CORE_PROTECT,  detectPlugin("CoreProtect",      "22.0"));
        statuses.put(IntegrationKey.ESSENTIALS_X,  detectPlugin("Essentials",       "2.20.0"));
        statuses.put(IntegrationKey.WORLD_EDIT,    detectPlugin("WorldEdit",        "7.0.0"));
        statuses.put(IntegrationKey.CHUNKY,        detectPlugin("Chunky",           "1.0.0"));
        statuses.put(IntegrationKey.PACKET_EVENTS, detectPlugin("packetevents",     "2.0.0"));
        statuses.put(IntegrationKey.DELUXE_MENUS,  detectPlugin("DeluxeMenus",      "0.0.0"));
        statuses.put(IntegrationKey.ORAXEN,        detectPlugin("Oraxen",           "0.0.0"));
        // Resource pack is always "present" from DreamCraft's perspective — it's a config option
        statuses.put(IntegrationKey.RESOURCE_PACK, IntegrationStatus.available(IntegrationKey.RESOURCE_PACK, "config"));

        logSummary();
    }

    private IntegrationStatus detectPlugin(String pluginName, String requiredMin) {
        Plugin plugin = pluginManager.getPlugin(pluginName);
        if (plugin == null || !plugin.isEnabled()) {
            return IntegrationStatus.missing(IntegrationKey.valueOf(toKey(pluginName)));
        }
        String version = plugin.getPluginMeta().getVersion();
        if (!versionAtLeast(version, requiredMin)) {
            return IntegrationStatus.incompatible(
                    IntegrationKey.valueOf(toKey(pluginName)), version, requiredMin);
        }
        return IntegrationStatus.available(IntegrationKey.valueOf(toKey(pluginName)), version);
    }

    private String toKey(String pluginName) {
        return switch (pluginName) {
            case "WorldGuard"   -> "WORLD_GUARD";
            case "LuckPerms"    -> "LUCK_PERMS";
            case "CoreProtect"  -> "CORE_PROTECT";
            case "Essentials"   -> "ESSENTIALS_X";
            case "WorldEdit"    -> "WORLD_EDIT";
            case "Chunky"       -> "CHUNKY";
            case "packetevents" -> "PACKET_EVENTS";
            case "DeluxeMenus"  -> "DELUXE_MENUS";
            case "Oraxen"       -> "ORAXEN";
            default             -> pluginName.toUpperCase().replace(" ", "_");
        };
    }

    /**
     * Very simple semver-ish comparison: splits by '.' and '-' and compares numerically.
     */
    private boolean versionAtLeast(String detected, String minimum) {
        try {
            int[] d = parseVersion(detected);
            int[] m = parseVersion(minimum);
            int len = Math.max(d.length, m.length);
            for (int i = 0; i < len; i++) {
                int dv = i < d.length ? d[i] : 0;
                int mv = i < m.length ? m[i] : 0;
                if (dv > mv) return true;
                if (dv < mv) return false;
            }
            return true;
        } catch (Exception e) {
            return true; // can't parse, assume ok
        }
    }

    private int[] parseVersion(String v) {
        String cleaned = v.split("[-+]")[0]; // strip build metadata
        String[] parts = cleaned.split("\\.");
        int[] result = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try { result[i] = Integer.parseInt(parts[i]); } catch (NumberFormatException e) { result[i] = 0; }
        }
        return result;
    }

    private void logSummary() {
        logger.info("[DreamCraft Integrations]");
        for (IntegrationStatus status : statuses.values()) {
            String symbol = status.available() ? "✓" : (status.present() ? "!" : "✗");
            String line = "  [" + symbol + "] " + status.key().name();
            if (status.detectedVersion() != null) line += " v" + status.detectedVersion();
            if (status.unavailableReason() != null) line += " — " + status.unavailableReason();
            logger.info(line);
        }
    }

    // ── Query API ─────────────────────────────────────────────────────────────

    public boolean isAvailable(IntegrationKey key) {
        IntegrationStatus s = statuses.get(key);
        return s != null && s.available();
    }

    public IntegrationStatus getStatus(IntegrationKey key) {
        return statuses.getOrDefault(key, IntegrationStatus.missing(key));
    }

    public Map<IntegrationKey, IntegrationStatus> allStatuses() {
        return Collections.unmodifiableMap(statuses);
    }
}
