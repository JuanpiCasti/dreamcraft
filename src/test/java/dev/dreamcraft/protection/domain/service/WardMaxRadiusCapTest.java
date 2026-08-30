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
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ward.max-radius: hard ceiling for a single Ward's protection radius.
 * createWard / addBaseScore / computeRadiusAfter clamp against the ceiling,
 * while the legacy 3-arg constructor stays uncapped (backwards compatibility).
 */
class WardMaxRadiusCapTest {

    private static final int CAP = 80;

    private InMemoryWardRepository repo;
    private WardService service;

    @BeforeEach
    void setUp() {
        repo = new InMemoryWardRepository();
        service = new WardService(repo, new TestWardTierProvider(), Duration.ofHours(24), CAP);
    }

    @Test
    void createWardStartsBelowCap() {
        Ward ward = service.createWard(UUID.randomUUID(), OwnerType.PLAYER, null, "world", 0, 64, 0);
        assertEquals(16, ward.radius()); // basic tier base radius
    }

    @Test
    void addBaseScoreClampsToCap() {
        Ward ward = ward("basic", 0);
        // TestWardTierProvider advanced: 64 + score*0.1 → score 1000 daría 164
        service.addBaseScore(ward, 1000);

        assertEquals("advanced", ward.tier());
        assertEquals(CAP, ward.radius());
        assertEquals(CAP, repo.findById(ward.id()).orElseThrow().radius());
    }

    @Test
    void computeRadiusAfterClampsToCap() {
        Ward ward = ward("basic", 0);
        assertTrue(service.computeRadiusAfter(ward, 1000) <= CAP);
    }

    @Test
    void radiusUnderCapGrowsFreely() {
        Ward ward = ward("basic", 0);
        service.addBaseScore(ward, 100); // reinforced: 32 + 100*0.1 = 42 < cap
        assertEquals(42, ward.radius());
    }

    @Test
    void legacyConstructorStaysUncapped() {
        WardService uncapped = new WardService(repo, new TestWardTierProvider(), Duration.ofHours(24));
        Ward ward = ward("basic", 0);
        uncapped.addBaseScore(ward, 1000);
        assertEquals(164, ward.radius()); // comportamiento histórico intacto
    }

    private static Ward ward(String tierKey, int baseScore) {
        return new Ward(UUID.randomUUID(), "TestWard", "world",
                UUID.randomUUID(), OwnerType.PLAYER, null,
                baseScore, tierKey, 16, 0,
                Instant.EPOCH, Instant.EPOCH, Instant.EPOCH,
                0, 64, 0, null,
                EnumSet.noneOf(WardPermission.class));
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
        @Override public Optional<Ward> findAtLocation(String worldName, int x, int z) { return Optional.empty(); }
        @Override public Optional<Ward> findConflicting(String worldName, int x, int z, int radius, UUID excludeId) { return Optional.empty(); }
        @Override public Optional<Ward> findByCenter(String worldName, int x, int y, int z) { return Optional.empty(); }
        @Override public Collection<Ward> findAll() { return java.util.List.copyOf(cache.values()); }
        @Override public void save(Ward ward) { cache.put(ward.id(), ward); }
        @Override public void delete(UUID id) { cache.remove(id); }
        @Override public void saveAll(Collection<Ward> wards) {
            cache.clear();
            wards.forEach(w -> cache.put(w.id(), w));
        }
    }
}
