package dev.dreamcraft.protection.integration.coreprotect;

import dev.dreamcraft.protection.integration.registry.CapabilityRegistry;
import dev.dreamcraft.protection.integration.registry.IntegrationKey;
import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * CoreProtect 22+ implementation of {@link CoreProtectAdapter}.
 *
 * <p>Uses the {@link CoreProtectAPI} stable public interface.
 *
 * <p><b>Verified public API surface (CoreProtect CE 24.0):</b>
 * <ul>
 *   <li>{@code CoreProtect#getAPI()} — retrieves the API handle</li>
 *   <li>{@code CoreProtectAPI#isEnabled()} — confirms availability</li>
 *   <li>{@code CoreProtectAPI#logPlacement(String, Location, Material, BlockData)}</li>
 *   <li>{@code CoreProtectAPI#logRemoval(String, Location, Material, BlockData)}</li>
 *   <li>{@code CoreProtectAPI#blockLookup(Block, int)} — returns {@code List<String[]>}</li>
 * </ul>
 */
public final class CoreProtectAdapterImpl implements CoreProtectAdapter {

    private final CapabilityRegistry registry;
    private final Logger logger;

    public CoreProtectAdapterImpl(CapabilityRegistry registry, Logger logger) {
        this.registry = registry;
        this.logger = logger;
    }

    @Override
    public boolean isAvailable() {
        return registry.isAvailable(IntegrationKey.CORE_PROTECT);
    }

    private CoreProtectAPI getApi() {
        if (!isAvailable()) return null;
        try {
            var plugin = Bukkit.getPluginManager().getPlugin("CoreProtect");
            if (!(plugin instanceof CoreProtect cp)) return null;
            CoreProtectAPI api = cp.getAPI();
            return (api != null && api.isEnabled()) ? api : null;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void logBlockPlace(String actorName, String worldName, int x, int y, int z, String materialName) {
        CoreProtectAPI api = getApi();
        if (api == null) return;
        try {
            World world = Bukkit.getWorld(worldName);
            if (world == null) return;
            Material mat = Material.matchMaterial(materialName);
            if (mat == null) return;
            Location loc = world.getBlockAt(x, y, z).getLocation();
            api.logPlacement(actorName, loc, mat, mat.createBlockData());
        } catch (Exception e) {
            logger.warning("[CoreProtect] logBlockPlace failed: " + e.getMessage());
        }
    }

    @Override
    public void logBlockBreak(String actorName, String worldName, int x, int y, int z, String materialName) {
        CoreProtectAPI api = getApi();
        if (api == null) return;
        try {
            World world = Bukkit.getWorld(worldName);
            if (world == null) return;
            Material mat = Material.matchMaterial(materialName);
            if (mat == null) return;
            Location loc = world.getBlockAt(x, y, z).getLocation();
            api.logRemoval(actorName, loc, mat, mat.createBlockData());
        } catch (Exception e) {
            logger.warning("[CoreProtect] logBlockBreak failed: " + e.getMessage());
        }
    }

    @Override
    public int countRecentPlacements(UUID playerId, String worldName,
                                      int minX, int minZ, int maxX, int maxZ,
                                      int lookbackSeconds) {
        CoreProtectAPI api = getApi();
        if (api == null) return -1;
        try {
            World world = Bukkit.getWorld(worldName);
            if (world == null) return -1;
            String playerName = Bukkit.getOfflinePlayer(playerId).getName();
            if (playerName == null) return -1;
            // blockLookup returns List<String[]> — each row: [time, world, x, y, z, type, data, player, action, rolled_back]
            int cx = (minX + maxX) / 2;
            int cz = (minZ + maxZ) / 2;
            List<String[]> results = api.blockLookup(world.getBlockAt(cx, 64, cz), lookbackSeconds);
            if (results == null) return 0;
            int count = 0;
            for (String[] row : results) {
                // row[7] = player, row[8] = action (1 = place)
                if (row.length >= 9 && row[7].equalsIgnoreCase(playerName) && "1".equals(row[8])) {
                    count++;
                }
            }
            return count;
        } catch (Exception e) {
            logger.warning("[CoreProtect] countRecentPlacements failed: " + e.getMessage());
            return -1;
        }
    }
}
