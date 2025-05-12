package com.bishop.FinanceTracker.controller;

import com.bishop.FinanceTracker.client.TestWebClient;
import com.bishop.FinanceTracker.model.SaveTransactionResponse;
import com.bishop.FinanceTracker.model.domain.*;
import com.bishop.FinanceTracker.model.json.*;
import com.bishop.FinanceTracker.service.AggregationService;
import com.bishop.FinanceTracker.service.CategoryService;
import com.bishop.FinanceTracker.service.TransactionService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Data
@Slf4j
@RestController
@RequestMapping(value="/api/finance")
public class TrackerController {

    private final TransactionService transactionService;
    private final CategoryService categoryService;
    private final AggregationService aggregationService;
    private final TestWebClient testWebClient;

    @PostMapping("/submit-transaction")
    public Mono<ResponseEntity> submitTransaction(@RequestBody final TransactionJson transactionJson) {
        log.info("Received request to save new transaction: {}", transactionJson);
        return Mono.just(transactionService.addTransaction(transactionJson));
    }

    @PostMapping("/submit-transaction-batch")
    public Flux<SaveTransactionResponse> submitTransactions(@RequestBody final TransactionsJson transactionsJson) {
        log.info("Received request to save new transactions list: {}", transactionsJson);
        return transactionService.addNewTransactions(transactionsJson);
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

}
