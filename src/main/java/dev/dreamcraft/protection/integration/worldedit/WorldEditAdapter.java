package dev.dreamcraft.protection.integration.worldedit;

import java.util.UUID;

/**
 * Integration adapter contract for WorldEdit.
 *
 * <p>WorldEdit handles selection and administrative block operations.
 * DreamCraft uses it for:
 * <ul>
 *   <li>Reading a player's current WE selection to define region bounds.</li>
 *   <li>Admin fill/clear operations on Ward regions.</li>
 * </ul>
 *
 * <p>No WE session is created or held by DreamCraft — the adapter queries
 * the existing session created by the player.
 */
public interface WorldEditAdapter {

    /**
     * Returns the bounding box of the player's current WorldEdit selection,
     * or null if the player has no active selection.
     */
    SelectionBounds getSelection(UUID playerId, String worldName);

    /**
     * Expands the player's current WE selection to the full ward bounds.
     * Useful for admin operations. No-op if WE unavailable.
     */
    void setSelection(UUID playerId, String worldName,
                      int minX, int minY, int minZ,
                      int maxX, int maxY, int maxZ);

    boolean isAvailable();

    /**
     * Axis-aligned bounding box returned by a WE selection query.
     */
    record SelectionBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public int width()  { return maxX - minX + 1; }
        public int height() { return maxY - minY + 1; }
        public int depth()  { return maxZ - minZ + 1; }
    }
}
