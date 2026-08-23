package dev.dreamcraft.protection.listener;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Truth tables for the pure surcharge decisions of
 * {@link WardBlockGateListener}: the founder-exempt placement rule and the
 * below-tier material classification shared by placement gating, break relief
 * and world re-scans. No Bukkit server required.
 */
class WardBlockGateDecisionsTest {

    /** Gated set: reinforced-rank table, advanced-rank beacon. */
    private static final Map<Material, String> GATED = Map.of(
            Material.ENCHANTING_TABLE, "reinforced",
            Material.BEACON, "advanced");

    /** basic=0, reinforced=1, advanced=2; unknown keys → -1 (provider contract). */
    private static final Function<String, Integer> RANK_OF = key -> switch (key == null ? "" : key.toLowerCase()) {
        case "basic" -> 0;
        case "reinforced" -> 1;
        case "advanced" -> 2;
        default -> -1;
    };

    // ── shouldChargeSurcharge ─────────────────────────────────────────────────

    @Test
    void founderItemIsAlwaysExempt() {
        assertFalse(WardBlockGateListener.shouldChargeSurcharge(true, -1, 1));
        assertFalse(WardBlockGateListener.shouldChargeSurcharge(true, 0, 0));
        assertFalse(WardBlockGateListener.shouldChargeSurcharge(true, 5, 1));
        assertFalse(WardBlockGateListener.shouldChargeSurcharge(true, 0, -1));
    }

    @Test
    void unknownWardTierNeverCharges() {
        assertFalse(WardBlockGateListener.shouldChargeSurcharge(false, -1, 1));
        assertFalse(WardBlockGateListener.shouldChargeSurcharge(false, -1, -1));
    }

    @Test
    void unknownRequiredTierNeverCharges() {
        assertFalse(WardBlockGateListener.shouldChargeSurcharge(false, 0, -1));
        assertFalse(WardBlockGateListener.shouldChargeSurcharge(false, -1, -1));
    }

    @Test
    void wardBelowRequiredRankCharges() {
        assertTrue(WardBlockGateListener.shouldChargeSurcharge(false, 0, 1));
        assertTrue(WardBlockGateListener.shouldChargeSurcharge(false, 0, 2));
        assertTrue(WardBlockGateListener.shouldChargeSurcharge(false, 1, 2));
    }

    @Test
    void wardAtOrAboveRequiredRankDoesNotCharge() {
        assertFalse(WardBlockGateListener.shouldChargeSurcharge(false, 1, 1));
        assertFalse(WardBlockGateListener.shouldChargeSurcharge(false, 2, 1));
        assertFalse(WardBlockGateListener.shouldChargeSurcharge(false, 0, 0));
    }

    // ── isBelowTierGated ──────────────────────────────────────────────────────

    @Test
    void ungatedMaterialIsNeverBelowTier() {
        assertFalse(WardBlockGateListener.isBelowTierGated(Material.STONE, GATED, 0, RANK_OF));
        assertFalse(WardBlockGateListener.isBelowTierGated(Material.STONE, GATED, -1, RANK_OF));
    }

    @Test
    void unknownRequiredTierIsNeverBelowTier() {
        Map<Material, String> broken = Map.of(Material.ENCHANTING_TABLE, "ghost-tier");
        assertFalse(WardBlockGateListener.isBelowTierGated(Material.ENCHANTING_TABLE, broken, 0, RANK_OF));
    }

    @Test
    void unknownWardRankIsNeverBelowTier() {
        assertFalse(WardBlockGateListener.isBelowTierGated(Material.ENCHANTING_TABLE, GATED, -1, RANK_OF));
    }

    @Test
    void gatedMaterialAboveWardRankIsBelowTier() {
        assertTrue(WardBlockGateListener.isBelowTierGated(Material.ENCHANTING_TABLE, GATED, 0, RANK_OF));
        assertTrue(WardBlockGateListener.isBelowTierGated(Material.BEACON, GATED, 1, RANK_OF));
    }

    @Test
    void gatedMaterialCoveredByWardRankIsNotBelowTier() {
        assertFalse(WardBlockGateListener.isBelowTierGated(Material.ENCHANTING_TABLE, GATED, 1, RANK_OF));
        assertFalse(WardBlockGateListener.isBelowTierGated(Material.BEACON, GATED, 2, RANK_OF));
    }
}
