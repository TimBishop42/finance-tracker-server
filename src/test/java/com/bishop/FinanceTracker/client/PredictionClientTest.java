package com.bishop.FinanceTracker.client;

import com.bishop.FinanceTracker.model.TransactionRaw;
import com.bishop.FinanceTracker.model.CategorizedTransaction;
import com.bishop.FinanceTracker.model.dto.PredictRequestDto;
import com.bishop.FinanceTracker.model.dto.PredictResponse;
import com.bishop.FinanceTracker.model.dto.TrainRequestDto;
import com.bishop.FinanceTracker.model.json.PredictedTransactionsJson;
import com.bishop.FinanceTracker.model.json.PredictedTransactionJson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.HashMap;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import org.mockito.ArgumentCaptor;

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
        Date date1 = new Date();
        transaction1.setTransactionDate(date1);
        transaction1.setTransactionAmount(25.50f);
        transaction1.setTransactionBusiness("STARBUCKS");
        transactions.add(transaction1);

        TransactionRaw transaction2 = new TransactionRaw();
        transaction2.setId(2);
        Date date2 = new Date();
        transaction2.setTransactionDate(date2);
        transaction2.setTransactionAmount(45.00f);
        transaction2.setTransactionBusiness("UBER");
        transactions.add(transaction2);

        List<CategorizedTransaction> categorizedTransactions = new ArrayList<>();
        CategorizedTransaction categorizedTransaction1 = new CategorizedTransaction();
        categorizedTransaction1.setTransactionId(1);
        categorizedTransaction1.setPredictedCategory("FOOD");
        categorizedTransaction1.setConfidenceScore(0.95f);
        categorizedTransactions.add(categorizedTransaction1);

        CategorizedTransaction categorizedTransaction2 = new CategorizedTransaction();
        categorizedTransaction2.setTransactionId(2);
        categorizedTransaction2.setPredictedCategory("TRANSPORT");
        categorizedTransaction2.setConfidenceScore(0.85f);
        categorizedTransactions.add(categorizedTransaction2);

        PredictResponse response = new PredictResponse();
        response.setCategorizedTransactions(categorizedTransactions);

        // Capture the request to verify its format
        ArgumentCaptor<PredictRequestDto> requestCaptor = ArgumentCaptor.forClass(PredictRequestDto.class);
        when(restTemplate.postForObject(eq(mlServiceUrl), requestCaptor.capture(), eq(PredictResponse.class)))
            .thenReturn(response);

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

        // Verify request format
        PredictRequestDto capturedRequest = requestCaptor.getValue();
        assertNotNull(capturedRequest);
        assertNotNull(capturedRequest.getTransactions());
        assertNull(capturedRequest.getCategories());

        List<PredictRequestDto.TransactionDto> capturedTransactions = capturedRequest.getTransactions();
        assertEquals(2, capturedTransactions.size());

        // Verify first transaction
        PredictRequestDto.TransactionDto firstTransaction = capturedTransactions.get(0);
        assertEquals(1, firstTransaction.getTransactionId());
        assertEquals(date1.toInstant().toString(), firstTransaction.getDate());
        assertEquals(25.50f, firstTransaction.getAmount());
        assertEquals("STARBUCKS", firstTransaction.getBusinessName());

        // Verify second transaction
        PredictRequestDto.TransactionDto secondTransaction = capturedTransactions.get(1);
        assertEquals(2, secondTransaction.getTransactionId());
        assertEquals(date2.toInstant().toString(), secondTransaction.getDate());
        assertEquals(45.00f, secondTransaction.getAmount());
        assertEquals("UBER", secondTransaction.getBusinessName());

        verify(restTemplate, times(1)).postForObject(eq(mlServiceUrl), any(PredictRequestDto.class), eq(PredictResponse.class));
    }

    @Test
    public void testPredictBatchWithEmptyList() {
        // Arrange
        List<TransactionRaw> transactions = new ArrayList<>();
        PredictResponse response = new PredictResponse();
        response.setCategorizedTransactions(new ArrayList<>());

        ArgumentCaptor<PredictRequestDto> requestCaptor = ArgumentCaptor.forClass(PredictRequestDto.class);
        when(restTemplate.postForObject(eq(mlServiceUrl), requestCaptor.capture(), eq(PredictResponse.class)))
            .thenReturn(response);

        // Act
        List<CategorizedTransaction> result = predictionClient.predictBatch(transactions);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());

        // Verify request format
        PredictRequestDto capturedRequest = requestCaptor.getValue();
        assertNotNull(capturedRequest);
        assertNotNull(capturedRequest.getTransactions());
        assertTrue(capturedRequest.getTransactions().isEmpty());
        assertNull(capturedRequest.getCategories());
    }

    @Test
    public void testTrainModel() {
        // Arrange
        PredictedTransactionsJson predictedTransactionsJson = new PredictedTransactionsJson();
        List<PredictedTransactionJson> transactions = new ArrayList<>();
        
        long date1 = new Date().getTime();
        PredictedTransactionJson transaction1 = PredictedTransactionJson.builder()
            .transactionDate(date1)
            .amount("25.50")
            .transactionBusiness("STARBUCKS")
            .predictedCategory("FOOD")
            .userCorrectedCategory("COFFEE")
            .essential(false)
            .confidenceScore(0.95f)
            .build();
        transactions.add(transaction1);

        long date2 = new Date().getTime();
        PredictedTransactionJson transaction2 = PredictedTransactionJson.builder()
            .transactionDate(date2)
            .amount("45.00")
            .transactionBusiness("UBER")
            .predictedCategory("TRANSPORT")
            .userCorrectedCategory(null)
            .essential(false)
            .confidenceScore(0.85f)
            .build();
        transactions.add(transaction2);

        predictedTransactionsJson.setTransactionJsonList(transactions);

        String trainingUrl = mlServiceUrl.replace("/predict/batch", "/train");
        ArgumentCaptor<TrainRequestDto> requestCaptor = ArgumentCaptor.forClass(TrainRequestDto.class);
        when(restTemplate.postForEntity(eq(trainingUrl), requestCaptor.capture(), eq(Void.class)))
            .thenReturn(new ResponseEntity<>(HttpStatus.OK));

        // Act
        ResponseEntity<Void> response = predictionClient.trainModel(predictedTransactionsJson);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());

        // Verify request format
        TrainRequestDto capturedRequest = requestCaptor.getValue();
        assertNotNull(capturedRequest);
        assertNotNull(capturedRequest.getTransactions());
        assertEquals(2, capturedRequest.getTransactions().size());
        assertNotNull(capturedRequest.getCategories());
        assertEquals(2, capturedRequest.getCategories().size());
        assertNotNull(capturedRequest.getConfidenceScores());
        assertEquals(2, capturedRequest.getConfidenceScores().size());
        assertNotNull(capturedRequest.getUserCorrections());
        assertEquals(1, capturedRequest.getUserCorrections().size());

        // Verify first transaction
        TrainRequestDto.TransactionDto firstTransaction = capturedRequest.getTransactions().get(0);
        assertEquals(String.valueOf(date1), firstTransaction.getDate());
        assertEquals(25.50f, firstTransaction.getAmount());
        assertEquals("STARBUCKS", firstTransaction.getBusinessName());

        // Verify second transaction
        TrainRequestDto.TransactionDto secondTransaction = capturedRequest.getTransactions().get(1);
        assertEquals(String.valueOf(date2), secondTransaction.getDate());
        assertEquals(45.00f, secondTransaction.getAmount());
        assertEquals("UBER", secondTransaction.getBusinessName());

        // Verify categories (should use userCorrectedCategory if available, otherwise predictedCategory)
        assertEquals("COFFEE", capturedRequest.getCategories().get(0));
        assertEquals("TRANSPORT", capturedRequest.getCategories().get(1));

        // Verify confidence scores
        assertEquals(0.95f, capturedRequest.getConfidenceScores().get(0));
        assertEquals(0.85f, capturedRequest.getConfidenceScores().get(1));

        // Verify user corrections (only for transactions with userCorrectedCategory)
        assertEquals("COFFEE", capturedRequest.getUserCorrections().get("0"));

        verify(restTemplate, times(1)).postForEntity(eq(trainingUrl), any(TrainRequestDto.class), eq(Void.class));
    }

    @Test
    public void testTrainModelWithEmptyList() {
        // Arrange
        PredictedTransactionsJson predictedTransactionsJson = new PredictedTransactionsJson();
        predictedTransactionsJson.setTransactionJsonList(new ArrayList<>());

        String trainingUrl = mlServiceUrl.replace("/predict/batch", "/train");
        ArgumentCaptor<TrainRequestDto> requestCaptor = ArgumentCaptor.forClass(TrainRequestDto.class);
        when(restTemplate.postForEntity(eq(trainingUrl), requestCaptor.capture(), eq(Void.class)))
            .thenReturn(new ResponseEntity<>(HttpStatus.OK));

        // Act
        ResponseEntity<Void> response = predictionClient.trainModel(predictedTransactionsJson);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());

        // Verify request format
        TrainRequestDto capturedRequest = requestCaptor.getValue();
        assertNotNull(capturedRequest);
        assertNotNull(capturedRequest.getTransactions());
        assertTrue(capturedRequest.getTransactions().isEmpty());
        assertNotNull(capturedRequest.getCategories());
        assertTrue(capturedRequest.getCategories().isEmpty());
        assertNotNull(capturedRequest.getConfidenceScores());
        assertTrue(capturedRequest.getConfidenceScores().isEmpty());
        assertNotNull(capturedRequest.getUserCorrections());
        assertTrue(capturedRequest.getUserCorrections().isEmpty());
    }
}