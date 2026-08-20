package dev.dreamcraft.protection.listener;

import dev.dreamcraft.protection.model.ProtectionAction;
import dev.dreamcraft.protection.model.ProtectionCheckResult;
import dev.dreamcraft.protection.service.ProtectionChecker;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryMoveItemEvent;

/**
 * Prevents hoppers from moving items into or out of containers inside a foreign claim.
 */
public final class HopperProtectionListener implements Listener {
    private final ProtectionChecker protectionChecker;

    public HopperProtectionListener(ProtectionChecker protectionChecker) {
        this.protectionChecker = protectionChecker;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        if (isProtectedInventory(event.getDestination().getLocation())
                || isProtectedInventory(event.getSource().getLocation())) {
            event.setCancelled(true);
        }
    }

    private boolean isProtectedInventory(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        ProtectionCheckResult result = protectionChecker.checkNoPlayer(
                location.getWorld().getName(),
                location.getBlockX(),
                location.getBlockZ(),
                ProtectionAction.INTERACT);
        return result == ProtectionCheckResult.PROTECTED_DENIED;
    }
}
