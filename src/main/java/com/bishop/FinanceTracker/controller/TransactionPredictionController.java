package com.bishop.FinanceTracker.controller;

import com.bishop.FinanceTracker.model.TransactionRaw;
import com.bishop.FinanceTracker.model.CategorizedTransaction;
import com.bishop.FinanceTracker.service.TransactionPredictionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/transactions")
public class TransactionPredictionController {

    @Autowired
    private TransactionPredictionService predictionService;

    @PostMapping("/predict-batch")
    public ResponseEntity<List<CategorizedTransaction>> predictBatch(@RequestBody List<TransactionRaw> transactionRawJsonList) {
        List<CategorizedTransaction> result = predictionService.predictBatch(transactionRawJsonList);
        return ResponseEntity.ok(result);
    }
} 