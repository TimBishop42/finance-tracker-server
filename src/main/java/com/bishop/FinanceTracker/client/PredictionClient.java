package com.bishop.FinanceTracker.client;

import com.bishop.FinanceTracker.model.TransactionRaw;
import com.bishop.FinanceTracker.model.CategorizedTransaction;
import com.bishop.FinanceTracker.model.json.PredictedTransactionsJson;
import com.bishop.FinanceTracker.model.json.PredictedTransactionJson;
import com.bishop.FinanceTracker.model.dto.PredictRequestDto;
import com.bishop.FinanceTracker.model.dto.PredictResponse;
import com.bishop.FinanceTracker.model.dto.TrainRequestDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import java.util.Map;
import java.util.HashMap;

@Slf4j
@Component
public class PredictionClient {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    @Setter
    private String mlServiceUrl;

    public List<CategorizedTransaction> predictBatch(List<TransactionRaw> transactions) {
        // Convert to DTO
        PredictRequestDto request = new PredictRequestDto();
        request.setTransactions(transactions.stream()
            .map(transaction -> {
                PredictRequestDto.TransactionDto dto = new PredictRequestDto.TransactionDto();
                dto.setTransactionId(transaction.getId());
                dto.setDate(transaction.getTransactionDate().toInstant().toString());
                dto.setAmount(transaction.getTransactionAmount());
                dto.setBusinessName(transaction.getTransactionBusiness());
                return dto;
            })
            .collect(Collectors.toList()));
        request.setCategories(null);

        log.info("Sending prediction request to ML service: {}", request);

        // Call ML service for prediction with the entire batch
        PredictResponse response = restTemplate.postForObject(
            mlServiceUrl,
            request,
            PredictResponse.class
        );

        log.info("Received prediction response from ML service: {}", response);
        
        List<CategorizedTransaction> categorizedTransactions = new ArrayList<>();
        if (response != null && response.getCategorizedTransactions() != null) {
            for (CategorizedTransaction categorizedTransaction : response.getCategorizedTransactions()) {
                log.info("Processing categorized transaction: {}", categorizedTransaction);
                categorizedTransactions.add(categorizedTransaction);
            }
        }
        log.info("Predicted {} transactions", categorizedTransactions.size());
        return categorizedTransactions;
    }

    public ResponseEntity<Void> trainModel(PredictedTransactionsJson predictedTransactionsJson) {
        // Convert to DTO
        TrainRequestDto request = new TrainRequestDto();
        request.setTransactions(predictedTransactionsJson.getTransactionJsonList().stream()
            .map(transaction -> {
                TrainRequestDto.TransactionDto dto = new TrainRequestDto.TransactionDto();
                dto.setDate(String.valueOf(transaction.getTransactionDate()));
                dto.setAmount(Float.parseFloat(transaction.getAmount()));
                dto.setBusinessName(transaction.getTransactionBusiness());
                return dto;
            })
            .collect(Collectors.toList()));

        // Map categories and confidence scores
        request.setCategories(predictedTransactionsJson.getTransactionJsonList().stream()
            .map(transaction -> transaction.getUserCorrectedCategory() != null ? 
                transaction.getUserCorrectedCategory() : 
                transaction.getPredictedCategory())
            .collect(Collectors.toList()));

        request.setConfidenceScores(predictedTransactionsJson.getTransactionJsonList().stream()
            .map(transaction -> transaction.getConfidenceScore())
            .collect(Collectors.toList()));

        // Map user corrections to a map of index -> category
        Map<String, String> userCorrections = new HashMap<>();
        List<PredictedTransactionJson> transactions = predictedTransactionsJson.getTransactionJsonList();
        for (int i = 0; i < transactions.size(); i++) {
            PredictedTransactionJson transaction = transactions.get(i);
            if (transaction.getUserCorrectedCategory() != null) {
                userCorrections.put(String.valueOf(i), transaction.getUserCorrectedCategory());
            }
        }
        request.setUserCorrections(userCorrections);

        // Call ML service for training
        return restTemplate.postForEntity(
            mlServiceUrl.replace("/predict/batch", "/train"),
            request,
            Void.class
        );
    }

    public ResponseEntity<Void> trainModelDirect(TrainRequestDto request) {
        log.info("Sending direct training request to ML service with {} transactions", request.getTransactions().size());
        
        // Call ML service for training
        String trainingUrl = mlServiceUrl.replace("/predict/batch", "/train");
        return restTemplate.postForEntity(
            trainingUrl,
            request,
            Void.class
        );
    }
}