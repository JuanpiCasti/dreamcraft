package dev.dreamcraft.protection.persistence;

import dev.dreamcraft.protection.model.ClaimStats;
import dev.dreamcraft.protection.model.ProtectionClaim;
import dev.dreamcraft.protection.model.ProtectionState;
import dev.dreamcraft.protection.model.UpkeepStorage;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.*;

public final class ClaimRepository {
    private final File file;

    public ClaimRepository(File file) {
        this.file = file;
    }

    public List<ProtectionClaim> loadAll() {
        if (!file.exists()) {
            return List.of();
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection claimsSection = yaml.getConfigurationSection("claims");
        if (claimsSection == null) {
            return List.of();
        }
        List<ProtectionClaim> claims = new ArrayList<>();
        for (String key : claimsSection.getKeys(false)) {
            ConfigurationSection section = claimsSection.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            try {
                claims.add(readClaim(section));
            } catch (Exception e) {
                // Log but continue loading other claims
                System.err.println("[DreamCraftProtection] Failed to load claim " + key + ": " + e.getMessage());
            }
        }
        return claims;
    }

    public void saveAll(Collection<ProtectionClaim> claims) throws IOException {
        file.getParentFile().mkdirs();
        YamlConfiguration yaml = new YamlConfiguration();
        ConfigurationSection claimsSection = yaml.createSection("claims");
        for (ProtectionClaim claim : claims) {
            ConfigurationSection section = claimsSection.createSection(claim.id().toString());
            writeClaim(section, claim);
        }
        yaml.save(file);
    }

    private ProtectionClaim readClaim(ConfigurationSection section) {
        ClaimStats stats = new ClaimStats();
        Map<String, Integer> materials = new HashMap<>();
        Map<String, Integer> categories = new HashMap<>();
        int totalBlocks = 0;
        ConfigurationSection materialSection = section.getConfigurationSection("stats.materials");
        if (materialSection != null) {
            for (String key : materialSection.getKeys(false)) {
                int count = materialSection.getInt(key);
                materials.put(key, count);
                totalBlocks += count;
            }
        }
        ConfigurationSection categoriesSection = section.getConfigurationSection("stats.categories");
        if (categoriesSection != null) {
            for (String key : categoriesSection.getKeys(false)) {
                categories.put(key, categoriesSection.getInt(key));
            }
        }
        stats.restore(materials, categories, totalBlocks);

        UpkeepStorage storage = new UpkeepStorage();
        ConfigurationSection upkeepSection = section.getConfigurationSection("upkeep");
        if (upkeepSection != null) {
            for (String key : upkeepSection.getKeys(false)) {
                storage.deposit(key, upkeepSection.getInt(key));
            }
        }

        List<String> rawMembers = section.getStringList("members");
        Set<UUID> members = new HashSet<>();
        for (String rawMember : rawMembers) {
            try {
                members.add(UUID.fromString(rawMember));
            } catch (IllegalArgumentException ignored) {
                // skip malformed UUID
            }
        }

        // lastActivityAt: default to createdAt if missing (safe for old data)
        String lastActivityRaw = section.getString("last-activity-at");
        Instant createdAt = Instant.parse(section.getString("created-at"));
        Instant lastActivityAt = lastActivityRaw != null ? Instant.parse(lastActivityRaw) : createdAt;

        // lastNotifiedState: optional, may be absent in old data
        String lastNotifiedRaw = section.getString("last-notified-state");

        ProtectionClaim claim = new ProtectionClaim(
                UUID.fromString(section.getName()),
                section.getString("name", "Protección"),
                section.getString("world", "world"),
                UUID.fromString(section.getString("owner")),
                section.getInt("center.x"),
                section.getInt("center.y"),
                section.getInt("center.z"),
                section.getInt("radius"),
                section.getInt("build-radius"),
                ProtectionState.valueOf(section.getString("status", "ACTIVE")),
                section.getString("tier", "advanced"),
                createdAt,
                Instant.parse(section.getString("last-upkeep-at")),
                Instant.parse(section.getString("next-upkeep-at")),
                lastActivityAt,
                members,
                new HashMap<>(),
                stats,
                storage,
                section.getInt("wardrobe.x"),
                section.getInt("wardrobe.y"),
                section.getInt("wardrobe.z")
        );

        if (lastNotifiedRaw != null && !lastNotifiedRaw.isEmpty()) {
            try {
                claim.lastNotifiedState(ProtectionState.valueOf(lastNotifiedRaw));
            } catch (IllegalArgumentException ignored) {
                // skip unknown state
            }
        }

        return claim;
    }

    private void writeClaim(ConfigurationSection section, ProtectionClaim claim) {
        section.set("name", claim.name());
        section.set("world", claim.world());
        section.set("owner", claim.ownerUuid().toString());
        section.set("center.x", claim.centerX());
        section.set("center.y", claim.centerY());
        section.set("center.z", claim.centerZ());
        section.set("wardrobe.x", claim.wardrobeX());
        section.set("wardrobe.y", claim.wardrobeY());
        section.set("wardrobe.z", claim.wardrobeZ());
        section.set("radius", claim.radius());
        section.set("build-radius", claim.buildRadius());
        section.set("status", claim.status().name());
        section.set("tier", claim.tier());
        section.set("created-at", claim.createdAt().toString());
        section.set("last-upkeep-at", claim.lastUpkeepAt().toString());
        section.set("next-upkeep-at", claim.nextUpkeepAt().toString());
        section.set("last-activity-at", claim.lastActivityAt().toString());
        if (claim.lastNotifiedState() != null) {
            section.set("last-notified-state", claim.lastNotifiedState().name());
        }
        section.set("members", claim.members().stream().map(UUID::toString).toList());
        for (Map.Entry<String, Integer> entry : claim.stats().materialCounts().entrySet()) {
            section.set("stats.materials." + entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, Integer> entry : claim.stats().categoryCounts().entrySet()) {
            section.set("stats.categories." + entry.getKey(), entry.getValue());
        }
        section.set("stats.total-blocks", claim.stats().totalBlocks());
        for (Map.Entry<String, Integer> entry : claim.upkeepStorage().snapshot().entrySet()) {
            section.set("upkeep." + entry.getKey(), entry.getValue());
        }
    }
}
