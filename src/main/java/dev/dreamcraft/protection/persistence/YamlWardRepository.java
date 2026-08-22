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
    public Optional<Ward> findConflicting(String worldName, int x, int z, int radius, UUID excludeId) {
        return cache.values().stream()
                .filter(w -> w.worldName().equals(worldName))
                .filter(w -> !w.id().equals(excludeId))
                .filter(w -> Math.abs(w.centerX() - x) <= radius
                        && Math.abs(w.centerZ() - z) <= radius)
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
            WardPermission perm = parsePermission(p);
            if (perm != null) permissions.add(perm);
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
                readInstant(s, "created-at"),
                readInstant(s, "last-upkeep-at"),
                readInstant(s, "next-upkeep-at"),
                s.getInt("center-x"),
                s.getInt("center-y"),
                s.getInt("center-z"),
                s.getString("wg-region-id"),
                permissions
        );
    }

    /**
     * Reads an {@link Instant} stored either as epoch millis (current format)
     * or as an ISO-8601 / mangled-date string (legacy format). Bukkit's YAML
     * loader auto-converts ISO date-looking strings into {@code java.util.Date},
     * whose toString() breaks naive {@code Instant.parse} calls — hence the
     * tolerant parsing.
     */
    private Instant readInstant(ConfigurationSection s, String key) {
        Object raw = s.get(key);
        if (raw instanceof Number number) return Instant.ofEpochMilli(number.longValue());
        String text = String.valueOf(raw);
        try { return Instant.parse(text); } catch (Exception ignored) {}
        try {
            return new java.text.SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy", java.util.Locale.US)
                    .parse(text).toInstant();
        } catch (Exception ignored) {}
        return Instant.now();
    }

    /**
     * Resolves a persisted permission name, mapping legacy names to their
     * renamed constants (e.g. {@code PUBLIC_INTERACT} → {@code PUBLIC_CONTAINERS}).
     */
    private WardPermission parsePermission(String raw) {
        if ("PUBLIC_INTERACT".equals(raw)) return WardPermission.PUBLIC_CONTAINERS;
        try { return WardPermission.valueOf(raw); } catch (IllegalArgumentException e) { return null; }
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
        s.set("created-at", w.createdAt().toEpochMilli());
        s.set("last-upkeep-at", w.lastUpkeepAt().toEpochMilli());
        s.set("next-upkeep-at", w.nextUpkeepAt().toEpochMilli());
        s.set("center-x", w.centerX());
        s.set("center-y", w.centerY());
        s.set("center-z", w.centerZ());
        s.set("wg-region-id", w.worldGuardRegionId());
        s.set("permissions", w.permissions().stream().map(Enum::name).toList());
    }
}
