package dev.dreamcraft.protection.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Pure (no Bukkit) calculator that translates a Ward's upkeep balance into
 * player-facing time projections.
 *
 * <p><b>Semantics mirrored from the real system</b> ({@code WardService#deductUpkeep} +
 * {@code WardUpkeepTickTask}): every {@code interval} the tier charges
 * {@code unitsPerInterval} units from the balance. Coverage therefore lasts
 * {@code balance / unitsPerInterval} intervals. When the balance cannot cover a
 * charge the domain zeroes it and the Ward stops paying (debt).
 *
 * <p><b>Rounding decision:</b> {@code intervalsRemaining} is
 * {@code floor(balance / unitsPerInterval)} — the number of FULL intervals the
 * balance certainly covers. The humanized remaining time uses the exact
 * fractional coverage ({@code interval × balance / unitsPerInterval}, truncated
 * to whole seconds), because a balance covering half an interval really does
 * protect for half an interval. {@link Projection#fractionalIntervals()} exposes
 * the precise value for callers that want it.
 *
 * <p><b>Qualitative state cuts</b> reuse the system's own configured durations
 * (never invented ones), evaluated in this order:
 * <ol>
 *   <li>{@code balance <= 0} → {@link State#EXPIRADO}: nothing left to charge.</li>
 *   <li>{@code balance < unitsPerInterval} → {@link State#GRACIA}: the next
 *       charge will fail — the Ward enters the debt/grace path on its next tick.</li>
 *   <li>{@code timeRemaining < expiringThreshold} → {@link State#POR_VENCER}.</li>
 *   <li>{@code timeRemaining < warningThreshold} → {@link State#AVISO}.</li>
 *   <li>otherwise → {@link State#PROTEGIDO}.</li>
 * </ol>
 * Note: with the shipped defaults ({@code interval = warning-threshold = 24h})
 * every payable Ward shows PROTEGIDO; AVISO/POR_VENCER become reachable as soon
 * as a server raises {@code warning-threshold} / {@code expiring-threshold}
 * above the interval or shortens the interval.
 *
 * <p><b>Material equivalences:</b> each accepted material buys
 * {@code unitsPerItem / unitsPerInterval × interval} of protection per single
 * item, computed against the same interval (e.g. DIAMOND 64 u at 8 u/interval
 * over 24 h ≈ 3d per diamond).
 */
public final class UpkeepProjectionCalculator {

    /** Qualitative protection state shown to players. */
    public enum State {
        PROTEGIDO("&a"),
        AVISO("&e"),
        POR_VENCER("&6"),
        GRACIA("&c"),
        EXPIRADO("&4");

        private final String legacyColor;

        State(String legacyColor) { this.legacyColor = legacyColor; }

        /** Legacy {@code &}-code matching this severity (green/yellow/orange/red/red). */
        public String legacyColor() { return legacyColor; }

        /** Spanish display label. */
        public String displayName() {
            return switch (this) {
                case POR_VENCER -> "POR VENCER";
                default -> name();
            };
        }
    }

    /** Time one unit-currency item buys, ready for display. */
    public record Equivalence(String materialName, String label, int unitsPerItem,
                              Duration timeBought, String timeBoughtText) {}

    /**
     * Full projection for one Ward snapshot.
     *
     * @param state               qualitative protection state (see class doc)
     * @param intervalsRemaining  floor(balance / unitsPerInterval) — full intervals covered
     * @param fractionalIntervals exact balance / unitsPerInterval (≥ 0)
     * @param timeRemaining       real covered time: interval × fractionalIntervals
     * @param timeRemainingText   compact Spanish rendering of timeRemaining ("6d 12h")
     * @param unitsPerInterval    units charged each interval (as resolved by the caller)
     * @param balanceUnits        the balance this projection was computed from
     * @param equivalences        material → time bought per single item (config order)
     */
    public record Projection(State state,
                             int intervalsRemaining,
                             double fractionalIntervals,
                             Duration timeRemaining,
                             String timeRemainingText,
                             int unitsPerInterval,
                             int balanceUnits,
                             List<Equivalence> equivalences) {}

    private final Duration interval;
    private final Duration warningThreshold;
    private final Duration expiringThreshold;
    /** Accepted materials keyed by name ("DIAMOND") → units credited per single item. */
    private final Map<String, Integer> materialsByUnitsPerItem;
    private final Function<String, String> labelResolver;
    private final Map<Integer, List<Equivalence>> equivalenceCache = new ConcurrentHashMap<>();

    public UpkeepProjectionCalculator(Duration interval,
                                      Duration warningThreshold,
                                      Duration expiringThreshold,
                                      Map<String, Integer> materialsByUnitsPerItem) {
        this(interval, warningThreshold, expiringThreshold, materialsByUnitsPerItem, Function.identity());
    }

    /**
     * @param labelResolver turns a material name ("DIAMOND") into a Spanish display
     *                      label ("Diamante"); identity when absent
     */
    public UpkeepProjectionCalculator(Duration interval,
                                      Duration warningThreshold,
                                      Duration expiringThreshold,
                                      Map<String, Integer> materialsByUnitsPerItem,
                                      Function<String, String> labelResolver) {
        this.interval = interval != null && !interval.isNegative() && !interval.isZero()
                ? interval : Duration.ofHours(24);
        this.warningThreshold = warningThreshold != null ? warningThreshold : Duration.ZERO;
        this.expiringThreshold = expiringThreshold != null ? expiringThreshold : Duration.ZERO;
        // LinkedHashMap + unmodifiable wrapper: keeps the config insertion order
        // (Map.copyOf would scramble it), which lore/chat rendering relies on.
        this.materialsByUnitsPerItem = java.util.Collections.unmodifiableMap(
                new LinkedHashMap<>(materialsByUnitsPerItem));
        this.labelResolver = labelResolver;
    }

    /** The upkeep interval this calculator was built with. */
    public Duration interval() { return interval; }

    /** Accepted materials in config order (name → units per single item). */
    public Map<String, Integer> materials() {
        return new LinkedHashMap<>(materialsByUnitsPerItem);
    }

    /**
     * Projects the given balance against the given per-interval consumption.
     *
     * @param balanceUnits     current upkeep balance of the Ward (clamped at ≥ 0)
     * @param unitsPerInterval units charged per interval (values ≤ 0 are treated as 1)
     */
    public Projection project(int balanceUnits, int unitsPerInterval) {
        int upi = Math.max(1, unitsPerInterval);
        int balance = Math.max(0, balanceUnits);

        int fullIntervals = balance / upi;
        double fractional = balance / (double) upi;
        // Exact fractional coverage truncated to seconds — a partial interval still protects.
        Duration coverage = interval.multipliedBy(balance).dividedBy(upi);
        String coverageText = humanize(coverage);

        State state;
        if (balance <= 0) {
            state = State.EXPIRADO;
        } else if (balance < upi) {
            state = State.GRACIA;
        } else if (coverage.compareTo(expiringThreshold) < 0) {
            state = State.POR_VENCER;
        } else if (coverage.compareTo(warningThreshold) < 0) {
            state = State.AVISO;
        } else {
            state = State.PROTEGIDO;
        }

        return new Projection(state, fullIntervals, fractional, coverage, coverageText,
                upi, balance, equivalences(upi));
    }

    /**
     * Per-item time equivalences for the given consumption rate, in config order.
     * Cached per distinct {@code unitsPerInterval}.
     */
    public List<Equivalence> equivalences(int unitsPerInterval) {
        int upi = Math.max(1, unitsPerInterval);
        return equivalenceCache.computeIfAbsent(upi, rate -> {
            List<Equivalence> list = new ArrayList<>(materialsByUnitsPerItem.size());
            materialsByUnitsPerItem.forEach((name, unitsPerItem) -> {
                Duration bought = interval.multipliedBy(Math.max(0, unitsPerItem)).dividedBy(rate);
                String label = labelResolver.apply(name);
                list.add(new Equivalence(name, label != null ? label : name, unitsPerItem,
                        bought, humanize(bought)));
            });
            return List.copyOf(list);
        });
    }

    /**
     * Compact Spanish duration: two most significant units among d/h/m —
     * "6d 12h", "3h 20m", "45m"; zero/negative renders as "0m".
     */
    public static String humanize(Duration duration) {
        long totalSeconds = duration == null ? 0 : Math.max(0, duration.getSeconds());
        long days = totalSeconds / 86400;
        long hours = (totalSeconds % 86400) / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        if (days > 0) {
            return hours > 0 ? days + "d " + hours + "h" : days + "d";
        }
        if (hours > 0) {
            return minutes > 0 ? hours + "h " + minutes + "m" : hours + "h";
        }
        return minutes + "m";
    }

    /** Lowercase-safe material key normalization ("diamond" → "DIAMOND"). */
    public static String normalizeMaterialName(String raw) {
        return raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
    }
}
