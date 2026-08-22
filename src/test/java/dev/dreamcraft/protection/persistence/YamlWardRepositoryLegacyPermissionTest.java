package dev.dreamcraft.protection.persistence;

import dev.dreamcraft.protection.domain.model.WardPermission;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that legacy persisted permission names keep loading after the
 * PUBLIC_INTERACT → PUBLIC_CONTAINERS rename.
 */
class YamlWardRepositoryLegacyPermissionTest {

    @TempDir
    Path tempDir;

    @Test
    void legacyPublicInteractLoadsAsPublicContainers() throws IOException {
        UUID wardId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        String yaml = """
                wards:
                  %s:
                    name: Legacy
                    world: world
                    owner-id: %s
                    owner-type: PLAYER
                    city-id: ""
                    base-score: 0
                    tier: basic
                    radius: 16
                    upkeep-balance: 0
                    created-at: 2024-01-01T00:00:00Z
                    last-upkeep-at: 2024-01-01T00:00:00Z
                    next-upkeep-at: 2024-01-02T00:00:00Z
                    center-x: 0
                    center-y: 64
                    center-z: 0
                    wg-region-id: dc_ward_legacy
                    permissions:
                      - PUBLIC_INTERACT
                      - PUBLIC_STATUS_VIEW
                """.formatted(wardId, ownerId);

        File file = tempDir.resolve("wards.yml").toFile();
        Files.writeString(file.toPath(), yaml);

        YamlWardRepository repo = new YamlWardRepository(file);
        repo.loadAll();

        var ward = repo.findById(wardId).orElseThrow();
        assertTrue(ward.hasPermission(WardPermission.PUBLIC_CONTAINERS),
                "PUBLIC_INTERACT should load as PUBLIC_CONTAINERS");
        assertTrue(ward.hasPermission(WardPermission.PUBLIC_STATUS_VIEW));
        assertFalse(ward.permissions().stream()
                .anyMatch(p -> p.name().equals("PUBLIC_INTERACT")));
    }

    @Test
    void unknownPermissionNamesAreIgnored() throws IOException {
        UUID wardId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        String yaml = """
                wards:
                  %s:
                    name: Weird
                    world: world
                    owner-id: %s
                    owner-type: PLAYER
                    city-id: ""
                    base-score: 0
                    tier: basic
                    radius: 16
                    upkeep-balance: 0
                    created-at: 2024-01-01T00:00:00Z
                    last-upkeep-at: 2024-01-01T00:00:00Z
                    next-upkeep-at: 2024-01-02T00:00:00Z
                    center-x: 0
                    center-y: 64
                    center-z: 0
                    wg-region-id: null
                    permissions:
                      - NOT_A_REAL_PERMISSION
                      - PUBLIC_STATUS_VIEW
                """.formatted(wardId, ownerId);

        File file = tempDir.resolve("wards.yml").toFile();
        Files.writeString(file.toPath(), yaml);

        YamlWardRepository repo = new YamlWardRepository(file);
        repo.loadAll();

        var ward = repo.findById(wardId).orElseThrow();
        assertEquals(1, ward.permissions().size());
        assertTrue(ward.hasPermission(WardPermission.PUBLIC_STATUS_VIEW));
    }

    @Test
    void epochFormatRoundTripsWithPermissions() throws IOException {
        File file = tempDir.resolve("wards.yml").toFile();
        YamlWardRepository repo = new YamlWardRepository(file);

        UUID wardId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        dev.dreamcraft.protection.domain.model.Ward ward =
                new dev.dreamcraft.protection.domain.model.Ward(
                        wardId, "RoundTrip", "world", ownerId,
                        dev.dreamcraft.protection.domain.model.OwnerType.PLAYER, null,
                        120, "reinforced", 42, 5,
                        java.time.Instant.now().minusSeconds(60),
                        java.time.Instant.now(),
                        java.time.Instant.now().plusSeconds(3600),
                        10, 64, 20, "dc_ward_rt",
                        java.util.EnumSet.of(WardPermission.PUBLIC_CONTAINERS));
        repo.save(ward);
        repo.flush();

        YamlWardRepository reloaded = new YamlWardRepository(file);
        reloaded.loadAll();

        var back = reloaded.findById(wardId).orElseThrow();
        assertTrue(back.hasPermission(WardPermission.PUBLIC_CONTAINERS));
        assertEquals("dc_ward_rt", back.worldGuardRegionId());
        assertEquals(42, back.radius());
        var millis = java.time.temporal.ChronoUnit.MILLIS;
        assertEquals(ward.createdAt().truncatedTo(millis), back.createdAt());
        assertEquals(ward.nextUpkeepAt().truncatedTo(millis), back.nextUpkeepAt());
    }
}
