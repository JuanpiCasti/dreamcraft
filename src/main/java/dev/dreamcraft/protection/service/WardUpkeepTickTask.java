package dev.dreamcraft.protection.service;

import dev.dreamcraft.protection.config.CommandNames;

import dev.dreamcraft.protection.domain.model.Ward;
import dev.dreamcraft.protection.domain.port.WardTierProvider;
import dev.dreamcraft.protection.domain.service.WardService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Periodic task that drains Ward upkeep balances and warns owners in debt.
 *
 * <p>Runs every minute (cheap scan) and processes every Ward whose persisted
 * {@code nextUpkeepAt} is due — including intervals missed while the server
 * was offline, because timestamps survive restarts and each deduction advances
 * {@code nextUpkeepAt}, so no interval is ever charged twice.
 *
 * <p>When a Ward cannot pay, its balance is zeroed by the domain service and
 * the owner is warned at most once per upkeep interval. (Region enforcement —
 * e.g. suspending protection after a grace period — is a future concern.)
 */
public final class WardUpkeepTickTask extends BukkitRunnable {

    private static final long PERIOD_TICKS = 1200L; // 60s

    private final WardService wardService;
    private final WardTierProvider tierProvider;
    private final dev.dreamcraft.protection.persistence.YamlWardRepository wardRepository;
    private final Duration notifyThrottle;
    /** Recurring surcharge per gated block placed below its tier (ward.below-tier-surcharge-units). */
    private final int belowTierSurchargeUnits;
    private final JavaPlugin plugin;
    private final Map<UUID, Instant> lastDebtWarning = new HashMap<>();
    /** Optional: refreshes the core block's active/inactive visual after charges. */
    private java.util.function.Consumer<Ward> coreVisualRefresh = ward -> { };

    public WardUpkeepTickTask(WardService wardService,
                              WardTierProvider tierProvider,
                              dev.dreamcraft.protection.persistence.YamlWardRepository wardRepository,
                              Duration upkeepInterval,
                              int belowTierSurchargeUnits,
                              JavaPlugin plugin) {
        this.wardService = wardService;
        this.tierProvider = tierProvider;
        this.wardRepository = wardRepository;
        this.notifyThrottle = upkeepInterval;
        this.belowTierSurchargeUnits = Math.max(0, belowTierSurchargeUnits);
        this.plugin = plugin;
    }

    /** Wires the core-block visual refresher (see {@link WardCoreVisual}). */
    public void setCoreVisualRefresh(java.util.function.Consumer<Ward> consumer) {
        this.coreVisualRefresh = consumer == null ? ward -> { } : consumer;
    }

    /** Registers this task on the Bukkit scheduler. */
    public void register() {
        runTaskTimer(plugin, PERIOD_TICKS, PERIOD_TICKS);
    }

    @Override
    public void run() {
        Logger log = plugin.getLogger();
        Instant now = Instant.now();
        int processed = 0;
        int indebted = 0;

        for (Ward ward : wardService.findAll()) {
            if (ward.nextUpkeepAt().isAfter(now)) continue;

            // Effective upkeep: base tier cost + surcharge for each gated block
            // placed while the Ward was below the required rank
            int baseCost = tierProvider.findByKey(ward.tier())
                    .map(dev.dreamcraft.protection.domain.model.WardTier::upkeepPerInterval)
                    .orElse(1);
            int cost = baseCost + belowTierSurchargeUnits * Math.max(0, ward.belowTierBlocks());
            boolean paid = wardService.deductUpkeep(ward, cost);
            coreVisualRefresh.accept(ward);
            processed++;
            if (paid) {
                lastDebtWarning.remove(ward.id());
            } else {
                indebted++;
                warnOwnerOncePerInterval(ward, cost, now);
            }
        }

        if (processed > 0) {
            log.info("[WardUpkeep] Procesados " + processed + " Ward(s)"
                    + (indebted > 0 ? ", " + indebted + " sin fondos." : "."));
            try {
                wardRepository.flush();
            } catch (java.io.IOException e) {
                log.warning("[WardUpkeep] No se pudo guardar wards: " + e.getMessage());
            }
        }
    }

    private void warnOwnerOncePerInterval(Ward ward, int cost, Instant now) {
        Instant last = lastDebtWarning.get(ward.id());
        if (last != null && last.plus(notifyThrottle).isAfter(now)) return;
        lastDebtWarning.put(ward.id(), now);

        Player owner = Bukkit.getPlayer(ward.ownerId());
        if (owner == null) return;
        int surcharge = belowTierSurchargeUnits * Math.max(0, ward.belowTierBlocks());
        String extra = surcharge > 0
                ? " (incluye +" + surcharge + " de sobrecosto por " + ward.belowTierBlocks()
                + " bloque(s) fuera de fase)" : "";
        owner.sendMessage("§c[Sincronía] Tu Núcleo §f" + ward.name()
                + "§c no pudo pagar el mantenimiento (" + cost + " unidades)" + extra
                + ". Alimentalo en su bloque o con §f/" + CommandNames.root("ward") + " upkeep deposit§c.");
    }
}
