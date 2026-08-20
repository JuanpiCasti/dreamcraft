package dev.dreamcraft.protection.integration.coreprotect;

import java.util.UUID;

/**
 * Integration adapter contract for CoreProtect.
 *
 * <p>CoreProtect is the authority for block history and audit trails.
 * DreamCraft does NOT implement its own logging — it delegates to CoreProtect.
 *
 * <p>DreamCraft uses this adapter to:
 * <ul>
 *   <li>Log block changes initiated by domain actions (Ward creation/removal).</li>
 *   <li>Query recent block activity inside a Ward region for audit purposes.</li>
 * </ul>
 */
public interface CoreProtectAdapter {

    /**
     * Logs a block placement event attributed to the given player/system.
     *
     * @param actorName the player name or system label (e.g. "#dreamcraft")
     * @param worldName world name
     * @param x         block X
     * @param y         block Y
     * @param z         block Z
     * @param materialName the Material name of the placed block
     */
    void logBlockPlace(String actorName, String worldName, int x, int y, int z, String materialName);

    /**
     * Logs a block break event attributed to the given actor.
     */
    void logBlockBreak(String actorName, String worldName, int x, int y, int z, String materialName);

    /**
     * Returns the number of block placements by the given player within the last
     * {@code lookbackSeconds} inside the bounding box, or -1 if unavailable.
     */
    int countRecentPlacements(UUID playerId, String worldName,
                               int minX, int minZ, int maxX, int maxZ,
                               int lookbackSeconds);

    boolean isAvailable();
}
