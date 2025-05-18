package com.bishop.FinanceTracker.service;

import com.bishop.FinanceTracker.client.PredictionClient;
import com.bishop.FinanceTracker.model.TransactionRaw;
import com.bishop.FinanceTracker.model.CategorizedTransaction;
import com.bishop.FinanceTracker.model.TrainingResponse;
import com.bishop.FinanceTracker.model.json.PredictedTransactionsJson;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class TransactionPredictionService {

    private final PredictionClient predictionClient;

    public TransactionPredictionService(PredictionClient predictionClient) {
        this.predictionClient = predictionClient;
    }

    public List<CategorizedTransaction> predictBatch(List<TransactionRaw> transactionRawJsonList) {
        // Assign temporary IDs to each transaction
        List<TransactionRaw> transactionsWithIds = new ArrayList<>();
        for (int i = 0; i < transactionRawJsonList.size(); i++) {
            TransactionRaw transaction = transactionRawJsonList.get(i);
            transaction.setId(i + 1); // Temporary ID
            transactionsWithIds.add(transaction);
        }

        // Call the PredictionClient to get categorized transactions
        return predictionClient.predictBatch(transactionsWithIds);
    }

    public TrainingResponse trainModel(PredictedTransactionsJson predictedTransactionsJson) {
        int transactionCount = predictedTransactionsJson.getTransactionJsonList().size();
        log.info("Starting ML model training with {} transactions", transactionCount);
        
        try {
            predictionClient.trainModel(predictedTransactionsJson);
            log.info("Successfully completed ML model training");
            return TrainingResponse.builder()
                .success(true)
                .message("Successfully trained model")
                .transactionCount(transactionCount)
                .build();
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.BAD_REQUEST) {
                log.error("Invalid training data: {}", e.getMessage());
                return TrainingResponse.builder()
                    .success(false)
                    .message("Invalid training data: " + e.getMessage())
                    .transactionCount(transactionCount)
                    .build();
            }
            log.error("Client error during ML model training: {}", e.getMessage());
            return TrainingResponse.builder()
                .success(false)
                .message("Client error: " + e.getMessage())
                .transactionCount(transactionCount)
                .build();
        } catch (HttpServerErrorException e) {
            log.error("Server error during ML model training: {}", e.getMessage());
            return TrainingResponse.builder()
                .success(false)
                .message("Server error: " + e.getMessage())
                .transactionCount(transactionCount)
                .build();
        }
    }
} 