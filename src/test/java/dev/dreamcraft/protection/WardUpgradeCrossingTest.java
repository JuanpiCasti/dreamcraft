package dev.dreamcraft.protection;

import dev.dreamcraft.protection.config.ProtectionConfig;
import dev.dreamcraft.protection.config.WardUpgradeCost;
import dev.dreamcraft.protection.domain.model.OwnerType;
import dev.dreamcraft.protection.domain.model.Ward;
import dev.dreamcraft.protection.domain.model.WardPermission;
import dev.dreamcraft.protection.service.WardUpgradeService;
import dev.dreamcraft.protection.service.WardUpgradeService.UpgradeQuote;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the "esquema B" upgrade economy in {@link WardUpgradeService}:
 * only the upgrade that crosses the next tier's min base score pays; intermediate
 * upgrades are free (costs empty, crossingTier false) while the radius still grows.
 */
class WardUpgradeCrossingTest {

    private static final List<WardUpgradeCost> REINFORCED_COSTS =
            List.of(new WardUpgradeCost(Material.IRON_INGOT, 8));
    private static final List<WardUpgradeCost> ADVANCED_COSTS =
            List.of(new WardUpgradeCost(Material.DIAMOND, 4));

    // ── Pure helper ───────────────────────────────────────────────────────────

    @Test
    void costsForCrossingChargesOnlyWhenThresholdReached() {
        assertTrue(WardUpgradeService.costsForCrossing(100, 100, ADVANCED_COSTS)
                .containsAll(ADVANCED_COSTS));
        assertTrue(WardUpgradeService.costsForCrossing(500, 100, ADVANCED_COSTS)
                .containsAll(ADVANCED_COSTS));
        assertTrue(WardUpgradeService.costsForCrossing(99, 100, ADVANCED_COSTS).isEmpty());
        assertTrue(WardUpgradeService.costsForCrossing(0, 1, ADVANCED_COSTS).isEmpty());
    }

    // ── quoteNext integration (tiers: basic < reinforced < advanced) ─────────

    @Test
    void intermediateUpgradeIsFree() {
        WardUpgradeService service = new WardUpgradeService(
                new TestWardTierProvider(), configWithScorePerUpgrade(50));

        Ward ward = ward("basic", 0);
        var quoteOpt = service.quoteNext(ward);

        assertTrue(quoteOpt.isPresent());
        UpgradeQuote quote = quoteOpt.get();
        assertEquals("reinforced", quote.targetTierKey());
        // newScore = 50 < reinforced.minBaseScore = 100 → free growth upgrade
        assertFalse(quote.crossingTier());
        assertTrue(quote.costs().isEmpty());
        // Radius still grows with score
        assertTrue(quote.radiusAfter() > ward.radius());
    }

    @Test
    void exactThresholdCrossingPaysTargetTierCosts() {
        WardUpgradeService service = new WardUpgradeService(
                new TestWardTierProvider(), configWithScorePerUpgrade(100));

        Ward ward = ward("basic", 0);
        var quoteOpt = service.quoteNext(ward);

        assertTrue(quoteOpt.isPresent());
        UpgradeQuote quote = quoteOpt.get();
        assertEquals("reinforced", quote.targetTierKey());
        // newScore == minBaseScore counts as crossing (>= boundary)
        assertTrue(quote.crossingTier());
        assertEquals(REINFORCED_COSTS, quote.costs());
    }

    @Test
    void advancedFromMidReinforcedIsFree() {
        WardUpgradeService service = new WardUpgradeService(
                new TestWardTierProvider(), configWithScorePerUpgrade(100));

        Ward ward = ward("reinforced", 200);
        var quoteOpt = service.quoteNext(ward);

        assertTrue(quoteOpt.isPresent());
        UpgradeQuote quote = quoteOpt.get();
        assertEquals("advanced", quote.targetTierKey());
        // newScore = 300 < advanced.minBaseScore = 500
        assertFalse(quote.crossingTier());
        assertTrue(quote.costs().isEmpty());
    }

    @Test
    void maxedWardHasNoQuote() {
        WardUpgradeService service = new WardUpgradeService(
                new TestWardTierProvider(), configWithScorePerUpgrade(100));
        assertTrue(service.quoteNext(ward("advanced", 600)).isEmpty());
    }

    // ── Fixtures ──────────────────────────────────────────────────────────────

    /** Minimal config: only score-per-upgrade and the per-tier upgrade costs matter. */
    private static ProtectionConfig configWithScorePerUpgrade(int scorePerUpgrade) {
        return new ProtectionConfig(
                true, 10, 5, false,
                null, 27,
                Duration.ofHours(24), Duration.ofHours(2), Duration.ofHours(6), Duration.ofHours(48),
                Duration.ofDays(3),
                null, 1, 8, true, Duration.ofDays(30),
                false, false, false, 0, null,
                null, null, 0,
                scorePerUpgrade,
                Map.of(), Map.of(), Map.of(),
                Map.of("reinforced", REINFORCED_COSTS, "advanced", ADVANCED_COSTS));
    }

    private static Ward ward(String tierKey, int baseScore) {
        return new Ward(UUID.randomUUID(), "TestWard", "world",
                UUID.randomUUID(), OwnerType.PLAYER, null,
                baseScore, tierKey, 16, 0,
                Instant.EPOCH, Instant.EPOCH, Instant.EPOCH,
                0, 64, 0, null,
                EnumSet.noneOf(WardPermission.class));
    }
}
