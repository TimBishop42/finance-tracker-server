package com.bishop.FinanceTracker.controller;

import com.bishop.FinanceTracker.model.SaveTransactionResponse;
import com.bishop.FinanceTracker.model.domain.*;
import com.bishop.FinanceTracker.model.json.*;
import com.bishop.FinanceTracker.service.AggregationService;
import com.bishop.FinanceTracker.service.CategoryService;
import com.bishop.FinanceTracker.service.TransactionService;
import com.bishop.FinanceTracker.service.UserSettingsService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import org.springframework.http.HttpStatus;
import com.bishop.FinanceTracker.model.TrainingResponse;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@Slf4j
@RestController
@RequestMapping(value="/api/finance")
public class TrackerController {

    private final TransactionService transactionService;
    private final CategoryService categoryService;
    private final AggregationService aggregationService;
    private final UserSettingsService userSettingsService;

    @PostMapping("/submit-transaction")
    public Mono<ResponseEntity> submitTransaction(@RequestBody final TransactionJson transactionJson) {
        log.info("Received request to save new transaction: {}", transactionJson);
        return Mono.just(transactionService.addTransaction(transactionJson));
    }

    @PostMapping("/submit-transaction-batch")
    public ResponseEntity<Flux<SaveTransactionResponse>> submitTransactions(@RequestBody final PredictedTransactionsJson transactionsJson) {
        log.info("Received request to save new transactions list: {}", transactionsJson);
        return transactionService.handlePredictedTransactions(transactionsJson);
    }

    @GetMapping("/find-all-transactions")
    public Flux<Transaction> getAllTransactions(
            @RequestParam(value = "categoryName", required = false) String categoryName,
            @RequestParam(value = "recentMonth", required = false) Boolean recentMonth) {
        log.info("Received request to get all transactions");
        return Flux.fromIterable(transactionService.getAllWithOptionalFilters(categoryName, recentMonth));
    }

    @GetMapping("/get-categories")
    @CrossOrigin(origins = "http://localhost:3000")
    public Mono<List<Category>> getAllCategories() {
        log.info("Received request to get all categories");
        return Mono.just(categoryService.getAllCategories());
    }

    @GetMapping("/get-summary-months")
    public Mono<List<DisplayMonth>> getSummaryMonths(@RequestParam(required = false) Integer months) {
        if (months == null) {
            months = 3;
        }
        log.info("Received request to get summary months for {} months of data", months);
        return Mono.just(aggregationService.aggregateDisplayMonths(months));
    }

    @PostMapping("/add-category")
    public Mono<String> addCategory(@RequestBody CategoryRequest request) {
        log.info("Received request to add category: {}", request.getCategoryName());
        return Mono.just(categoryService.addCategory(request.getCategoryName()));
    }

    @GetMapping("/get-home-data")
    public Mono<HomeData> getHomeData() {
        log.info("Received request to collect home-assistant data");
        return Mono.just(aggregationService.homeData());
    }

    @PostMapping("/delete-category")
    public Mono<ResponseEntity<String>> deleteCategory(@RequestBody CategoryRequest request) {
        log.info("Received request to delete category: {}", request.getCategoryName());
        try {
            categoryService.deleteCategory(request);
            return Mono.just(ResponseEntity.ok("Category deleted successfully"));
        } catch (IllegalArgumentException e) {
            return Mono.just(ResponseEntity.badRequest().body(e.getMessage()));
        }
    }

    @PostMapping("/delete-transaction")
    public Mono<ResponseEntity<String>> deleteTransaction(@RequestBody TransactionDeleteRequest request) {
        log.info("Received request to delete transaction: {}", request.getTransactionId());
        try {
            transactionService.deleteTransaction(request);
            return Mono.just(ResponseEntity.ok("Transaction deleted successfully"));
        } catch (IllegalArgumentException e) {
            return Mono.just(ResponseEntity.badRequest().body(e.getMessage()));
        }
    }

    @GetMapping("/monthly-spend-comparison")
    public ResponseEntity<MonthlySpendComparisonResponse> getMonthlySpendComparison() {
        log.info("Received request for monthly spend comparison");
        try {
            MonthlySpendComparisonResponse response = aggregationService.getMonthlySpendComparison();
            log.info("Successfully retrieved monthly spend comparison");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error retrieving monthly spend comparison", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/get-cumulative-spend")
    public ResponseEntity<CumulativeSpendResponse> getCumulativeSpend(
            @RequestParam(value = "month", required = false) Integer month,
            @RequestParam(value = "year", required = false) Integer year) {
        
        if (month != null && year != null) {
            log.info("Received request for cumulative spend data for month {} year {}", month, year);
        } else {
            log.info("Received request for cumulative spend data for current month");
        }
        
        try {
            CumulativeSpendResponse response = aggregationService.getCumulativeSpend(month, year);
            log.info("Successfully retrieved cumulative spend data");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid request parameters: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error retrieving cumulative spend data", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/get-max-spend-value")
    public ResponseEntity<BigDecimal> getMaxSpendValue() {
        log.info("Received request for max spend value");
        try {
            BigDecimal value = userSettingsService.getMaxSpendValue();
            log.info("Successfully retrieved max spend value: {}", value);
            return ResponseEntity.ok(value);
        } catch (Exception e) {
            log.error("Error retrieving max spend value", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/set-max-spend-value")
    public ResponseEntity<Void> setMaxSpendValue(@RequestBody BigDecimal maxSpendValue) {
        log.info("Received request to set max spend value: {}", maxSpendValue);
        try {
            userSettingsService.setMaxSpendValue(maxSpendValue);
            log.info("Successfully set max spend value");
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            log.error("Invalid max spend value: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error setting max spend value", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/train-model")
    public ResponseEntity<TrainingResponse> triggerModelTraining() {
        log.info("Received request to trigger full ML model training");
        try {
            TrainingResponse response = transactionService.triggerFullModelTraining();
            if (response.isSuccess()) {
                log.info("Successfully completed full model training with {} transactions", response.getTransactionCount());
                return ResponseEntity.ok(response);
            } else {
                log.error("Model training failed: {}", response.getMessage());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
            }
        } catch (Exception e) {
            log.error("Error during model training", e);
            TrainingResponse errorResponse = TrainingResponse.builder()
                .success(false)
                .message("Training failed with error: " + e.getMessage())
                .transactionCount(0)
                .build();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

}
