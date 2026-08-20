package dev.dreamcraft.protection.service;

import dev.dreamcraft.protection.config.ProtectionConfig;
import org.bukkit.Material;
import org.bukkit.Tag;

import java.util.Locale;

public final class BuildingCostService {
    private final ProtectionConfig config;

    public BuildingCostService(ProtectionConfig config) {
        this.config = config;
    }

    public String categoryOf(Material material) {
        String override = config.materialOverrides().get(material);
        if (override != null) {
            return override;
        }
        if (Tag.LOGS.isTagged(material) || Tag.PLANKS.isTagged(material) || Tag.WOODEN_DOORS.isTagged(material)) {
            return "light";
        }
        if (Tag.STONE_BRICKS.isTagged(material) || material.name().endsWith("_STONE") || material.name().endsWith("_BRICKS")) {
            return "basic";
        }
        String key = material.name().toLowerCase(Locale.ROOT);
        if (key.contains("iron") || key.contains("obsidian")) {
            return "reinforced";
        }
        if (key.contains("diamond") || key.contains("copper") || key.contains("redstone")) {
            return "advanced";
        }
        if (key.contains("netherite") || key.contains("beacon") || key.contains("end_crystal")) {
            return "special";
        }
        return "basic";
    }

    public int unitCost(Material material) {
        return config.categoryBaseCosts().getOrDefault(categoryOf(material), 1);
    }
}
