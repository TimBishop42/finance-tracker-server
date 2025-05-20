package com.bishop.FinanceTracker.controller;

import com.bishop.FinanceTracker.model.TransactionRaw;
import com.bishop.FinanceTracker.model.CategorizedTransaction;
import com.bishop.FinanceTracker.service.TransactionPredictionService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@Data
@Slf4j
@RestController
@RequestMapping("/api/transactions")
public class TransactionPredictionController {

    private final TransactionPredictionService predictionService;

    @PostMapping("/predict-batch")
    public ResponseEntity<List<CategorizedTransaction>> predictBatch(@RequestBody List<TransactionRaw> transactionRawJsonList) {
        log.info("Received request to predict categories of {} transactions", transactionRawJsonList.size());
        List<CategorizedTransaction> result = predictionService.predictBatch(transactionRawJsonList);
        return ResponseEntity.ok(result);
    }
}