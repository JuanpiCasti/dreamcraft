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
}
