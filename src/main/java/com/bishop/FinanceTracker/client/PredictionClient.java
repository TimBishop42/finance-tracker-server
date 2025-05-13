package com.bishop.FinanceTracker.client;

import com.bishop.FinanceTracker.model.TransactionRaw;
import com.bishop.FinanceTracker.model.CategorizedTransaction;
import com.bishop.FinanceTracker.config.AppConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import java.util.List;
import java.util.ArrayList;

@Component
public class PredictionClient {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private String mlServiceUrl;

    public List<CategorizedTransaction> predictBatch(List<TransactionRaw> transactions) {
        // Call ML service for prediction with the entire batch
        CategorizedTransaction[] categorizedTransactionArray = restTemplate.postForObject(
            mlServiceUrl,
            transactions,
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
} 