package dev.dreamcraft.protection.config;

import org.bukkit.Material;

/**
 * A single item requirement for upgrading a Ward to a target tier.
 * Purely configuration data — parsed from {@code ward-upgrade-costs} in config.yml.
 */
public record WardUpgradeCost(Material material, int amount) {
    public WardUpgradeCost {
        if (material == null) throw new IllegalArgumentException("material no puede ser null");
        if (amount <= 0) throw new IllegalArgumentException("amount debe ser > 0");
    }
}
