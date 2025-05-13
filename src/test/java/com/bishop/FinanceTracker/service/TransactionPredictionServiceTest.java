package com.bishop.FinanceTracker.service;

import com.bishop.FinanceTracker.model.TransactionRaw;
import com.bishop.FinanceTracker.model.CategorizedTransaction;
import com.bishop.FinanceTracker.client.PredictionClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

public class TransactionPredictionServiceTest {

    @Mock
    private PredictionClient predictionClient;

    @InjectMocks
    private TransactionPredictionService predictionService;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void testPredictBatch() {
        // Arrange
        List<TransactionRaw> transactionRawJsonList = new ArrayList<>();
        TransactionRaw transaction1 = new TransactionRaw();
        transaction1.setTransactionDate(new Date());
        transaction1.setTransactionAmount(25.50f);
        transaction1.setTransactionBusiness("STARBUCKS");
        transactionRawJsonList.add(transaction1);

        TransactionRaw transaction2 = new TransactionRaw();
        transaction2.setTransactionDate(new Date());
        transaction2.setTransactionAmount(45.00f);
        transaction2.setTransactionBusiness("UBER");
        transactionRawJsonList.add(transaction2);

        List<CategorizedTransaction> expectedCategorizedTransactions = new ArrayList<>();
        CategorizedTransaction categorizedTransaction1 = new CategorizedTransaction();
        categorizedTransaction1.setTransactionId(1);
        categorizedTransaction1.setPredictedCategory("FOOD");
        categorizedTransaction1.setConfidenceScore(0.95f);
        expectedCategorizedTransactions.add(categorizedTransaction1);

        CategorizedTransaction categorizedTransaction2 = new CategorizedTransaction();
        categorizedTransaction2.setTransactionId(2);
        categorizedTransaction2.setPredictedCategory("TRANSPORT");
        categorizedTransaction2.setConfidenceScore(0.85f);
        expectedCategorizedTransactions.add(categorizedTransaction2);

        when(predictionClient.predictBatch(anyList())).thenReturn(expectedCategorizedTransactions);

        // Act
        List<CategorizedTransaction> result = predictionService.predictBatch(transactionRawJsonList);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(1, result.get(0).getTransactionId());
        assertEquals("FOOD", result.get(0).getPredictedCategory());
        assertEquals(0.95f, result.get(0).getConfidenceScore());
        assertEquals(2, result.get(1).getTransactionId());
        assertEquals("TRANSPORT", result.get(1).getPredictedCategory());
        assertEquals(0.85f, result.get(1).getConfidenceScore());

        verify(predictionClient, times(1)).predictBatch(anyList());
    }
} 