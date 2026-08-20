package dev.dreamcraft.protection.persistence;

import dev.dreamcraft.protection.domain.model.WardTier;
import dev.dreamcraft.protection.domain.port.WardTierProvider;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;

/**
 * Bukkit-config-backed implementation of {@link WardTierProvider}.
 * Reads tier configuration from the plugin's config.yml at startup.
 *
 * <p>This lives in the persistence layer (infrastructure), not the domain,
 * because it depends on Bukkit's {@link FileConfiguration}.
 */
public final class ConfigWardTierProvider implements WardTierProvider {

    private final Map<String, WardTier> tiers;
    private final String defaultKey;

    public ConfigWardTierProvider(FileConfiguration config) {
        this.tiers = new LinkedHashMap<>();
        ConfigurationSection section = config.getConfigurationSection("ward-tiers");
        String resolvedDefault = "basic";
        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection t = section.getConfigurationSection(key);
                if (t == null) continue;
                String normalized = key.toLowerCase(Locale.ROOT);
                tiers.put(normalized, new WardTier(
                        normalized,
                        t.getInt("min-base-score", 0),
                        t.getInt("max-base-score", Integer.MAX_VALUE),
                        t.getInt("base-radius", 16),
                        t.getDouble("radius-per-score", 0.0),
                        t.getInt("upkeep-per-interval", 1)
                ));
            }
            resolvedDefault = config.getString("ward-tiers-default", "basic").toLowerCase(Locale.ROOT);
        }
        // Fallback if no tiers configured
        if (tiers.isEmpty()) {
            tiers.put("basic", new WardTier("basic", 0, Integer.MAX_VALUE, 16, 0.0, 1));
        }
        this.defaultKey = tiers.containsKey(resolvedDefault) ? resolvedDefault : tiers.keySet().iterator().next();
    }

    @Override
    public Optional<WardTier> findByKey(String key) {
        return Optional.ofNullable(tiers.get(key.toLowerCase(Locale.ROOT)));
    }

    @Override
    public WardTier resolveForScore(int baseScore) {
        // Find the first tier whose range contains the score
        for (WardTier t : tiers.values()) {
            if (baseScore >= t.minBaseScore() && baseScore <= t.maxBaseScore()) {
                return t;
            }
        }
        return tiers.get(defaultKey);
    }

    @Override
    public Map<String, WardTier> allTiers() {
        return Collections.unmodifiableMap(tiers);
    }

    @Override
    public String defaultTierKey() {
        return defaultKey;
    }
}
