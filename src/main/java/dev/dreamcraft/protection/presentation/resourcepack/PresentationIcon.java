package dev.dreamcraft.protection.presentation.resourcepack;

import org.bukkit.Material;

/**
 * A resolved visual icon ready to be rendered by any menu backend.
 *
 * <p>Pure data — no Bukkit {@code ItemStack}. Exactly one of
 * {@code customModelData} (vanilla item + CMD from the resource pack) or
 * {@code providerItemId} (e.g. an Oraxen item id) may be present;
 * both absent means plain vanilla material.
 */
public record PresentationIcon(
        Material material,
        Integer customModelData,
        String providerItemId
) {
    public static PresentationIcon vanilla(Material material) {
        return new PresentationIcon(material, null, null);
    }

    public boolean hasCustomModel() {
        return customModelData != null || providerItemId != null;
    }
}
