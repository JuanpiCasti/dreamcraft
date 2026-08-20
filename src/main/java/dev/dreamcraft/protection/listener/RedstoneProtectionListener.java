package dev.dreamcraft.protection.listener;

import dev.dreamcraft.protection.model.ProtectionAction;
import dev.dreamcraft.protection.model.ProtectionCheckResult;
import dev.dreamcraft.protection.service.ProtectionChecker;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockRedstoneEvent;

/**
 * Prevents redstone signals from activating mechanisms inside a foreign claim.
 * Only blocks redstone pulses that originate from outside the claim boundary.
 */
public final class RedstoneProtectionListener implements Listener {
    private final ProtectionChecker protectionChecker;

    public RedstoneProtectionListener(ProtectionChecker protectionChecker) {
        this.protectionChecker = protectionChecker;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockRedstone(BlockRedstoneEvent event) {
        Block block = event.getBlock();
        ProtectionCheckResult result = protectionChecker.checkNoPlayer(
                block.getWorld().getName(), block.getX(), block.getZ(), ProtectionAction.REDSTONE);
        if (result == ProtectionCheckResult.PROTECTED_DENIED) {
            // Revert the signal change by setting new current back to old current
            event.setNewCurrent(event.getOldCurrent());
        }
    }
}
