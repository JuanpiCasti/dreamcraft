package dev.dreamcraft.protection;

import dev.dreamcraft.protection.config.ProtectionConfig;
import dev.dreamcraft.protection.model.ClaimStats;
import dev.dreamcraft.protection.model.ProtectionClaim;
import dev.dreamcraft.protection.model.ProtectionState;
import dev.dreamcraft.protection.model.UpkeepStorage;
import dev.dreamcraft.protection.service.UpkeepCalculator;
import dev.dreamcraft.protection.service.UpkeepManager;
import dev.dreamcraft.protection.service.UpkeepSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class UpkeepCalculatorTest {

    // ── Basic calculation ─────────────────────────────────────────────────────

    @Test
    void computesDailyCostAndRemainingTime() {
        ProtectionConfig config = testConfig();
        ClaimStats stats = new ClaimStats();
        stats.restore(Map.of("STONE", 10, "OBSIDIAN", 5), Map.of("basic", 10, "reinforced", 5), 15);
        UpkeepStorage storage = new UpkeepStorage();
        storage.deposit("maintenance", 300);
        ProtectionClaim claim = makeClaim(stats, storage);

        UpkeepSnapshot snapshot = new UpkeepCalculator(config).calculate(claim);
        // basic=10*1=10, reinforced=5*3=15, total=25
        assertEquals(25, snapshot.dailyCost());
        assertTrue(snapshot.timeRemaining().toHours() > 0);
        assertEquals(300, snapshot.storedUnits());
    }

    @Test
    void zeroCategoryCountsYieldsMinimumCostOne() {
        ProtectionConfig config = testConfig();
        ClaimStats stats = new ClaimStats(); // empty stats → dailyCost clamped to 1
        UpkeepStorage storage = new UpkeepStorage();
        storage.deposit("maintenance", 100);
        ProtectionClaim claim = makeClaim(stats, storage);

        UpkeepSnapshot snapshot = new UpkeepCalculator(config).calculate(claim);
        assertEquals(1, snapshot.dailyCost());
        assertEquals(100, snapshot.storedUnits());
    }

    @Test
    void zeroStoredUnitsGivesZeroTimeRemaining() {
        ProtectionConfig config = testConfig();
        ClaimStats stats = new ClaimStats();
        stats.restore(Map.of("STONE", 1), Map.of("basic", 1), 1);
        UpkeepStorage storage = new UpkeepStorage(); // empty
        ProtectionClaim claim = makeClaim(stats, storage);

        UpkeepSnapshot snapshot = new UpkeepCalculator(config).calculate(claim);
        assertEquals(0, snapshot.storedUnits());
        assertEquals(Duration.ZERO, snapshot.timeRemaining());
    }

    @Test
    void depositing10ItemsGives640Units() {
        UpkeepStorage storage = new UpkeepStorage();
        int unitsPerItem = 64;
        int items = 10;
        storage.deposit("maintenance", items * unitsPerItem);
        assertEquals(640, storage.get("maintenance"));
    }

    @Test
    void depositThenWithdraw() {
        UpkeepStorage storage = new UpkeepStorage();
        storage.deposit("maintenance", 200);
        boolean ok = storage.withdraw("maintenance", 50);
        assertTrue(ok);
        assertEquals(150, storage.get("maintenance"));
    }

    @Test
    void cannotWithdrawMoreThanAvailable() {
        UpkeepStorage storage = new UpkeepStorage();
        storage.deposit("maintenance", 10);
        assertFalse(storage.withdraw("maintenance", 11));
        assertEquals(10, storage.get("maintenance")); // unchanged
    }

    // ── State transitions via UpkeepManager ───────────────────────────────────

    @Test
    void claimIsActiveWithAmpleResources() {
        ProtectionConfig config = testConfig();
        UpkeepStorage storage = new UpkeepStorage();
        storage.deposit("maintenance", 10000); // very large amount
        ProtectionClaim claim = makeClaim(new ClaimStats(), storage);
        new UpkeepManager(config, new UpkeepCalculator(config)).recalculateState(claim);
        assertEquals(ProtectionState.ACTIVE, claim.status());
    }

    @Test
    void claimEntersWarningState() {
        // Create config with short warning-threshold
        ProtectionConfig config = new ProtectionConfig(
                true, 16, 16, false, null, 27,
                Duration.ofHours(24), Duration.ofHours(48), Duration.ofHours(6),
                Duration.ofHours(24), Duration.ofHours(48),
                null, 64, 8, true, Duration.ofDays(7),
                true, true, true, 41001, "dreamcraft:protection_wardrobe",
                Map.of(),
                Map.of("basic", 1),
                Map.of()
        );
        // daily cost=1, stored=30 → timeRemaining=30*86400=720h, which is > warning(48h)
        // so still ACTIVE — we need stored=1 so remaining=24h < warning(48h)
        UpkeepStorage storage = new UpkeepStorage();
        storage.deposit("maintenance", 1);
        ClaimStats stats = new ClaimStats();
        stats.restore(Map.of("STONE", 1), Map.of("basic", 1), 1);
        ProtectionClaim claim = makeClaim(stats, storage);
        new UpkeepManager(config, new UpkeepCalculator(config)).recalculateState(claim);
        assertEquals(ProtectionState.WARNING, claim.status());
    }

    /**
     * When storage is empty and nextUpkeepAt is in the past but within the grace period,
     * the state should be NO_RESOURCES.
     * UpkeepManager logic: unprotectedAt = nextUpkeepAt + gracePeriod
     * If now < unprotectedAt → NO_RESOURCES
     * If now > unprotectedAt → UNPROTECTED
     * Config grace = 24h, so nextUpkeepAt must be < 24h ago to stay in NO_RESOURCES.
     */
    @Test
    void claimEntersNoResourcesWhenEmptyAndWithinGrace() {
        ProtectionConfig config = testConfig();
        UpkeepStorage storage = new UpkeepStorage(); // empty
        ClaimStats stats = new ClaimStats();
        // nextUpkeepAt = 1h ago → unprotectedAt = 1h ago + 24h = 23h from now → still in grace
        Instant nextUpkeep = Instant.now().minus(Duration.ofHours(1));
        Instant past = Instant.now().minus(Duration.ofHours(25));
        ProtectionClaim claim = new ProtectionClaim(
                UUID.randomUUID(), "world", UUID.randomUUID(),
                0, 64, 0, 16, 16,
                ProtectionState.ACTIVE, "advanced",
                past, past, nextUpkeep, past,
                new HashSet<>(), new HashMap<>(),
                stats, storage,
                0, 64, 0
        );
        new UpkeepManager(config, new UpkeepCalculator(config)).recalculateState(claim);
        assertEquals(ProtectionState.NO_RESOURCES, claim.status());
    }

    @Test
    void claimEntersUnprotectedAfterGraceExpires() {
        ProtectionConfig config = testConfig();
        UpkeepStorage storage = new UpkeepStorage(); // empty
        ClaimStats stats = new ClaimStats();
        // nextUpkeepAt = 25h ago → unprotectedAt = 25h ago + 24h = 1h ago → past grace
        Instant nextUpkeep = Instant.now().minus(Duration.ofHours(25));
        Instant past = Instant.now().minus(Duration.ofHours(48));
        ProtectionClaim claim = new ProtectionClaim(
                UUID.randomUUID(), "world", UUID.randomUUID(),
                0, 64, 0, 16, 16,
                ProtectionState.ACTIVE, "advanced",
                past, past, nextUpkeep, past,
                new HashSet<>(), new HashMap<>(),
                stats, storage,
                0, 64, 0
        );
        new UpkeepManager(config, new UpkeepCalculator(config)).recalculateState(claim);
        assertEquals(ProtectionState.UNPROTECTED, claim.status());
    }

    // ── UpkeepStorage snapshot ────────────────────────────────────────────────

    @Test
    void snapshotIsUnmodifiable() {
        UpkeepStorage storage = new UpkeepStorage();
        storage.deposit("maintenance", 100);
        Map<String, Integer> snap = storage.snapshot();
        assertThrows(UnsupportedOperationException.class, () -> snap.put("x", 1));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ProtectionClaim makeClaim(ClaimStats stats, UpkeepStorage storage) {
        Instant now = Instant.now();
        return new ProtectionClaim(
                UUID.randomUUID(), "world", UUID.randomUUID(),
                0, 64, 0, 16, 16,
                ProtectionState.ACTIVE, "advanced",
                now, now, now, now,
                new HashSet<>(), new HashMap<>(),
                stats, storage,
                0, 64, 0
        );
    }

    static ProtectionConfig testConfig() {
        // null for Material fields: tests cover pure upkeep math, not item resolution
        return new ProtectionConfig(
                true, 16, 16, false, null, 27,
                Duration.ofHours(24), Duration.ofHours(24), Duration.ofHours(6),
                Duration.ofHours(24), Duration.ofHours(48),
                null, 64, 8, true, Duration.ofDays(7),
                true, true, true, 41001, "dreamcraft:protection_wardrobe",
                Map.of(),
                Map.of("basic", 1, "reinforced", 3, "advanced", 10, "special", 15, "light", 1),
                Map.of()
        );
    }
}
