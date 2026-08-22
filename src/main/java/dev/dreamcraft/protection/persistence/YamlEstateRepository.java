package dev.dreamcraft.protection.persistence;

import dev.dreamcraft.protection.domain.model.Estate;
import dev.dreamcraft.protection.domain.model.EstateType;
import dev.dreamcraft.protection.domain.port.EstateRepository;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * YAML-backed implementation of {@link EstateRepository}.
 */
public final class YamlEstateRepository implements EstateRepository {

    private final File file;
    private final Map<UUID, Estate> cache = new ConcurrentHashMap<>();

    public YamlEstateRepository(File file) {
        this.file = file;
    }

    // ── Load ──────────────────────────────────────────────────────────────────

    public void loadAll() {
        cache.clear();
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("estates");
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(key);
            if (s == null) continue;
            try {
                Estate estate = readEstate(s, key);
                cache.put(estate.id(), estate);
            } catch (Exception e) {
                System.err.println("[DreamCraft] Failed to load estate " + key + ": " + e.getMessage());
            }
        }
    }

    // ── EstateRepository ──────────────────────────────────────────────────────

    @Override
    public Optional<Estate> findById(UUID id) {
        return Optional.ofNullable(cache.get(id));
    }

    @Override
    public Collection<Estate> findByOwnerId(UUID ownerId) {
        return cache.values().stream()
                .filter(e -> e.ownerId().equals(ownerId))
                .collect(Collectors.toList());
    }

    @Override
    public Collection<Estate> findByMember(UUID memberId) {
        return cache.values().stream()
                .filter(e -> e.isMember(memberId))
                .collect(Collectors.toList());
    }

    @Override
    public Collection<Estate> findByAdventureId(String adventureId) {
        return cache.values().stream()
                .filter(e -> adventureId.equals(e.adventureId()))
                .collect(Collectors.toList());
    }

    @Override
    public Collection<Estate> findAll() {
        return Collections.unmodifiableCollection(cache.values());
    }

    @Override
    public void save(Estate estate) {
        cache.put(estate.id(), estate);
    }

    @Override
    public void delete(UUID id) {
        cache.remove(id);
    }

    @Override
    public void deleteAllTransient() {
        cache.values().removeIf(e -> !e.persistent());
    }

    // ── Flush to disk ─────────────────────────────────────────────────────────

    public void flush() throws IOException {
        file.getParentFile().mkdirs();
        YamlConfiguration yaml = new YamlConfiguration();
        ConfigurationSection root = yaml.createSection("estates");
        for (Estate e : cache.values()) {
            ConfigurationSection s = root.createSection(e.id().toString());
            writeEstate(s, e);
        }
        yaml.save(file);
    }

    // ── Serialization helpers ─────────────────────────────────────────────────

    private Estate readEstate(ConfigurationSection s, String key) {
        Set<UUID> members = new HashSet<>();
        for (String m : s.getStringList("members")) {
            try { members.add(UUID.fromString(m)); } catch (IllegalArgumentException ignored) {}
        }
        EstateType type;
        String typeRaw = s.getString("type", null);
        if (typeRaw != null) {
            type = EstateType.parse(typeRaw);
        } else {
            // Legacy fallback: infer from the adventure id suffix ("adv-end", "admin-trial_chamber")
            String adv = s.getString("adventure-id", null);
            type = adv != null && adv.contains("-")
                    ? EstateType.parse(adv.substring(adv.indexOf('-') + 1))
                    : EstateType.STANDARD;
        }
        Estate estate = new Estate(
                UUID.fromString(key),
                UUID.fromString(s.getString("owner-id")),
                members,
                s.getString("adventure-id"),
                s.getString("instance-id"),
                Instant.parse(s.getString("created-at")),
                s.getBoolean("persistent", false),
                s.getString("name", "Estate"),
                type,
                s.getString("area-world", null),
                s.getInt("area-x", 0),
                s.getInt("area-y", 0),
                s.getInt("area-z", 0),
                s.getInt("area-radius", 0)
        );
        estate.portalFrames(s.getStringList("portal-frames"));
        estate.containerLoot(s.getStringList("containers-loot"));
        return estate;
    }

    private void writeEstate(ConfigurationSection s, Estate e) {
        s.set("owner-id", e.ownerId().toString());
        s.set("name", e.name());
        s.set("type", e.type().key());
        s.set("adventure-id", e.adventureId());
        s.set("instance-id", e.instanceId());
        s.set("created-at", e.createdAt().toString());
        s.set("persistent", e.persistent());
        s.set("members", e.members().stream().map(UUID::toString).toList());
        if (e.hasArea()) {
            s.set("area-world", e.areaWorld());
            s.set("area-x", e.areaX());
            s.set("area-y", e.areaY());
            s.set("area-z", e.areaZ());
            s.set("area-radius", e.areaRadius());
        }
        if (!e.portalFrames().isEmpty()) {
            s.set("portal-frames", e.portalFrames());
        }
        if (!e.containerLoot().isEmpty()) {
            s.set("containers-loot", e.containerLoot());
        }
    }
}
