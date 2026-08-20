package dev.dreamcraft.protection.service;

import dev.dreamcraft.protection.config.ProtectionConfig;
import dev.dreamcraft.protection.model.ProtectionClaim;
import dev.dreamcraft.protection.model.ProtectionState;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.IOException;
import java.time.Instant;
import java.util.Collection;
import java.util.logging.Logger;

/**
 * Periodic task that drains upkeep resources from every active claim and drives state transitions.
 *
 * <p>Runs once per {@code config.upkeepInterval()}. For each claim whose {@code nextUpkeepAt}
 * is in the past it:
 * <ol>
 *   <li>Consumes one interval's worth of upkeep units from the claim's storage.</li>
 *   <li>Delegates state recalculation to {@link UpkeepManager}.</li>
 *   <li>Notifies owners/members when the claim enters WARNING or EXPIRING (once per state change).</li>
 * </ol>
 *
 * <p>State reconstruction after restart:
 * Upkeep timestamps ({@code nextUpkeepAt}) are persisted. On startup, any claim whose
 * {@code nextUpkeepAt} is already in the past will be processed on the first tick,
 * consuming the correct number of missed intervals and updating state accordingly.
 * This prevents double-consumption: once an interval is consumed, {@code nextUpkeepAt}
 * is advanced, so the same interval is never charged twice.
 */
public final class UpkeepTickTask extends BukkitRunnable {
    private final ClaimManager claimManager;
    private final UpkeepManager upkeepManager;
    private final UpkeepCalculator upkeepCalculator;
    private final ProtectionConfig config;
    private final JavaPlugin plugin;

    public UpkeepTickTask(
            ClaimManager claimManager,
            UpkeepManager upkeepManager,
            UpkeepCalculator upkeepCalculator,
            ProtectionConfig config,
            JavaPlugin plugin) {
        this.claimManager = claimManager;
        this.upkeepManager = upkeepManager;
        this.upkeepCalculator = upkeepCalculator;
        this.config = config;
        this.plugin = plugin;
    }

    /**
     * Registers this task on the Bukkit scheduler.
     * The period is the upkeep interval (converted to server ticks).
     * Minimum 1-minute period to avoid accidental runaway.
     */
    public void register() {
        long intervalTicks = config.upkeepInterval().getSeconds() * 20L;
        long period = Math.max(intervalTicks, 1200L);
        runTaskTimer(plugin, period, period);
    }

    @Override
    public void run() {
        Logger log = plugin.getLogger();
        Instant now = Instant.now();
        Collection<ProtectionClaim> claims = claimManager.allClaims();
        int processed = 0;
        for (ProtectionClaim claim : claims) {
            if (claim.nextUpkeepAt().isAfter(now)) {
                continue;
            }
            // Consume one interval's cost (may be zero if units already depleted)
            UpkeepSnapshot snapshot = upkeepCalculator.calculate(claim);
            if (snapshot.storedUnits() > 0) {
                int toDrain = Math.min(snapshot.dailyCost(), snapshot.storedUnits());
                claim.upkeepStorage().withdraw("maintenance", toDrain);
            }
            // Recalculate state after drain
            upkeepManager.recalculateState(claim);
            // Notify only on state change (anti-spam: lastNotifiedState tracks last sent state)
            notifyIfStateChanged(claim);
            processed++;
        }
        if (processed > 0) {
            log.info("[UpkeepTick] Procesados " + processed + " claim(s).");
        }
        // Persist after every tick cycle so timestamps survive restarts
        try {
            claimManager.save();
        } catch (IOException e) {
            log.warning("[UpkeepTick] No se pudo guardar claims: " + e.getMessage());
        }
    }

    /**
     * Sends a warning message to all online members of the claim only when
     * the state has changed since the last notification. Prevents spam on repeated ticks.
     */
    private void notifyIfStateChanged(ProtectionClaim claim) {
        ProtectionState state = claim.status();
        if (state != ProtectionState.WARNING && state != ProtectionState.EXPIRING
                && state != ProtectionState.NO_RESOURCES) {
            // Clear last notified so we re-notify if state regresses back
            if (claim.lastNotifiedState() != null) {
                claim.lastNotifiedState(null);
            }
            return;
        }
        // Only notify if state changed since last notification
        if (state == claim.lastNotifiedState()) {
            return;
        }
        claim.lastNotifiedState(state);
        String message = warningMessage(claim);
        if (message.isEmpty()) return;
        plugin.getServer().getOnlinePlayers().stream()
                .filter(p -> claim.isMember(p.getUniqueId()))
                .forEach(p -> p.sendMessage(message));
    }

    private String warningMessage(ProtectionClaim claim) {
        String coords = claim.centerX() + "," + claim.centerZ() + " (" + claim.world() + ")";
        return switch (claim.status()) {
            case WARNING     -> "§e[Protección] Tu claim en " + coords + " tiene recursos bajos.";
            case EXPIRING    -> "§c[Protección] Tu claim en " + coords + " ¡está a punto de expirar!";
            case NO_RESOURCES -> "§4[Protección] Tu claim en " + coords + " se quedó sin recursos.";
            default          -> "";
        };
    }
}
