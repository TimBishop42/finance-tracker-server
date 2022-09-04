package com.bishop.FinanceTracker.service;

import com.bishop.FinanceTracker.model.domain.*;
import com.bishop.FinanceTracker.util.DateUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Month;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AggregationService {

    private final TransactionService transactionService;
    private final CategoryService categoryService;

    public List<SummarizingMonth> summarizedMonths() {
        List<Transaction> allTransactions = transactionService.getAll();

        List<SummarizingMonth> summarizedMonths = initSummaryMonths();
        Map<MonthYearKey, SummarizingMonth> monthsMap = new HashMap<>();

        allTransactions.stream()
                .map(t -> {
                    MonthYearKey key = MonthYearKey.builder()
                            .month(DateUtil.getMonthFromStringDate(t.getTransactionDate()).name())
                            .year(DateUtil.getYearFromStringDate(t.getTransactionDate()))
                            .build();

                })



    }

    private List<SummarizingMonth> initSummaryMonths() {
        List<CategoryValue> categories = categoryService.getAllCategories().stream()
                .map(c -> new CategoryValue(c.getCategoryName()))
                .collect(Collectors.toList());
        return Arrays.stream(Month.values()).map(m -> new SummarizingMonth(m, null, categories)).collect(Collectors.toList());
    }

}
