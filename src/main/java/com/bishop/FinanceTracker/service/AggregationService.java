package com.bishop.FinanceTracker.service;

import com.bishop.FinanceTracker.model.domain.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Month;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AggregationService {

    private final TransactionService transactionService;
    private final CategoryService categoryService;

    public List<SummarizingMonth> summarizedMonths() {
        List<Transaction> allTransactions = transactionService.getAll();

        List<SummarizingMonth> summarizedMonths = initSummaryMonths();

        Map<MonthYearKey, SummarizingMonth> monthMap;


    }

    private List<SummarizingMonth> initSummaryMonths() {
        List<CategoryValue> categories = categoryService.getAllCategories().stream()
                .map(c -> new CategoryValue(c.getCategoryName()))
                .collect(Collectors.toList());
        return Arrays.stream(Month.values()).map(m -> new SummarizingMonth(m, null, categories)).collect(Collectors.toList());
    }

}
