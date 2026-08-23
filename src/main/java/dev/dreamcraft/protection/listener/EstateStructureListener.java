package dev.dreamcraft.protection.listener;

import dev.dreamcraft.protection.command.CommandMessages;
import dev.dreamcraft.protection.domain.model.Estate;
import dev.dreamcraft.protection.domain.service.EstateService;
import dev.dreamcraft.protection.service.EstateZoneJournal;
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
 *   <li><b>Indestructible core</b>: ores, frames, vaults and trial spawners
 *       inside END/TRIAL_CHAMBER areas cannot be broken — no party can deplete
 *       the stronghold for the next group. Vanilla {@code SPAWNER} blocks
 *       (silverfish) are deliberately breakable: the zone journal snapshots
 *       their full BlockData and every session close restores them.</li>
 *   <li><b>Zone journal</b>: any other modification (placed blocks, broken
 *       stone, buckets, explosions) records the original state; the runtime
 *       rolls it back when the party closes the zone, so other adventurers
 *       never collide with the previous group's mess.</li>
 *   <li><b>Membership gate</b>: players belonging to none of the estates that
 *       cover a position cannot edit inside the area at all — they get the
 *       same zone-nearby prompt shown on entry.</li>
 * </ul>
 */
public final class EstateStructureListener implements Listener {

    /**
     * Progression-critical blocks: unbreakable outright. Vanilla {@code
     * SPAWNER} is NOT listed — silverfish spawners are farmable/breakable and
     * come back with the zone rollback.
     */
    private static final Material[] PROTECTED = {
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
    /** Per-player gate-title throttle (ms) — same pattern as {@link #lastNotice}. */
    private final Map<UUID, Long> lastGateTitle = new ConcurrentHashMap<>();

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
        boolean admin = isAdmin(player);
        if (!admin && !isMemberAnywhere(zones, player.getUniqueId())) {
            event.setCancelled(true);
            gateTitle(player, zones.get(0));
            return;
        }

        if (!admin && protectStructure && isProtected(block.getType())) {
            event.setCancelled(true);
            notice(player);
            return;
        }
        // Loot vessels stay in place even when general protection is off
        if (!admin && isContainer(block.getType()) && !player.hasPermission("dreamcraft.ward.admin")) {
            event.setCancelled(true);
            notice(player);
            return;
        }
        // Members AND admins journal here: admin setup edits are attributed to
        // the persistent zone ({@link #owningZone}), so the scheduled portal
        // reset restores everything — including silverfish spawners an op broke.
        if (regenerateZone && journal != null) {
            UUID zoneId = owningZone(player.getUniqueId(), zones);
            journal.record(zoneId, block, block.getState());
        }
    }

    // ── Place: allowed while fighting, rolled back on close ───────────────────

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();
        List<Estate> zones = zonesAt(block);
        if (zones.isEmpty()) return;
        if (isAdmin(player)) return;
        if (!isMemberAnywhere(zones, player.getUniqueId())) {
            event.setCancelled(true);
            gateTitle(player, zones.get(0));
            return;
        }
        // Journal what the placement REPLACED so rollback restores it exactly
        if (!regenerateZone || journal == null) return;
        journal.record(owningZone(player.getUniqueId(), zones), block,
                event.getBlockReplacedState().getBlockData());
    }

    // ── Buckets: liquids are the classic scar left behind ─────────────────────

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Block block = event.getBlockClicked().getRelative(event.getBlockFace());
        Player player = event.getPlayer();
        List<Estate> zones = zonesAt(block);
        if (zones.isEmpty()) return;
        if (isAdmin(player)) return;
        if (!isMemberAnywhere(zones, player.getUniqueId())) {
            event.setCancelled(true);
            gateTitle(player, zones.get(0));
            return;
        }
        if (!regenerateZone || journal == null) return;
        journal.record(owningZone(player.getUniqueId(), zones), block, block.getBlockData());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        Block block = event.getBlockClicked().getRelative(event.getBlockFace());
        Player player = event.getPlayer();
        List<Estate> zones = zonesAt(block);
        if (zones.isEmpty()) return;
        if (isAdmin(player)) return;
        if (!isMemberAnywhere(zones, player.getUniqueId())) {
            event.setCancelled(true);
            gateTitle(player, zones.get(0));
            return;
        }
        if (!regenerateZone || journal == null) return;
        journal.record(owningZone(player.getUniqueId(), zones), block, block.getBlockData());
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

    /**
     * Attributes an edit to the estate whose session owns it: the editor's own
     * party first (its close reverts exactly their group's mess), falling back
     * to the persistent zone template. Never pick by collection order — the
     * admin zone and every party share the same area, and an unordered pick
     * used to journal admin setup work under a party id, silently reverting it
     * when that party closed.
     */
    private static UUID owningZone(UUID playerId, List<Estate> zones) {
        for (Estate zone : zones) {
            if (!zone.persistent() && (zone.isMember(playerId) || zone.isOwner(playerId))) {
                return zone.id();
            }
        }
        for (Estate zone : zones) {
            if (zone.persistent()) return zone.id();
        }
        return zones.get(0).id();
    }

    /** Admin construction inside a zone is deliberate setup, never adventurer damage. */
    private static boolean isAdmin(Player player) {
        return player.hasPermission("dreamcraft.protection.admin");
    }

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

    /** True when the player belongs to at least one estate covering the position. */
    private static boolean isMemberAnywhere(List<Estate> zones, UUID playerId) {
        for (Estate zone : zones) {
            if (zone.isOwner(playerId) || zone.isMember(playerId)) return true;
        }
        return false;
    }

    /**
     * Non-member tried to edit inside the area: same zone-nearby prompt shown
     * on entry (same keys/placeholders), throttled to ~5s per player.
     */
    private void gateTitle(Player player, Estate zone) {
        long now = System.currentTimeMillis();
        Long last = lastGateTitle.get(player.getUniqueId());
        if (last != null && now - last <= 5000L) return;
        lastGateTitle.put(player.getUniqueId(), now);
        CommandMessages.adventureZoneNearby(player, zone.type());
    }

    private void notice(Player player) {
        long now = System.currentTimeMillis();
        Long last = lastNotice.get(player.getUniqueId());
        if (last == null || now - last > 2000L) {
            lastNotice.put(player.getUniqueId(), now);
            player.sendActionBar(CommandMessages.legacy(CommandMessages.tr(
                    "adventure.structure-indestructible",
                    "&b✦ La estructura de la aventura es indestructible.")));
        }
    }
}
