package com.bishop.FinanceTracker.service;

import com.bishop.FinanceTracker.client.PredictionClient;
import com.bishop.FinanceTracker.model.TransactionRaw;
import com.bishop.FinanceTracker.model.CategorizedTransaction;
import com.bishop.FinanceTracker.model.TrainingResponse;
import com.bishop.FinanceTracker.model.json.PredictedTransactionsJson;
import com.bishop.FinanceTracker.model.domain.Transaction;
import com.bishop.FinanceTracker.model.dto.TrainRequestDto;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import java.util.ArrayList;
import java.util.List;
import java.util.Date;
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

    public TrainingResponse trainModelWithAllTransactions(List<Transaction> transactions) {
        log.info("Starting ML model training with {} transactions from database", transactions.size());

        try {
            // Convert domain transactions to training request format
            TrainRequestDto trainRequest = convertToTrainRequest(transactions);

            // Call PredictionClient with the training request
            predictionClient.trainModelDirect(trainRequest);

            log.info("Successfully completed ML model training");
            return TrainingResponse.builder()
                .success(true)
                .message("Successfully trained model with all transactions")
                .transactionCount(transactions.size())
                .build();
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.BAD_REQUEST) {
                log.error("Invalid training data: {}", e.getMessage());
                return TrainingResponse.builder()
                    .success(false)
                    .message("Invalid training data: " + e.getMessage())
                    .transactionCount(transactions.size())
                    .build();
            }
            log.error("Client error during ML model training: {}", e.getMessage());
            return TrainingResponse.builder()
                .success(false)
                .message("Client error: " + e.getMessage())
                .transactionCount(transactions.size())
                .build();
        } catch (HttpServerErrorException e) {
            log.error("Server error during ML model training: {}", e.getMessage());
            return TrainingResponse.builder()
                .success(false)
                .message("Server error: " + e.getMessage())
                .transactionCount(transactions.size())
                .build();
        }
    }

    private TrainRequestDto convertToTrainRequest(List<Transaction> transactions) {
        TrainRequestDto request = new TrainRequestDto();

        for (int i = 0; i < transactions.size(); i++) {
            Transaction t = transactions.get(i);

            // Convert transaction to DTO
            TrainRequestDto.TransactionDto dto = new TrainRequestDto.TransactionDto();
            dto.setTransactionId(i + 1); // Use index as temporary ID
            dto.setDate(new Date(t.getTransactionDateTime()).toInstant().toString());
            dto.setAmount(t.getAmount().floatValue());
            dto.setBusinessName(t.getBusinessName());
            dto.setComment(t.getComment());

            request.getTransactions().add(dto);
            request.getCategories().add(t.getCategory());
            request.getConfidenceScores().add(1.0f); // Full confidence for existing categorized transactions
        }

        // No user corrections for full training - we trust the existing categories
        // request.getUserCorrections() is already initialized as empty HashMap

        return request;
    }
}