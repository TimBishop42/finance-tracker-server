package com.bishop.FinanceTracker.service;

import com.bishop.FinanceTracker.model.domain.*;
import com.bishop.FinanceTracker.model.json.HomeData;
import com.bishop.FinanceTracker.model.json.MonthlySpendComparisonResponse;
import com.bishop.FinanceTracker.model.json.CumulativeSpendResponse;
import com.bishop.FinanceTracker.model.json.CategoryYearOverYearResponse;
import com.bishop.FinanceTracker.util.DateUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AggregationService {

    private final TransactionService transactionService;
    private final CategoryService categoryService;
    private final UserSettingsService userSettingsService;

    public List<DisplayMonth> aggregateDisplayMonths(Integer months) {
        return summarizedMonths(months)
                .stream()
                .map(DisplayMonth::to)
                .sorted(Comparator
                        .comparing(DisplayMonth::getYear).reversed()
                        .thenComparing(DisplayMonth::getMonth, Comparator.reverseOrder()))
                .limit(months)
                .sorted(Comparator
                        .comparing(DisplayMonth::getYear)
                        .thenComparing(DisplayMonth::getMonth))
                .collect(Collectors.toList());
    }


    private Collection<SummarizingMonth> summarizedMonths(int months) {
        List<Transaction> allTransactions = transactionService.getAllSinceNMonthsAgo(months);

        Map<MonthYearKey, SummarizingMonth> monthsMap = new HashMap<>();

        // Single pass over expense + income transactions (NEUTRAL is skipped) so a
        // month with income but no expenses — or vice versa — still gets an entry.
        allTransactions.stream()
                .filter(t -> isExpense(t) || isIncome(t))
                .forEach(t -> {
                    LocalDate date = DateUtil.tryParseTransactionDate(t.getTransactionDate());
                    if (date == null) return;
                    MonthYearKey key = MonthYearKey.builder()
                            .month(date.getMonth().name())
                            .year(date.getYear())
                            .build();
                    SummarizingMonth summarizingMonth = monthsMap.computeIfAbsent(key, k -> SummarizingMonth.builder()
                            .categoryValues(categoryService.getAllCategories().stream().map(c -> new CategoryValue(c.getCategoryName(), k.getMonth()))
                                    .collect(Collectors.toMap(CategoryValue::getCategory, Function.identity())))
                            .month(Month.valueOf(k.getMonth()))
                            .year(k.getYear())
                            .totalIncome(0.0)
                            .build());

                    if (isIncome(t)) {
                        summarizingMonth.setTotalIncome(summarizingMonth.getTotalIncome() + t.getAmount().doubleValue());
                        return;
                    }

                    CategoryValue categoryValue = summarizingMonth.getCategoryValues().get(t.getCategory());
                    if (categoryValue == null) {
                        log.warn("Skipping transaction id={} — category '{}' not found in category map (orphaned category)",
                                t.getTransactionId(), t.getCategory());
                    } else {
                        try {
                            categoryValue.incrementValue(t.getAmount().doubleValue());
                        } catch (Exception e) {
                            log.error("Error encountered adding transaction to map: {}", t);
                        }
                    }

                });
        return monthsMap.values();
    }

    public HomeData homeData() {
        YearMonth thisMonth = YearMonth.now(DateUtil.APP_ZONE);
        double currentMonthAmount = last(cumulativeSpend(thisMonth)).doubleValue();
        double priorMonthAmount = last(cumulativeSpend(thisMonth.minusMonths(1))).doubleValue();
        double budgetTarget = userSettingsService.getMaxSpendValue().doubleValue();
        HomeData homeResult = HomeData.builder()
                .currentMonth(currentMonthAmount)
                .priorMonth(priorMonthAmount)
                .status(currentMonthAmount < budgetTarget ? "WITHIN BUDGET" : "OVER BUDGET")
                .build();
        log.info("Built home-assistant data: {}", homeResult);
        return homeResult;
    }

    /** Month-to-date spend vs the prior month over the same number of days. */
    public MonthlySpendComparisonResponse getMonthlySpendComparison() {
        LocalDate today = LocalDate.now(DateUtil.APP_ZONE);
        YearMonth thisMonth = YearMonth.from(today);
        BigDecimal currentMonthSpend = last(cumulativeSpend(thisMonth));
        List<BigDecimal> prior = cumulativeSpend(thisMonth.minusMonths(1));
        // A shorter prior month (e.g. Feb vs 31 Mar) is compared in full.
        BigDecimal priorMonthSpend = prior.get(Math.min(today.getDayOfMonth(), prior.size()) - 1);

        BigDecimal percentageChange;
        if (priorMonthSpend.signum() == 0) {
            percentageChange = currentMonthSpend.signum() > 0 ? new BigDecimal("100.0") : BigDecimal.ZERO;
        } else {
            percentageChange = currentMonthSpend.subtract(priorMonthSpend)
                    .divide(priorMonthSpend, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
        }

        log.info("Monthly spend comparison - Current: {}, Prior: {}, Change: {}%",
                currentMonthSpend, priorMonthSpend, percentageChange);
        return new MonthlySpendComparisonResponse(
                currentMonthSpend.toPlainString(),
                priorMonthSpend.toPlainString(),
                percentageChange.setScale(1, RoundingMode.HALF_UP).toString());
    }

    public CategoryYearOverYearResponse getCategoryYearOverYear() {
        LocalDate today = LocalDate.now(DateUtil.APP_ZONE);
        // Same calendar day last year is the cut-off, so both windows cover identical elapsed days.
        LocalDate sameDayLastYear = today.minusYears(1);

        Map<String, Double> thisYearByCategory = new HashMap<>();
        Map<String, Double> lastYearByCategory = new HashMap<>();
        for (Transaction t : transactionService.getAll()) {
            if (!isExpense(t)) continue;
            LocalDate date = DateUtil.tryParseTransactionDate(t.getTransactionDate());
            if (date == null) continue;
            Map<String, Double> bucket = date.getYear() == today.getYear() && !date.isAfter(today) ? thisYearByCategory
                    : date.getYear() == sameDayLastYear.getYear() && !date.isAfter(sameDayLastYear) ? lastYearByCategory
                    : null;
            if (bucket != null) {
                bucket.merge(t.getCategory() != null ? t.getCategory() : "Unknown", t.getAmount().doubleValue(), Double::sum);
            }
        }

        Set<String> allCategories = new HashSet<>();
        allCategories.addAll(thisYearByCategory.keySet());
        allCategories.addAll(lastYearByCategory.keySet());

        List<CategoryYearOverYearResponse.CategoryRow> rows = allCategories.stream()
                .map(category -> {
                    double ty = round2(thisYearByCategory.getOrDefault(category, 0.0));
                    double ly = round2(lastYearByCategory.getOrDefault(category, 0.0));
                    double delta = round2(ty - ly);
                    double deltaPercent = ly == 0 ? (ty > 0 ? 100.0 : 0.0)
                            : round2((delta / ly) * 100.0);
                    return CategoryYearOverYearResponse.CategoryRow.builder()
                            .category(category)
                            .thisYear(ty)
                            .lastYear(ly)
                            .delta(delta)
                            .deltaPercent(deltaPercent)
                            .build();
                })
                .sorted(Comparator.comparingDouble(CategoryYearOverYearResponse.CategoryRow::getThisYear).reversed())
                .collect(Collectors.toList());

        double thisYearTotal = round2(rows.stream().mapToDouble(CategoryYearOverYearResponse.CategoryRow::getThisYear).sum());
        double lastYearTotal = round2(rows.stream().mapToDouble(CategoryYearOverYearResponse.CategoryRow::getLastYear).sum());

        String period = String.format("Jan 1 – %s %d", today.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH), today.getDayOfMonth());

        log.info("Year-over-year comparison: thisYear={}, lastYear={}, categories={}", thisYearTotal, lastYearTotal, rows.size());

        return CategoryYearOverYearResponse.builder()
                .categories(rows)
                .thisYearTotal(thisYearTotal)
                .lastYearTotal(lastYearTotal)
                .comparisonPeriod(period)
                .build();
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    /**
     * Only EXPENSE transactions count as spend. INCOME (salary, positive
     * inflows) and NEUTRAL (internal transfers / credit-card payments) are
     * excluded from every spend aggregation. Legacy rows with a null type
     * predate the type field and are treated as expenses.
     */
    static boolean isExpense(Transaction t) {
        String type = t.getTransactionType();
        return type == null || "EXPENSE".equalsIgnoreCase(type);
    }

    static boolean isIncome(Transaction t) {
        return "INCOME".equalsIgnoreCase(t.getTransactionType());
    }

    public CumulativeSpendResponse getCumulativeSpend() {
        return getCumulativeSpend(null, null);
    }

    public CumulativeSpendResponse getCumulativeSpend(Integer month, Integer year) {
        if (month != null && year != null) {
            if (month < 1 || month > 12) {
                throw new IllegalArgumentException("Month must be between 1 and 12, got: " + month);
            }
            if (year < 1900 || year > 2100) {
                throw new IllegalArgumentException("Year must be between 1900 and 2100, got: " + year);
            }
        } else if (month != null || year != null) {
            throw new IllegalArgumentException("Both month and year parameters must be provided together or not at all");
        }
        YearMonth target = month == null ? YearMonth.now(DateUtil.APP_ZONE) : YearMonth.of(year, month);
        return new CumulativeSpendResponse(cumulativeSpend(target).stream().map(BigDecimal::toPlainString).toList());
    }

    /**
     * Running expense total (to the cent) for each day of {@code month}, by calendar
     * date. The in-progress month stops at today; other months run to their last day.
     * The shared basis for the dashboard's cumulative chart, month-on-month
     * comparison, home-assistant data and the monthly review.
     */
    public List<BigDecimal> cumulativeSpend(YearMonth month) {
        LocalDate today = LocalDate.now(DateUtil.APP_ZONE);
        int days = month.equals(YearMonth.from(today)) ? today.getDayOfMonth() : month.lengthOfMonth();
        BigDecimal[] daily = new BigDecimal[days + 1];
        Arrays.fill(daily, BigDecimal.ZERO);
        for (Transaction t : transactionService.getAll()) {
            if (!isExpense(t)) continue;
            LocalDate date = DateUtil.tryParseTransactionDate(t.getTransactionDate());
            if (date != null && YearMonth.from(date).equals(month) && date.getDayOfMonth() <= days) {
                daily[date.getDayOfMonth()] = daily[date.getDayOfMonth()].add(t.getAmount());
            }
        }
        List<BigDecimal> running = new ArrayList<>(days);
        BigDecimal total = BigDecimal.ZERO;
        for (int day = 1; day <= days; day++) {
            total = total.add(daily[day]);
            running.add(total.setScale(2, RoundingMode.HALF_UP));
        }
        return running;
    }

    private static BigDecimal last(List<BigDecimal> values) {
        return values.get(values.size() - 1);
    }
}
