package com.bishop.FinanceTracker.service;

import com.bishop.FinanceTracker.model.domain.Transaction;
import com.bishop.FinanceTracker.model.json.MonthlySpendComparisonResponse;
import com.bishop.FinanceTracker.model.json.CumulativeSpendResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AggregationServiceTest {

    @InjectMocks
    private AggregationService aggregationService;

    @Mock
    private TransactionService transactionService;

    private Transaction createTransaction(LocalDate date, BigDecimal amount) {
        Transaction transaction = new Transaction();
        transaction.setTransactionDateTime(date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli());
        transaction.setAmount(amount);
        return transaction;
    }

    @Test
    void testGetMonthlySpendComparison() {
        // Setup test data
        LocalDate now = LocalDate.now();
        LocalDate startOfCurrentMonth = now.withDayOfMonth(1);
        LocalDate startOfPriorMonth = startOfCurrentMonth.minusMonths(1);

        // Create test transactions
        List<Transaction> currentMonthTransactions = Arrays.asList(
            createTransaction(startOfCurrentMonth.plusDays(1), new BigDecimal("100.00")),
            createTransaction(startOfCurrentMonth.plusDays(5), new BigDecimal("200.00"))
        );

        List<Transaction> priorMonthTransactions = Arrays.asList(
            createTransaction(startOfPriorMonth.plusDays(1), new BigDecimal("150.00")),
            createTransaction(startOfPriorMonth.plusDays(5), new BigDecimal("250.00"))
        );

        when(transactionService.getAllInRecentYear()).thenReturn(
            Arrays.asList(
                currentMonthTransactions.get(0),
                currentMonthTransactions.get(1),
                priorMonthTransactions.get(0),
                priorMonthTransactions.get(1)
            )
        );

        // Execute
        MonthlySpendComparisonResponse response = aggregationService.getMonthlySpendComparison();

        // Verify
        assertEquals("300.00", response.getCurrentMonthSpend());
        assertEquals("400.00", response.getPriorMonthSpend());
        assertEquals("-25.0", response.getComparisonToPriorMonth());
    }

    @Test
    void testGetMonthlySpendComparisonWithZeroPriorMonth() {
        // Setup test data
        LocalDate now = LocalDate.now();
        LocalDate startOfCurrentMonth = now.withDayOfMonth(1);

        // Create test transactions
        List<Transaction> currentMonthTransactions = Arrays.asList(
            createTransaction(startOfCurrentMonth.plusDays(1), new BigDecimal("100.00"))
        );

        when(transactionService.getAllInRecentYear()).thenReturn(currentMonthTransactions);

        // Execute
        MonthlySpendComparisonResponse response = aggregationService.getMonthlySpendComparison();

        // Verify
        assertEquals("100.00", response.getCurrentMonthSpend());
        assertEquals("0.00", response.getPriorMonthSpend());
        assertEquals("100.0", response.getComparisonToPriorMonth());
    }

    @Test
    void testGetMonthlySpendComparisonWithZeroCurrentMonth() {
        // Setup test data
        LocalDate now = LocalDate.now();
        LocalDate startOfPriorMonth = now.withDayOfMonth(1).minusMonths(1);

        // Create test transactions
        List<Transaction> priorMonthTransactions = Arrays.asList(
            createTransaction(startOfPriorMonth.plusDays(1), new BigDecimal("100.00"))
        );

        when(transactionService.getAllInRecentYear()).thenReturn(priorMonthTransactions);

        // Execute
        MonthlySpendComparisonResponse response = aggregationService.getMonthlySpendComparison();

        // Verify
        assertEquals("0.00", response.getCurrentMonthSpend());
        assertEquals("100.00", response.getPriorMonthSpend());
        assertEquals("-100.0", response.getComparisonToPriorMonth());
    }

    @Test
    void testGetCumulativeSpend() {
        // Setup test data
        LocalDate now = LocalDate.now();
        LocalDate startOfCurrentMonth = now.withDayOfMonth(1);
        
        // Create test transactions for different days
        List<Transaction> transactions = Arrays.asList(
            createTransaction(startOfCurrentMonth.plusDays(0), new BigDecimal("100.00")), // Day 1
            createTransaction(startOfCurrentMonth.plusDays(0), new BigDecimal("50.00")),  // Day 1
            createTransaction(startOfCurrentMonth.plusDays(1), new BigDecimal("200.00")), // Day 2
            createTransaction(startOfCurrentMonth.plusDays(2), new BigDecimal("75.50"))   // Day 3
        );
        
        when(transactionService.getAllInRecentYear()).thenReturn(transactions);
        
        // Execute
        CumulativeSpendResponse response = aggregationService.getCumulativeSpend();
        
        // Verify
        List<String> actualValues = response.getCumulativeValues();
        assertEquals(now.getDayOfMonth(), actualValues.size(), "Should have one value per day of the month");
        
        // Check first 3 days have correct values
        assertEquals("150.00", actualValues.get(0), "Day 1 should be sum of both transactions");
        assertEquals("350.00", actualValues.get(1), "Day 2 should include previous day's total");
        assertEquals("425.50", actualValues.get(2), "Day 3 should include previous day's total");
        
        // Check remaining days maintain the last total
        for (int i = 3; i < actualValues.size(); i++) {
            assertEquals("425.50", actualValues.get(i), "Remaining days should maintain the last total");
        }
    }
    
    @Test
    void testGetCumulativeSpendWithNoTransactions() {
        // Setup test data
        when(transactionService.getAllInRecentYear()).thenReturn(Collections.emptyList());
        
        // Execute
        CumulativeSpendResponse response = aggregationService.getCumulativeSpend();
        
        // Verify
        List<String> actualValues = response.getCumulativeValues();
        assertEquals(LocalDate.now().getDayOfMonth(), actualValues.size(), "Should have one value per day of the month");
        
        // All days should be 0.00
        for (String value : actualValues) {
            assertEquals("0.00", value, "All days should be 0.00 with no transactions");
        }
    }
    
    @Test
    void testGetCumulativeSpendWithGaps() {
        // Setup test data
        LocalDate now = LocalDate.now();
        LocalDate startOfCurrentMonth = now.withDayOfMonth(1);
        
        // Create test transactions with gaps
        List<Transaction> transactions = Arrays.asList(
            createTransaction(startOfCurrentMonth.plusDays(0), new BigDecimal("100.00")), // Day 1
            createTransaction(startOfCurrentMonth.plusDays(2), new BigDecimal("200.00")), // Day 3
            createTransaction(startOfCurrentMonth.plusDays(4), new BigDecimal("300.00"))  // Day 5
        );
        
        when(transactionService.getAllInRecentYear()).thenReturn(transactions);
        
        // Execute
        CumulativeSpendResponse response = aggregationService.getCumulativeSpend();
        
        // Verify
        List<String> actualValues = response.getCumulativeValues();
        assertEquals(now.getDayOfMonth(), actualValues.size(), "Should have one value per day of the month");
        
        // Check specific days
        assertEquals("100.00", actualValues.get(0), "Day 1 should be first transaction");
        assertEquals("100.00", actualValues.get(1), "Day 2 should maintain previous total");
        assertEquals("300.00", actualValues.get(2), "Day 3 should include new transaction");
        assertEquals("300.00", actualValues.get(3), "Day 4 should maintain previous total");
        assertEquals("600.00", actualValues.get(4), "Day 5 should include new transaction");
        
        // Check remaining days maintain the last total
        for (int i = 5; i < actualValues.size(); i++) {
            assertEquals("600.00", actualValues.get(i), "Remaining days should maintain the last total");
        }
    }
}