package com.bishop.FinanceTracker.controller;

import com.bishop.FinanceTracker.client.TestWebClient;
import com.bishop.FinanceTracker.model.SaveTransactionResponse;
import com.bishop.FinanceTracker.model.domain.*;
import com.bishop.FinanceTracker.model.json.DeleteCategoryRequest;
import com.bishop.FinanceTracker.model.json.HomeData;
import com.bishop.FinanceTracker.model.json.TransactionJson;
import com.bishop.FinanceTracker.model.json.TransactionsJson;
import com.bishop.FinanceTracker.service.AggregationService;
import com.bishop.FinanceTracker.service.CategoryService;
import com.bishop.FinanceTracker.service.TransactionService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Collection;
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
    private final TestWebClient testWebClient;

    @PostMapping("/submit-transaction")
    public Mono<ResponseEntity> submitTransaction(@RequestBody final TransactionJson transactionJson) {
        log.info("Received request to save new transaction: {}", transactionJson);
        return Mono.just(transactionService.addTransaction(transactionJson));
    }

    @PostMapping("/submit-transaction-batch")
    public Flux<SaveTransactionResponse> submitTransactions(@RequestBody final TransactionsJson transactionsJson) {
        log.info("Received request to save new transaction: {}", transactionsJson);
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
    public Mono<List<DisplayMonth>> getSummaryMonths() {
        log.info("Received request to get summary months");
        return Mono.just(aggregationService.aggregateDisplayMonths());
    }

    @PostMapping("/add-category")
    public Mono<String> addCategory(@RequestBody String category) {
        log.info("Received request to add category");
        return Mono.just(categoryService.addCategory(category));
    }

    @GetMapping("/flux-test")
    public Flux<Integer> getFlux() {
        return Flux.just(1,2,3,4,5).delayElements(Duration.ofSeconds(2));
    }

    @GetMapping("/web-test")
    public ResponseEntity triggerFlux() {
        testWebClient.triggerFlux();
        return ResponseEntity.ok().build();
    }

    @GetMapping("/submit-test")
    public ResponseEntity triggerInsert() {
        testWebClient.triggerFlux();
        return ResponseEntity.ok().build();
    }

    @GetMapping("/get-home-data")
    public Mono<HomeData> getHomeData() {
        log.info("Received request to collect home-assistant data");
        return Mono.just(aggregationService.homeData());
    }

    @PostMapping("/delete-category")
    public Mono<ResponseEntity<String>> deleteCategory(@RequestBody DeleteCategoryRequest request) {
        log.info("Received request to delete category: {}", request.getCategoryName());
        try {
            categoryService.deleteCategory(request);
            return Mono.just(ResponseEntity.ok("Category deleted successfully"));
        } catch (IllegalArgumentException e) {
            return Mono.just(ResponseEntity.badRequest().body(e.getMessage()));
        }
    }

}
