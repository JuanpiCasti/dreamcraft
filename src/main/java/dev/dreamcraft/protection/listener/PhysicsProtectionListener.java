package dev.dreamcraft.protection.listener;

import dev.dreamcraft.protection.model.ProtectionAction;
import dev.dreamcraft.protection.model.ProtectionCheckResult;
import dev.dreamcraft.protection.service.ProtectionChecker;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.util.Iterator;

/**
 * Handles explosion and piston protection for claimed areas.
 */
public final class PhysicsProtectionListener implements Listener {
    private final ProtectionChecker protectionChecker;

    public PhysicsProtectionListener(ProtectionChecker protectionChecker) {
        this.protectionChecker = protectionChecker;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        removeProtectedBlocks(event.blockList());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        removeProtectedBlocks(event.blockList());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block block : event.getBlocks()) {
            if (isProtected(block)) {
                event.setCancelled(true);
                return;
            }
        }
        // also protect the destination cells
        for (Block block : event.getBlocks()) {
            Block destination = block.getRelative(event.getDirection());
            if (isProtected(destination)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block block : event.getBlocks()) {
            if (isProtected(block)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    private void removeProtectedBlocks(java.util.List<Block> blocks) {
        Iterator<Block> iterator = blocks.iterator();
        while (iterator.hasNext()) {
            Block block = iterator.next();
            ProtectionCheckResult result = protectionChecker.checkNoPlayer(
                    block.getWorld().getName(), block.getX(), block.getZ(), ProtectionAction.EXPLOSION);
            if (result == ProtectionCheckResult.PROTECTED_DENIED) {
                iterator.remove();
            }
        }
    }

    private boolean isProtected(Block block) {
        return protectionChecker.checkNoPlayer(
                block.getWorld().getName(), block.getX(), block.getZ(), ProtectionAction.BUILD)
                == ProtectionCheckResult.PROTECTED_DENIED;
    }
}
