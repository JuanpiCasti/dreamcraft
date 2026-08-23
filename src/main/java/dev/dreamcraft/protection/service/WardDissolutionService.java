package dev.dreamcraft.protection.service;

import dev.dreamcraft.protection.domain.model.Ward;
import dev.dreamcraft.protection.domain.service.WardService;
import dev.dreamcraft.protection.integration.worldguard.WorldGuardAdapter;
import dev.dreamcraft.protection.ui.WardItems;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Single dissolution contract for Wards.
 *
 * <p>Every route that removes a Ward (command, menu or physical block break)
 * delegates here so the teardown is always identical:
 * <ol>
 *   <li>WorldGuard region removed</li>
 *   <li>repository entry deleted + domain data saved</li>
 *   <li>physical core block removed when it still holds the configured
 *       ward material (guard {@code type == ward.material}) — no orphan beacons</li>
 *   <li>when the OWNER dissolves their own ward, the tagged founder item comes
 *       back (inventory, or dropped at their feet when full); an ADMIN tearing
 *       down someone else's ward gets nothing back</li>
 * </ol>
 *
 * <p>The block-break listener cancels the vanilla break event before calling
 * this service, so the generic untagged material drop never appears.
 */
public final class WardDissolutionService {

    /** Outcome flags callers may surface to the player. */
    public record Result(boolean coreBlockRemoved, boolean refunded) {}

    private final WardService wardService;
    private final WorldGuardAdapter worldGuardAdapter;
    private final WardItems wardItems;
    private final Material wardMaterial;
    private final Runnable saveAction;

    public WardDissolutionService(WardService wardService,
                                  WorldGuardAdapter worldGuardAdapter,
                                  WardItems wardItems,
                                  Material wardMaterial,
                                  Runnable saveAction) {
        this.wardService = wardService;
        this.worldGuardAdapter = worldGuardAdapter;
        this.wardItems = wardItems;
        this.wardMaterial = wardMaterial;
        this.saveAction = saveAction;
    }

    /**
     * Dissolves the Ward through the single contract described in the class doc.
     *
     * @param ward                    the aggregate to dissolve
     * @param actor                   player triggering the dissolution (may be null for system paths)
     * @param ownerDissolvingOwnWard  true → refund the tagged founder item to the actor
     */
    public Result dissolve(Ward ward, Player actor, boolean ownerDissolvingOwnWard) {
        worldGuardAdapter.removeRegion(ward);
        wardService.delete(ward);
        saveAction.run();
        boolean coreRemoved = removeCoreBlock(ward);

        // Anti-duplication guard: the founder item only comes back when the
        // physical core was actually removed. If the block is gone/absent, a
        // refund would duplicate the core (the item IS the block).
        boolean refunded = false;
        if (shouldRefund(ownerDissolvingOwnWard, coreRemoved)
                && actor != null && actor.isOnline()) {
            refunded = refundFounderItem(actor);
        }
        return new Result(coreRemoved, refunded);
    }

    /**
     * Pure decision: does this dissolution return the tagged founder item?
     * Only when the OWNER dissolves their own ward AND the physical core block
     * was actually removed (no block → nothing returned, no duplication).
     */
    static boolean shouldRefund(boolean ownerOwnWard, boolean coreRemoved) {
        return ownerOwnWard && coreRemoved;
    }

    /** Removes the physical core block only while it still is the configured ward material. */
    private boolean removeCoreBlock(Ward ward) {
        if (wardMaterial == null) return false;
        World world = Bukkit.getWorld(ward.worldName());
        if (world == null || !world.isChunkLoaded(ward.centerX() >> 4, ward.centerZ() >> 4)) return false;
        Block block = world.getBlockAt(ward.centerX(), ward.centerY(), ward.centerZ());
        if (block.getType() != wardMaterial) return false;
        block.setType(Material.AIR);
        return true;
    }

    /** Returns the tagged founder item: into the inventory, or dropped at the feet when full. */
    private boolean refundFounderItem(Player player) {
        ItemStack item = wardItems.createWardItem();
        var leftovers = player.getInventory().addItem(item);
        for (ItemStack leftover : leftovers.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
        return true;
    }
}
