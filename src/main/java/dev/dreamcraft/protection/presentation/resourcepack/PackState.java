package dev.dreamcraft.protection.presentation.resourcepack;

import java.util.UUID;

/**
 * Read-side of the per-player resource pack state.
 *
 * <p>Implemented by {@link PackStatusTracker} (tracks real client state) and
 * replaceable by constant implementations — e.g. {@code id -> true} when the
 * server ships the pack as mandatory ({@code menus.provider: rp}).
 */
public interface PackState {

    /** Whether this viewer renders CustomModelData assets (true) or vanilla fallbacks (false). */
    boolean has(UUID playerId);

    /** Overrides the pack detection for a specific viewer (true=RP, false=vanilla, null=auto). */
    default void setOverride(UUID playerId, Boolean override) {}

    /** Gets the current manual override for a specific viewer (null if automatic). */
    default Boolean getOverride(UUID playerId) {
        return null;
    }
}
