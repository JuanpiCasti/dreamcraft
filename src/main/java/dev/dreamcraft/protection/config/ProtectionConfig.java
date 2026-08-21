package dev.dreamcraft.protection.config;

import dev.dreamcraft.protection.model.TierDefinition;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public record ProtectionConfig(
        boolean enabled,
        int defaultRadius,
        int defaultBuildRadius,
        boolean allowOverlap,
        Material wardrobeMaterial,
        int inventorySize,
        Duration upkeepInterval,
        Duration warningThreshold,
        Duration expiringThreshold,
        Duration gracePeriod,
        Duration destructionDelay,
        Material upkeepResourceMaterial,
        int upkeepUnitsPerItem,
        int defaultMaxMembers,
        boolean ownerTransfer,
        Duration abandonedAfter,
        boolean resourcePackEnabled,
        boolean resourcePackOptional,
        boolean resourcePackFallbackVanilla,
        int customModelData,
        String resourceItemId,
        Material wardMaterial,
        String wardItemId,
        int wardCustomModelData,
        int wardScorePerUpgrade,
        Map<String, TierDefinition> tiers,
        Map<String, Integer> categoryBaseCosts,
        Map<Material, String> materialOverrides,
        Map<String, List<WardUpgradeCost>> wardUpgradeCosts
) {
    public static ProtectionConfig load(FileConfiguration config) {
        ConfigurationSection protection = config.getConfigurationSection("protection");
        ConfigurationSection upkeep = config.getConfigurationSection("upkeep");
        ConfigurationSection members = config.getConfigurationSection("members");
        ConfigurationSection claim = config.getConfigurationSection("claim");
        ConfigurationSection resourcePack = config.getConfigurationSection("resource-pack");
        ConfigurationSection ward = config.getConfigurationSection("ward");
        ConfigurationSection tiersSection = config.getConfigurationSection("tiers");
        ConfigurationSection buildingCost = config.getConfigurationSection("building-cost");
        ConfigurationSection categorySection = buildingCost == null ? null : buildingCost.getConfigurationSection("categories");
        ConfigurationSection overridesSection = buildingCost == null ? null : buildingCost.getConfigurationSection("overrides");

        Map<String, TierDefinition> tiers = new HashMap<>();
        if (tiersSection != null) {
            for (String key : tiersSection.getKeys(false)) {
                ConfigurationSection tierSection = tiersSection.getConfigurationSection(key);
                if (tierSection == null) {
                    continue;
                }
                tiers.put(key.toLowerCase(Locale.ROOT), new TierDefinition(
                        key.toLowerCase(Locale.ROOT),
                        tierSection.getInt("radius"),
                        tierSection.getInt("build-radius"),
                        tierSection.getInt("max-members")
                ));
            }
        }

        Map<String, Integer> categoryBaseCosts = new HashMap<>();
        if (categorySection != null) {
            for (String key : categorySection.getKeys(false)) {
                ConfigurationSection entry = categorySection.getConfigurationSection(key);
                if (entry == null) {
                    continue;
                }
                categoryBaseCosts.put(key.toLowerCase(Locale.ROOT), entry.getInt("base-cost"));
            }
        }

        Map<Material, String> materialOverrides = new HashMap<>();
        if (overridesSection != null) {
            for (String key : overridesSection.getKeys(false)) {
                Material material = Material.matchMaterial(key);
                if (material != null) {
                    materialOverrides.put(material, overridesSection.getString(key, "basic").toLowerCase(Locale.ROOT));
                }
            }
        }

        Map<String, List<WardUpgradeCost>> wardUpgradeCosts = new HashMap<>();
        ConfigurationSection upgradeCostsSection = config.getConfigurationSection("ward-upgrade-costs");
        if (upgradeCostsSection != null) {
            for (String tierKey : upgradeCostsSection.getKeys(false)) {
                List<WardUpgradeCost> costs = new ArrayList<>();
                for (Map<?, ?> entry : upgradeCostsSection.getMapList(tierKey)) {
                    Object matObj = entry.get("material");
                    if (matObj == null) continue;
                    Material material = Material.matchMaterial(String.valueOf(matObj));
                    Object amtObj = entry.get("amount");
                    int amount = amtObj instanceof Number n ? n.intValue() : 1;
                    if (material != null && amount > 0) {
                        costs.add(new WardUpgradeCost(material, amount));
                    }
                }
                wardUpgradeCosts.put(tierKey.toLowerCase(Locale.ROOT), List.copyOf(costs));
            }
        }

        return new ProtectionConfig(
                protection != null && protection.getBoolean("enabled", true),
                protection == null ? 16 : protection.getInt("default-radius", 16),
                protection == null ? 16 : protection.getInt("default-build-radius", 16),
                protection != null && protection.getBoolean("allow-overlap", false),
                Material.matchMaterial(protection == null ? "LODESTONE" : protection.getString("wardrobe-material", "LODESTONE")),
                protection == null ? 27 : protection.getInt("inventory-size", 27),
                DurationParser.parse(upkeep == null ? "24h" : upkeep.getString("interval", "24h")),
                DurationParser.parse(upkeep == null ? "24h" : upkeep.getString("warning-threshold", "24h")),
                DurationParser.parse(upkeep == null ? "6h" : upkeep.getString("expiring-threshold", "6h")),
                DurationParser.parse(upkeep == null ? "24h" : upkeep.getString("grace-period", "24h")),
                DurationParser.parse(upkeep == null ? "48h" : upkeep.getString("destruction-delay", "48h")),
                Material.matchMaterial(upkeep == null ? "DIAMOND" : upkeep.getString("resource-material", "DIAMOND")),
                upkeep == null ? 64 : upkeep.getInt("units-per-item", 64),
                members == null ? 8 : members.getInt("default-max", 8),
                claim == null || claim.getBoolean("owner-transfer", true),
                DurationParser.parse(claim == null ? "7d" : claim.getString("abandoned-after", "7d")),
                resourcePack == null || resourcePack.getBoolean("enabled", true),
                resourcePack == null || resourcePack.getBoolean("optional", true),
                resourcePack == null || resourcePack.getBoolean("fallback-vanilla", true),
                resourcePack == null ? 41001 : resourcePack.getInt("custom-model-data", 41001),
                resourcePack == null ? "dreamcraft:protection_wardrobe" : resourcePack.getString("item-id", "dreamcraft:protection_wardrobe"),
                Material.matchMaterial(ward == null ? "BEACON" : ward.getString("material", "BEACON")),
                ward == null ? "dreamcraft:ward_beacon" : ward.getString("item-id", "dreamcraft:ward_beacon"),
                ward == null ? 41002 : ward.getInt("custom-model-data", 41002),
                ward == null ? 100 : ward.getInt("score-per-upgrade", 100),
                tiers,
                categoryBaseCosts,
                materialOverrides,
                wardUpgradeCosts
        );
    }
}
