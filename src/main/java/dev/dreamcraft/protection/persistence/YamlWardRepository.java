package dev.dreamcraft.protection.persistence;

import dev.dreamcraft.protection.domain.model.*;
import dev.dreamcraft.protection.domain.port.WardRepository;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * YAML-backed implementation of {@link WardRepository}.
 * All Wards are kept in an in-memory map; {@link #saveAll} flushes to disk.
 */
public final class YamlWardRepository implements WardRepository {

    private final File file;
    private final Map<UUID, Ward> cache = new ConcurrentHashMap<>();

    public YamlWardRepository(File file) {
        this.file = file;
    }

    // ── Load ──────────────────────────────────────────────────────────────────

    public void loadAll() {
        cache.clear();
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("wards");
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(key);
            if (s == null) continue;
            try {
                Ward ward = readWard(s, key);
                cache.put(ward.id(), ward);
            } catch (Exception e) {
                System.err.println("[DreamCraft] Failed to load ward " + key + ": " + e.getMessage());
            }
        }
    }

    // ── WardRepository ────────────────────────────────────────────────────────

    @Override
    public Optional<Ward> findById(UUID id) {
        return Optional.ofNullable(cache.get(id));
    }

    @Override
    public Collection<Ward> findByOwnerId(UUID ownerId) {
        return cache.values().stream()
                .filter(w -> w.ownerId().equals(ownerId))
                .collect(Collectors.toList());
    }

    @Override
    public Collection<Ward> findByCityId(UUID cityId) {
        return cache.values().stream()
                .filter(w -> cityId.equals(w.cityId()))
                .collect(Collectors.toList());
    }

    @Override
    public Optional<Ward> findAtLocation(String worldName, int x, int z) {
        return cache.values().stream()
                .filter(w -> w.worldName().equals(worldName))
                .filter(w -> {
                    int dx = Math.abs(w.centerX() - x);
                    int dz = Math.abs(w.centerZ() - z);
                    return dx <= w.radius() && dz <= w.radius();
                })
                .findFirst();
    }

    @Override
    public Optional<Ward> findByCenter(String worldName, int x, int y, int z) {
        return cache.values().stream()
                .filter(w -> w.worldName().equals(worldName))
                .filter(w -> w.centerX() == x && w.centerY() == y && w.centerZ() == z)
                .findFirst();
    }

    @Override
    public Collection<Ward> findAll() {
        return Collections.unmodifiableCollection(cache.values());
    }

    @Override
    public void save(Ward ward) {
        cache.put(ward.id(), ward);
    }

    @Override
    public void delete(UUID id) {
        cache.remove(id);
    }

    @Override
    public void saveAll(Collection<Ward> wards) {
        cache.clear();
        for (Ward w : wards) cache.put(w.id(), w);
    }

    // ── Flush to disk ─────────────────────────────────────────────────────────

    public void flush() throws IOException {
        file.getParentFile().mkdirs();
        YamlConfiguration yaml = new YamlConfiguration();
        ConfigurationSection root = yaml.createSection("wards");
        for (Ward w : cache.values()) {
            ConfigurationSection s = root.createSection(w.id().toString());
            writeWard(s, w);
        }
        yaml.save(file);
    }

    // ── Serialization helpers ─────────────────────────────────────────────────

    private Ward readWard(ConfigurationSection s, String key) {
        Set<WardPermission> permissions = EnumSet.noneOf(WardPermission.class);
        for (String p : s.getStringList("permissions")) {
            try { permissions.add(WardPermission.valueOf(p)); } catch (IllegalArgumentException ignored) {}
        }
        String cityIdRaw = s.getString("city-id");
        return new Ward(
                UUID.fromString(key),
                s.getString("name", "Ward"),
                s.getString("world", "world"),
                UUID.fromString(s.getString("owner-id")),
                OwnerType.valueOf(s.getString("owner-type", "PLAYER")),
                cityIdRaw != null && !cityIdRaw.isEmpty() ? UUID.fromString(cityIdRaw) : null,
                s.getInt("base-score", 0),
                s.getString("tier", "basic"),
                s.getInt("radius", 16),
                s.getInt("upkeep-balance", 0),
                Instant.parse(s.getString("created-at")),
                Instant.parse(s.getString("last-upkeep-at")),
                Instant.parse(s.getString("next-upkeep-at")),
                s.getInt("center-x"),
                s.getInt("center-y"),
                s.getInt("center-z"),
                s.getString("wg-region-id"),
                permissions
        );
    }

    private void writeWard(ConfigurationSection s, Ward w) {
        s.set("name", w.name());
        s.set("world", w.worldName());
        s.set("owner-id", w.ownerId().toString());
        s.set("owner-type", w.ownerType().name());
        s.set("city-id", w.cityId() != null ? w.cityId().toString() : "");
        s.set("base-score", w.baseScore());
        s.set("tier", w.tier());
        s.set("radius", w.radius());
        s.set("upkeep-balance", w.upkeepBalance());
        s.set("created-at", w.createdAt().toString());
        s.set("last-upkeep-at", w.lastUpkeepAt().toString());
        s.set("next-upkeep-at", w.nextUpkeepAt().toString());
        s.set("center-x", w.centerX());
        s.set("center-y", w.centerY());
        s.set("center-z", w.centerZ());
        s.set("wg-region-id", w.worldGuardRegionId());
        s.set("permissions", w.permissions().stream().map(Enum::name).toList());
    }
}
