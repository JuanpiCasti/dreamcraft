package dev.dreamcraft.protection.service;

import dev.dreamcraft.protection.service.UpkeepProjectionCalculator.Equivalence;
import dev.dreamcraft.protection.service.UpkeepProjectionCalculator.Projection;
import dev.dreamcraft.protection.service.UpkeepProjectionCalculator.State;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure tests for {@link UpkeepProjectionCalculator} (no MockBukkit).
 * Default material map and 24h interval mirror ProtectionConfig defaults.
 */
class UpkeepProjectionCalculatorTest {

    /** ward.upkeep-materials defaults: DIAMOND 64, EMERALD 48, GOLD_INGOT 16, IRON_INGOT 8, COAL 2. */
    private static final Map<String, Integer> DEFAULT_MATERIALS = buildDefaults();

    private static Map<String, Integer> buildDefaults() {
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put("DIAMOND", 64);
        m.put("EMERALD", 48);
        m.put("GOLD_INGOT", 16);
        m.put("IRON_INGOT", 8);
        m.put("COAL", 2);
        return m;
    }

    private static UpkeepProjectionCalculator calculator(Duration interval,
                                                         Duration warning,
                                                         Duration expiring) {
        return new UpkeepProjectionCalculator(interval, warning, expiring, DEFAULT_MATERIALS);
    }

    // ── Balance 0 → EXPIRADO / zero time ─────────────────────────────────────

    @Test
    void zeroBalanceIsExpiredWithZeroTime() {
        Projection p = calculator(Duration.ofHours(24), Duration.ofHours(24),
                Duration.ofHours(6)).project(0, 8);
        assertEquals(State.EXPIRADO, p.state());
        assertEquals(0, p.intervalsRemaining());
        assertEquals(Duration.ZERO, p.timeRemaining());
        assertEquals("0m", p.timeRemainingText());
        assertEquals(0.0, p.fractionalIntervals(), 1e-9);
    }

    @Test
    void negativeBalanceClampsToExpired() {
        Projection p = calculator(Duration.ofHours(24), Duration.ofHours(24),
                Duration.ofHours(6)).project(-5, 8);
        assertEquals(State.EXPIRADO, p.state());
        assertEquals(Duration.ZERO, p.timeRemaining());
    }

    // ── Exactly one interval ──────────────────────────────────────────────────

    @Test
    void exactlyOneIntervalIsProtected() {
        Projection p = calculator(Duration.ofHours(24), Duration.ofHours(24),
                Duration.ofHours(6)).project(8, 8);
        assertEquals(State.PROTEGIDO, p.state());
        assertEquals(1, p.intervalsRemaining());
        assertEquals(Duration.ofHours(24), p.timeRemaining());
        assertEquals("1d", p.timeRemainingText());
        assertEquals(1.0, p.fractionalIntervals(), 1e-9);
    }

    // ── Fractions: floor documented + exact coverage shown separately ────────

    @Test
    void fractionalBalanceFloorsIntervalsButKeepsExactCoverage() {
        // 12 units at 8 u/interval over 24h → floor 1 full interval,
        // real coverage 12/8 × 24h = 36h.
        Projection p = calculator(Duration.ofHours(24), Duration.ofHours(24),
                Duration.ofHours(6)).project(12, 8);
        assertEquals(1, p.intervalsRemaining());
        assertEquals(1.5, p.fractionalIntervals(), 1e-9);
        assertEquals(Duration.ofHours(36), p.timeRemaining());
        assertEquals("1d 12h", p.timeRemainingText());
        assertEquals(State.PROTEGIDO, p.state()); // 36h ≥ warning 24h
    }

    @Test
    void subIntervalBalanceEntersGraceWithPartialCoverage() {
        // 3 units at 8 u/interval → cannot pay next charge → GRACIA even though
        // the remaining fraction still covers 3/8 × 24h = 9h.
        Projection p = calculator(Duration.ofHours(24), Duration.ofHours(48),
                Duration.ofHours(12)).project(3, 8);
        assertEquals(State.GRACIA, p.state());
        assertEquals(0, p.intervalsRemaining());
        assertEquals(0.375, p.fractionalIntervals(), 1e-9);
        assertEquals(Duration.ofHours(9), p.timeRemaining());
        assertEquals("9h", p.timeRemainingText());
    }

    // ── Threshold cuts (system's own config durations) ───────────────────────

    @Test
    void avisoWhenCoverageBelowWarningThreshold() {
        // 12 u → 36h coverage; warning 48h, expiring 12h → AVISO.
        Projection p = calculator(Duration.ofHours(24), Duration.ofHours(48),
                Duration.ofHours(12)).project(12, 8);
        assertEquals(State.AVISO, p.state());
    }

    @Test
    void porVencerWhenCoverageBelowExpiringThreshold() {
        // balance ≥ upi (10 ≥ 10) so not GRACIA; coverage 6h < expiring 12h.
        Projection p = calculator(Duration.ofHours(6), Duration.ofHours(48),
                Duration.ofHours(12)).project(10, 10);
        assertEquals(State.POR_VENCER, p.state());
    }

    @Test
    void protegidoWhenCoverageAtOrAboveWarningThreshold() {
        // Coverage exactly equals warning threshold → still PROTEGIDO (cut is strict <).
        Projection p = calculator(Duration.ofHours(24), Duration.ofHours(48),
                Duration.ofHours(12)).project(16, 8);
        assertEquals(State.PROTEGIDO, p.state());
        assertEquals(Duration.ofHours(48), p.timeRemaining());
    }

    // ── Material equivalences against the default map, interval 24h ──────────

    @Test
    void defaultMaterialEquivalencesAtEightUnitsPerIntervalOver24h() {
        List<Equivalence> eqs = calculator(Duration.ofHours(24), Duration.ofHours(24),
                Duration.ofHours(6)).equivalences(8);

        assertEquals(5, eqs.size());
        // Insertion order preserved: DIAMOND, EMERALD, GOLD_INGOT, IRON_INGOT, COAL
        assertEquals("DIAMOND", eqs.get(0).materialName());
        assertEquals(Duration.ofDays(8), eqs.get(0).timeBought());       // DIAMOND 64/8 = 8 intervals
        assertEquals("8d", eqs.get(0).timeBoughtText());

        assertEquals(Duration.ofDays(6), eqs.get(1).timeBought());       // EMERALD 48/8 = 6
        assertEquals("6d", eqs.get(1).timeBoughtText());

        assertEquals(Duration.ofDays(2), eqs.get(2).timeBought());       // GOLD_INGOT 16/8 = 2
        assertEquals("2d", eqs.get(2).timeBoughtText());

        assertEquals(Duration.ofDays(1), eqs.get(3).timeBought());       // IRON_INGOT 8/8 = 1
        assertEquals("1d", eqs.get(3).timeBoughtText());

        assertEquals(Duration.ofHours(6), eqs.get(4).timeBought());      // COAL 2/8 = ¼ interval
        assertEquals("6h", eqs.get(4).timeBoughtText());
    }

    @Test
    void projectionsCarryCachedEquivalences() {
        UpkeepProjectionCalculator calc = calculator(Duration.ofHours(24),
                Duration.ofHours(24), Duration.ofHours(6));
        Projection p = calc.project(64, 8);
        assertEquals(calc.equivalences(8), p.equivalences());
        assertSame(calc.equivalences(8), calc.equivalences(8)); // cached instance
    }

    // ── Effective rate: tier base + below-tier surcharge (mirrors TickTask) ──

    @Test
    void effectiveSurchargeCanDropProtectedIntoGrace() {
        // WardUpkeepTickTask charges base + surcharge×blocks. A balance that paid
        // exactly one interval at the base rate (8) cannot cover the next charge
        // once the effective rate rises to 12 (8 + 2×2 gated blocks) → GRACIA.
        var calc = calculator(Duration.ofHours(24), Duration.ofHours(24), Duration.ofHours(6));
        assertEquals(State.PROTEGIDO, calc.project(8, 8).state());

        Projection effective = calc.project(8, 12);
        assertEquals(State.GRACIA, effective.state());
        assertEquals(Duration.ofHours(16), effective.timeRemaining()); // 24h × 8/12
        assertEquals("16h", effective.timeRemainingText());
    }

    @Test
    void effectiveSurchargeCanDropProtectedIntoPorVencer() {
        // Base rate 12: 24 u → 48h coverage ≥ warning 44h → PROTEGIDO.
        // Effective rate 16 (surcharge): coverage falls to 36h < expiring 40h
        // while still covering one full interval (24 ≥ 16) → POR_VENCER.
        var calc = calculator(Duration.ofHours(24), Duration.ofHours(44), Duration.ofHours(40));
        assertEquals(State.PROTEGIDO, calc.project(24, 12).state());
        Projection p = calc.project(24, 16);
        assertEquals(State.POR_VENCER, p.state());
        assertEquals(Duration.ofHours(36), p.timeRemaining());
        assertEquals("1d 12h", p.timeRemainingText());
        assertEquals(1, p.intervalsRemaining());
    }

    @Test
    void equivalencesCoverEveryConfiguredMaterialAtEffectiveRate() {
        // Rate 12 u/intervalo over 24h — every material in the config map must be
        // present (COAL included) with its time recalculated at the passed rate.
        List<Equivalence> eqs = calculator(Duration.ofHours(24), Duration.ofHours(24),
                Duration.ofHours(6)).equivalences(12);

        assertEquals(List.of("DIAMOND", "EMERALD", "GOLD_INGOT", "IRON_INGOT", "COAL"),
                eqs.stream().map(Equivalence::materialName).toList());
        assertEquals(Duration.ofHours(128), eqs.get(0).timeBought()); // DIAMOND 64/12 → 5⅓ intervals
        assertEquals("5d 8h", eqs.get(0).timeBoughtText());
        assertEquals(Duration.ofDays(4), eqs.get(1).timeBought());    // EMERALD 48/12
        assertEquals(Duration.ofHours(32), eqs.get(2).timeBought());  // GOLD_INGOT 16/12 → 1d 8h
        assertEquals("1d 8h", eqs.get(2).timeBoughtText());
        assertEquals(Duration.ofHours(16), eqs.get(3).timeBought());  // IRON_INGOT 8/12
        assertEquals("16h", eqs.get(3).timeBoughtText());
        assertEquals(Duration.ofHours(4), eqs.get(4).timeBought());   // COAL 2/12
        assertEquals("4h", eqs.get(4).timeBoughtText());
    }

    // ── Humanizer edges ───────────────────────────────────────────────────────

    @Test
    void humanizeCompactSpanish() {
        assertEquals("6d 12h", UpkeepProjectionCalculator.humanize(Duration.ofHours(156)));
        assertEquals("3h 20m", UpkeepProjectionCalculator.humanize(Duration.ofMinutes(200)));
        assertEquals("2d", UpkeepProjectionCalculator.humanize(Duration.ofDays(2)));
        assertEquals("45m", UpkeepProjectionCalculator.humanize(Duration.ofMinutes(45)));
        assertEquals("0m", UpkeepProjectionCalculator.humanize(Duration.ZERO));
        assertEquals("0m", UpkeepProjectionCalculator.humanize(null));
    }

    // ── Label resolver propagation ────────────────────────────────────────────

    @Test
    void labelResolverFeedsEquivalenceLabels() {
        var calc = new UpkeepProjectionCalculator(Duration.ofHours(24), Duration.ofHours(24),
                Duration.ofHours(6), DEFAULT_MATERIALS,
                Function.identity());
        assertEquals("DIAMOND", calc.equivalences(8).get(0).label());
        var es = new UpkeepProjectionCalculator(Duration.ofHours(24), Duration.ofHours(24),
                Duration.ofHours(6), DEFAULT_MATERIALS, name -> "Diamante");
        assertEquals("Diamante", es.equivalences(8).get(0).label());
    }
}
