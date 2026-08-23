package dev.dreamcraft.protection.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract of the one-time free nucleus claim store ("/ward reclamar"):
 * exactly one grant per UUID, persisted synchronously and re-loaded on boot.
 */
class NucleusClaimStoreTest {

    @TempDir
    Path tempDir;

    private NucleusClaimStore store() {
        return new NucleusClaimStore(tempDir.resolve("nucleus-claims.yml").toFile());
    }

    @Test
    void grantsExactlyOncePerUuid() throws IOException {
        NucleusClaimStore store = store();
        UUID player = UUID.randomUUID();

        assertTrue(store.claimPersistently(player), "first claim is granted");
        assertFalse(store.claimPersistently(player), "second claim is refused");
        assertFalse(store.claimPersistently(player), "still refused after repeats");
        assertTrue(store.hasClaimed(player));
    }

    @Test
    void otherPlayersKeepTheirOwnGrant() throws IOException {
        NucleusClaimStore store = store();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();

        assertTrue(store.claimPersistently(a));
        assertTrue(store.claimPersistently(b), "a different UUID is unaffected");
        assertFalse(store.claimPersistently(a));
    }

    @Test
    void survivesReloadRoundTrip() throws IOException {
        UUID claimed = UUID.randomUUID();
        UUID fresh = UUID.randomUUID();

        NucleusClaimStore first = store();
        first.claimPersistently(claimed);

        // New instance simulates a server reboot: same file, empty cache
        NucleusClaimStore rebooted = store();
        rebooted.loadAll();

        assertTrue(rebooted.hasClaimed(claimed), "claim survives the reload");
        assertTrue(rebooted.claimPersistently(fresh), "unclaimed UUID can still claim after reboot");
        assertEquals(2, rebooted.size());
    }

    @Test
    void emptyOrMissingFileStartsWithNothingClaimed() {
        NucleusClaimStore store = store();
        store.loadAll(); // file does not exist yet

        assertEquals(0, store.size());
        assertFalse(store.hasClaimed(UUID.randomUUID()));
        assertDoesNotThrow(() -> store.flush(), "flushing an empty store creates a valid file");
        assertTrue(tempDir.resolve("nucleus-claims.yml").toFile().isFile());
    }

    @Test
    void ignoresCorruptedUuidLinesInsteadOfFailingBoot() throws IOException {
        java.nio.file.Files.writeString(
                tempDir.resolve("nucleus-claims.yml"),
                "claims:\n- not-a-uuid\n- " + UUID.randomUUID() + "\n");

        NucleusClaimStore store = store();
        assertDoesNotThrow(store::loadAll);
        assertEquals(1, store.size(), "only the valid UUID loads");
    }
}
