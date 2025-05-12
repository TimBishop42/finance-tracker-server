package com.bishop.FinanceTracker.service;

import com.bishop.FinanceTracker.model.domain.*;
import com.bishop.FinanceTracker.model.json.HomeData;
import com.bishop.FinanceTracker.util.DateUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Month;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.TemporalUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

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
}
