package dev.dreamcraft.protection.listener;

import dev.dreamcraft.protection.config.CommandNames;
import dev.dreamcraft.protection.config.ProtectionConfig;
import dev.dreamcraft.protection.domain.model.Ward;
import dev.dreamcraft.protection.domain.model.WardTier;
import dev.dreamcraft.protection.domain.port.WardTierProvider;
import dev.dreamcraft.protection.domain.service.WardService;
import dev.dreamcraft.protection.ui.WardItems;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Gates "advanced" blocks (enchanting table, brewing stand, beacon, …) behind
 * Ward tiers: inside a Ward whose tier is below the configured minimum for the
 * material, placement is allowed but puts the Ward into a surcharge state.
 *
 * <p>Surcharge model (replaces the old hard denial):
 * <ul>
 *   <li>Placement of a gated block below the required rank increments the
 *       Ward's {@code belowTierBlocks} counter and informs the player.</li>
 *   <li>Every upkeep interval costs {@code tier.upkeepPerInterval
 *       + below-tier-surcharge-units × belowTierBlocks} — see
 *       {@code WardUpkeepTickTask}. The founder item is exempt.</li>
 *   <li>Breaking a still-gated block decrements the counter (documented
 *       approximation: it does not track WHO placed each block).</li>
 * </ul>
 *
 * <p>Backfill: {@link #seedExistingBelowTierBlocks(Ward)} replaces the counter
 * with a fresh scan of the core's surroundings. It runs when a Ward is founded
 * over pre-existing gated blocks and when a tier transition descends (tier
 * ascent instead wipes the counter in the domain — see
 * {@code WardService#addBaseScore}). Documented approximation: chunks that are
 * not loaded are exempt from the count until the next re-scan; worst case
 * radius 16 ≈ 42k block reads, executed once per founding/re-scan only.
 *
 * <p>Configuration: {@code ward.tier-gated-blocks} maps material → minimum
 * ward-tier key; {@code ward.below-tier-surcharge-units} is the per-block
 * recurring cost. Tier ordering derives from {@code min-base-score}.
 * Admins ({@code dreamcraft.ward.admin}) bypass the gate entirely.
 */
public final class WardBlockGateListener implements Listener {

    private static final String ADMIN_PERM = "dreamcraft.ward.admin";

    private final WardService wardService;
    private final WardTierProvider tierProvider;
    private final Map<Material, String> gatedBlocks;
    private final int surchargeUnits;
    private final WardItems wardItems;
    /** Persists domain data after counter changes (same hook as other listeners). */
    private final Runnable saveAction;

    public WardBlockGateListener(WardService wardService,
                                 WardTierProvider tierProvider,
                                 ProtectionConfig config,
                                 WardItems wardItems,
                                 Runnable saveAction) {
        this.wardService = wardService;
        this.tierProvider = tierProvider;
        this.gatedBlocks = config.wardTierGatedBlocks();
        this.surchargeUnits = Math.max(0, config.belowTierSurchargeUnits());
        this.wardItems = wardItems;
        this.saveAction = saveAction;
    }

    /**
     * Pure decision: should placing this gated block charge the surcharge?
     *
     * @param founderItem  the hand held the tagged founder item (exempt)
     * @param wardRank     0-based rank of the ward tier, or -1 if unknown
     * @param requiredRank 0-based rank of the required tier, or -1 if unknown
     * @return true only for a non-founder item inside an under-ranked known-tier ward
     */
    static boolean shouldChargeSurcharge(boolean founderItem, int wardRank, int requiredRank) {
        if (founderItem) return false;      // founder exemption: no gate, no surcharge
        if (wardRank < 0 || requiredRank < 0) return false; // unknown tiers → don't charge
        return wardRank < requiredRank;
    }

    /**
     * Pure decision (single source of truth for placement gating AND world
     * re-scans): is this material a gated block whose required rank exceeds
     * the ward's current rank — i.e. does it belong to the below-tier
     * surcharge? Unknown materials/tiers never count.
     *
     * @param rankOf maps a tier key to its 0-based min-base-score rank, or a
     *               negative value when the key is unknown
     */
    static boolean isBelowTierGated(Material material,
                                    Map<Material, String> gated,
                                    int wardRank,
                                    Function<String, Integer> rankOf) {
        String requiredTierKey = gated.get(material);
        if (requiredTierKey == null) return false; // not gated at all
        return shouldChargeSurcharge(false, wardRank, rankOf.apply(requiredTierKey));
    }

    // ── Placement → surcharge ─────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Material placed = event.getBlockPlaced().getType();
        String requiredTierKey = gatedBlocks.get(placed);
        if (requiredTierKey == null) return;

        Block block = event.getBlockPlaced();
        Optional<Ward> found = wardService.findAtLocation(
                block.getWorld().getName(), block.getX(), block.getZ());
        if (found.isEmpty()) return; // outside any Ward — not gated by this listener

        Player player = event.getPlayer();
        boolean founderItem = wardItems.isWardItem(event.getItemInHand());
        if (founderItem) return; // founder exemption: neither gate nor surcharge applies
        if (player.hasPermission(ADMIN_PERM)) return;

        Ward ward = found.get();
        if (!isBelowTierGated(placed, gatedBlocks, rankOf(ward.tier()), this::rankOf)) return;

        int newCount = ward.belowTierBlocks() + 1;
        wardService.setBelowTierBlocks(ward, newCount);
        saveAction.run();
        player.sendActionBar(Component.text(
                "⚠ Sobrecosto activo: este Núcleo paga +" + surchargeUnits + " u/intervalo"
                        + " por bloque fuera de fase (total: " + newCount + ").",
                NamedTextColor.YELLOW));
    }

    // ── Break → relieve surcharge ─────────────────────────────────────────────

    /**
     * Breaking a still-gated block inside an under-ranked Ward relieves one unit
     * of surcharge. Approximation documented in the class doc: the counter does
     * not distinguish who placed the block.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Material broken = event.getBlock().getType();
        String requiredTierKey = gatedBlocks.get(broken);
        if (requiredTierKey == null) return;

        Block block = event.getBlock();
        Optional<Ward> found = wardService.findAtLocation(
                block.getWorld().getName(), block.getX(), block.getZ());
        if (found.isEmpty()) return;

        Ward ward = found.get();
        if (ward.belowTierBlocks() <= 0) return; // nothing to relieve
        if (!isBelowTierGated(broken, gatedBlocks, rankOf(ward.tier()), this::rankOf)) {
            return; // unknown tiers or already-covered blocks → untouched
        }

        int newCount = ward.belowTierBlocks() - 1;
        wardService.setBelowTierBlocks(ward, newCount);
        saveAction.run();
        Player player = event.getPlayer();
        player.sendActionBar(Component.text(
                "Sobrecosto reducido: quedan " + newCount + " bloque(s) fuera de fase.",
                NamedTextColor.GREEN));
    }

    // ── Backfill → founding seed / tier-descent re-scan ──────────────────────

    /**
     * Replaces the Ward's {@code belowTierBlocks} counter with a fresh scan of
     * the cube around its core (horizontal ±radius, vertical ±radius clamped
     * to world limits). Used on two routes:
     * <ul>
     *   <li><b>Founding</b> — gated blocks that predate the core (placed before
     *       it existed) enter the surcharge counter immediately.</li>
     *   <li><b>Tier descent</b> (admin score edits only) — replaces any stale
     *       count with what is actually still below-tier in the area.</li>
     * </ul>
     *
     * <p>Documented approximations:
     * <ul>
     *   <li>Unloaded chunks are exempt from the count — they are never loaded
     *       synchronously; the next re-scan picks them up.</li>
     *   <li>Worst case radius 16 ≈ 42k block reads, once per founding/re-scan.</li>
     *   <li>A scan finding 0 below-tier blocks leaves the stored counter
     *       untouched (only positive findings replace it).</li>
     * </ul>
     */
    public void seedExistingBelowTierBlocks(Ward ward) {
        World world = Bukkit.getWorld(ward.worldName());
        if (world == null) return;

        int radius = ward.radius();
        if (radius <= 0 || radius > 64) return; // degenerate/absurd radius guard

        int minX = ward.centerX() - radius, maxX = ward.centerX() + radius;
        int minZ = ward.centerZ() - radius, maxZ = ward.centerZ() + radius;
        int minY = Math.max(world.getMinHeight(), ward.centerY() - radius);
        int maxY = Math.min(world.getMaxHeight() - 1, ward.centerY() + radius);
        if (minY > maxY) return;

        int wardRank = rankOf(ward.tier());
        int total = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                // Doctrine: never load chunks synchronously — skip unloaded ones.
                if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;
                for (int y = minY; y <= maxY; y++) {
                    if (isBelowTierGated(world.getBlockAt(x, y, z).getType(),
                            gatedBlocks, wardRank, this::rankOf)) {
                        total++;
                    }
                }
            }
        }

        if (total > 0) {
            wardService.setBelowTierBlocks(ward, total); // REPLACES, never adds
            saveAction.run();
            Player owner = Bukkit.getPlayer(ward.ownerId());
            if (owner != null) {
                owner.sendMessage(Component.text(
                        "⚠ " + total + " bloque(s) fuera de fase dentro del área: sobrecosto +"
                                + (surchargeUnits * total) + " u/intervalo.",
                        NamedTextColor.YELLOW));
            }
        }
    }

    /** 0-based position of the tier when ordered by min-base-score; -1 if unknown. */
    private int rankOf(String tierKey) {
        List<WardTier> ordered = tierProvider.allTiers().values().stream()
                .sorted(Comparator.comparingInt(WardTier::minBaseScore))
                .toList();
        for (int i = 0; i < ordered.size(); i++) {
            if (ordered.get(i).key().equalsIgnoreCase(tierKey)) return i;
        }
        return -1;
    }
}
