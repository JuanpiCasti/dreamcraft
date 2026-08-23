package dev.dreamcraft.protection.domain.service;

import dev.dreamcraft.protection.TestWardTierProvider;
import dev.dreamcraft.protection.domain.model.OwnerType;
import dev.dreamcraft.protection.domain.model.Ward;
import dev.dreamcraft.protection.domain.model.WardPermission;
import dev.dreamcraft.protection.domain.port.WardRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fix A: a real tier ascent through {@link WardService#addBaseScore} wipes the
 * below-tier surcharge counter (the higher phase now covers those blocks);
 * intra-tier changes and descents leave it untouched. Also verifies the
 * optional presentation hooks fire only on their respective transitions.
 */
class WardServiceTierAlignmentTest {

    private InMemoryWardRepository repo;
    private WardService service;

    /** Records the hook invocations: [kind, wardId, payload]. */
    private List<String> events;

    @BeforeEach
    void setUp() {
        repo = new InMemoryWardRepository();
        service = new WardService(repo, new TestWardTierProvider(), Duration.ofHours(24));
        events = new ArrayList<>();
        service.setTierAlignedCallback((ward, previous) -> events.add("aligned:" + previous));
        service.setTierDescendedCallback(ward -> events.add("descended"));
    }

    @Test
    void tierAscentWipesBelowTierCounter() {
        Ward ward = ward("basic", 0);
        ward.belowTierBlocks(3);

        service.addBaseScore(ward, 100); // 0 → 100 crosses into "reinforced"

        assertEquals("reinforced", ward.tier());
        assertEquals(0, ward.belowTierBlocks());
        assertEquals(List.of("aligned:3"), events);
        // Persisted wiped
        assertEquals(0, repo.findById(ward.id()).orElseThrow().belowTierBlocks());
    }

    @Test
    void intraTierScoreChangeKeepsCounter() {
        Ward ward = ward("basic", 10);
        ward.belowTierBlocks(3);

        service.addBaseScore(ward, 50); // 10 → 60, still "basic"

        assertEquals("basic", ward.tier());
        assertEquals(3, ward.belowTierBlocks());
        assertTrue(events.isEmpty()); // no hooks fired
    }

    @Test
    void tierDescentKeepsCounterForRescan() {
        Ward ward = ward("reinforced", 200);
        ward.belowTierBlocks(3);

        service.addBaseScore(ward, -150); // 200 → 50 falls back to "basic"

        assertEquals("basic", ward.tier());
        assertEquals(3, ward.belowTierBlocks()); // NOT touched here
        assertEquals(List.of("descended"), events); // re-scan hook notified instead
    }

    @Test
    void nullCallbacksRestoreNoOpDefaults() {
        service.setTierAlignedCallback(null);
        service.setTierDescendedCallback(null);

        Ward ward = ward("basic", 0);
        ward.belowTierBlocks(5);
        service.addBaseScore(ward, 100); // ascent with no-op hooks must not throw

        assertEquals(0, ward.belowTierBlocks());
        assertTrue(events.isEmpty());
    }

    private static Ward ward(String tierKey, int baseScore) {
        Ward w = new Ward(UUID.randomUUID(), "TestWard", "world",
                UUID.randomUUID(), OwnerType.PLAYER, null,
                baseScore, tierKey, 16, 0,
                Instant.EPOCH, Instant.EPOCH, Instant.EPOCH,
                0, 64, 0, null,
                EnumSet.noneOf(WardPermission.class));
        return w;
    }

    /** Minimal in-memory fake of the persistence port (no Bukkit). */
    private static final class InMemoryWardRepository implements WardRepository {
        private final Map<UUID, Ward> cache = new HashMap<>();

        @Override public Optional<Ward> findById(UUID id) { return Optional.ofNullable(cache.get(id)); }
        @Override public Collection<Ward> findByOwnerId(UUID ownerId) {
            return cache.values().stream().filter(w -> w.ownerId().equals(ownerId)).toList();
        }
        @Override public Collection<Ward> findByCityId(UUID cityId) {
            return cache.values().stream().filter(w -> cityId.equals(w.cityId())).toList();
        }
        @Override public Optional<Ward> findAtLocation(String worldName, int x, int z) {
            return cache.values().stream()
                    .filter(w -> w.worldName().equals(worldName))
                    .filter(w -> Math.abs(w.centerX() - x) <= w.radius()
                            && Math.abs(w.centerZ() - z) <= w.radius())
                    .findFirst();
        }
        @Override public Optional<Ward> findConflicting(String worldName, int x, int z, int radius, UUID excludeId) {
            return cache.values().stream()
                    .filter(w -> w.worldName().equals(worldName))
                    .filter(w -> !w.id().equals(excludeId))
                    .filter(w -> Math.abs(w.centerX() - x) <= radius
                            && Math.abs(w.centerZ() - z) <= radius)
                    .findFirst();
        }
        @Override public Optional<Ward> findByCenter(String worldName, int x, int y, int z) {
            return cache.values().stream()
                    .filter(w -> w.worldName().equals(worldName))
                    .filter(w -> w.centerX() == x && w.centerY() == y && w.centerZ() == z)
                    .findFirst();
        }
        @Override public Collection<Ward> findAll() { return List.copyOf(cache.values()); }
        @Override public void save(Ward ward) { cache.put(ward.id(), ward); }
        @Override public void delete(UUID id) { cache.remove(id); }
        @Override public void saveAll(Collection<Ward> wards) {
            cache.clear();
            wards.forEach(w -> cache.put(w.id(), w));
        }
    }
}
