package dev.dreamcraft.protection.listener;

import dev.dreamcraft.protection.domain.model.Estate;
import dev.dreamcraft.protection.domain.service.EstateService;
import dev.dreamcraft.protection.service.EstateZoneJournal;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps adventure zones pristine (lore «El Despertar»):
 *
 * <ul>
 *   <li><b>Indestructible core</b>: ores, frames, vaults, spawners and loot
 *       containers inside END/TRIAL_CHAMBER areas cannot be broken — no party
 *       can deplete the stronghold for the next group.</li>
 *   <li><b>Zone journal</b>: any other modification (placed blocks, broken
 *       stone, buckets, explosions) records the original state; the runtime
 *       rolls it back when the party closes the zone, so other adventurers
 *       never collide with the previous group's mess.</li>
 * </ul>
 */
public final class EstateStructureListener implements Listener {

    /** Progression-critical blocks: unbreakable outright. */
    private static final Material[] PROTECTED = {
            Material.SPAWNER,
            Material.TRIAL_SPAWNER,
            Material.VAULT,
            Material.END_PORTAL_FRAME,
    };

    /** Loot vessels: unbreakable (opening stays allowed). */
    private static final Material[] CONTAINERS = {
            Material.CHEST,
            Material.TRAPPED_CHEST,
            Material.BARREL,
    };

    private final EstateService estateService;
    private final boolean protectStructure;
    private final boolean regenerateZone;
    private final EstateZoneJournal journal;
    private final int bandBelow;
    private final int bandAbove;
    /** Per-player notice throttle (ms). */
    private final Map<UUID, Long> lastNotice = new ConcurrentHashMap<>();

    public EstateStructureListener(EstateService estateService,
                                   boolean protectStructure,
                                   boolean regenerateZone,
                                   EstateZoneJournal journal,
                                   int areaBandBelow,
                                   int areaBandAbove) {
        this.estateService = estateService;
        this.protectStructure = protectStructure;
        this.regenerateZone = regenerateZone;
        this.journal = journal;
        this.bandBelow = Math.max(0, areaBandBelow);
        this.bandAbove = Math.max(4, areaBandAbove);
    }

    // ── Break: protect the core, journal everything else ──────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();
        List<Estate> zones = zonesAt(block);
        if (zones.isEmpty()) return;

        if (protectStructure && isProtected(block.getType())) {
            event.setCancelled(true);
            notice(player);
            return;
        }
        // Loot vessels stay in place even when general protection is off
        if (isContainer(block.getType()) && !event.getPlayer().hasPermission("dreamcraft.ward.admin")) {
            event.setCancelled(true);
            notice(player);
            return;
        }
        if (regenerateZone && journal != null) {
            UUID zoneId = zones.get(0).id();
            journal.record(zoneId, block, block.getBlockData());
        }
    }

    // ── Place: allowed while fighting, rolled back on close ───────────────────

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!regenerateZone || journal == null) return;
        Block block = event.getBlock();
        List<Estate> zones = zonesAt(block);
        if (zones.isEmpty()) return;
        // Journal what the placement REPLACED so rollback restores it exactly
        journal.record(zones.get(0).id(), block, event.getBlockReplacedState().getBlockData());
    }

    // ── Buckets: liquids are the classic scar left behind ─────────────────────

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (!regenerateZone || journal == null) return;
        Block block = event.getBlockClicked().getRelative(event.getBlockFace());
        List<Estate> zones = zonesAt(block);
        if (!zones.isEmpty()) {
            journal.record(zones.get(0).id(), block, block.getBlockData());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (!regenerateZone || journal == null) return;
        Block block = event.getBlockClicked().getRelative(event.getBlockFace());
        List<Estate> zones = zonesAt(block);
        if (!zones.isEmpty()) {
            journal.record(zones.get(0).id(), block, block.getBlockData());
        }
    }

    // ── Explosions: simply excluded from zone blocks (no scars, no rollback) ──

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        stripZoneBlocks(event.blockList().iterator());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        stripZoneBlocks(event.blockList().iterator());
    }

    private void stripZoneBlocks(Iterator<Block> it) {
        while (it.hasNext()) {
            if (!zonesAt(it.next()).isEmpty()) it.remove();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Instanced-adventure zones containing this block (2D circle + vertical band). */
    private List<Estate> zonesAt(Block block) {
        return estateService.findInstancedAreasAt(
                block.getWorld().getName(), block.getX(), block.getZ()).stream()
                .filter(e -> {
                    double dy = block.getY() - e.areaY();
                    return dy >= -bandBelow && dy <= bandAbove;
                })
                .toList();
    }

    private static boolean isProtected(Material material) {
        for (Material protectedMaterial : PROTECTED) {
            if (material == protectedMaterial) return true;
        }
        String name = material.name();
        return name.endsWith("_ORE") || name.equals("ANCIENT_DEBRIS");
    }

    private static boolean isContainer(Material material) {
        for (Material container : CONTAINERS) {
            if (material == container) return true;
        }
        return false;
    }

    private void notice(Player player) {
        long now = System.currentTimeMillis();
        Long last = lastNotice.get(player.getUniqueId());
        if (last == null || now - last > 2000L) {
            lastNotice.put(player.getUniqueId(), now);
            player.sendActionBar(Component.text(
                    "✦ La estructura de la aventura es indestructible.",
                    NamedTextColor.AQUA));
        }
    }
}
