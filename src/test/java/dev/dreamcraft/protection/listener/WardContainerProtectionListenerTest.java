package dev.dreamcraft.protection.listener;

import dev.dreamcraft.protection.domain.model.OwnerType;
import dev.dreamcraft.protection.domain.model.Ward;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.EnumSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the hopper/dropper cross-boundary transfer rule.
 * Pure domain objects — no Bukkit server required.
 */
class WardContainerProtectionListenerTest {

    private final UUID ownerA = UUID.randomUUID();
    private final UUID ownerB = UUID.randomUUID();
    private final UUID cityX = UUID.randomUUID();
    private final UUID cityY = UUID.randomUUID();

    private Ward ward(UUID id, UUID ownerId, UUID cityId) {
        return new Ward(id, "W-" + id.toString().substring(0, 4), "world",
                ownerId, OwnerType.PLAYER, cityId,
                0, "basic", 16, 0,
                Instant.now(), Instant.now(), Instant.now().plusSeconds(3600),
                0, 64, 0, null,
                EnumSet.noneOf(dev.dreamcraft.protection.domain.model.WardPermission.class));
    }

    @Test
    void transfersOutsideAllWardsAreAllowed() {
        assertTrue(WardContainerProtectionListener.transferAllowed(null, null));
    }

    @Test
    void transfersInsideSameWardAreAllowed() {
        Ward w = ward(UUID.randomUUID(), ownerA, null);
        assertTrue(WardContainerProtectionListener.transferAllowed(w, w));
    }

    @Test
    void crossingIntoOrOutOfAWardIsBlocked() {
        Ward w = ward(UUID.randomUUID(), ownerA, null);
        assertFalse(WardContainerProtectionListener.transferAllowed(null, w)); // push in
        assertFalse(WardContainerProtectionListener.transferAllowed(w, null)); // drain out
    }

    @Test
    void crossingBetweenDifferentOwnersIsBlocked() {
        Ward a = ward(UUID.randomUUID(), ownerA, null);
        Ward b = ward(UUID.randomUUID(), ownerB, null);
        assertFalse(WardContainerProtectionListener.transferAllowed(a, b));
        assertFalse(WardContainerProtectionListener.transferAllowed(b, a));
    }

    @Test
    void sameOwnerAcrossOwnWardsIsAllowed() {
        Ward a = ward(UUID.randomUUID(), ownerA, null);
        Ward b = ward(UUID.randomUUID(), ownerA, null);
        assertTrue(WardContainerProtectionListener.transferAllowed(a, b));
    }

    @Test
    void sameCityAcrossWardsIsAllowed() {
        Ward a = ward(UUID.randomUUID(), ownerA, cityX);
        Ward b = ward(UUID.randomUUID(), ownerB, cityX);
        assertTrue(WardContainerProtectionListener.transferAllowed(a, b));

        Ward c = ward(UUID.randomUUID(), ownerB, cityY);
        assertFalse(WardContainerProtectionListener.transferAllowed(a, c));
    }

    @Test
    void sameOwnerTakesPrecedenceOverCityBoundary() {
        // Both wards belong to the same player; one annexed to a city, one not.
        // The owner runs his own infrastructure — allowed despite city mismatch.
        Ward noCity = ward(UUID.randomUUID(), ownerA, null);
        Ward inCity = ward(UUID.randomUUID(), ownerA, cityX);
        assertTrue(WardContainerProtectionListener.transferAllowed(noCity, inCity));
    }

    @Test
    void differentOwnersAcrossCitiesAreBlocked() {
        Ward a = ward(UUID.randomUUID(), ownerA, cityX);
        Ward b = ward(UUID.randomUUID(), ownerB, cityY);
        assertFalse(WardContainerProtectionListener.transferAllowed(a, b));
    }
}
