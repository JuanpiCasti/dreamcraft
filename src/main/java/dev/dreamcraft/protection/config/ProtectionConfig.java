package dev.dreamcraft.protection.config;

import dev.dreamcraft.protection.model.TierDefinition;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
        Map<String, List<WardUpgradeCost>> wardUpgradeCosts,
        Map<Material, Integer> wardUpkeepMaterials,
        Map<Material, String> wardTierGatedBlocks,
        List<CityLevelDefinition> cityLevels
) {
    /** Convenience constructor: no ward upkeep materials, no gated blocks, no city levels. */
    public ProtectionConfig(
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
            Map<String, List<WardUpgradeCost>> wardUpgradeCosts) {
        this(enabled, defaultRadius, defaultBuildRadius, allowOverlap, wardrobeMaterial, inventorySize,
                upkeepInterval, warningThreshold, expiringThreshold, gracePeriod, destructionDelay,
                upkeepResourceMaterial, upkeepUnitsPerItem, defaultMaxMembers, ownerTransfer, abandonedAfter,
                resourcePackEnabled, resourcePackOptional, resourcePackFallbackVanilla, customModelData,
                resourceItemId, wardMaterial, wardItemId, wardCustomModelData, wardScorePerUpgrade,
                tiers, categoryBaseCosts, materialOverrides, wardUpgradeCosts,
                Map.of(), Map.of(), List.of());
    }

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

        Map<Material, Integer> wardUpkeepMaterials = new LinkedHashMap<>();
        ConfigurationSection wardUpkeepSection =
                ward == null ? null : ward.getConfigurationSection("upkeep-materials");
        if (wardUpkeepSection != null) {
            for (String key : wardUpkeepSection.getKeys(false)) {
                Material material = Material.matchMaterial(key);
                int units = wardUpkeepSection.getInt(key, 0);
                if (material != null && units > 0) {
                    wardUpkeepMaterials.put(material, units);
                }
            }
        }

        Map<Material, String> wardTierGatedBlocks = new LinkedHashMap<>();
        ConfigurationSection gatedSection =
                ward == null ? null : ward.getConfigurationSection("tier-gated-blocks");
        if (gatedSection != null) {
            for (String key : gatedSection.getKeys(false)) {
                Material material = Material.matchMaterial(key);
                String tierKey = gatedSection.getString(key);
                if (material != null && tierKey != null && !tierKey.isBlank()) {
                    wardTierGatedBlocks.put(material, tierKey.toLowerCase(Locale.ROOT));
                }
            }
        }

        List<CityLevelDefinition> cityLevels = new ArrayList<>();
        ConfigurationSection cityLevelsSection = config.getConfigurationSection("city-levels.levels");
        if (cityLevelsSection != null) {
            for (String key : cityLevelsSection.getKeys(false)) {
                ConfigurationSection lvl = cityLevelsSection.getConfigurationSection(key);
                if (lvl == null) continue;
                cityLevels.add(new CityLevelDefinition(
                        key.toLowerCase(Locale.ROOT),
                        lvl.getString("display-name", key),
                        lvl.getInt("min-wards", 0),
                        lvl.getInt("min-members", 0),
                        lvl.getInt("min-wealth", 0)
                ));
            }
            // Lowest requirements first so level resolution can scan ascending
            cityLevels.sort(Comparator.comparingInt(CityLevelDefinition::minWealth)
                    .thenComparingInt(CityLevelDefinition::minWards)
                    .thenComparingInt(CityLevelDefinition::minMembers));
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
                wardUpgradeCosts,
                wardUpkeepMaterials,
                wardTierGatedBlocks,
                cityLevels
        );
    }
}
