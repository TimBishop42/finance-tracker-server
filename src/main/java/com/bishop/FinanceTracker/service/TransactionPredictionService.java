package com.bishop.FinanceTracker.service;

import com.bishop.FinanceTracker.model.TransactionRaw;
import com.bishop.FinanceTracker.model.CategorizedTransaction;
import com.bishop.FinanceTracker.client.PredictionClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;

@Service
public class TransactionPredictionService {

    @Autowired
    private PredictionClient predictionClient;

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
} 