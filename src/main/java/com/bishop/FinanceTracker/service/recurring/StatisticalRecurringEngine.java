package com.bishop.FinanceTracker.service.recurring;

import com.bishop.FinanceTracker.model.domain.CustomMerchant;
import com.bishop.FinanceTracker.model.domain.Transaction;
import com.bishop.FinanceTracker.model.recurring.Cadence;
import com.bishop.FinanceTracker.model.recurring.CadenceResult;
import com.bishop.FinanceTracker.model.recurring.MerchantClass;
import com.bishop.FinanceTracker.model.recurring.NormalizedMerchant;
import com.bishop.FinanceTracker.model.recurring.RecurringCandidate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Pure-maths recurring detection engine (feature doc §2.1). Groups transactions
 * by normalised merchant, runs phase-space cadence detection, applies an amount
 * band, blends a knowledge-base prior into a confidence score, and predicts the
 * next charge. No ML — but sits behind {@link RecurringDetectionEngine} so an ML
 * engine can replace it wholesale later.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StatisticalRecurringEngine implements RecurringDetectionEngine {

    private static final ZoneId ZONE = ZoneId.of("Australia/Sydney");
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;

    /** A group needs at least this many charges to be considered at all. */
    private static final int MIN_OCCURRENCES = 2;
    /** Amount CV below which spend looks like a fixed subscription. */
    private static final double SUBSCRIPTION_CV_THRESHOLD = 0.15;
    /** Amount CV above which we label "variable amount" rather than reject (doc §2.1 step 4). */
    private static final double AMOUNT_VARIABLE_CV = 0.20;
    /** Max day-of-month spread for a monthly-ish charge to count as a scheduled bill. */
    private static final double MAX_DAY_STDDEV = 10;
    /** Last charge must be within period × this to still be "active" (cadence-aware recency). */
    private static final double RECENCY_MULTIPLIER = 1.8;
    /** Ignore cheap noise from unknown merchants (KB-known subs bypass this floor). */
    private static final double MIN_AVG_AMOUNT_UNKNOWN = 5.0;

    private final MerchantNormalizer normalizer;
    private final CadenceDetector cadenceDetector;
    private final MerchantKnowledgeBase knowledgeBase;

    @Override
    public String name() {
        return "phase-space";
    }

    @Override
    public List<RecurringCandidate> detect(List<Transaction> transactions, List<CustomMerchant> customRules) {
        if (transactions == null || transactions.isEmpty()) {
            return List.of();
        }

        Map<String, Group> groups = new LinkedHashMap<>();
        for (Transaction t : transactions) {
            if (t.getBusinessName() == null || t.getBusinessName().isBlank()) {
                continue;
            }
            NormalizedMerchant nm = normalizer.normalize(t.getBusinessName());
            if (nm.key().isBlank()) {
                continue;
            }
            groups.computeIfAbsent(nm.key(), k -> new Group(nm)).add(t);
        }

        LocalDate today = LocalDate.now(ZONE);
        List<RecurringCandidate> out = new ArrayList<>();
        for (Group g : groups.values()) {
            RecurringCandidate c = evaluate(g, customRules, today);
            if (c != null) {
                out.add(c);
            }
        }

        out.sort(Comparator
                .comparingDouble(RecurringCandidate::getConfidence).reversed()
                .thenComparing(rc -> rc.getTotalPaid() == null ? BigDecimal.ZERO : rc.getTotalPaid(),
                        Comparator.reverseOrder()));
        return out;
    }

    private RecurringCandidate evaluate(Group g, List<CustomMerchant> customRules, LocalDate today) {
        if (g.txns.size() < MIN_OCCURRENCES) {
            return null;
        }

        MerchantClass knowledge = knowledgeBase.classify(g.representativeRawName(), customRules);
        if (knowledge.type() == MerchantClass.Type.NOISE) {
            return null; // hard-exclude everyday noise regardless of pattern
        }
        boolean known = knowledge.isKnown();

        List<LocalDate> dates = g.dates();
        Optional<CadenceResult> cadenceOpt = cadenceDetector.detect(dates);

        // Unknown merchant with no detectable cadence → not recurring.
        if (cadenceOpt.isEmpty() && !known) {
            return null;
        }

        double[] amounts = g.amounts();
        double avg = mean(amounts);
        double expected = trimmedMean(amounts);
        double amountCv = coefficientOfVariation(amounts);

        if (!known && avg < MIN_AVG_AMOUNT_UNKNOWN) {
            return null;
        }

        // Resolve a working cadence: the phase-space result, or a gap-estimate for
        // KB-known merchants that have too few points to score statistically.
        CadenceResult cadenceResult = cadenceOpt.orElseGet(() -> estimateCadence(dates));
        if (cadenceResult == null) {
            return null;
        }
        Cadence cadence = cadenceResult.cadence();
        double periodDays = cadence.periodDays();

        LocalDate first = dates.get(0);
        LocalDate last = dates.get(dates.size() - 1);

        // Cadence-aware recency: a cancelled sub shouldn't linger (doc §2.2).
        long daysSinceLast = ChronoUnit.DAYS.between(last, today);
        if (daysSinceLast > periodDays * RECENCY_MULTIPLIER) {
            return null;
        }

        int[] daysOfMonth = dates.stream().mapToInt(LocalDate::getDayOfMonth).toArray();
        int predictedDom = (int) Math.round(median(toDouble(daysOfMonth)));
        double domStdDev = stdDev(toDouble(daysOfMonth));

        boolean monthlyOrShorter = cadence == Cadence.WEEKLY
                || cadence == Cadence.FORTNIGHTLY
                || cadence == Cadence.MONTHLY;
        boolean isBill = knowledge.type() == MerchantClass.Type.BILL
                || (monthlyOrShorter && domStdDev <= MAX_DAY_STDDEV && cadence != Cadence.WEEKLY
                        && cadence != Cadence.FORTNIGHTLY);
        boolean isSubscription = knowledge.type() == MerchantClass.Type.SUBSCRIPTION
                || (knowledge.type() != MerchantClass.Type.BILL && amountCv < SUBSCRIPTION_CV_THRESHOLD);

        double confidence = confidence(cadenceResult, g.txns.size(), amountCv, monthsRange(first, last), known);

        LocalDate nextDate = predictNext(last, cadence, predictedDom, today);
        boolean domMeaningful = cadence != Cadence.WEEKLY && cadence != Cadence.FORTNIGHTLY;

        return RecurringCandidate.builder()
                .key(g.normalized.key())
                .name(g.normalized.displayName())
                .rawNames(g.distinctRawNames())
                .cadence(cadence.label())
                .cadenceScore(round2(cadenceResult.fitScore()))
                .vectorStrength(round2(cadenceResult.vectorStrength()))
                .confidence(round2(confidence))
                .occurrences(g.txns.size())
                .distinctMonths(g.distinctMonths())
                .monthsRange(monthsRange(first, last))
                .firstDate(first.format(ISO))
                .lastDate(last.format(ISO))
                .predictedDay(domMeaningful ? predictedDom : null)
                .nextPredictedDate(nextDate.format(ISO))
                .chargeDates(dates.stream().map(d -> d.format(ISO)).toList())
                .averageAmount(money(avg))
                .expectedAmount(money(expected))
                .totalPaid(money(sum(amounts)))
                .monthlyEquivalent(money(expected * cadence.perMonth()))
                .amountCv(round2(amountCv))
                .amountVariable(amountCv > AMOUNT_VARIABLE_CV)
                .category(g.topCategory())
                .subscription(isSubscription)
                .bill(isBill)
                .knowledgeType(knowledge.type().name().toLowerCase())
                .knowledgeSource(knowledge.source())
                .build();
    }

    /**
     * Confidence in [0,1]: mostly the phase-space fit, boosted by more
     * occurrences, tighter amounts, a longer series, and a KB prior (doc §2.1
     * step 5 — the prior augments, never replaces, the statistical signal).
     */
    private double confidence(CadenceResult cr, int occurrences, double amountCv, int monthsRange, boolean known) {
        double fit = cr.fitScore();
        double occ = Math.min(1.0, occurrences / 6.0);
        double amountConsistency = 1.0 - Math.min(amountCv, 1.0);
        double series = Math.min(1.0, monthsRange / 12.0);
        double prior = known ? 0.15 : 0.0;
        double score = 0.5 * fit + 0.2 * occ + 0.2 * amountConsistency + 0.1 * series + prior;
        return Math.max(0.0, Math.min(1.0, score));
    }

    /** Roll the next predicted charge forward until it is on/after today. */
    private LocalDate predictNext(LocalDate last, Cadence cadence, int predictedDom, LocalDate today) {
        LocalDate next = addPeriod(last, cadence, predictedDom);
        int guard = 0;
        while (next.isBefore(today) && guard++ < 200) {
            next = addPeriod(next, cadence, predictedDom);
        }
        return next;
    }

    private LocalDate addPeriod(LocalDate from, Cadence cadence, int predictedDom) {
        return switch (cadence) {
            case WEEKLY -> from.plusWeeks(1);
            case FORTNIGHTLY -> from.plusWeeks(2);
            case MONTHLY -> snapMonths(from, 1, predictedDom);
            case QUARTERLY -> snapMonths(from, 3, predictedDom);
            case SEMI_ANNUAL -> snapMonths(from, 6, predictedDom);
            case ANNUAL -> snapMonths(from, 12, predictedDom);
        };
    }

    /** Add months and snap to the target day-of-month, clamping to short months (Firefly-style). */
    private LocalDate snapMonths(LocalDate from, int months, int day) {
        LocalDate base = from.plusMonths(months);
        int dom = Math.min(Math.max(day, 1), base.lengthOfMonth());
        return base.withDayOfMonth(dom);
    }

    /** Fallback cadence when phase-space can't score (few points): nearest bucket to the median gap. */
    private CadenceResult estimateCadence(List<LocalDate> dates) {
        if (dates.size() < 2) {
            return null;
        }
        double[] gaps = new double[dates.size() - 1];
        for (int i = 1; i < dates.size(); i++) {
            gaps[i - 1] = ChronoUnit.DAYS.between(dates.get(i - 1), dates.get(i));
        }
        double medianGap = median(gaps);
        if (medianGap <= 0) {
            return null;
        }
        Cadence best = Cadence.MONTHLY;
        double bestDiff = Double.MAX_VALUE;
        for (Cadence c : Cadence.values()) {
            double diff = Math.abs(c.periodDays() - medianGap);
            if (diff < bestDiff) {
                bestDiff = diff;
                best = c;
            }
        }
        // Low, honest score — this path exists to surface KB-known subs, not to assert periodicity.
        return new CadenceResult(best, 0.0, 0.5, 0.0, 0.35);
    }

    // ── stats helpers ──────────────────────────────────────────────────────

    private static double mean(double[] a) {
        if (a.length == 0) {
            return 0;
        }
        double s = 0;
        for (double v : a) {
            s += v;
        }
        return s / a.length;
    }

    private static double sum(double[] a) {
        double s = 0;
        for (double v : a) {
            s += v;
        }
        return s;
    }

    private static double stdDev(double[] a) {
        if (a.length < 2) {
            return 0;
        }
        double m = mean(a);
        double s = 0;
        for (double v : a) {
            s += (v - m) * (v - m);
        }
        return Math.sqrt(s / a.length);
    }

    /** CV = std/mean, guarding NaN/zero-mean (fixes UI-N7). */
    private static double coefficientOfVariation(double[] a) {
        if (a.length < 2) {
            return 0;
        }
        double m = mean(a);
        if (m == 0) {
            return 1;
        }
        return stdDev(a) / Math.abs(m);
    }

    private static double median(double[] a) {
        if (a.length == 0) {
            return 0;
        }
        double[] sorted = a.clone();
        Arrays.sort(sorted);
        int mid = sorted.length / 2;
        return sorted.length % 2 == 1 ? sorted[mid] : (sorted[mid - 1] + sorted[mid]) / 2.0;
    }

    /** Mean after dropping the top and bottom 10% to survive one-off spikes. */
    private static double trimmedMean(double[] a) {
        if (a.length < 4) {
            return mean(a);
        }
        double[] sorted = a.clone();
        Arrays.sort(sorted);
        int trim = (int) Math.floor(sorted.length * 0.1);
        double s = 0;
        int count = 0;
        for (int i = trim; i < sorted.length - trim; i++) {
            s += sorted[i];
            count++;
        }
        return count == 0 ? mean(a) : s / count;
    }

    private static double[] toDouble(int[] a) {
        double[] out = new double[a.length];
        for (int i = 0; i < a.length; i++) {
            out[i] = a[i];
        }
        return out;
    }

    private static int monthsRange(LocalDate first, LocalDate last) {
        return (last.getYear() - first.getYear()) * 12 + (last.getMonthValue() - first.getMonthValue()) + 1;
    }

    private static BigDecimal money(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    // ── per-merchant grouping ──────────────────────────────────────────────

    private static final class Group {
        private final NormalizedMerchant normalized;
        private final List<Transaction> txns = new ArrayList<>();

        Group(NormalizedMerchant normalized) {
            this.normalized = normalized;
        }

        void add(Transaction t) {
            txns.add(t);
        }

        List<LocalDate> dates() {
            return txns.stream()
                    .map(t -> Instant.ofEpochMilli(t.getTransactionDateTime()).atZone(ZONE).toLocalDate())
                    .sorted()
                    .toList();
        }

        double[] amounts() {
            return txns.stream()
                    .mapToDouble(t -> t.getAmount() == null ? 0.0 : t.getAmount().doubleValue())
                    .toArray();
        }

        /** Most frequent raw business name — the best input to the knowledge base. */
        String representativeRawName() {
            Map<String, Integer> counts = new LinkedHashMap<>();
            for (Transaction t : txns) {
                counts.merge(t.getBusinessName(), 1, Integer::sum);
            }
            return counts.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(normalized.displayName());
        }

        List<String> distinctRawNames() {
            return txns.stream().map(Transaction::getBusinessName).distinct().sorted().toList();
        }

        int distinctMonths() {
            return (int) txns.stream()
                    .map(t -> {
                        LocalDate d = Instant.ofEpochMilli(t.getTransactionDateTime()).atZone(ZONE).toLocalDate();
                        return d.getYear() * 12 + d.getMonthValue();
                    })
                    .distinct()
                    .count();
        }

        String topCategory() {
            Map<String, Integer> counts = new LinkedHashMap<>();
            for (Transaction t : txns) {
                if (t.getCategory() != null) {
                    counts.merge(t.getCategory(), 1, Integer::sum);
                }
            }
            return counts.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(null);
        }
    }
}
