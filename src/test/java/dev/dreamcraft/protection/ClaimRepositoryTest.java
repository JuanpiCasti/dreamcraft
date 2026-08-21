package dev.dreamcraft.protection;

import dev.dreamcraft.protection.model.*;
import dev.dreamcraft.protection.persistence.ClaimRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ClaimRepository: save → reload → verify state equivalence.
 * These tests run in a temp directory without requiring a Bukkit server.
 */
class ClaimRepositoryTest {

    @TempDir
    File tmpDir;

    // ── Round-trip ────────────────────────────────────────────────────────────

    @Test
    void saveThenLoadProducesSameClaim() throws IOException {
        File file = new File(tmpDir, "claims.yml");
        ClaimRepository repo = new ClaimRepository(file);

        UUID claimId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID member1 = UUID.randomUUID();
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);

        ClaimStats stats = new ClaimStats();
        stats.restore(Map.of("STONE", 5), Map.of("basic", 5), 5);
        UpkeepStorage storage = new UpkeepStorage();
        storage.deposit("maintenance", 128);

        ProtectionClaim original = new ProtectionClaim(
                claimId, "Claim Principal", "world", ownerId,
                10, 64, 20, 16, 14,
                ProtectionState.WARNING, "advanced",
                now, now, now.plusSeconds(86400), now,
                Set.of(member1), new HashMap<>(),
                stats, storage,
                10, 64, 20
        );

        repo.saveAll(List.of(original));
        List<ProtectionClaim> loaded = repo.loadAll();

        assertEquals(1, loaded.size());
        ProtectionClaim loaded1 = loaded.get(0);

        assertEquals(claimId, loaded1.id());
        assertEquals("Claim Principal", loaded1.name());
        assertEquals("world", loaded1.world());
        assertEquals(ownerId, loaded1.ownerUuid());
        assertEquals(10, loaded1.centerX());
        assertEquals(20, loaded1.centerZ());
        assertEquals(16, loaded1.radius());
        assertEquals(14, loaded1.buildRadius());
        assertEquals(ProtectionState.WARNING, loaded1.status());
        assertEquals("advanced", loaded1.tier());
        assertEquals(now, loaded1.createdAt());
        assertTrue(loaded1.members().contains(member1));
        assertEquals(128, loaded1.upkeepStorage().get("maintenance"));
        assertEquals(5, loaded1.stats().totalBlocks());
    }

    @Test
    void emptyRepositoryReturnsEmptyList() {
        File file = new File(tmpDir, "claims_empty.yml");
        ClaimRepository repo = new ClaimRepository(file);
        List<ProtectionClaim> claims = repo.loadAll();
        assertTrue(claims.isEmpty());
    }

    @Test
    void multipleClaimsSavedAndLoaded() throws IOException {
        File file = new File(tmpDir, "multi.yml");
        ClaimRepository repo = new ClaimRepository(file);

        ProtectionClaim c1 = minimalClaim(UUID.randomUUID(), 0, 0, "basic");
        ProtectionClaim c2 = minimalClaim(UUID.randomUUID(), 100, 100, "fortress");
        ProtectionClaim c3 = minimalClaim(UUID.randomUUID(), -200, 50, "domain");

        repo.saveAll(List.of(c1, c2, c3));
        List<ProtectionClaim> loaded = repo.loadAll();

        assertEquals(3, loaded.size());
        Set<UUID> ids = new HashSet<>();
        loaded.forEach(c -> ids.add(c.id()));
        assertTrue(ids.contains(c1.id()));
        assertTrue(ids.contains(c2.id()));
        assertTrue(ids.contains(c3.id()));
    }

    @Test
    void upkeepStorageRoundTrip() throws IOException {
        File file = new File(tmpDir, "upkeep.yml");
        ClaimRepository repo = new ClaimRepository(file);

        UpkeepStorage storage = new UpkeepStorage();
        storage.deposit("maintenance", 999);
        ProtectionClaim claim = minimalClaimWithStorage(storage);

        repo.saveAll(List.of(claim));
        List<ProtectionClaim> loaded = repo.loadAll();

        assertEquals(999, loaded.get(0).upkeepStorage().get("maintenance"));
    }

    @Test
    void lastActivityAtIsPersistedAndRestored() throws IOException {
        File file = new File(tmpDir, "activity.yml");
        ClaimRepository repo = new ClaimRepository(file);

        Instant activity = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        Instant now = activity;
        ProtectionClaim claim = new ProtectionClaim(
                UUID.randomUUID(), "Test Claim", "world", UUID.randomUUID(),
                0, 64, 0, 16, 16,
                ProtectionState.ACTIVE, "basic",
                now, now, now, activity,
                new HashSet<>(), new HashMap<>(),
                new ClaimStats(), new UpkeepStorage(),
                0, 64, 0
        );

        repo.saveAll(List.of(claim));
        List<ProtectionClaim> loaded = repo.loadAll();

        assertEquals(activity, loaded.get(0).lastActivityAt());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ProtectionClaim minimalClaim(UUID id, int x, int z, String tier) {
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        return new ProtectionClaim(
                id, "Test Claim", "world", UUID.randomUUID(),
                x, 64, z, 16, 16,
                ProtectionState.ACTIVE, tier,
                now, now, now, now,
                new HashSet<>(), new HashMap<>(),
                new ClaimStats(), new UpkeepStorage(),
                x, 64, z
        );
    }

    private ProtectionClaim minimalClaimWithStorage(UpkeepStorage storage) {
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        return new ProtectionClaim(
                UUID.randomUUID(), "Test Claim", "world", UUID.randomUUID(),
                0, 64, 0, 16, 16,
                ProtectionState.ACTIVE, "basic",
                now, now, now, now,
                new HashSet<>(), new HashMap<>(),
                new ClaimStats(), storage,
                0, 64, 0
        );
    }
}
