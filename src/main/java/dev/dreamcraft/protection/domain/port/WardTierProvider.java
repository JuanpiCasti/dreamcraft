package dev.dreamcraft.protection.domain.port;

import dev.dreamcraft.protection.domain.model.WardTier;

import java.util.Map;
import java.util.Optional;

/**
 * Output port: provides Ward tier configuration to the domain services.
 * Loaded from the plugin config; the domain does not depend on Bukkit config APIs.
 */
public interface WardTierProvider {

    Optional<WardTier> findByKey(String key);

    /** Returns the tier whose score range contains the given baseScore. Fallback to default tier. */
    WardTier resolveForScore(int baseScore);

    Map<String, WardTier> allTiers();

    /** Returns the name of the default/starting tier. */
    String defaultTierKey();
}
