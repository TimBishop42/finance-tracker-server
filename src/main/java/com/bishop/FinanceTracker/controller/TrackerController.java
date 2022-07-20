package com.bishop.FinanceTracker.controller;

import com.bishop.FinanceTracker.model.SaveTransactionResponse;
import com.bishop.FinanceTracker.model.domain.Category;
import com.bishop.FinanceTracker.model.domain.Transaction;
import com.bishop.FinanceTracker.model.json.TransactionJson;
import com.bishop.FinanceTracker.model.json.TransactionsJson;
import com.bishop.FinanceTracker.service.CategoryService;
import com.bishop.FinanceTracker.service.TransactionService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

@Data
@Slf4j
@RestController
@RequestMapping(value="/finance")
public class TrackerController {

    private final TransactionService transactionService;
    private final CategoryService categoryService;

    @PostMapping("/submit-transaction")
    @CrossOrigin(origins = "http://localhost:3000")
    public Mono<SaveTransactionResponse> submitTransaction(@RequestBody final TransactionJson transactionJson) {
        log.info("Received request to save new transaction: {}", transactionJson);
        return Mono.just(transactionService.addNewTransaction(transactionJson));
    }

    @PostMapping("/submit-transaction-batch")
    @CrossOrigin(origins = "http://localhost:3000")
    public Flux<SaveTransactionResponse> submitTransactions(@RequestBody final TransactionsJson transactionsJson) {
        log.info("Received request to save new transaction: {}", transactionsJson);
        return transactionService.addNewTransactions(transactionsJson);
    }

    @GetMapping("/find-all-transactions")
    public Flux<Transaction> getAllTransactions() {
        log.info("Received request to get all transactions");
        return Flux.fromIterable(transactionService.getAll());
    }

    @GetMapping("/get-categories")
    @CrossOrigin(origins = "http://localhost:3000")
    public Mono<List<Category>> getAllCategories() {
        log.info("Received request to get all categories");
        return Mono.just(categoryService.getAllCategories());
    }

    @PostMapping("/add-category")
    public Mono<String> addCategory(@RequestBody String category) {
        log.info("Received request to add category");
        return Mono.just(categoryService.addCategory(category));
    }

    @GetMapping("/flux-test")
    public Flux<Integer> getFlux() {
        return Flux.just(1,2,3,4,5).delayElements(Duration.ofSeconds(1));
    }

}
