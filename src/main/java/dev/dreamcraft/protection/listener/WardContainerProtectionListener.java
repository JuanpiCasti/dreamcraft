package dev.dreamcraft.protection.listener;

import dev.dreamcraft.protection.domain.model.Ward;
import dev.dreamcraft.protection.domain.service.WardService;
import org.bukkit.Location;
import org.bukkit.inventory.BlockInventoryHolder;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.InventoryHolder;

import java.util.Optional;

/**
 * Blocks hopper/dropper/piston-style item transfers that cross a Ward's
 * boundary — WorldGuard does not stop container-to-container movement on its
 * own, so an outside hopper could silently drain a protected chest.
 *
 * <p>Rule ({@link #transferAllowed}):
 * <ul>
 *   <li>Both containers outside Wards → allowed.</li>
 *   <li>Both inside the same Ward, or inside Wards with the same owner, or
 *       inside Wards of the same City → allowed (intra-infrastructure).</li>
 *   <li>Anything crossing into/out of/between different owners' Wards →
 *       cancelled.</li>
 * </ul>
 *
 * <p>Player-driven movement (clicks, shift-clicks) is not this event; it is
 * already covered by the {@code chest-access} flag set by the WG adapter.
 */
public final class WardContainerProtectionListener implements Listener {

    private final WardService wardService;

    public WardContainerProtectionListener(WardService wardService) {
        this.wardService = wardService;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemMove(InventoryMoveItemEvent event) {
        Ward source = resolveWard(event.getSource().getHolder());
        Ward destination = resolveWard(event.getDestination().getHolder());
        if (!transferAllowed(source, destination)) {
            event.setCancelled(true);
        }
    }

    /** Resolves the Ward containing the holder's block/entity position, or null. */
    private Ward resolveWard(InventoryHolder holder) {
        Location loc = holderLocation(holder);
        if (loc == null || loc.getWorld() == null) return null;
        return wardService.findAtLocation(
                loc.getWorld().getName(), loc.getBlockX(), loc.getBlockZ()).orElse(null);
    }

    private static Location holderLocation(InventoryHolder holder) {
        if (holder instanceof BlockInventoryHolder block) return block.getBlock().getLocation();
        if (holder instanceof Entity entity) return entity.getLocation();
        return null;
    }

    /**
     * Pure boundary decision, package-private so it can be unit-tested without
     * a Bukkit server. {@code null} means "outside any Ward" or "unresolvable".
     */
    static boolean transferAllowed(Ward source, Ward destination) {
        // Unresolvable side next to (or inside) a Ward → fail closed.
        if ((source == null) != (destination == null)) return false;
        if (source == null) return true; // both outside / both unresolvable
        if (source.id().equals(destination.id())) return true;
        if (source.ownerId().equals(destination.ownerId())) return true;
        return source.cityId() != null && source.cityId().equals(destination.cityId());
    }
}
