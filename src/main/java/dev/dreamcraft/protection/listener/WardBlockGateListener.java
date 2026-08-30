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
import org.bukkit.ChunkSnapshot;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
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
 * radius 80 ≈ 2.6M block reads, executed off-thread over chunk snapshots.
 *
 * <p>Reconciliation: {@link #startReconciliationTask(JavaPlugin)} runs a cheap
 * round-robin re-scan (one Ward per pass, loaded chunks only, chunk snapshots
 * scanned OFF-thread) that catches any below-tier block whose placement event
 * never reached {@link #onBlockPlace} (cancelled ordering, Bedrock/Geyser edge
 * cases, plugins mutating worlds). The counter only ever moves UP here — break
 * relief stays event-driven — so a missed placement self-heals within one
 * sweep without risking undercharging from unloaded chunks.
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
    /** Owning plugin: async scan scheduling + logger. Null disables reconciliation. */
    private final JavaPlugin pluginRef;

    public WardBlockGateListener(WardService wardService,
                                 WardTierProvider tierProvider,
                                 ProtectionConfig config,
                                 WardItems wardItems,
                                 Runnable saveAction) {
        this(wardService, tierProvider, config, wardItems, saveAction, null);
    }

    public WardBlockGateListener(WardService wardService,
                                 WardTierProvider tierProvider,
                                 ProtectionConfig config,
                                 WardItems wardItems,
                                 Runnable saveAction,
                                 JavaPlugin pluginRef) {
        this.wardService = wardService;
        this.tierProvider = tierProvider;
        this.gatedBlocks = config.wardTierGatedBlocks();
        this.surchargeUnits = Math.max(0, config.belowTierSurchargeUnits());
        this.wardItems = wardItems;
        this.saveAction = saveAction;
        this.pluginRef = pluginRef;
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

    // ── Backfill → founding seed / tier-descent re-scan / reconciliation ─────

    /** Sanity bound for the scan cube side; real radius is capped by ward.max-radius. */
    private static final int MAX_SCAN_RADIUS = 256;
    /** Ticks between reconciliation passes (one Ward per pass). */
    private static final long RECONCILE_PERIOD_TICKS = 100L; // 5s
    /** Hard cap on snapshots collected per pass (radius 80 ≈ 121 chunks). */
    private static final int MAX_CHUNKS_PER_SCAN = 1024;

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
     * <p>The scan is asynchronous: chunk SNAPSHOTS are taken on the main thread
     * (cheap copies, never loads chunks) and the block-by-block counting runs
     * off-thread, so large radii never freeze the server. The result is applied
     * back on the main thread.
     *
     * <p>Documented approximations:
     * <ul>
     *   <li>Unloaded chunks are exempt from the count — they are never loaded
     *       synchronously; the next re-scan picks them up.</li>
     *   <li>A scan finding 0 below-tier blocks leaves the stored counter
     *       untouched (only positive findings replace it).</li>
     * </ul>
     */
    public void seedExistingBelowTierBlocks(Ward ward) {
        scanWardArea(ward, true);
    }

    /**
     * Starts the periodic reconciliation task: every {@link #RECONCILE_PERIOD_TICKS}
     * it re-scans ONE Ward (round-robin over all Wards) and raises its counter
     * when the world contains more below-tier gated blocks than accounted for.
     * This is the safety net for placements whose event never incremented the
     * counter — including blocks added AFTER the core was founded while some
     * other plugin swallowed or reordered the placement event.
     */
    public void startReconciliationTask(JavaPlugin plugin) {
        JavaPlugin owner = plugin != null ? plugin : pluginRef;
        if (owner == null) return; // no plugin context → reconciliation unavailable
        new BukkitRunnable() {
            int index = 0;

            @Override
            public void run() {
                List<Ward> all = new ArrayList<>(wardService.findAll());
                if (all.isEmpty()) return;
                Ward ward = all.get(index % all.size());
                index = (index + 1) % all.size();
                scanWardArea(ward, false);
            }
        }.runTaskTimer(owner, RECONCILE_PERIOD_TICKS, RECONCILE_PERIOD_TICKS);
    }

    /**
     * Shared scan pipeline. {@code authoritative} mode (founding/descent) keeps
     * the historical replace-with-scan semantics; {@code reconcile} mode is
     * monotonic upward-only so unloaded chunks can never UNDERCHARGE a Ward.
     */
    private void scanWardArea(Ward ward, boolean authoritative) {
        World world = Bukkit.getWorld(ward.worldName());
        if (world == null) return;

        int radius = ward.radius();
        if (radius <= 0 || radius > MAX_SCAN_RADIUS) return; // degenerate/absurd radius guard

        int minX = ward.centerX() - radius, maxX = ward.centerX() + radius;
        int minZ = ward.centerZ() - radius, maxZ = ward.centerZ() + radius;
        int minY = Math.max(world.getMinHeight(), ward.centerY() - radius);
        int maxY = Math.min(world.getMaxHeight() - 1, ward.centerY() + radius);
        if (minY > maxY) return;

        // Doctrine: never load chunks synchronously — snapshot ONLY loaded ones.
        List<ChunkSnapshot> snapshots = new ArrayList<>();
        for (int cx = minX >> 4; cx <= maxX >> 4 && snapshots.size() < MAX_CHUNKS_PER_SCAN; cx++) {
            for (int cz = minZ >> 4; cz <= maxZ >> 4 && snapshots.size() < MAX_CHUNKS_PER_SCAN; cz++) {
                if (!world.isChunkLoaded(cx, cz)) continue;
                snapshots.add(world.getChunkAt(cx, cz).getChunkSnapshot(false, false, false));
            }
        }
        if (snapshots.isEmpty()) return;

        int wardRank = rankOf(ward.tier());

        // Without a plugin context (legacy constructor) fall back to the
        // historical synchronous scan; production always wires pluginRef.
        if (pluginRef == null) {
            int total = 0;
            for (ChunkSnapshot snap : snapshots) {
                total += countBelowTierInSnapshot(snap, minY, maxY, wardRank);
            }
            applyScanResult(ward, total, authoritative);
            return;
        }

        // Block reads happen OFF-thread: ChunkSnapshot is an immutable copy and
        // its getBlockType is documented thread-safe. The result hops back to
        // the main thread before mutating domain state.
        new BukkitRunnable() {
            @Override
            public void run() {
                int total = 0;
                for (ChunkSnapshot snap : snapshots) {
                    total += countBelowTierInSnapshot(snap, minY, maxY, wardRank);
                }
                final int counted = total;
                Bukkit.getScheduler().runTask(pluginRef,
                        () -> applyScanResult(ward, counted, authoritative));
            }
        }.runTaskAsynchronously(pluginRef);
    }

    /**
     * Counts below-tier gated blocks inside one chunk snapshot, restricted to
     * the vertical band [minY..maxY]. Pure over immutable data — safe off-thread.
     */
    private int countBelowTierInSnapshot(ChunkSnapshot snap, int minY, int maxY, int wardRank) {
        int total = 0;
        for (int y = minY; y <= maxY; y++) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    if (isBelowTierGated(snap.getBlockType(x, y, z), gatedBlocks, wardRank, this::rankOf)) {
                        total++;
                    }
                }
            }
        }
        return total;
    }

    /**
     * Main-thread application of a finished scan.
     * Authoritative scans REPLACE the counter when they find anything (founding/
     * descent contract); reconciliations only RAISE it (monotonic upward).
     */
    private void applyScanResult(Ward ward, int scanned, boolean authoritative) {
        if (!wardService.findAll().stream().anyMatch(w -> w.id().equals(ward.id()))) return; // dissolved meanwhile
        int current = ward.belowTierBlocks();
        boolean apply = authoritative ? scanned > 0 : scanned > current;
        if (!apply) return;

        wardService.setBelowTierBlocks(ward, scanned);
        saveAction.run();
        if (scanned > current) {
            pluginRef.getLogger().info("[WardGate] " + ward.name()
                    + ": sobrecosto " + current + " → " + scanned
                    + " bloque(s) fuera de fase (escaneo).");
            Player owner = Bukkit.getPlayer(ward.ownerId());
            if (owner != null) {
                owner.sendMessage(Component.text(
                        "⚠ " + scanned + " bloque(s) fuera de fase dentro del área: sobrecosto +"
                                + (surchargeUnits * scanned) + " u/intervalo.",
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
