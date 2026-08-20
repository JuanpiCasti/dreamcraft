package dev.dreamcraft.protection.domain.model;

/**
 * Tier configuration for a Ward, managed entirely by DreamCraft.
 * WorldGuard manages the actual region geometry; DreamCraft manages the meaning
 * of the tier (score thresholds, radius formula, upkeep rate).
 */
public record WardTier(
        String key,
        int minBaseScore,
        int maxBaseScore,
        int baseRadius,
        /** Multiplier applied to baseScore to compute radius: radius = baseRadius + baseScore * radiusPerScore */
        double radiusPerScore,
        int upkeepPerInterval
) {
    /**
     * Computes the effective protection radius for a given base score.
     */
    public int computeRadius(int baseScore) {
        return baseRadius + (int) Math.floor(baseScore * radiusPerScore);
    }
}
