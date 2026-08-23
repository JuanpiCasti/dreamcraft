package dev.dreamcraft.protection;

import dev.dreamcraft.protection.domain.service.WardHealth;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Truth table for {@link WardHealth#classify}.
 *
 * <p>Orphan = core MISSING or region MISSING. CHUNK_UNLOADED and WG_INACTIVE
 * are unknown/degraded facts and must never count as absence.
 */
class WardHealthTest {

    @Test
    void presentCoreAndRegionIsHealthy() {
        var report = WardHealth.classify(
                WardHealth.CoreState.PRESENT, WardHealth.RegionState.PRESENT);
        assertFalse(report.orphan());
        assertEquals(WardHealth.CoreState.PRESENT, report.coreState());
        assertEquals(WardHealth.RegionState.PRESENT, report.regionState());
    }

    @Test
    void missingCoreIsOrphanRegardlessOfRegion() {
        assertTrue(classify(WardHealth.CoreState.MISSING, WardHealth.RegionState.MISSING).orphan());
        assertTrue(classify(WardHealth.CoreState.MISSING, WardHealth.RegionState.PRESENT).orphan());
        assertTrue(classify(WardHealth.CoreState.MISSING, WardHealth.RegionState.WG_INACTIVE).orphan());
    }

    @Test
    void missingRegionIsOrphanRegardlessOfCore() {
        assertTrue(classify(WardHealth.CoreState.MISSING, WardHealth.RegionState.MISSING).orphan());
        assertTrue(classify(WardHealth.CoreState.PRESENT, WardHealth.RegionState.MISSING).orphan());
        assertTrue(classify(WardHealth.CoreState.CHUNK_UNLOADED, WardHealth.RegionState.MISSING).orphan());
    }

    @Test
    void unknownStatesWithPresentCounterpartAreNotOrphan() {
        // Chunk unloaded: core presence unknown — not absence
        assertFalse(classify(WardHealth.CoreState.CHUNK_UNLOADED, WardHealth.RegionState.PRESENT).orphan());
        // WG inactive: region existence cannot be checked — not absence
        assertFalse(classify(WardHealth.CoreState.PRESENT, WardHealth.RegionState.WG_INACTIVE).orphan());
    }

    private static WardHealth.HealthReport classify(WardHealth.CoreState core, WardHealth.RegionState region) {
        return WardHealth.classify(core, region);
    }
}
