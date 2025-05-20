package com.bishop.FinanceTracker.client;

import com.bishop.FinanceTracker.model.TransactionRaw;
import com.bishop.FinanceTracker.model.CategorizedTransaction;
import com.bishop.FinanceTracker.model.json.PredictedTransactionsJson;
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

        // Call ML service for prediction with the entire batch
        PredictResponse response = restTemplate.postForObject(
            mlServiceUrl,
            request,
            PredictResponse.class
        );

        log.info("Temp Logging: prediction response: {}", response);
        
        List<CategorizedTransaction> categorizedTransactions = new ArrayList<>();
        if (response != null && response.getCategorizedTransactions() != null) {
            for (CategorizedTransaction categorizedTransaction : response.getCategorizedTransactions()) {
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
                dto.setDate(transaction.getTransactionDate().toString());
                dto.setAmount(Float.parseFloat(transaction.getAmount()));
                dto.setBusinessName(transaction.getTransactionBusiness());
                dto.setComment(transaction.getComment());
                return dto;
            })
            .collect(Collectors.toList()));

        request.setCategories(predictedTransactionsJson.getTransactionJsonList().stream()
            .map(transaction -> transaction.getPredictedCategory())
            .collect(Collectors.toList()));

        request.setConfidenceScores(predictedTransactionsJson.getTransactionJsonList().stream()
            .map(transaction -> 1.0f) // Default confidence score
            .collect(Collectors.toList()));

        // Build user corrections map
        request.setUserCorrections(predictedTransactionsJson.getTransactionJsonList().stream()
            .filter(transaction -> transaction.getUserCorrectedCategory() != null)
            .collect(Collectors.toMap(
                transaction -> predictedTransactionsJson.getTransactionJsonList().indexOf(transaction),
                transaction -> transaction.getUserCorrectedCategory()
            )));

        // Call ML service training endpoint
        String trainingUrl = mlServiceUrl.replace("/predict/batch", "/train");
        return restTemplate.postForEntity(trainingUrl, request, Void.class);
    }
}