package com.bishop.FinanceTracker.client;

import com.bishop.FinanceTracker.model.TransactionRaw;
import com.bishop.FinanceTracker.model.CategorizedTransaction;
import com.bishop.FinanceTracker.model.json.PredictedTransactionsJson;
import com.bishop.FinanceTracker.config.AppConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import lombok.Setter;

@Component
public class PredictionClient {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    @Setter
    private String mlServiceUrl;

    public List<CategorizedTransaction> predictBatch(List<TransactionRaw> transactions) {
        // Prepare request in ML service format
        Map<String, Object> request = new HashMap<>();
        request.put("transactions", transactions);
        request.put("categories", null);

        // Call ML service for prediction with the entire batch
        CategorizedTransaction[] categorizedTransactionArray = restTemplate.postForObject(
            mlServiceUrl,
            request,
            CategorizedTransaction[].class
        );
        List<CategorizedTransaction> categorizedTransactions = new ArrayList<>();
        if (categorizedTransactionArray != null) {
            for (CategorizedTransaction categorizedTransaction : categorizedTransactionArray) {
                categorizedTransactions.add(categorizedTransaction);
            }
        }
        return categorizedTransactions;
    }

    public ResponseEntity<Void> trainModel(PredictedTransactionsJson predictedTransactionsJson) {
        // Extract data for training request
        List<Map<String, Object>> transactions = new ArrayList<>();
        List<String> categories = new ArrayList<>();
        Map<String, String> userCorrections = new HashMap<>();

        predictedTransactionsJson.getTransactionJsonList().forEach(transaction -> {
            // Add transaction data
            Map<String, Object> transactionData = new HashMap<>();
            transactionData.put("date", transaction.getTransactionDate());
            transactionData.put("amount", transaction.getAmount());
            transactionData.put("business_name", transaction.getTransactionBusiness());
            transactionData.put("comment", transaction.getComment());
            transactions.add(transactionData);

            // Add category
            categories.add(transaction.getPredictedCategory());

            // If user corrected the category, add to corrections map
            if (transaction.getUserCorrectedCategory() != null) {
                userCorrections.put(String.valueOf(transactions.size() - 1),
                                  transaction.getUserCorrectedCategory());
            }
        });

        // Prepare training request
        Map<String, Object> trainingRequest = new HashMap<>();
        trainingRequest.put("transactions", transactions);
        trainingRequest.put("categories", categories);
        trainingRequest.put("user_corrections", userCorrections);

        // Call ML service training endpoint
        String trainingUrl = mlServiceUrl.replace("/predict/batch", "/train");
        return restTemplate.postForEntity(trainingUrl, trainingRequest, Void.class);
    }
}