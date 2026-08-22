package dev.dreamcraft.protection.presentation.resourcepack;

import java.util.Optional;

/**
 * Port to the visual asset provider (MD §7). The domain never touches Oraxen
 * or any concrete pack API: presentation resolves semantic asset keys through
 * this interface, and each implementation decides how to serve them.
 *
 * <p>Implementations:
 * <ul>
 *   <li>{@link VanillaCmdProvider} — vanilla materials + CustomModelData
 *       (works with a plain resource pack, zero external plugins)</li>
 *   <li>future {@code OraxenResourcePackAdapter} — resolves item ids through
 *       the Oraxen API</li>
 * </ul>
 *
 * <p>Every lookup is optional-by-contract: returning {@code empty()} triggers
 * the vanilla fallback chain (MD §9). A missing resource pack must never
 * break the plugin.
 */
public interface ResourcePackProvider {

    /** Stable identifier used in logs and the integration status output. */
    String providerName();

    /** Whether this provider can currently resolve assets at all. */
    boolean isAvailable();

    /**
     * Resolves an icon asset key (e.g. {@code ward.icon}) to renderable data.
     * Empty when the key is unknown or the provider can't serve it — callers
     * must fall back to vanilla materials.
     */
    Optional<PresentationIcon> icon(String assetKey);

    /** Resolves a sound asset key to a Sound key string; null for vanilla fallback. */
    default String sound(String assetKey) {
        return null;
    }

    /** Resolves a font asset key to an Adventure font namespace:key; null if unavailable. */
    default String font(String assetKey) {
        return null;
    }

    /** Resolves a symbol/glyph asset key to its character(s); null if unavailable. */
    default String symbol(String assetKey) {
        return null;
    }

    /** Resolves a particle asset key to a Bukkit Particle enum name; null for fallback. */
    default String particle(String assetKey) {
        return null;
    }
}
