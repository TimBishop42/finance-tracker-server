package com.bishop.FinanceTracker.service.recurring;

import com.bishop.FinanceTracker.model.recurring.Cadence;
import com.bishop.FinanceTracker.model.recurring.CadenceResult;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Phase-space / circular-statistics cadence detector (feature doc §2.1, step 3,
 * after patent US11144935B2). For each candidate period P it maps every
 * transaction's ordinal day to an angle {@code 2π·(day mod P)/P} and measures:
 *
 * <ul>
 *   <li><b>vector strength r</b> — how tightly the angles cluster (0 scattered, 1 periodic),</li>
 *   <li><b>coverage</b> — fraction of cycles between first and last txn that have ≥1 txn,</li>
 *   <li><b>redundancy</b> — fraction of cycles with more than one txn (a noise signal).</li>
 * </ul>
 *
 * The winning period minimises the distance from {@code (r, coverage, redundancy)}
 * to the ideal {@code (1, 1, 0)}. This tolerates day-of-month drift and the odd
 * missed/late payment far better than raw inter-transaction gaps.
 */
@Component
public class PhaseSpaceCadenceDetector implements CadenceDetector {

    /** Need at least this many charges before a period is statistically meaningful. */
    static final int MIN_OCCURRENCES = 3;
    /** ...and they must land on at least this many distinct cycles (guards spurious fits). */
    private static final int MIN_OCCUPIED_CYCLES = 3;

    private static final double MIN_VECTOR_STRENGTH = 0.6;
    private static final double MIN_COVERAGE = 0.6;
    private static final double MIN_FIT = 0.6;

    // Distance weights emphasising clustering and coverage over redundancy.
    private static final double W_R = 0.5;
    private static final double W_COVERAGE = 0.3;
    private static final double W_REDUNDANCY = 0.2;

    private static final double TWO_PI = 2 * Math.PI;

    @Override
    public Optional<CadenceResult> detect(List<LocalDate> dates) {
        if (dates == null || dates.size() < MIN_OCCURRENCES) {
            return Optional.empty();
        }

        long[] ordinals = dates.stream().mapToLong(LocalDate::toEpochDay).sorted().toArray();
        long first = ordinals[0];
        long last = ordinals[ordinals.length - 1];
        long span = last - first;
        if (span <= 0) {
            return Optional.empty();
        }

        CadenceResult best = null;
        for (Cadence cadence : Cadence.values()) {
            double p = cadence.periodDays();
            // Need at least two full cycles of history to trust this period.
            if (span < p) {
                continue;
            }

            double sumCos = 0;
            double sumSin = 0;
            Map<Long, Integer> perCycle = new HashMap<>();
            for (long d : ordinals) {
                double phase = ((d - first) % p) / p;   // 0..1 within the cycle
                double angle = TWO_PI * phase;
                sumCos += Math.cos(angle);
                sumSin += Math.sin(angle);
                // Assign to the nearest ideal cycle (round, not floor) so a payment a
                // day early/late — or a ~365d year vs a 365.24d period — still counts
                // in the right cycle rather than bleeding into the previous one.
                long cycle = Math.round((d - first) / p);
                perCycle.merge(cycle, 1, Integer::sum);
            }

            int n = ordinals.length;
            double r = Math.sqrt(sumCos * sumCos + sumSin * sumSin) / n;

            long totalCycles = Math.round(span / p) + 1;
            long occupied = perCycle.size();
            long multi = perCycle.values().stream().filter(c -> c > 1).count();
            double coverage = (double) occupied / totalCycles;
            double redundancy = (double) multi / totalCycles;

            if (r < MIN_VECTOR_STRENGTH || coverage < MIN_COVERAGE || occupied < MIN_OCCUPIED_CYCLES) {
                continue;
            }

            double dist = Math.sqrt(
                    W_R * sq(1 - r)
                            + W_COVERAGE * sq(1 - coverage)
                            + W_REDUNDANCY * sq(redundancy));
            double fit = 1 - dist; // weights sum to 1, so dist ∈ [0,1]

            if (fit < MIN_FIT) {
                continue;
            }

            if (best == null || fit > best.fitScore()) {
                best = new CadenceResult(cadence, r, coverage, redundancy, fit);
            }
        }

        return Optional.ofNullable(best);
    }

    private static double sq(double x) {
        return x * x;
    }

    /** Exposed for the engine's minimum-occurrence gate. */
    public static int minOccurrences() {
        return MIN_OCCURRENCES;
    }
}
