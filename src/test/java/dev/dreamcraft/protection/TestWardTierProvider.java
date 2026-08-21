package dev.dreamcraft.protection;

import dev.dreamcraft.protection.domain.model.WardTier;
import dev.dreamcraft.protection.domain.port.WardTierProvider;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Test-only {@link WardTierProvider} that does not depend on Bukkit config.
 * Provides three tiers (basic, reinforced, advanced) for deterministic testing.
 */
public final class TestWardTierProvider implements WardTierProvider {

    private final Map<String, WardTier> tiers = new LinkedHashMap<>();

    public TestWardTierProvider() {
        tiers.put("basic", new WardTier("basic", 0, 99, 16, 0.1, 1));
        tiers.put("reinforced", new WardTier("reinforced", 100, 499, 32, 0.1, 3));
        tiers.put("advanced", new WardTier("advanced", 500, Integer.MAX_VALUE, 64, 0.1, 10));
    }

    @Override
    public Optional<WardTier> findByKey(String key) {
        return Optional.ofNullable(tiers.get(key.toLowerCase()));
    }

    @Override
    public WardTier resolveForScore(int baseScore) {
        for (WardTier t : tiers.values()) {
            if (baseScore >= t.minBaseScore() && baseScore <= t.maxBaseScore()) {
                return t;
            }
        }
        return tiers.get("basic");
    }

    @Override
    public Map<String, WardTier> allTiers() {
        return Map.copyOf(tiers);
    }

    @Override
    public String defaultTierKey() {
        return "basic";
    }
}
