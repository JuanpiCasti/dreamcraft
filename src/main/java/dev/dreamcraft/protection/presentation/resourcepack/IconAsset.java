package dev.dreamcraft.protection.presentation.resourcepack;

import org.bukkit.Material;

/**
 * One semantic asset entry from {@code presentation-assets.yml}.
 *
 * <pre>
 * icons:
 *   ward.icon:
 *     material: LODESTONE      # base vanilla material (always renderable)
 *     cmd: 41101               # CustomModelData applied when the viewer has the pack
 *     fallback: BEACON         # material used when the viewer has NO pack (optional)
 *     hide-name: true          # hide the vanilla item name while the CMD renders (optional)
 * </pre>
 */
public record IconAsset(Material material, int cmd, Material fallback, boolean hideName) {

    /** Legacy arity — no hide-name flag. */
    public IconAsset(Material material, int cmd, Material fallback) {
        this(material, cmd, fallback, false);
    }

    public static IconAsset plain(Material material) {
        return new IconAsset(material, 0, null, false);
    }

    /** Resolves per the §9 chain: CMD when supported, else fallback, else base material. */
    public PresentationIcon resolve(boolean viewerHasResourcePack, ResourcePackProvider provider) {
        if (!viewerHasResourcePack || cmd <= 0) {
            return PresentationIcon.vanilla(fallback != null ? fallback : material);
        }
        if (provider != null && provider.isAvailable()) {
            return new PresentationIcon(material, cmd, null);
        }
        return PresentationIcon.vanilla(fallback != null ? fallback : material);
    }
}
