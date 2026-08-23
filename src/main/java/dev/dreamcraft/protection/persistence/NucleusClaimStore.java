package dev.dreamcraft.protection.persistence;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * YAML-backed store for the one-time free nucleus claim ("/ward reclamar").
 *
 * <p>Each player UUID may consume the free founder item exactly once. The
 * record persists in {@code nucleus-claims.yml} and is written synchronously
 * at claim time, so a crash/relog can never grant a second free core.
 */
public final class NucleusClaimStore {

    private final File file;
    private final Set<UUID> claimed = ConcurrentHashMap.newKeySet();

    public NucleusClaimStore(File file) {
        this.file = file;
    }

    // ── Load / flush ──────────────────────────────────────────────────────────

    public void loadAll() {
        claimed.clear();
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String raw : yaml.getStringList("claims")) {
            try {
                claimed.add(UUID.fromString(raw));
            } catch (IllegalArgumentException e) {
                System.err.println("[DreamCraft] Invalid claimed UUID: " + raw);
            }
        }
    }

    public void flush() throws IOException {
        file.getParentFile().mkdirs();
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("claims", claimed.stream().map(UUID::toString).sorted().toList());
        yaml.save(file);
    }

    // ── Access ────────────────────────────────────────────────────────────────

    /** @return true when the UUID had not claimed before (and now did). */
    public boolean hasClaimed(UUID playerId) {
        return claimed.contains(playerId);
    }

    /**
     * Consumes the one-time free nucleus and persists immediately.
     *
     * @return false when the UUID had already claimed (nothing is written)
     * @throws IOException when persisting fails — the in-memory mark is rolled
     *                     back so the caller can retry without losing the grant
     */
    public boolean claimPersistently(UUID playerId) throws IOException {
        if (!claimed.add(playerId)) return false;
        try {
            flush();
        } catch (IOException e) {
            claimed.remove(playerId);
            throw e;
        }
        return true;
    }

    /** Claimed UUID count — for boot diagnostics. */
    public int size() {
        return claimed.size();
    }

    /** Read-only view for diagnostics/tests. */
    public List<UUID> claimedIds() {
        return List.copyOf(claimed);
    }
}
