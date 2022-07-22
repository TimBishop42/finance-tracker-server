package com.bishop.FinanceTracker.service;

import com.bishop.FinanceTracker.model.SaveTransactionResponse;
import com.bishop.FinanceTracker.model.domain.Transaction;
import com.bishop.FinanceTracker.model.json.TransactionJson;
import com.bishop.FinanceTracker.model.json.TransactionsJson;
import com.bishop.FinanceTracker.repository.TransactionRepository;
import com.bishop.FinanceTracker.util.JsonValidator;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import javax.annotation.PostConstruct;
import javax.validation.ConstraintViolation;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Objects.nonNull;


@Data
@Slf4j
@Service
public class TransactionService {

    private final JsonValidator jsonValidator;
    private final TransactionRepository transactionRepository;
    private Cache<Long, Transaction> transactionCache;

    private static final String CONSTRAINT_VIOLOATION_MESSAGE = "Error on field: %s. Reason: %s";

    @PostConstruct
    public void init() {
        transactionCache = Caffeine.newBuilder()
                .maximumSize(10000)
                .build();
        fetchAll().stream().forEach(t -> transactionCache.put(t.getTransactionId(), t));
    }

    public Flux<SaveTransactionResponse> addNewTransactions(TransactionsJson transactionsJson) {
        long startTime = System.currentTimeMillis();
        if (nonNull(transactionsJson.getTransactionJsonList()) && transactionsJson.getTransactionJsonList().size() > 0) {
            Set<ConstraintViolation<TransactionJson>> violations = new HashSet<>();
            transactionsJson.getTransactionJsonList().stream().forEach(tj -> {
                violations.addAll(jsonValidator.validateJson(tj));
            });
            if (violations.size() > 0) {
                return Flux.fromIterable(violations.stream().map(v -> SaveTransactionResponse.badRequest("Request Failed", null, v.getMessage())).collect(Collectors.toList()));
            }
            return Flux.fromStream(transactionsJson.getTransactionJsonList().stream()
                    .map(tj -> addNewTransaction(tj)));
        } else {
            log.info("No Transactions present in transactionsJson. No updates will be made");
            return Flux.just(SaveTransactionResponse.badRequest("No transaction present in payload", transactionsJson.toString(), "Cannot save empty transaction list"));
        }
    }

    public SaveTransactionResponse addNewTransaction(TransactionJson transactionJson) {
        long startTime = System.currentTimeMillis();
        Set<ConstraintViolation<TransactionJson>> validationResult = jsonValidator.validateJson(transactionJson);
        if (validationResult.size() > 0) {
            log.error("Failed to save new transaction, constraint violation on input: {}", validationResult);
            StringBuilder constraintViolation = new StringBuilder();
            validationResult
                    .forEach(cv -> constraintViolation.append(String.format(CONSTRAINT_VIOLOATION_MESSAGE, cv.getPropertyPath(), cv.getMessage())));
            return SaveTransactionResponse.badRequest(constraintViolation.toString(), transactionJson.toString(), validationResult.toString());
        }
        Transaction newTransaction = Transaction.from(transactionJson);
        try {
            transactionRepository.save(newTransaction);
        } catch (Exception e) {
            log.error("Failed saving new transaction");
            return SaveTransactionResponse.serverError(e.getMessage(), transactionJson.toString());
        }
        transactionCache.put(newTransaction.getTransactionId(), newTransaction);
        log.info("Saved new transaction: {} in {} milliseconds", newTransaction, System.currentTimeMillis() - startTime);
        return SaveTransactionResponse.success(String.format("Successfully saved transaction with id %s", newTransaction.getTransactionId()), newTransaction.toString(), null);
    }

    public ResponseEntity addTransaction(TransactionJson transactionJson) {
        SaveTransactionResponse response = addNewTransaction(transactionJson);
        return new ResponseEntity(response, response.getStatus());
    }

    public List<Transaction> getAll() {
        long startTime = System.currentTimeMillis();
        List<Transaction> transactions = new ArrayList<>(transactionCache.asMap().values());
        log.info("Successfully retrieved transactions in {} milliseconds", System.currentTimeMillis() - startTime);
        return transactions;
    }

    private List<Transaction> fetchAll() {
        long startTime = System.currentTimeMillis();
        List<Transaction> transactions = transactionRepository.findAll();
        log.info("Successfully retrieved transactions in {} milliseconds", System.currentTimeMillis() - startTime);
        return transactions;
    }
}
