package com.bishop.FinanceTracker.service;

import com.bishop.FinanceTracker.model.domain.*;
import com.bishop.FinanceTracker.model.json.HomeData;
import com.bishop.FinanceTracker.model.json.MonthlySpendComparisonResponse;
import com.bishop.FinanceTracker.model.json.CumulativeSpendResponse;
import com.bishop.FinanceTracker.util.DateUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Month;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;

@Slf4j
@Service
@RequiredArgsConstructor
public class AggregationService {

    private final TransactionService transactionService;
    private final CategoryService categoryService;

    public List<DisplayMonth> aggregateDisplayMonths(Integer months) {
        return summarizedMonths()
                .stream()
                .map(DisplayMonth::to)
                .sorted(Comparator
                        .comparing(DisplayMonth::getYear).reversed()
                        .thenComparing(DisplayMonth::getMonth, Comparator.reverseOrder()))
                .limit(months)
                .collect(Collectors.toList());
    }

    private static final Double MONTHLY_BUDGET_TARGET = 11000.0;

    private Collection<SummarizingMonth> summarizedMonths() {
        List<Transaction> allTransactions = transactionService.getAllInRecentYear();

        Map<MonthYearKey, SummarizingMonth> monthsMap = new HashMap<>();

        allTransactions
                .forEach(t -> {
                    MonthYearKey key = MonthYearKey.builder()
                            .month(DateUtil.getMonthFromStringDate(t.getTransactionDate()).name())
                            .year(DateUtil.getYearFromStringDate(t.getTransactionDate()))
                            .build();
                    monthsMap.computeIfAbsent(key, k -> SummarizingMonth.builder()
                            .categoryValues(categoryService.getAllCategories().stream().map(c -> new CategoryValue(c.getCategoryName(), k.getMonth()))
                                    .collect(Collectors.toMap(CategoryValue::getCategory, Function.identity())))
                            .month(Month.valueOf(k.getMonth()))
                            .year(k.getYear())
                            .build());
                    try {
                        monthsMap.get(key).getCategoryValues().get(t.getCategory()).incrementValue(t.getAmount().doubleValue());
                    } catch (Exception e) {
                        log.error("Error encountered adding transaction to map: {}", t);
                    }

                });
        return monthsMap.values();
    }

    public HomeData homeData() {
        long startTime = System.currentTimeMillis();
        List<Transaction> allTransactions = transactionService.getAllInRecentYear();

        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("UTC"));

        ZonedDateTime firstDayOfLastMonth = now
                .minusMonths(1)
                .with(TemporalAdjusters.firstDayOfMonth())
                .truncatedTo(ChronoUnit.DAYS);

        ZonedDateTime firstDayOfCurrentMonth = now
                .with(TemporalAdjusters.firstDayOfMonth())
                .truncatedTo(ChronoUnit.DAYS);
        ;

        Double priorMonthAmount = allTransactions.stream()
                .filter(t -> t.getTransactionDateTime() >= firstDayOfLastMonth.toInstant().toEpochMilli()
                        && t.getTransactionDateTime() < firstDayOfCurrentMonth.toInstant().toEpochMilli())
                .mapToDouble(t -> t.getAmount().doubleValue())
                .sum();
        log.info("Sum for prior month {}, prior month dateTime {}", priorMonthAmount, firstDayOfLastMonth);

        Double currentMonthAmount = allTransactions.stream()
                .filter(t -> t.getTransactionDateTime() >= firstDayOfCurrentMonth.toInstant().toEpochMilli())
                .mapToDouble(t -> t.getAmount().doubleValue())
                .sum();
        log.info("Sum for current month {}, current month dateTime {}", currentMonthAmount, firstDayOfCurrentMonth);

        HomeData homeResult = HomeData.builder()
                .currentMonth(currentMonthAmount.intValue())
                .priorMonth(priorMonthAmount.intValue())
                .status(currentMonthAmount < MONTHLY_BUDGET_TARGET ? "WITHIN BUDGET" : "OVER BUDGET")
                .build();

        log.info("Retrieved summarized value for home data in {} millis, data: {}",
                System.currentTimeMillis() - startTime, homeResult);

        return homeResult;
    }

    public MonthlySpendComparisonResponse getMonthlySpendComparison() {
        log.info("Calculating monthly spend comparison");

        LocalDate now = LocalDate.now();
        LocalDate startOfCurrentMonth = now.withDayOfMonth(1);
        LocalDate startOfPriorMonth = startOfCurrentMonth.minusMonths(1);

        List<Transaction> allTransactions = transactionService.getAllInRecentYear();

        // Get current month's spend
        BigDecimal currentMonthSpend = allTransactions.stream()
            .filter(t -> t.getTransactionDateTime() >= startOfCurrentMonth.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
                && t.getTransactionDateTime() <= now.atTime(LocalTime.MAX).toInstant(ZoneOffset.UTC).toEpochMilli())
            .map(Transaction::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Get prior month's spend up to same day
        LocalDate endOfPriorMonth = startOfPriorMonth.plusDays(now.getDayOfMonth() - 1);
        LocalDate adjustedEndOfPriorMonth = endOfPriorMonth.isAfter(startOfPriorMonth.plusMonths(1).minusDays(1)) 
            ? startOfPriorMonth.plusMonths(1).minusDays(1) 
            : endOfPriorMonth;

        BigDecimal priorMonthSpend = allTransactions.stream()
            .filter(t -> t.getTransactionDateTime() >= startOfPriorMonth.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
                && t.getTransactionDateTime() <= adjustedEndOfPriorMonth.atTime(LocalTime.MAX).toInstant(ZoneOffset.UTC).toEpochMilli())
            .map(Transaction::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Calculate percentage change
        BigDecimal percentageChange;
        if (priorMonthSpend.compareTo(BigDecimal.ZERO) == 0) {
            percentageChange = currentMonthSpend.compareTo(BigDecimal.ZERO) > 0 ?
                new BigDecimal("100.0") : BigDecimal.ZERO;
        } else {
            percentageChange = currentMonthSpend.subtract(priorMonthSpend)
                .divide(priorMonthSpend, 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));
        }

        log.info("Monthly spend comparison calculated - Current: {}, Prior: {}, Change: {}%",
            currentMonthSpend, priorMonthSpend, percentageChange);

        return new MonthlySpendComparisonResponse(
            currentMonthSpend.setScale(2, RoundingMode.HALF_UP).toString(),
            priorMonthSpend.setScale(2, RoundingMode.HALF_UP).toString(),
            percentageChange.setScale(1, RoundingMode.HALF_UP).toString()
        );
    }

    public CumulativeSpendResponse getCumulativeSpend() {
        return getCumulativeSpend(null, null);
    }

    public CumulativeSpendResponse getCumulativeSpend(Integer month, Integer year) {
        // Validate input parameters
        if (month != null && year != null) {
            // Validate month parameter
            if (month < 1 || month > 12) {
                throw new IllegalArgumentException("Month must be between 1 and 12, got: " + month);
            }
            
            // Validate year parameter
            if (year < 1900 || year > 2100) {
                throw new IllegalArgumentException("Year must be between 1900 and 2100, got: " + year);
            }
        } else if (month != null || year != null) {
            // If only one parameter is provided, throw exception
            throw new IllegalArgumentException("Both month and year parameters must be provided together or not at all");
        }
        
        LocalDate now = LocalDate.now();
        LocalDate targetDate;
        
        // If month and year are provided, use them; otherwise use current month
        if (month != null && year != null) {
            targetDate = LocalDate.of(year, month, 1);
            log.info("Calculating cumulative spend for month {} year {}", month, year);
        } else {
            targetDate = now.withDayOfMonth(1);
            log.info("Calculating cumulative spend for current month");
        }

        LocalDate startOfTargetMonth = targetDate.withDayOfMonth(1);
        LocalDate endOfTargetMonth = targetDate.withDayOfMonth(targetDate.lengthOfMonth());
        
        // For current month, only go up to current day; for past months, go to end of month
        LocalDate effectiveEndDate;
        if (targetDate.getMonth() == now.getMonth() && targetDate.getYear() == now.getYear()) {
            effectiveEndDate = now;
        } else {
            effectiveEndDate = endOfTargetMonth;
        }
        
        List<Transaction> allTransactions = transactionService.getAllInRecentYear();
        
        // Filter transactions for target month
        List<Transaction> monthTransactions = allTransactions.stream()
            .filter(t -> t.getTransactionDateTime() >= startOfTargetMonth.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
                && t.getTransactionDateTime() <= effectiveEndDate.atTime(LocalTime.MAX).toInstant(ZoneOffset.UTC).toEpochMilli())
            .collect(Collectors.toList());

        // Group transactions by day and calculate daily totals
        Map<Integer, BigDecimal> dailyTotals = monthTransactions.stream()
            .collect(Collectors.groupingBy(
                t -> LocalDate.ofInstant(
                    java.time.Instant.ofEpochMilli(t.getTransactionDateTime()),
                    ZoneOffset.UTC
                ).getDayOfMonth(),
                Collectors.mapping(
                    Transaction::getAmount,
                    Collectors.reducing(BigDecimal.ZERO, BigDecimal::add)
                )
            ));

        // Calculate cumulative totals for each day
        List<String> cumulativeValues = new ArrayList<>();
        BigDecimal runningTotal = BigDecimal.ZERO;

        // For current month, only show up to current day; for past months, show all days
        int daysToShow = effectiveEndDate.getDayOfMonth();
        
        for (int day = 1; day <= daysToShow; day++) {
            runningTotal = runningTotal.add(dailyTotals.getOrDefault(day, BigDecimal.ZERO));
            cumulativeValues.add(runningTotal.setScale(2, RoundingMode.HALF_UP).toString());
        }

        log.info("Calculated cumulative spend values for {}/{}: {}", month != null ? month : now.getMonthValue(), 
                year != null ? year : now.getYear(), cumulativeValues);
        return new CumulativeSpendResponse(cumulativeValues);
    }
}
