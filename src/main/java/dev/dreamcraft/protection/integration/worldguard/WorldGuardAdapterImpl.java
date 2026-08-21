package dev.dreamcraft.protection.integration.worldguard;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.domains.DefaultDomain;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import dev.dreamcraft.protection.domain.model.City;
import dev.dreamcraft.protection.domain.model.Estate;
import dev.dreamcraft.protection.domain.model.Ward;
import dev.dreamcraft.protection.integration.registry.CapabilityRegistry;
import dev.dreamcraft.protection.integration.registry.IntegrationKey;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.util.UUID;
import java.util.logging.Logger;

/**
 * WorldGuard 7.x implementation of {@link WorldGuardAdapter}.
 *
 * <p>Uses the stable WorldGuard 7 RegionContainer API.
 * DreamCraft region IDs are prefixed with {@code dc_ward_} to avoid collision.
 *
 * <p><b>Responsibility boundary:</b> This adapter only manages geometry and owner/member
 * sync. All DreamCraft-specific semantics (score, upkeep, tier) stay in the domain layer.
 */
public final class WorldGuardAdapterImpl implements WorldGuardAdapter {

    private static final String REGION_PREFIX = "dc_ward_";

    private final CapabilityRegistry registry;
    private final Logger logger;

    public WorldGuardAdapterImpl(CapabilityRegistry registry, Logger logger) {
        this.registry = registry;
        this.logger = logger;
    }

    @Override
    public boolean isAvailable() {
        return registry.isAvailable(IntegrationKey.WORLD_GUARD);
    }

    @Override
    public String createRegion(Ward ward, String worldName, int minY, int maxY) {
        if (!isAvailable()) return null;
        try {
            RegionManager rm = getRegionManager(worldName);
            if (rm == null) return null;

            String regionId = REGION_PREFIX + ward.id().toString().replace("-", "");
            int r = ward.radius();
            BlockVector3 min = BlockVector3.at(ward.centerX() - r, minY, ward.centerZ() - r);
            BlockVector3 max = BlockVector3.at(ward.centerX() + r, maxY, ward.centerZ() + r);
            ProtectedCuboidRegion region = new ProtectedCuboidRegion(regionId, min, max);

            DefaultDomain ownerDomain = new DefaultDomain();
            ownerDomain.addPlayer(ward.ownerId());
            region.setOwners(ownerDomain);

            rm.addRegion(region);
            return regionId;
        } catch (Exception e) {
            logger.warning("[WorldGuard] Failed to create region for ward " + ward.id() + ": " + e.getMessage());
            return null;
        }
    }

    @Override
    public void resizeRegion(Ward ward, int minY, int maxY) {
        if (!isAvailable() || ward.worldGuardRegionId() == null) return;
        try {
            RegionManager rm = getRegionManager(ward.worldName());
            if (rm == null) return;
            ProtectedRegion existing = rm.getRegion(ward.worldGuardRegionId());
            if (existing == null) return;

            int r = ward.radius();
            BlockVector3 min = BlockVector3.at(ward.centerX() - r, minY, ward.centerZ() - r);
            BlockVector3 max = BlockVector3.at(ward.centerX() + r, maxY, ward.centerZ() + r);
            ProtectedCuboidRegion replacement =
                    new ProtectedCuboidRegion(ward.worldGuardRegionId(), min, max);
            replacement.setOwners(existing.getOwners());
            replacement.setMembers(existing.getMembers());
            replacement.setFlags(existing.getFlags());
            replacement.setPriority(existing.getPriority());

            rm.removeRegion(ward.worldGuardRegionId());
            rm.addRegion(replacement);
        } catch (Exception e) {
            logger.warning("[WorldGuard] Failed to resize region for ward " + ward.id() + ": " + e.getMessage());
        }
    }

    @Override
    public void removeRegion(Ward ward) {
        if (!isAvailable() || ward.worldGuardRegionId() == null) return;
        try {
            RegionManager rm = getRegionManager(ward.worldName());
            if (rm != null) rm.removeRegion(ward.worldGuardRegionId());
        } catch (Exception e) {
            logger.warning("[WorldGuard] Failed to remove region for ward " + ward.id() + ": " + e.getMessage());
        }
    }

    @Override
    public void addMember(Ward ward, UUID playerId) {
        if (!isAvailable() || ward.worldGuardRegionId() == null) return;
        try {
            ProtectedRegion region = getRegion(ward);
            if (region != null) region.getMembers().addPlayer(playerId);
        } catch (Exception e) {
            logger.warning("[WorldGuard] addMember failed for ward " + ward.id() + ": " + e.getMessage());
        }
    }

    @Override
    public void removeMember(Ward ward, UUID playerId) {
        if (!isAvailable() || ward.worldGuardRegionId() == null) return;
        try {
            ProtectedRegion region = getRegion(ward);
            if (region != null) region.getMembers().removePlayer(playerId);
        } catch (Exception e) {
            logger.warning("[WorldGuard] removeMember failed for ward " + ward.id() + ": " + e.getMessage());
        }
    }

    @Override
    public void syncOwner(Ward ward) {
        if (!isAvailable() || ward.worldGuardRegionId() == null) return;
        try {
            ProtectedRegion region = getRegion(ward);
            if (region == null) return;
            DefaultDomain ownerDomain = new DefaultDomain();
            ownerDomain.addPlayer(ward.ownerId());
            region.setOwners(ownerDomain);
        } catch (Exception e) {
            logger.warning("[WorldGuard] syncOwner failed for ward " + ward.id() + ": " + e.getMessage());
        }
    }

    @Override
    public void syncCityMembership(Ward ward, City city) {
        if (!isAvailable() || ward.worldGuardRegionId() == null) return;
        try {
            ProtectedRegion region = getRegion(ward);
            if (region == null) return;
            // Add all city members as WG region members (inheritance of city access)
            DefaultDomain members = region.getMembers();
            for (UUID memberId : city.members().keySet()) {
                members.addPlayer(memberId);
            }
        } catch (Exception e) {
            logger.warning("[WorldGuard] syncCityMembership failed for ward " + ward.id() + ": " + e.getMessage());
        }
    }

    @Override
    public void applyEstateInstanceFlags(Ward ward, Estate estate) {
        if (!isAvailable() || ward.worldGuardRegionId() == null) return;
        try {
            ProtectedRegion region = getRegion(ward);
            if (region == null) return;
            // Temporal access: add estate members as region members for the instance duration
            DefaultDomain members = region.getMembers();
            for (UUID memberId : estate.members()) {
                members.addPlayer(memberId);
            }
            // Raise region priority so estate access takes effect during the instance
            region.setPriority(Math.max(region.getPriority(), 100));
        } catch (Exception e) {
            logger.warning("[WorldGuard] applyEstateInstanceFlags failed for ward " + ward.id() + ": " + e.getMessage());
        }
    }

    @Override
    public void clearEstateInstanceFlags(Ward ward) {
        if (!isAvailable() || ward.worldGuardRegionId() == null) return;
        try {
            ProtectedRegion region = getRegion(ward);
            if (region == null) return;
            // Restore priority to a neutral value; members are left intact (they may
            // have legitimate access outside the instance). The caller can removeMember
            // for estate-only members if needed.
            if (region.getPriority() >= 100) {
                region.setPriority(0);
            }
        } catch (Exception e) {
            logger.warning("[WorldGuard] clearEstateInstanceFlags failed for ward " + ward.id() + ": " + e.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private RegionManager getRegionManager(String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        return WorldGuard.getInstance().getPlatform().getRegionContainer()
                .get(BukkitAdapter.adapt(world));
    }

    private ProtectedRegion getRegion(Ward ward) {
        RegionManager rm = getRegionManager(ward.worldName());
        if (rm == null) return null;
        return rm.getRegion(ward.worldGuardRegionId());
    }
}
