package dev.dreamcraft.protection.presentation.viewmodel;

import java.util.List;

/**
 * Pre-computed upgrade preview for the Ward menu: what the next tier gives,
 * what it costs, and whether the viewer can afford it.
 *
 * <p>Pure display data — no Bukkit types, no domain references.
 */
public record WardUpgradePreview(
        /** false when the Ward is already at the highest configured tier. */
        boolean available,
        boolean canAfford,
        String targetTier,
        int scoreGain,
        int radiusAfter,
        int upkeepPerInterval,
        List<CostLine> costs
) {

    /** One cost row with per-line affordability for coloring. */
    public record CostLine(int amount, String materialDisplay, boolean affordable) {}

    /** Preview for a maxed-out Ward (no further tiers configured). */
    public static WardUpgradePreview unavailable() {
        return new WardUpgradePreview(false, false, null, 0, 0, 0, List.of());
    }
}
