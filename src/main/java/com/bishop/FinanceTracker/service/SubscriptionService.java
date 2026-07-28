package com.bishop.FinanceTracker.service;

import com.bishop.FinanceTracker.model.domain.Subscription;
import com.bishop.FinanceTracker.model.domain.SubscriptionPriceHistory;
import com.bishop.FinanceTracker.model.domain.Transaction;
import com.bishop.FinanceTracker.model.json.SubscriptionDashboardResponse;
import com.bishop.FinanceTracker.model.json.SubscriptionRequest;
import com.bishop.FinanceTracker.model.recurring.RecurringCandidate;
import com.bishop.FinanceTracker.repository.SubscriptionPriceHistoryRepository;
import com.bishop.FinanceTracker.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The unified store + logic for recurring commitments (feature doc §2A.4):
 * user-entered bills AND confirmed statistically-detected recurrings live in one
 * {@code subscriptions} table. Supersedes the former manual-bills service.
 *
 * <ul>
 *   <li>CRUD for the Bill Calendar (kind=bill) and Subscription dashboard (kind=subscription).</li>
 *   <li>{@link #tryMatchAndMarkPaid} — on every new transaction, mark the matching
 *       commitment paid for that period (grace-window) and record price history.</li>
 *   <li>{@link #confirmFromDetection} — promote a detected candidate into the table.</li>
 *   <li>Automatic cancel-candidate signals (§2A.4.3): price creep and dormant
 *       (no recent payment). No manual usage input in v1.</li>
 *   <li>{@link #dashboard} — normalised monthly/yearly totals, budget, breakdowns.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private static final ZoneId ZONE = ZoneId.of("Australia/Sydney");
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final String EXPENSE = "EXPENSE";

    // Amount match tolerance — bills vary slightly month to month. Bypassed when amountVariable.
    private static final double AMOUNT_TOLERANCE_PCT = 0.10;
    private static final double AMOUNT_TOLERANCE_MIN = 2.0;

    // Grace window around a due date (handles rent paid a few days early in the prior month).
    private static final int GRACE_DAYS_BEFORE = 5;
    private static final int GRACE_DAYS_AFTER = 3;

    // Cancel-candidate thresholds (§2A.4.3).
    private static final double PRICE_CREEP_PCT = 0.15;   // 15%+ rise over history
    private static final int DORMANT_PERIODS = 2;         // no payment in 2+ periods

    static final String KIND_BILL = "bill";
    static final String KIND_SUBSCRIPTION = "subscription";
    static final String STATUS_ACTIVE = "active";
    static final String STATUS_TRIAL = "trial";
    static final String STATUS_CANCELLED = "cancelled";
    static final String STATUS_PAUSED = "paused";

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionPriceHistoryRepository priceHistoryRepository;
    private final UserSettingsService userSettingsService;

    // ── reads ────────────────────────────────────────────────────────────────

    /** All commitments as UI candidates (with price history + cancel signals). */
    public List<RecurringCandidate> getAll() {
        List<Subscription> all = subscriptionRepository.findAll();
        Map<Long, List<String>> cancel = computeCancelSignals(all);
        return all.stream().map(s -> toCandidate(s, cancel.get(s.getId()))).collect(Collectors.toList());
    }

    /** Normalised keys of persisted subscriptions, for detection dedupe/annotation. */
    public Map<String, Long> confirmedKeyToId() {
        Map<String, Long> map = new HashMap<>();
        for (Subscription s : subscriptionRepository.findAll()) {
            if (s.getNormalizedKey() != null && !s.getNormalizedKey().isBlank()) {
                map.putIfAbsent(s.getNormalizedKey(), s.getId());
            }
        }
        return map;
    }

    // ── create / update / delete ──────────────────────────────────────────────

    public RecurringCandidate create(SubscriptionRequest req) {
        if (req.getName() == null || req.getName().isBlank()) {
            throw new IllegalArgumentException("Name cannot be empty");
        }
        BigDecimal amount = parseAmount(req.getAmount());

        Integer dueDay = null;
        if (req.getDueDate() != null && !req.getDueDate().isBlank()) {
            dueDay = parseDate(req.getDueDate(), "due date").getDayOfMonth();
        }

        String kind = normalizeKind(req.getKind(), KIND_BILL);
        String cycle = normalizeCycle(req.getBillingCycle());
        String status = req.getStatus() != null && !req.getStatus().isBlank()
                ? req.getStatus().trim()
                : (isBlank(req.getTrialEndDate()) ? STATUS_ACTIVE : STATUS_TRIAL);

        Subscription sub = Subscription.builder()
                .source("manual")
                .kind(kind)
                .status(status)
                .name(req.getName().trim())
                .category(blankToNull(req.getCategory()))
                .payer(blankToNull(req.getPayer()))
                .paymentMethod(blankToNull(req.getPaymentMethod()))
                .url(blankToNull(req.getUrl()))
                .logo(blankToNull(req.getLogo()))
                .billingCycle(cycle)
                .amount(amount)
                .amountVariable(req.isAmountVariable())
                .dueDay(dueDay)
                .trialEndDate(blankToNull(req.getTrialEndDate()))
                .notes(blankToNull(req.getNotes()))
                .paidDates("")
                .createTime(System.currentTimeMillis())
                .build();
        sub.setNextChargeDate(computeNextChargeDate(sub, null));

        Subscription saved = subscriptionRepository.save(sub);
        appendPriceHistory(saved.getId(), LocalDate.now(ZONE), amount);
        log.info("Created subscription '{}' ({}, {}, ${})", saved.getName(), saved.getKind(), saved.getBillingCycle(), amount);
        return toCandidate(saved, null);
    }

    public RecurringCandidate update(Long id, SubscriptionRequest req) {
        Subscription sub = subscriptionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Subscription not found: " + id));

        if (req.getName() != null && !req.getName().isBlank()) sub.setName(req.getName().trim());
        if (req.getCategory() != null) sub.setCategory(blankToNull(req.getCategory()));
        if (req.getKind() != null && !req.getKind().isBlank()) sub.setKind(normalizeKind(req.getKind(), sub.getKind()));
        if (req.getBillingCycle() != null && !req.getBillingCycle().isBlank()) sub.setBillingCycle(normalizeCycle(req.getBillingCycle()));
        if (req.getStatus() != null && !req.getStatus().isBlank()) sub.setStatus(req.getStatus().trim());
        if (req.getPaymentMethod() != null) sub.setPaymentMethod(blankToNull(req.getPaymentMethod()));
        if (req.getPayer() != null) sub.setPayer(blankToNull(req.getPayer()));
        if (req.getUrl() != null) sub.setUrl(blankToNull(req.getUrl()));
        if (req.getLogo() != null) sub.setLogo(blankToNull(req.getLogo()));
        if (req.getTrialEndDate() != null) sub.setTrialEndDate(blankToNull(req.getTrialEndDate()));
        if (req.getNotes() != null) sub.setNotes(blankToNull(req.getNotes()));
        sub.setAmountVariable(req.isAmountVariable());
        if (req.getDueDate() != null && !req.getDueDate().isBlank()) {
            sub.setDueDay(parseDate(req.getDueDate(), "due date").getDayOfMonth());
        }
        if (req.getAmount() != null && !req.getAmount().isBlank()) {
            BigDecimal newAmount = parseAmount(req.getAmount());
            if (sub.getAmount() == null || newAmount.compareTo(sub.getAmount()) != 0) {
                sub.setAmount(newAmount);
                appendPriceHistory(sub.getId(), LocalDate.now(ZONE), newAmount);
            }
        }
        sub.setNextChargeDate(computeNextChargeDate(sub, lastPaid(sub)));

        Subscription saved = subscriptionRepository.save(sub);
        return toCandidate(saved, null);
    }

    public void delete(Long id) {
        if (!subscriptionRepository.existsById(id)) {
            throw new IllegalArgumentException("Subscription not found: " + id);
        }
        priceHistoryRepository.deleteBySubscriptionId(id);
        subscriptionRepository.deleteById(id);
    }

    /** Manually flip a period's paid state (calendar "mark paid/unpaid" override). */
    public RecurringCandidate setPaid(Long id, String isoDate, boolean paid) {
        Subscription sub = subscriptionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Subscription not found: " + id));
        LocalDate date = parseDate(isoDate, "date");
        String period = periodKeyFor(date, sub);
        List<String> dates = parseDates(sub.getPaidDates());
        if (paid) {
            boolean has = period != null && dates.stream()
                    .anyMatch(d -> Objects.equals(period, periodKeyFor(LocalDate.parse(d), sub)));
            if (!has) dates.add(date.format(ISO));
        } else {
            dates.removeIf(d -> Objects.equals(period, periodKeyFor(LocalDate.parse(d), sub)));
        }
        sub.setPaidDates(serializeDates(dates));
        return toCandidate(subscriptionRepository.save(sub), null);
    }

    // ── detection convergence ─────────────────────────────────────────────────

    /** Promote a detected recurring candidate into the persisted table (§2A.4.2). Idempotent by key. */
    public RecurringCandidate confirmFromDetection(RecurringCandidate cand) {
        if (cand.getKey() == null || cand.getKey().isBlank()) {
            throw new IllegalArgumentException("Candidate key required to confirm");
        }
        List<Subscription> existing = subscriptionRepository.findByNormalizedKey(cand.getKey());
        if (!existing.isEmpty()) {
            return toCandidate(existing.get(0), null); // already confirmed
        }

        BigDecimal amount = cand.getExpectedAmount() != null ? cand.getExpectedAmount()
                : (cand.getAverageAmount() != null ? cand.getAverageAmount() : BigDecimal.ZERO);
        String kind = cand.isBill() ? KIND_BILL : KIND_SUBSCRIPTION;

        Subscription sub = Subscription.builder()
                .source("detected")
                .confidence(cand.getConfidence())
                .kind(kind)
                .status(STATUS_ACTIVE)
                .name(cand.getName())
                .normalizedKey(cand.getKey())
                .category(cand.getCategory())
                .billingCycle(normalizeCycle(cand.getCadence()))
                .amount(amount)
                .amountVariable(cand.isAmountVariable())
                .dueDay(cand.getPredictedDay())
                .nextChargeDate(cand.getNextPredictedDate())
                .trialEndDate(null)
                .paidDates(cand.getChargeDates() == null ? "" : String.join(",", cand.getChargeDates()))
                .createTime(System.currentTimeMillis())
                .build();

        Subscription saved = subscriptionRepository.save(sub);
        appendPriceHistory(saved.getId(),
                cand.getLastDate() != null ? parseDate(cand.getLastDate(), "last date") : LocalDate.now(ZONE),
                amount);
        log.info("Confirmed detected recurring '{}' → subscription id={}", cand.getName(), saved.getId());
        return toCandidate(saved, null);
    }

    // ── payment matching ──────────────────────────────────────────────────────

    /** On every new transaction: mark the matching commitment paid for its period + record price history. */
    public void tryMatchAndMarkPaid(Transaction transaction) {
        for (Subscription sub : subscriptionRepository.findAll()) {
            if (STATUS_CANCELLED.equals(sub.getStatus())) continue;
            if (!matches(sub, transaction)) continue;

            LocalDate txDate = toLocalDate(transaction);
            String period = periodKeyFor(txDate, sub);
            if (period == null) continue;

            List<String> paid = parseDates(sub.getPaidDates());
            boolean alreadyPaid = paid.stream()
                    .anyMatch(d -> Objects.equals(period, periodKeyFor(LocalDate.parse(d), sub)));
            if (alreadyPaid) continue;

            paid.add(txDate.format(ISO));
            sub.setPaidDates(serializeDates(paid));
            sub.setNextChargeDate(computeNextChargeDate(sub, txDate));
            subscriptionRepository.save(sub);
            appendPriceHistoryIfChanged(sub, txDate, transaction.getAmount());
            log.info("Matched transaction id={} ({}, ${}) to subscription '{}' — paid for {}",
                    transaction.getTransactionId(), transaction.getBusinessName(), transaction.getAmount(),
                    sub.getName(), period);
        }
    }

    /**
     * Replace the set of manually-linked transactions for a commitment (§2A.5) and
     * re-derive amount (median), due day (mode), billing cycle (from gaps) and next
     * charge from them — so linking the real charges fixes a mis-detected cadence.
     *
     * @param desired     transactions the user wants linked (the new full set)
     * @param previously  transactions that were linked before (to unmark their periods)
     */
    public RecurringCandidate setLinkedTransactions(Long id, List<Transaction> desired, List<Transaction> previously) {
        Subscription sub = subscriptionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Subscription not found: " + id));

        List<String> paid = parseDates(sub.getPaidDates());
        // Drop paid dates contributed by the previously-linked transactions.
        Set<String> prevDates = previously.stream()
                .map(t -> toLocalDate(t).format(ISO))
                .collect(Collectors.toSet());
        paid.removeIf(prevDates::contains);

        // Add the desired linked dates, deduped by billing period.
        Set<String> periodsSeen = paid.stream()
                .map(d -> periodKeyFor(LocalDate.parse(d), sub))
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        desired.stream()
                .sorted(Comparator.comparing(Transaction::getTransactionDateTime))
                .forEach(t -> {
                    LocalDate d = toLocalDate(t);
                    String period = periodKeyFor(d, sub);
                    if (period == null || periodsSeen.add(period)) {
                        paid.add(d.format(ISO));
                    }
                });
        sub.setPaidDates(serializeDates(paid));

        sub.setLinkedTransactionIds(desired.stream()
                .map(Transaction::getTransactionId)
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .collect(Collectors.joining(",")));

        // Re-derive from the linked charges.
        List<LocalDate> dates = desired.stream().map(SubscriptionService::toLocalDate).sorted().collect(Collectors.toList());
        if (!desired.isEmpty()) {
            BigDecimal median = medianAmount(desired);
            if (median != null && (sub.getAmount() == null || median.compareTo(sub.getAmount()) != 0)) {
                sub.setAmount(median);
                appendPriceHistory(sub.getId(), dates.get(dates.size() - 1), median);
            }
            sub.setDueDay(modeDayOfMonth(dates));
            String cycle = cycleFromGaps(dates);
            if (cycle != null) {
                sub.setBillingCycle(cycle);
            }
        }
        sub.setNextChargeDate(computeNextChargeDate(sub, lastPaid(sub)));

        Subscription saved = subscriptionRepository.save(sub);
        log.info("Linked {} transaction(s) to subscription '{}' — re-derived {} @ ${}",
                desired.size(), saved.getName(), saved.getBillingCycle(), saved.getAmount());
        return toCandidate(saved, null);
    }

    /** Resolve desired/previously-linked transactions from ids against the full set, then link. */
    public RecurringCandidate setLinkedTransactions(Long id, Set<Long> desiredIds, List<Transaction> allTransactions) {
        Subscription sub = subscriptionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Subscription not found: " + id));
        Set<Long> prevIds = new java.util.HashSet<>(parseLongCsv(sub.getLinkedTransactionIds()));
        List<Transaction> desired = allTransactions.stream()
                .filter(t -> t.getTransactionId() != null && desiredIds.contains(t.getTransactionId()))
                .collect(Collectors.toList());
        List<Transaction> previously = allTransactions.stream()
                .filter(t -> t.getTransactionId() != null && prevIds.contains(t.getTransactionId()))
                .collect(Collectors.toList());
        return setLinkedTransactions(id, desired, previously);
    }

    /** Scan existing history for a commitment just added, so already-paid periods show immediately. */
    public RecurringCandidate backfillFromHistory(Long id, List<Transaction> transactions) {
        Subscription sub = subscriptionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Subscription not found: " + id));

        List<String> paid = parseDates(sub.getPaidDates());
        Set<String> periodsSeen = paid.stream()
                .map(d -> periodKeyFor(LocalDate.parse(d), sub))
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        transactions.stream()
                .filter(t -> matches(sub, t))
                .sorted(Comparator.comparing(Transaction::getTransactionDateTime))
                .forEach(t -> {
                    LocalDate txDate = toLocalDate(t);
                    String period = periodKeyFor(txDate, sub);
                    if (period != null && periodsSeen.add(period)) {
                        paid.add(txDate.format(ISO));
                        appendPriceHistoryIfChanged(sub, txDate, t.getAmount());
                    }
                });

        sub.setPaidDates(serializeDates(paid));
        sub.setNextChargeDate(computeNextChargeDate(sub, lastPaid(sub)));
        Subscription updated = subscriptionRepository.save(sub);
        log.info("Backfilled subscription '{}' — {} payment(s) on record", updated.getName(), paid.size());
        return toCandidate(updated, null);
    }

    // ── dashboard ─────────────────────────────────────────────────────────────

    /** Subscription dashboard aggregation (§2A.3) — scoped to discretionary subscriptions. */
    public SubscriptionDashboardResponse dashboard() {
        List<Subscription> all = subscriptionRepository.findAll();
        Map<Long, List<String>> cancel = computeCancelSignals(all);

        List<Subscription> subs = all.stream()
                .filter(s -> KIND_SUBSCRIPTION.equals(s.getKind()))
                .collect(Collectors.toList());
        List<Subscription> active = subs.stream().filter(this::isActive).collect(Collectors.toList());

        BigDecimal monthlyTotal = active.stream()
                .map(this::monthlyEquivalent)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal yearlyTotal = monthlyTotal.multiply(BigDecimal.valueOf(12)).setScale(2, RoundingMode.HALF_UP);

        BigDecimal budget = userSettingsService.getSubscriptionBudget();
        BigDecimal remaining = budget.compareTo(BigDecimal.ZERO) > 0
                ? budget.subtract(monthlyTotal).setScale(2, RoundingMode.HALF_UP) : null;

        List<SubscriptionDashboardResponse.Breakdown> byCategory = breakdown(active, s ->
                s.getCategory() == null || s.getCategory().isBlank() ? "Uncategorised" : s.getCategory());
        List<SubscriptionDashboardResponse.Breakdown> byPaymentMethod = breakdown(active, s ->
                s.getPaymentMethod() == null || s.getPaymentMethod().isBlank() ? "Unspecified" : s.getPaymentMethod());

        LocalDate today = LocalDate.now(ZONE);
        LocalDate horizon = today.plusDays(30);
        List<RecurringCandidate> upcoming = active.stream()
                .filter(s -> s.getNextChargeDate() != null)
                .filter(s -> {
                    LocalDate n = tryParse(s.getNextChargeDate());
                    return n != null && !n.isBefore(today) && !n.isAfter(horizon);
                })
                .sorted(Comparator.comparing(Subscription::getNextChargeDate))
                .map(s -> toCandidate(s, cancel.get(s.getId())))
                .collect(Collectors.toList());

        List<RecurringCandidate> cancelCandidates = subs.stream()
                .filter(s -> cancel.getOrDefault(s.getId(), List.of()).size() > 0)
                .map(s -> toCandidate(s, cancel.get(s.getId())))
                .collect(Collectors.toList());

        return SubscriptionDashboardResponse.builder()
                .monthlyTotal(monthlyTotal)
                .yearlyTotal(yearlyTotal)
                .monthlyBudget(budget)
                .monthlyRemaining(remaining)
                .activeCount(active.size())
                .cancelledCount((int) subs.stream().filter(s -> STATUS_CANCELLED.equals(s.getStatus())).count())
                .byCategory(byCategory)
                .byPaymentMethod(byPaymentMethod)
                .upcoming(upcoming)
                .cancelCandidates(cancelCandidates)
                .build();
    }

    private List<SubscriptionDashboardResponse.Breakdown> breakdown(
            List<Subscription> subs, java.util.function.Function<Subscription, String> keyFn) {
        Map<String, List<Subscription>> grouped = subs.stream().collect(Collectors.groupingBy(keyFn));
        return grouped.entrySet().stream()
                .map(e -> SubscriptionDashboardResponse.Breakdown.builder()
                        .label(e.getKey())
                        .monthly(e.getValue().stream().map(this::monthlyEquivalent)
                                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP))
                        .count(e.getValue().size())
                        .build())
                .sorted(Comparator.comparing(SubscriptionDashboardResponse.Breakdown::getMonthly).reversed())
                .collect(Collectors.toList());
    }

    // ── cancel-candidate signals (automatic only, §2A.4.3) ─────────────────────

    private Map<Long, List<String>> computeCancelSignals(List<Subscription> all) {
        Map<Long, List<String>> result = new HashMap<>();

        LocalDate today = LocalDate.now(ZONE);
        for (Subscription s : all) {
            if (!KIND_SUBSCRIPTION.equals(s.getKind())) continue;
            List<String> reasons = new ArrayList<>();

            List<SubscriptionPriceHistory> hist = priceHistoryRepository.findBySubscriptionIdOrderByEffectiveDateAsc(s.getId());
            if (hist.size() >= 2) {
                double first = hist.get(0).getAmount().doubleValue();
                double last = hist.get(hist.size() - 1).getAmount().doubleValue();
                if (first > 0 && (last - first) / first >= PRICE_CREEP_PCT) {
                    long pct = Math.round((last - first) / first * 100);
                    reasons.add("Price up " + pct + "% since " + hist.get(0).getEffectiveDate());
                }
            }

            LocalDate last = lastPaid(s);
            if (isActive(s) && last != null) {
                long days = ChronoUnit.DAYS.between(last, today);
                int period = periodDays(cycle(s));
                if (period > 0 && days > (long) period * DORMANT_PERIODS) {
                    reasons.add("No payment in " + (days / 30) + " month(s)");
                }
            }

            if (!reasons.isEmpty()) result.put(s.getId(), reasons);
        }
        return result;
    }

    // ── mapping ───────────────────────────────────────────────────────────────

    private RecurringCandidate toCandidate(Subscription s, List<String> cancelReasons) {
        List<String> paid = parseDates(s.getPaidDates());
        List<RecurringCandidate.PricePoint> priceHistory =
                priceHistoryRepository.findBySubscriptionIdOrderByEffectiveDateAsc(s.getId()).stream()
                        .map(p -> RecurringCandidate.PricePoint.builder()
                                .date(p.getEffectiveDate()).amount(p.getAmount()).build())
                        .collect(Collectors.toList());

        boolean isBill = KIND_BILL.equals(s.getKind());
        return RecurringCandidate.builder()
                .key(s.getNormalizedKey() != null ? s.getNormalizedKey() : "SUB-" + s.getId())
                .name(s.getName())
                .rawNames(List.of(s.getName()))
                .cadence(s.getBillingCycle())
                .confidence(s.getConfidence() != null ? s.getConfidence() : 1.0)
                .category(s.getCategory())
                .expectedAmount(s.getAmount())
                .averageAmount(s.getAmount())
                .monthlyEquivalent(monthlyEquivalent(s))
                .predictedDay(s.getDueDay())
                .nextPredictedDate(s.getNextChargeDate())
                .chargeDates(paid)
                .occurrences(paid.size())
                .bill(isBill)
                .subscription(KIND_SUBSCRIPTION.equals(s.getKind()))
                .dismissed(false)
                .amountVariable(Boolean.TRUE.equals(s.getAmountVariable()))
                .knowledgeType(isBill ? "bill" : "subscription")
                .knowledgeSource(s.getSource())
                .manual("manual".equals(s.getSource()))
                .manualBillId(s.getId())
                .subscriptionId(s.getId())
                .confirmed(true)
                .kind(s.getKind())
                .status(s.getStatus())
                .paymentMethod(s.getPaymentMethod())
                .payer(s.getPayer())
                .url(s.getUrl())
                .logo(s.getLogo())
                .billingCycle(s.getBillingCycle())
                .trialEndDate(s.getTrialEndDate())
                .notes(s.getNotes())
                .priceHistory(priceHistory)
                .cancelCandidate(cancelReasons != null && !cancelReasons.isEmpty())
                .cancelReasons(cancelReasons != null ? cancelReasons : List.of())
                .linkedTransactionIds(parseLongCsv(s.getLinkedTransactionIds()))
                .build();
    }

    // ── matching helpers ──────────────────────────────────────────────────────

    private static boolean isExpenseLike(Transaction t) {
        String type = t.getTransactionType();
        if (type != null && !EXPENSE.equalsIgnoreCase(type)) return false;
        return t.getBusinessName() != null && !t.getBusinessName().isBlank()
                && t.getAmount() != null && t.getTransactionDateTime() != null;
    }

    private boolean matches(Subscription sub, Transaction t) {
        if (!isExpenseLike(t)) return false;
        String business = t.getBusinessName().trim().toLowerCase();
        if (!nameMatches(business, sub.getName())) return false;
        return Boolean.TRUE.equals(sub.getAmountVariable()) || amountMatches(t.getAmount(), sub.getAmount());
    }

    private static LocalDate toLocalDate(Transaction t) {
        return Instant.ofEpochMilli(t.getTransactionDateTime()).atZone(ZONE).toLocalDate();
    }

    private static boolean nameMatches(String business, String name) {
        if (name == null || name.isBlank()) return false;
        String n = name.trim().toLowerCase();
        return business.contains(n) || n.contains(business);
    }

    private static boolean amountMatches(BigDecimal actual, BigDecimal expected) {
        if (expected == null) return false;
        double diff = Math.abs(actual.doubleValue() - expected.doubleValue());
        double tolerance = Math.max(AMOUNT_TOLERANCE_MIN, expected.doubleValue() * AMOUNT_TOLERANCE_PCT);
        return diff <= tolerance;
    }

    // ── period / cadence maths ────────────────────────────────────────────────

    private String cycle(Subscription s) {
        return s.getBillingCycle() == null ? "monthly" : s.getBillingCycle();
    }

    /** Which billing period a date belongs to. Monthly uses a calendar grace-window; others bucket by period length. */
    private String periodKeyFor(LocalDate date, Subscription sub) {
        String cycle = cycle(sub);
        if ("lifetime".equals(cycle)) return "LIFETIME";
        if ("monthly".equals(cycle) && sub.getDueDay() != null) {
            return monthlyPeriod(date, sub.getDueDay());
        }
        int days = periodDays(cycle);
        if (days <= 0) return "LIFETIME";
        long bucket = Math.floorDiv(date.toEpochDay(), days);
        return cycle + "#" + bucket;
    }

    /** yyyy-MM of the due date this payment covers, allowing early/late grace; null if far from any due date. */
    private static String monthlyPeriod(LocalDate date, int dueDay) {
        for (int offset = -1; offset <= 1; offset++) {
            LocalDate anchor = date.withDayOfMonth(1).plusMonths(offset);
            LocalDate due = anchor.withDayOfMonth(Math.min(dueDay, anchor.lengthOfMonth()));
            long diff = ChronoUnit.DAYS.between(date, due);
            if (diff <= GRACE_DAYS_BEFORE && diff >= -GRACE_DAYS_AFTER) {
                return due.getYear() + "-" + String.format("%02d", due.getMonthValue());
            }
        }
        return null;
    }

    private static int periodDays(String cycle) {
        switch (cycle) {
            case "daily": return 1;
            case "weekly": return 7;
            case "fortnightly": return 14;
            case "monthly": return 30;
            case "quarterly": return 91;
            case "semiannual": return 182;
            case "annual": return 365;
            default: return 0; // lifetime / unknown
        }
    }

    private BigDecimal monthlyEquivalent(Subscription s) {
        if (s.getAmount() == null) return BigDecimal.ZERO;
        double a = s.getAmount().doubleValue();
        double monthly;
        switch (cycle(s)) {
            case "daily": monthly = a * 365.0 / 12.0; break;
            case "weekly": monthly = a * 52.0 / 12.0; break;
            case "fortnightly": monthly = a * 26.0 / 12.0; break;
            case "monthly": monthly = a; break;
            case "quarterly": monthly = a / 3.0; break;
            case "semiannual": monthly = a / 6.0; break;
            case "annual": monthly = a / 12.0; break;
            default: monthly = 0.0; break; // lifetime excluded from recurring totals
        }
        return BigDecimal.valueOf(monthly).setScale(2, RoundingMode.HALF_UP);
    }

    /** Next expected charge date as of today, given the last known payment (nullable). */
    private String computeNextChargeDate(Subscription sub, LocalDate lastPaid) {
        String cycle = cycle(sub);
        if ("lifetime".equals(cycle)) return null;
        LocalDate today = LocalDate.now(ZONE);

        if ("monthly".equals(cycle) && sub.getDueDay() != null) {
            LocalDate due = today.withDayOfMonth(Math.min(sub.getDueDay(), today.lengthOfMonth()));
            if (due.isBefore(today)) {
                LocalDate next = today.plusMonths(1);
                due = next.withDayOfMonth(Math.min(sub.getDueDay(), next.lengthOfMonth()));
            }
            return due.format(ISO);
        }

        int days = periodDays(cycle);
        if (days <= 0) return null;
        LocalDate base = lastPaid != null ? lastPaid : today;
        LocalDate next = base.plusDays(days);
        while (next.isBefore(today)) next = next.plusDays(days);
        return next.format(ISO);
    }

    private static BigDecimal medianAmount(List<Transaction> txns) {
        List<BigDecimal> amounts = txns.stream()
                .map(Transaction::getAmount)
                .filter(Objects::nonNull)
                .sorted()
                .collect(Collectors.toList());
        if (amounts.isEmpty()) return null;
        return amounts.get(amounts.size() / 2);
    }

    private static Integer modeDayOfMonth(List<LocalDate> dates) {
        if (dates.isEmpty()) return null;
        Map<Integer, Long> counts = dates.stream()
                .collect(Collectors.groupingBy(LocalDate::getDayOfMonth, Collectors.counting()));
        return counts.entrySet().stream()
                .max(Comparator.comparingLong(Map.Entry::getValue))
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    /** Infer a billing cycle from the median gap between sorted charge dates. */
    private static String cycleFromGaps(List<LocalDate> dates) {
        if (dates.size() < 2) return null;
        List<Long> gaps = new ArrayList<>();
        for (int i = 1; i < dates.size(); i++) {
            gaps.add(ChronoUnit.DAYS.between(dates.get(i - 1), dates.get(i)));
        }
        gaps.sort(Comparator.naturalOrder());
        long median = gaps.get(gaps.size() / 2);
        if (median <= 3) return "daily";
        if (median <= 10) return "weekly";
        if (median <= 20) return "fortnightly";
        if (median <= 45) return "monthly";
        if (median <= 135) return "quarterly";
        if (median <= 250) return "semiannual";
        return "annual";
    }

    private static List<Long> parseLongCsv(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        List<Long> out = new ArrayList<>();
        for (String part : csv.split(",")) {
            try {
                out.add(Long.parseLong(part.trim()));
            } catch (NumberFormatException ignored) {
                // skip malformed
            }
        }
        return out;
    }

    private LocalDate lastPaid(Subscription sub) {
        return parseDates(sub.getPaidDates()).stream()
                .map(SubscriptionService::tryParse)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
    }

    private boolean isActive(Subscription s) {
        return !STATUS_CANCELLED.equals(s.getStatus()) && !STATUS_PAUSED.equals(s.getStatus());
    }

    // ── price history ─────────────────────────────────────────────────────────

    private void appendPriceHistory(Long subscriptionId, LocalDate date, BigDecimal amount) {
        if (amount == null) return;
        priceHistoryRepository.save(SubscriptionPriceHistory.builder()
                .subscriptionId(subscriptionId)
                .effectiveDate(date.format(ISO))
                .amount(amount)
                .build());
    }

    private void appendPriceHistoryIfChanged(Subscription sub, LocalDate date, BigDecimal amount) {
        if (amount == null) return;
        List<SubscriptionPriceHistory> hist = priceHistoryRepository.findBySubscriptionIdOrderByEffectiveDateAsc(sub.getId());
        BigDecimal ref = hist.isEmpty() ? sub.getAmount() : hist.get(hist.size() - 1).getAmount();
        if (ref == null) {
            appendPriceHistory(sub.getId(), date, amount);
            return;
        }
        double diff = Math.abs(amount.doubleValue() - ref.doubleValue());
        double tol = Math.max(AMOUNT_TOLERANCE_MIN, ref.doubleValue() * 0.05);
        if (diff > tol) {
            appendPriceHistory(sub.getId(), date, amount);
        }
    }

    // ── small utils ───────────────────────────────────────────────────────────

    private static BigDecimal parseAmount(String raw) {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("Amount is required");
        BigDecimal amount;
        try {
            amount = new BigDecimal(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid amount: " + raw);
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException("Amount must be positive");
        return amount;
    }

    private static LocalDate parseDate(String raw, String label) {
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid " + label + ": " + raw);
        }
    }

    private static LocalDate tryParse(String raw) {
        try {
            return raw == null || raw.isBlank() ? null : LocalDate.parse(raw.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String normalizeKind(String kind, String fallback) {
        if (kind == null || kind.isBlank()) return fallback;
        String k = kind.trim().toLowerCase();
        return (KIND_BILL.equals(k) || KIND_SUBSCRIPTION.equals(k) || "other".equals(k)) ? k : fallback;
    }

    private static final Set<String> CYCLES = Set.of(
            "daily", "weekly", "fortnightly", "monthly", "quarterly", "semiannual", "annual", "lifetime");

    private static String normalizeCycle(String cycle) {
        if (cycle == null || cycle.isBlank()) return "monthly";
        String c = cycle.trim().toLowerCase().replace("semi-annual", "semiannual");
        return CYCLES.contains(c) ? c : "monthly";
    }

    private static List<String> parseDates(String csv) {
        if (csv == null || csv.isBlank()) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(csv.split(",")));
    }

    private static String serializeDates(List<String> dates) {
        return dates.stream().filter(d -> d != null && !d.isBlank()).collect(Collectors.joining(","));
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
