package dev.dreamcraft.protection.persistence;

import dev.dreamcraft.protection.domain.model.City;
import dev.dreamcraft.protection.domain.model.CityPolicy;
import dev.dreamcraft.protection.domain.model.CityRole;
import dev.dreamcraft.protection.domain.port.CityRepository;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * YAML-backed implementation of {@link CityRepository}.
 */
public final class YamlCityRepository implements CityRepository {

    private final File file;
    private final Map<UUID, City> cache = new ConcurrentHashMap<>();

    public YamlCityRepository(File file) {
        this.file = file;
    }

    // ── Load ──────────────────────────────────────────────────────────────────

    public void loadAll() {
        cache.clear();
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("cities");
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(key);
            if (s == null) continue;
            try {
                City city = readCity(s, key);
                cache.put(city.id(), city);
            } catch (Exception e) {
                System.err.println("[DreamCraft] Failed to load city " + key + ": " + e.getMessage());
            }
        }
    }

    // ── CityRepository ────────────────────────────────────────────────────────

    @Override
    public Optional<City> findById(UUID id) {
        return Optional.ofNullable(cache.get(id));
    }

    @Override
    public Optional<City> findByName(String name) {
        return cache.values().stream()
                .filter(c -> c.name().equalsIgnoreCase(name))
                .findFirst();
    }

    @Override
    public Optional<City> findByGovernor(UUID governorId) {
        return cache.values().stream()
                .filter(c -> c.governorId().equals(governorId))
                .findFirst();
    }

    @Override
    public Optional<City> findByMember(UUID memberId) {
        return cache.values().stream()
                .filter(c -> c.isMember(memberId))
                .findFirst();
    }

    @Override
    public Collection<City> findAll() {
        return Collections.unmodifiableCollection(cache.values());
    }

    @Override
    public void save(City city) {
        cache.put(city.id(), city);
    }

    @Override
    public void delete(UUID id) {
        cache.remove(id);
    }

    @Override
    public void saveAll(Collection<City> cities) {
        cache.clear();
        for (City c : cities) cache.put(c.id(), c);
    }

    // ── Flush to disk ─────────────────────────────────────────────────────────

    public void flush() throws IOException {
        file.getParentFile().mkdirs();
        YamlConfiguration yaml = new YamlConfiguration();
        ConfigurationSection root = yaml.createSection("cities");
        for (City c : cache.values()) {
            ConfigurationSection s = root.createSection(c.id().toString());
            writeCity(s, c);
        }
        yaml.save(file);
    }

    // ── Serialization helpers ─────────────────────────────────────────────────

    private City readCity(ConfigurationSection s, String key) {
        Map<UUID, CityRole> members = new HashMap<>();
        ConfigurationSection membersSection = s.getConfigurationSection("members");
        if (membersSection != null) {
            for (String m : membersSection.getKeys(false)) {
                try {
                    members.put(UUID.fromString(m), CityRole.valueOf(membersSection.getString(m, "CITIZEN")));
                } catch (Exception ignored) {}
            }
        }
        Set<CityPolicy> policies = EnumSet.noneOf(CityPolicy.class);
        for (String p : s.getStringList("policies")) {
            try { policies.add(CityPolicy.valueOf(p)); } catch (IllegalArgumentException ignored) {}
        }
        return new City(
                UUID.fromString(key),
                UUID.fromString(s.getString("governor-id")),
                members,
                s.getLong("treasury", 0L),
                s.getInt("city-score", 0),
                Instant.parse(s.getString("created-at")),
                s.getString("name", "Unnamed"),
                policies
        );
    }

    private void writeCity(ConfigurationSection s, City c) {
        s.set("name", c.name());
        s.set("governor-id", c.governorId().toString());
        s.set("treasury", c.treasury());
        s.set("city-score", c.cityScore());
        s.set("created-at", c.createdAt().toString());
        s.set("policies", c.policies().stream().map(Enum::name).toList());
        ConfigurationSection membersSection = s.createSection("members");
        for (Map.Entry<UUID, CityRole> entry : c.members().entrySet()) {
            membersSection.set(entry.getKey().toString(), entry.getValue().name());
        }
    }
}
