package com.bishop.FinanceTracker.client;

import com.bishop.FinanceTracker.model.TransactionRaw;
import com.bishop.FinanceTracker.model.CategorizedTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.client.RestTemplate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

public class PredictionClientTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private PredictionClient predictionClient;

    private String mlServiceUrl = "http://localhost:8000/api/v1/predict/batch";

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
        predictionClient.setMlServiceUrl(mlServiceUrl);
    }

    @Test
    public void testPredictBatch() {
        // Arrange
        List<TransactionRaw> transactions = new ArrayList<>();
        TransactionRaw transaction1 = new TransactionRaw();
        transaction1.setId(1);
        transaction1.setTransactionDate(new Date());
        transaction1.setTransactionAmount(25.50f);
        transaction1.setTransactionBusiness("STARBUCKS");
        transactions.add(transaction1);

        TransactionRaw transaction2 = new TransactionRaw();
        transaction2.setId(2);
        transaction2.setTransactionDate(new Date());
        transaction2.setTransactionAmount(45.00f);
        transaction2.setTransactionBusiness("UBER");
        transactions.add(transaction2);

        CategorizedTransaction[] categorizedTransactionArray = new CategorizedTransaction[2];
        CategorizedTransaction categorizedTransaction1 = new CategorizedTransaction();
        categorizedTransaction1.setTransactionId(1);
        categorizedTransaction1.setPredictedCategory("FOOD");
        categorizedTransaction1.setConfidenceScore(0.95f);
        categorizedTransactionArray[0] = categorizedTransaction1;

        CategorizedTransaction categorizedTransaction2 = new CategorizedTransaction();
        categorizedTransaction2.setTransactionId(2);
        categorizedTransaction2.setPredictedCategory("TRANSPORT");
        categorizedTransaction2.setConfidenceScore(0.85f);
        categorizedTransactionArray[1] = categorizedTransaction2;

        when(restTemplate.postForObject(eq(mlServiceUrl), any(Map.class), eq(CategorizedTransaction[].class)))
            .thenReturn(categorizedTransactionArray);

        // Act
        List<CategorizedTransaction> result = predictionClient.predictBatch(transactions);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(1, result.get(0).getTransactionId());
        assertEquals("FOOD", result.get(0).getPredictedCategory());
        assertEquals(0.95f, result.get(0).getConfidenceScore());
        assertEquals(2, result.get(1).getTransactionId());
        assertEquals("TRANSPORT", result.get(1).getPredictedCategory());
        assertEquals(0.85f, result.get(1).getConfidenceScore());

        verify(restTemplate, times(1)).postForObject(eq(mlServiceUrl), any(Map.class), eq(CategorizedTransaction[].class));
    }
}