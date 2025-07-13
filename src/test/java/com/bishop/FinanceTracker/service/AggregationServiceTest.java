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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
        // Use current date to avoid mocking issues
        LocalDate now = LocalDate.now();
        LocalDate currentMonthStart = now.withDayOfMonth(1);
        LocalDate priorMonthStart = currentMonthStart.minusMonths(1);
        
        // Create test transactions that will definitely be within the comparison range
        // Use day 1 and day 2 (or current day if we're on day 1)
        int dayToUse = Math.min(2, now.getDayOfMonth());
        
        List<Transaction> currentMonthTransactions = Arrays.asList(
            createTransaction(currentMonthStart.plusDays(0), new BigDecimal("100.00")), // Day 1
            createTransaction(currentMonthStart.plusDays(dayToUse - 1), new BigDecimal("200.00")) // Day 1 or 2
        );

        List<Transaction> priorMonthTransactions = Arrays.asList(
            createTransaction(priorMonthStart.plusDays(0), new BigDecimal("150.00")), // Day 1
            createTransaction(priorMonthStart.plusDays(dayToUse - 1), new BigDecimal("250.00")) // Day 1 or 2
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

        // Verify - The specific values will depend on whether we're on day 1 or later
        assertEquals("300.00", response.getCurrentMonthSpend());
        assertEquals("400.00", response.getPriorMonthSpend());
        assertEquals("-25.0", response.getComparisonToPriorMonth());
    }

    @Test
    void testGetMonthlySpendComparisonWithZeroPriorMonth() {
        // Use current date to avoid mocking issues
        LocalDate now = LocalDate.now();
        LocalDate currentMonthStart = now.withDayOfMonth(1);
        
        // Create test transactions - only current month
        List<Transaction> currentMonthTransactions = Arrays.asList(
            createTransaction(currentMonthStart.plusDays(1), new BigDecimal("100.00"))
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
        // Use current date to avoid mocking issues
        LocalDate now = LocalDate.now();
        LocalDate currentMonthStart = now.withDayOfMonth(1);
        LocalDate priorMonthStart = currentMonthStart.minusMonths(1);
        
        // Create test transactions - only prior month
        List<Transaction> priorMonthTransactions = Arrays.asList(
            createTransaction(priorMonthStart.plusDays(1), new BigDecimal("100.00"))
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
        // Use current date to avoid mocking issues
        LocalDate now = LocalDate.now();
        LocalDate currentMonthStart = now.withDayOfMonth(1);
        
        // Create test transactions for different days
        List<Transaction> transactions = Arrays.asList(
            createTransaction(currentMonthStart.plusDays(0), new BigDecimal("100.00")),
            createTransaction(currentMonthStart.plusDays(0), new BigDecimal("50.00")),
            createTransaction(currentMonthStart.plusDays(1), new BigDecimal("200.00")),
            createTransaction(currentMonthStart.plusDays(2), new BigDecimal("75.50"))
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
        // Use current date to avoid mocking issues
        LocalDate now = LocalDate.now();
        
        // Setup test data
        when(transactionService.getAllInRecentYear()).thenReturn(Collections.emptyList());
        
        // Execute
        CumulativeSpendResponse response = aggregationService.getCumulativeSpend();
        
        // Verify
        List<String> actualValues = response.getCumulativeValues();
        assertEquals(now.getDayOfMonth(), actualValues.size(), "Should have one value per day of the month");
        
        // All days should be 0.00
        for (String value : actualValues) {
            assertEquals("0.00", value, "All days should be 0.00 with no transactions");
        }
    }
    
    @Test
    void testGetCumulativeSpendWithGaps() {
        // Use current date to avoid mocking issues
        LocalDate now = LocalDate.now();
        LocalDate currentMonthStart = now.withDayOfMonth(1);
        
        // Skip test if we're too early in the month (need at least 5 days)
        if (now.getDayOfMonth() < 5) {
            return;
        }
        
        // Create test transactions with gaps
        List<Transaction> transactions = Arrays.asList(
            createTransaction(currentMonthStart.plusDays(0), new BigDecimal("100.00")),
            createTransaction(currentMonthStart.plusDays(2), new BigDecimal("200.00")),
            createTransaction(currentMonthStart.plusDays(4), new BigDecimal("300.00"))
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

    @Test
    void testGetCumulativeSpendWithValidMonthAndYear() {
        // Create test transactions for December 2023
        LocalDate targetDate = LocalDate.of(2023, 12, 1);
        List<Transaction> transactions = Arrays.asList(
            createTransaction(targetDate.plusDays(0), new BigDecimal("100.00")),
            createTransaction(targetDate.plusDays(1), new BigDecimal("200.00")),
            createTransaction(targetDate.plusDays(2), new BigDecimal("75.50"))
        );
        
        when(transactionService.getAllInRecentYear()).thenReturn(transactions);
        
        // Execute
        CumulativeSpendResponse response = aggregationService.getCumulativeSpend(12, 2023);
        
        // Verify
        List<String> actualValues = response.getCumulativeValues();
        assertEquals(31, actualValues.size(), "December should have 31 days");
        
        // Check first 3 days have correct values
        assertEquals("100.00", actualValues.get(0), "Day 1 should be first transaction");
        assertEquals("300.00", actualValues.get(1), "Day 2 should include previous day's total");
        assertEquals("375.50", actualValues.get(2), "Day 3 should include previous day's total");
        
        // Check remaining days maintain the last total
        for (int i = 3; i < actualValues.size(); i++) {
            assertEquals("375.50", actualValues.get(i), "Remaining days should maintain the last total");
        }
    }

    @Test
    void testGetCumulativeSpendWithInvalidMonth() {
        // Test invalid month values
        IllegalArgumentException exception1 = assertThrows(IllegalArgumentException.class, () -> {
            aggregationService.getCumulativeSpend(0, 2024);
        });
        assertEquals("Month must be between 1 and 12, got: 0", exception1.getMessage());

        IllegalArgumentException exception2 = assertThrows(IllegalArgumentException.class, () -> {
            aggregationService.getCumulativeSpend(13, 2024);
        });
        assertEquals("Month must be between 1 and 12, got: 13", exception2.getMessage());
    }

    @Test
    void testGetCumulativeSpendWithInvalidYear() {
        // Test invalid year values
        IllegalArgumentException exception1 = assertThrows(IllegalArgumentException.class, () -> {
            aggregationService.getCumulativeSpend(12, 1899);
        });
        assertEquals("Year must be between 1900 and 2100, got: 1899", exception1.getMessage());

        IllegalArgumentException exception2 = assertThrows(IllegalArgumentException.class, () -> {
            aggregationService.getCumulativeSpend(12, 2101);
        });
        assertEquals("Year must be between 1900 and 2100, got: 2101", exception2.getMessage());
    }

    @Test
    void testGetCumulativeSpendWithOnlyOneParameter() {
        // Test providing only month parameter
        IllegalArgumentException exception1 = assertThrows(IllegalArgumentException.class, () -> {
            aggregationService.getCumulativeSpend(12, null);
        });
        assertEquals("Both month and year parameters must be provided together or not at all", exception1.getMessage());

        // Test providing only year parameter
        IllegalArgumentException exception2 = assertThrows(IllegalArgumentException.class, () -> {
            aggregationService.getCumulativeSpend(null, 2024);
        });
        assertEquals("Both month and year parameters must be provided together or not at all", exception2.getMessage());
    }

    @Test
    void testGetCumulativeSpendWithCurrentMonth() {
        // Test that current month behavior is the same whether called with or without parameters
        LocalDate now = LocalDate.now();
        LocalDate currentMonthStart = now.withDayOfMonth(1);
        
        List<Transaction> transactions = Arrays.asList(
            createTransaction(currentMonthStart.plusDays(0), new BigDecimal("100.00")),
            createTransaction(currentMonthStart.plusDays(1), new BigDecimal("200.00"))
        );
        
        when(transactionService.getAllInRecentYear()).thenReturn(transactions);
        
        // Execute both versions
        CumulativeSpendResponse responseNoParams = aggregationService.getCumulativeSpend();
        CumulativeSpendResponse responseWithParams = aggregationService.getCumulativeSpend(now.getMonthValue(), now.getYear());
        
        // Verify both responses are identical
        assertEquals(responseNoParams.getCumulativeValues(), responseWithParams.getCumulativeValues());
    }

    @Test
    void testGetCumulativeSpendWithPastMonthFullData() {
        // Test that past months show full month data, not just up to current day
        LocalDate pastMonth = LocalDate.of(2023, 11, 1);
        List<Transaction> transactions = Arrays.asList(
            createTransaction(pastMonth.plusDays(0), new BigDecimal("100.00")),
            createTransaction(pastMonth.plusDays(29), new BigDecimal("200.00")) // Last day of November
        );
        
        when(transactionService.getAllInRecentYear()).thenReturn(transactions);
        
        // Execute
        CumulativeSpendResponse response = aggregationService.getCumulativeSpend(11, 2023);
        
        // Verify
        List<String> actualValues = response.getCumulativeValues();
        assertEquals(30, actualValues.size(), "November should have 30 days");
        
        // Check first and last days
        assertEquals("100.00", actualValues.get(0), "Day 1 should be first transaction");
        assertEquals("300.00", actualValues.get(29), "Day 30 should include both transactions");
    }
}