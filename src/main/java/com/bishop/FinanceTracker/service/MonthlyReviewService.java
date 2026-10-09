package com.bishop.FinanceTracker.service;

import com.bishop.FinanceTracker.model.domain.Subscription;
import com.bishop.FinanceTracker.model.domain.SubscriptionPriceHistory;
import com.bishop.FinanceTracker.model.domain.Transaction;
import com.bishop.FinanceTracker.model.json.MonthlyReviewResponse;
import com.bishop.FinanceTracker.model.json.MonthlyReviewResponse.*;
import com.bishop.FinanceTracker.model.recurring.NormalizedMerchant;
import com.bishop.FinanceTracker.repository.SubscriptionPriceHistoryRepository;
import com.bishop.FinanceTracker.repository.SubscriptionRepository;
import com.bishop.FinanceTracker.service.recurring.MerchantNormalizer;
import com.bishop.FinanceTracker.util.DateUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.reducing;

/**
 * Builds the spending half of the month-in-review (feature register: month-in-review).
 * Months are bucketed by each transaction's calendar date string, so the result
 * doesn't depend on the server's timezone.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MonthlyReviewService {

    static final int AVERAGE_MONTHS = 12;
    /** A category trends UP/DOWN when its 3-month slope exceeds this share of its 3-month mean. */
    static final BigDecimal TREND_THRESHOLD = new BigDecimal("0.10");
    private static final int TOP_N = 5;
    private static final int NEW_MERCHANTS_LIMIT = 10;
    private static final String NO_MERCHANT = "(no merchant)";
    private static final BigDecimal ZERO = cents(BigDecimal.ZERO);

    private final TransactionService transactionService;
    private final AggregationService aggregationService;
    private final WealthReviewService wealthReviewService;
    private final CategoryService categoryService;
    private final UserSettingsService userSettingsService;
    private final MerchantNormalizer merchantNormalizer;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionPriceHistoryRepository priceHistoryRepository;

    /** A transaction with its calendar date and merchant resolved once up front. */
    private record Dated(Transaction tx, LocalDate date, NormalizedMerchant merchant) {
        YearMonth month() {
            return YearMonth.from(date);
        }

        BigDecimal amount() {
            return tx.getAmount();
        }
    }

    public MonthlyReviewResponse review(YearMonth month) {
        long start = System.currentTimeMillis();
        List<Dated> all = transactionService.getAll().stream()
                .map(this::dated)
                .filter(Objects::nonNull)
                .toList();
        List<Dated> cashflow = all.stream()
                .filter(d -> AggregationService.isExpense(d.tx()) || AggregationService.isIncome(d.tx()))
                .toList();
        Map<YearMonth, List<Dated>> byMonth = cashflow.stream().collect(groupingBy(Dated::month));

        YearMonth firstMonth = cashflow.stream().map(Dated::month).min(Comparator.naturalOrder()).orElse(month);
        List<YearMonth> priorMonths = Stream.iterate(month.minusMonths(AVERAGE_MONTHS), m -> m.plusMonths(1))
                .limit(AVERAGE_MONTHS)
                .filter(m -> !m.isBefore(firstMonth))
                .toList();
        List<YearMonth> historyMonths = Stream.concat(priorMonths.stream(), Stream.of(month)).toList();

        List<Dated> current = byMonth.getOrDefault(month, List.of());
        List<Dated> currentExpenses = current.stream().filter(d -> AggregationService.isExpense(d.tx())).toList();

        Totals totals = totals(month, priorMonths, byMonth);
        MonthlyReviewResponse response = new MonthlyReviewResponse(
                month.toString(),
                all.stream().map(Dated::date).max(Comparator.naturalOrder()).orElse(null),
                totals,
                historyMonths.stream()
                        .map(m -> new MonthPoint(m.toString(), spend(byMonth.get(m)), income(byMonth.get(m))))
                        .toList(),
                categories(month, priorMonths, historyMonths, byMonth),
                topMerchants(currentExpenses),
                newMerchants(month, cashflow, currentExpenses),
                currentExpenses.stream()
                        .sorted(Comparator.comparing(Dated::amount).reversed())
                        .limit(TOP_N)
                        .map(d -> new TransactionRow(d.date(), d.tx().getBusinessName(), categoryOf(d), d.amount()))
                        .toList(),
                subscriptionChanges(month),
                new Cumulative(aggregationService.cumulativeSpend(month),
                        aggregationService.cumulativeSpend(month.minusMonths(1))),
                wealthReviewService.review(month, totals.net()));

        log.info("Built monthly review for {} in {} ms", month, System.currentTimeMillis() - start);
        return response;
    }

    /** Null for a row whose date can't be parsed, so one bad row can't sink the review. */
    private Dated dated(Transaction t) {
        LocalDate date = DateUtil.tryParseTransactionDate(t.getTransactionDate());
        return date == null ? null : new Dated(t, date, merchantNormalizer.normalize(t.getBusinessName()));
    }

    private Totals totals(YearMonth month, List<YearMonth> priorMonths, Map<YearMonth, List<Dated>> byMonth) {
        List<Dated> current = byMonth.get(month);
        BigDecimal spend = spend(current);
        BigDecimal income = income(current);
        BigDecimal net = income.subtract(spend);
        BigDecimal savingsRate = income.signum() > 0
                ? net.multiply(BigDecimal.valueOf(100)).divide(income, 1, RoundingMode.HALF_UP)
                : null;
        YearMonth last = month.minusMonths(1);
        return new Totals(spend, income, net, savingsRate, userSettingsService.getMaxSpendValue(),
                spend(byMonth.get(last)), income(byMonth.get(last)),
                average(priorMonths.stream().map(m -> spend(byMonth.get(m))).toList()),
                average(priorMonths.stream().map(m -> income(byMonth.get(m))).toList()),
                priorMonths.size());
    }

    private List<CategoryRow> categories(YearMonth month, List<YearMonth> priorMonths, List<YearMonth> historyMonths,
                                         Map<YearMonth, List<Dated>> byMonth) {
        Map<YearMonth, Map<String, BigDecimal>> spendByMonth = new HashMap<>();
        historyMonths.forEach(m -> spendByMonth.put(m, spendByCategory(byMonth.get(m))));
        // month-1 is always in historyMonths unless it predates all data, in which case it's empty anyway.
        Map<String, BigDecimal> lastMonth = spendByMonth.getOrDefault(month.minusMonths(1), Map.of());

        Map<String, BigDecimal> budgets = new HashMap<>();
        categoryService.getAllCategories().stream()
                .filter(c -> c.getMonthlyBudget() != null)
                .forEach(c -> budgets.put(c.getCategoryName(), c.getMonthlyBudget()));

        Set<String> names = new TreeSet<>(budgets.keySet());
        spendByMonth.values().forEach(m -> names.addAll(m.keySet()));

        return names.stream()
                .map(name -> {
                    List<BigDecimal> series = historyMonths.stream()
                            .map(m -> spendByMonth.get(m).getOrDefault(name, ZERO))
                            .toList();
                    return new CategoryRow(
                            name,
                            series.get(series.size() - 1),
                            budgets.get(name),
                            lastMonth.getOrDefault(name, ZERO),
                            average(priorMonths.stream()
                                    .map(m -> spendByMonth.get(m).getOrDefault(name, ZERO))
                                    .toList()),
                            trend(series),
                            series);
                })
                .sorted(Comparator.comparing(CategoryRow::spend).reversed().thenComparing(CategoryRow::category))
                .toList();
    }

    /** Slope of the last three months relative to their mean; null when there are fewer than three. */
    static Trend trend(List<BigDecimal> series) {
        int n = series.size();
        if (n < 3) return null;
        BigDecimal first = series.get(n - 3);
        BigDecimal last = series.get(n - 1);
        BigDecimal sum = first.add(series.get(n - 2)).add(last);
        // Refunds can push the sum to zero or below, where a ratio means nothing.
        if (sum.signum() <= 0) return Trend.FLAT;
        // slope / mean = ((last - first) / 2) / (sum / 3)
        BigDecimal relative = last.subtract(first).multiply(BigDecimal.valueOf(3))
                .divide(sum.multiply(BigDecimal.valueOf(2)), 4, RoundingMode.HALF_UP);
        if (relative.compareTo(TREND_THRESHOLD) > 0) return Trend.UP;
        if (relative.compareTo(TREND_THRESHOLD.negate()) < 0) return Trend.DOWN;
        return Trend.FLAT;
    }

    private List<MerchantRow> topMerchants(List<Dated> expenses) {
        return merchantRows(expenses, key -> true).stream().limit(TOP_N).toList();
    }

    /** Merchants spent at this month that never appear in any earlier transaction. */
    private List<MerchantRow> newMerchants(YearMonth month, List<Dated> cashflow, List<Dated> expenses) {
        LocalDate monthStart = month.atDay(1);
        Set<String> seenBefore = cashflow.stream()
                .filter(d -> d.date().isBefore(monthStart))
                .map(d -> d.merchant().key())
                .collect(Collectors.toSet());
        return merchantRows(expenses, key -> !key.isEmpty() && !seenBefore.contains(key)).stream()
                .limit(NEW_MERCHANTS_LIMIT)
                .toList();
    }

    /** Expenses grouped by normalised merchant, largest total first. */
    private List<MerchantRow> merchantRows(List<Dated> expenses, Predicate<String> keyFilter) {
        Map<String, List<Dated>> byKey = new HashMap<>();
        Map<String, String> displayNames = new HashMap<>();
        for (Dated d : expenses) {
            NormalizedMerchant m = d.merchant();
            if (!keyFilter.test(m.key())) continue;
            byKey.computeIfAbsent(m.key(), k -> new ArrayList<>()).add(d);
            displayNames.putIfAbsent(m.key(), m.key().isEmpty() ? NO_MERCHANT : m.displayName());
        }
        return byKey.entrySet().stream()
                .map(e -> new MerchantRow(displayNames.get(e.getKey()), sum(e.getValue()), e.getValue().size()))
                .sorted(Comparator.comparing(MerchantRow::amount).reversed())
                .toList();
    }

    private static String categoryOf(Dated d) {
        return Objects.requireNonNullElse(d.tx().getCategory(), "Unknown");
    }

    private SubscriptionChanges subscriptionChanges(YearMonth month) {
        Map<Long, List<SubscriptionPriceHistory>> priceHistory = priceHistoryRepository.findAll().stream()
                .sorted(Comparator.comparing(SubscriptionPriceHistory::getEffectiveDate)
                        .thenComparing(SubscriptionPriceHistory::getId))
                .collect(groupingBy(SubscriptionPriceHistory::getSubscriptionId));
        List<CommitmentRow> started = new ArrayList<>();
        List<CommitmentRow> trialsEnded = new ArrayList<>();
        List<PriceChangeRow> priceChanges = new ArrayList<>();

        for (Subscription s : subscriptionRepository.findAll()) {
            if (SubscriptionService.STATUS_CANCELLED.equals(s.getStatus())) continue;
            LocalDate firstCharge = firstChargeDate(s);
            if (inMonth(firstCharge, month)) started.add(commitment(s, firstCharge));
            LocalDate trialEnd = parseIso(s.getTrialEndDate());
            if (inMonth(trialEnd, month)) trialsEnded.add(commitment(s, trialEnd));

            List<SubscriptionPriceHistory> history = priceHistory.getOrDefault(s.getId(), List.of());
            for (int i = 1; i < history.size(); i++) {
                SubscriptionPriceHistory prev = history.get(i - 1);
                SubscriptionPriceHistory cur = history.get(i);
                LocalDate date = parseIso(cur.getEffectiveDate());
                if (inMonth(date, month) && cur.getAmount().compareTo(prev.getAmount()) != 0) {
                    priceChanges.add(new PriceChangeRow(s.getName(), prev.getAmount(), cur.getAmount(), date));
                }
            }
        }
        return new SubscriptionChanges(started, priceChanges, trialsEnded);
    }

    /** Earliest matched payment, falling back to when the commitment was added. */
    private static LocalDate firstChargeDate(Subscription s) {
        if (s.getPaidDates() != null && !s.getPaidDates().isBlank()) {
            Optional<LocalDate> earliest = Arrays.stream(s.getPaidDates().split(","))
                    .map(MonthlyReviewService::parseIso)
                    .filter(Objects::nonNull)
                    .min(Comparator.naturalOrder());
            if (earliest.isPresent()) return earliest.get();
        }
        return s.getCreateTime() == null ? null : LocalDate.ofInstant(Instant.ofEpochMilli(s.getCreateTime()), DateUtil.APP_ZONE);
    }

    private static CommitmentRow commitment(Subscription s, LocalDate date) {
        return new CommitmentRow(s.getName(), s.getAmount(), s.getBillingCycle(), date);
    }

    private static Map<String, BigDecimal> spendByCategory(List<Dated> txs) {
        if (txs == null) return Map.of();
        return txs.stream()
                .filter(d -> AggregationService.isExpense(d.tx()))
                .collect(groupingBy(MonthlyReviewService::categoryOf,
                        Collectors.collectingAndThen(
                                reducing(BigDecimal.ZERO, Dated::amount, BigDecimal::add),
                                MonthlyReviewService::cents)));
    }

    private static BigDecimal spend(List<Dated> txs) {
        return txs == null ? BigDecimal.ZERO : sum(txs.stream().filter(d -> AggregationService.isExpense(d.tx())).toList());
    }

    private static BigDecimal income(List<Dated> txs) {
        return txs == null ? BigDecimal.ZERO : sum(txs.stream().filter(d -> AggregationService.isIncome(d.tx())).toList());
    }

    private static BigDecimal sum(List<Dated> txs) {
        return cents(txs.stream().map(Dated::amount).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private static BigDecimal average(List<BigDecimal> values) {
        if (values.isEmpty()) return null;
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal cents(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static boolean inMonth(LocalDate date, YearMonth month) {
        return date != null && YearMonth.from(date).equals(month);
    }

    private static LocalDate parseIso(String date) {
        if (date == null || date.isBlank()) return null;
        try {
            return LocalDate.parse(date.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
