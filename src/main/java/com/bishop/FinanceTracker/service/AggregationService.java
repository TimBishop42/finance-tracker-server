package com.bishop.FinanceTracker.service;

import com.bishop.FinanceTracker.model.domain.*;
import com.bishop.FinanceTracker.util.DateUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Month;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AggregationService {

    private final TransactionService transactionService;
    private final CategoryService categoryService;

    public List<DisplayMonth> summarizedMonths() {
        List<Transaction> allTransactions = transactionService.getAll();

        Map<MonthYearKey, SummarizingMonth> monthsMap = new HashMap<>();

        allTransactions.stream()
                .forEach(t -> {
                    MonthYearKey key = MonthYearKey.builder()
                            .month(DateUtil.getMonthFromStringDate(t.getTransactionDate()).name())
                            .year(DateUtil.getYearFromStringDate(t.getTransactionDate()))
                            .build();
                    monthsMap.computeIfAbsent(key, k -> SummarizingMonth.builder()
                            .categoryValues(categoryService.getAllCategories().stream().map(c -> new CategoryValue(c.getCategoryName()))
                                    .collect(Collectors.toMap(CategoryValue::getCategory, Function.identity())))
                            .month(Month.valueOf(k.getMonth()))
                            .year(k.getYear())
                            .build());
                    try {
                        monthsMap.get(key).getCategoryValues().get(t.getCategory()).incrementValue(t.getAmount().doubleValue());
                    }
                    catch (Exception e) {
                        log.error("Error encountered adding transaction to map: {}", t);
                    }

                });
        return getDisplayMonths(monthsMap.values());
    }

    private List<DisplayMonth> getDisplayMonths(Collection<SummarizingMonth> values) {
        return values.stream()
                .map(DisplayMonth::to)
                .collect(Collectors.toList());
    }

}
