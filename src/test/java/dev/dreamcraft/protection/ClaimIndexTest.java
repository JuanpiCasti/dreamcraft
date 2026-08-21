package dev.dreamcraft.protection;

import dev.dreamcraft.protection.model.ClaimStats;
import dev.dreamcraft.protection.model.ProtectionClaim;
import dev.dreamcraft.protection.model.ProtectionState;
import dev.dreamcraft.protection.model.UpkeepStorage;
import dev.dreamcraft.protection.service.ClaimIndex;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ClaimIndexTest {

    // ── findClaim ─────────────────────────────────────────────────────────────

    @Test
    void findsClaimAtCenter() {
        ClaimIndex index = new ClaimIndex();
        index.add(claimAt(0, 0, 16));
        assertTrue(index.findClaim("world", 0, 0).isPresent());
    }

    @Test
    void findsClaimAtBoundaryEdge() {
        ClaimIndex index = new ClaimIndex();
        index.add(claimAt(0, 0, 16));
        // Bounds: -16..16 inclusive
        assertTrue(index.findClaim("world", 16, 16).isPresent());
        assertTrue(index.findClaim("world", -16, -16).isPresent());
        assertTrue(index.findClaim("world", 16, -16).isPresent());
        assertTrue(index.findClaim("world", -16, 16).isPresent());
    }

    @Test
    void missClaim_justOutsideBoundary() {
        ClaimIndex index = new ClaimIndex();
        index.add(claimAt(0, 0, 16));
        assertTrue(index.findClaim("world", 17, 17).isEmpty());
        assertTrue(index.findClaim("world", -17, -17).isEmpty());
    }

    @Test
    void missClaim_differentWorld() {
        ClaimIndex index = new ClaimIndex();
        index.add(claimAt(0, 0, 16));
        assertTrue(index.findClaim("nether", 0, 0).isEmpty());
    }

    @Test
    void findsCorrectClaimAmongMultiple() {
        ClaimIndex index = new ClaimIndex();
        ProtectionClaim a = claimAt(0, 0, 16);
        ProtectionClaim b = claimAt(100, 100, 16);
        index.add(a);
        index.add(b);

        // Point inside A
        assertTrue(index.findClaim("world", 5, 5).isPresent());
        assertEquals(a.id(), index.findClaim("world", 5, 5).get().id());

        // Point inside B
        assertTrue(index.findClaim("world", 100, 100).isPresent());
        assertEquals(b.id(), index.findClaim("world", 100, 100).get().id());

        // Point between A and B (unclaimed)
        assertTrue(index.findClaim("world", 50, 50).isEmpty());
    }

    @Test
    void findsClaimInsideBounds() {
        ClaimIndex index = new ClaimIndex();
        ProtectionClaim claim = claimAt(0, 0, 16);
        index.add(claim);

        assertTrue(index.findClaim("world", 10, 10).isPresent());
        assertTrue(index.findClaim("world", -16, -16).isPresent());
        assertTrue(index.findClaim("world", 16, 16).isPresent());
        assertTrue(index.findClaim("world", 17, 17).isEmpty());
    }

    // ── removeAndLookup ───────────────────────────────────────────────────────

    @Test
    void removedClaimNotFound() {
        ClaimIndex index = new ClaimIndex();
        ProtectionClaim claim = claimAt(0, 0, 16);
        index.add(claim);
        assertTrue(index.findClaim("world", 0, 0).isPresent());
        index.remove(claim.id());
        assertTrue(index.findClaim("world", 0, 0).isEmpty());
    }

    // ── overlap detection ─────────────────────────────────────────────────────

    @Test
    void detectsOverlapThroughGrid() {
        ClaimIndex index = new ClaimIndex();
        index.add(claimAt(0, 0, 16));

        assertTrue(index.overlaps(claimAt(20, 20, 16)));
        assertFalse(index.overlaps(claimAt(64, 64, 16)));
    }

    @Test
    void noOverlapForDistantClaims() {
        ClaimIndex index = new ClaimIndex();
        index.add(claimAt(0, 0, 8));
        // Claim at (100,100) with radius 8: bounds 92–108 vs -8..8, no overlap
        assertFalse(index.overlaps(claimAt(100, 100, 8)));
    }

    @Test
    void exactlyAdjacentClaimsDoNotOverlap() {
        ClaimIndex index = new ClaimIndex();
        // Claim A: center (0,0) radius 10 → bounds -10..10
        // Claim B: center (21,0) radius 10 → bounds 11..31  →  just touching at x=10/11 — no overlap
        index.add(claimAt(0, 0, 10));
        assertFalse(index.overlaps(claimAt(21, 0, 10)));
    }

    @Test
    void touchingBoundaryCausesOverlap() {
        ClaimIndex index = new ClaimIndex();
        // Claim A: center (0,0) radius 10 → bounds -10..10
        // Claim B: center (20,0) radius 10 → bounds 10..30  → share x=10, so intersects
        index.add(claimAt(0, 0, 10));
        assertTrue(index.overlaps(claimAt(20, 0, 10)));
    }

    // ── different radii ───────────────────────────────────────────────────────

    @Test
    void largeRadiusContainsSmallClaimCenter() {
        ClaimIndex index = new ClaimIndex();
        ProtectionClaim large = claimAt(0, 0, 48); // domain tier radius
        index.add(large);
        // A point at (30, 30) is inside the large claim
        assertTrue(index.findClaim("world", 30, 30).isPresent());
        // A point far out
        assertTrue(index.findClaim("world", 60, 60).isEmpty());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    static ProtectionClaim claimAt(int x, int z, int radius) {
        Instant now = Instant.now();
        return new ProtectionClaim(
                UUID.randomUUID(),
                "Test Claim",
                "world",
                UUID.randomUUID(),
                x, 64, z,
                radius, radius,
                ProtectionState.ACTIVE,
                "advanced",
                now, now, now, now,
                new HashSet<>(),
                new HashMap<>(),
                new ClaimStats(),
                new UpkeepStorage(),
                x, 64, z
        );
    }
}
