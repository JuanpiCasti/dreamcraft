package dev.dreamcraft.protection.integration.worldedit;

import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.regions.selector.CuboidRegionSelector;
import dev.dreamcraft.protection.integration.registry.CapabilityRegistry;
import dev.dreamcraft.protection.integration.registry.IntegrationKey;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.logging.Logger;

/**
 * WorldEdit 7.x implementation of {@link WorldEditAdapter}.
 *
 * <p><b>Verified API surface (worldedit-bukkit 7.4.5):</b>
 * <ul>
 *   <li>{@code WorldEdit.getInstance().getSessionManager()}</li>
 *   <li>{@code LocalSession#getSelection(World)}</li>
 *   <li>{@code Region#getMinimumPoint()} / {@code getMaximumPoint()}</li>
 *   <li>{@code CuboidRegionSelector} — in package {@code com.sk89q.worldedit.regions.selector}</li>
 * </ul>
 */
public final class WorldEditAdapterImpl implements WorldEditAdapter {

    private final CapabilityRegistry registry;
    private final Logger logger;

    public WorldEditAdapterImpl(CapabilityRegistry registry, Logger logger) {
        this.registry = registry;
        this.logger = logger;
    }

    @Override
    public boolean isAvailable() {
        return registry.isAvailable(IntegrationKey.WORLD_EDIT);
    }

    @Override
    public SelectionBounds getSelection(UUID playerId, String worldName) {
        if (!isAvailable()) return null;
        try {
            Player bukkit = Bukkit.getPlayer(playerId);
            if (bukkit == null) return null;
            com.sk89q.worldedit.entity.Player wePlayer = BukkitAdapter.adapt(bukkit);
            LocalSession session = WorldEdit.getInstance().getSessionManager().get(wePlayer);
            com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(Bukkit.getWorld(worldName));
            Region region = session.getSelection(weWorld);
            BlockVector3 min = region.getMinimumPoint();
            BlockVector3 max = region.getMaximumPoint();
            return new SelectionBounds(min.x(), min.y(), min.z(), max.x(), max.y(), max.z());
        } catch (IncompleteRegionException e) {
            return null;
        } catch (Exception e) {
            logger.warning("[WorldEdit] getSelection failed for " + playerId + ": " + e.getMessage());
            return null;
        }
    }

    @Override
    public void setSelection(UUID playerId, String worldName,
                              int minX, int minY, int minZ,
                              int maxX, int maxY, int maxZ) {
        if (!isAvailable()) return;
        try {
            Player bukkit = Bukkit.getPlayer(playerId);
            if (bukkit == null) return;
            com.sk89q.worldedit.entity.Player wePlayer = BukkitAdapter.adapt(bukkit);
            LocalSession session = WorldEdit.getInstance().getSessionManager().get(wePlayer);
            com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(Bukkit.getWorld(worldName));

            CuboidRegionSelector selector = new CuboidRegionSelector(weWorld,
                    BlockVector3.at(minX, minY, minZ),
                    BlockVector3.at(maxX, maxY, maxZ));
            session.setRegionSelector(weWorld, selector);
        } catch (Exception e) {
            logger.warning("[WorldEdit] setSelection failed for " + playerId + ": " + e.getMessage());
        }
    }
}
