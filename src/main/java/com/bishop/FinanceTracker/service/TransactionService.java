package com.bishop.FinanceTracker.service;

import com.bishop.FinanceTracker.model.SaveTransactionResponse;
import com.bishop.FinanceTracker.model.TrainingResponse;
import com.bishop.FinanceTracker.model.domain.Transaction;
import com.bishop.FinanceTracker.model.json.TransactionDeleteRequest;
import com.bishop.FinanceTracker.model.json.TransactionJson;
import com.bishop.FinanceTracker.model.json.TransactionsJson;
import com.bishop.FinanceTracker.model.json.PredictedTransactionsJson;
import com.bishop.FinanceTracker.repository.TransactionRepository;
import com.bishop.FinanceTracker.util.JsonValidator;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import javax.annotation.PostConstruct;
import javax.validation.ConstraintViolation;
import java.util.*;
import java.util.stream.Collectors;

import static com.bishop.FinanceTracker.util.DateUtil.getFirstDayOfYearEpochMilli;
import static com.bishop.FinanceTracker.util.DateUtil.getRecentMonthStartEpochMilli;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;


@Data
@Slf4j
@Service
public class TransactionService {

    private final JsonValidator jsonValidator;
    private final TransactionRepository transactionRepository;
    private Cache<Long, Transaction> transactionCache;
    private final TransactionPredictionService predictionService;

    private static final String CONSTRAINT_VIOLOATION_MESSAGE = "Error on field: %s. Reason: %s";

    @PostConstruct
    public void init() {
        transactionCache = Caffeine.newBuilder()
                .maximumSize(10000)
                .build();
        fetchAll().forEach(t -> transactionCache.put(t.getTransactionId(), t));
    }

    public ResponseEntity<Flux<SaveTransactionResponse>> addNewTransactions(TransactionsJson transactionsJson) {
        long startTime = System.currentTimeMillis();
        if (nonNull(transactionsJson.getTransactionJsonList()) && transactionsJson.getTransactionJsonList().size() > 0) {
            Set<ConstraintViolation<TransactionJson>> violations = new HashSet<>();
            transactionsJson.getTransactionJsonList().forEach(tj -> {
                violations.addAll(jsonValidator.validateJson(tj));
            });
            if (violations.size() > 0) {
                log.info("Failed to save new batch transactions due to constraint violations: {}", violations);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Flux.fromIterable(violations.stream().map(v -> SaveTransactionResponse.badRequest(
                        "Request Failed", null, v.getMessage())).collect(Collectors.toList())));
            }

            // Check for duplicates in cache only for non-reviewed transactions
            List<Transaction> transactions = transactionsJson.getTransactionJsonList().stream()
                    .filter(tj -> !tj.isDuplicateReviewed())
                    .map(Transaction::from)
                    .collect(Collectors.toList());

            if (!transactions.isEmpty()) {
                Map<Transaction, List<Transaction>> duplicateMap = findPotentialDuplicates(transactions);
                if (!duplicateMap.isEmpty()) {
                    return ResponseEntity.ok(Flux.just(SaveTransactionResponse.withDuplicates(
                            "Potential duplicates found. Please review before saving.",
                            transactionsJson.toString(),
                            duplicateMap
                    )));
                }
            }

            ResponseEntity<Flux<SaveTransactionResponse>> saveTransactionsResponse = ResponseEntity
                    .ok(Flux.fromStream(transactionsJson.getTransactionJsonList().stream()
                    .map(this::addNewTransaction)));
            log.info("Saved {} new transactions in {} milliseconds", transactionsJson.getTransactionJsonList().size(), System.currentTimeMillis() - startTime);

            return saveTransactionsResponse;
        } else {
            log.info("No Transactions present in transactionsJson. No updates will be made");
            return ResponseEntity.ok(Flux.just(SaveTransactionResponse.badRequest("No transaction present in payload",
                    transactionsJson.toString(), "Cannot save empty transaction list")));
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

        // Skip duplicate check if already reviewed
        if (!transactionJson.isDuplicateReviewed()) {
            // Check for duplicates in cache
            List<Transaction> potentialDuplicates = findPotentialDuplicates(newTransaction);
            if (!potentialDuplicates.isEmpty()) {
                Map<Transaction, List<Transaction>> duplicateMap = new HashMap<>();
                duplicateMap.put(newTransaction, potentialDuplicates);
                return SaveTransactionResponse.withDuplicates(
                        "Potential duplicates found. Please review before saving.",
                        transactionJson.toString(),
                        duplicateMap
                );
            }
        }

        try {
            transactionRepository.save(newTransaction);
        } catch (Exception e) {
            log.error("Failed saving new transaction");
            return SaveTransactionResponse.serverError(e.getMessage(), transactionJson.toString());
        }
        transactionCache.put(newTransaction.getTransactionId(), newTransaction);
        log.info("Saved new transaction: {} in {} milliseconds", newTransaction, System.currentTimeMillis() - startTime);
        return SaveTransactionResponse.success(String.format("Successfully saved transaction with id %s", newTransaction.getTransactionId()), newTransaction.toString(), "");
    }

    public ResponseEntity<SaveTransactionResponse> addTransaction(TransactionJson transactionJson) {
        SaveTransactionResponse response = addNewTransaction(transactionJson);
        return new ResponseEntity<>(response, response.getStatus());
    }

    public List<Transaction> getAll() {
        long startTime = System.currentTimeMillis();
        List<Transaction> transactions = transactionCache.asMap().values().stream().sorted(Comparator.comparing(Transaction::getTransactionDateTime)).collect(Collectors.toList());
        log.info("Successfully retrieved transactions in {} milliseconds", System.currentTimeMillis() - startTime);
        return transactions;
    }

    public List<Transaction> getAllInRecentYear() {
        //Hardcoded to 1st Jan for now
        long greaterThanDateTime = getFirstDayOfYearEpochMilli();
        long startTime = System.currentTimeMillis();
        List<Transaction> transactions = transactionCache.asMap()
                .values().stream()
//                .filter(t -> t.getTransactionDateTime() > greaterThanDateTime)
                .sorted(Comparator.comparing(Transaction::getTransactionDateTime)).collect(Collectors.toList());
        log.info("Successfully retrieved transactions in {} milliseconds", System.currentTimeMillis() - startTime);
        return transactions;
    }

    public List<Transaction> getAllWithOptionalFilters(String categoryNameFilter, Boolean recentMonthFilter) {
        long greaterThanDateTime;
        if (nonNull(recentMonthFilter) && recentMonthFilter) {
            greaterThanDateTime = getRecentMonthStartEpochMilli();
        }//1000 to match against millis, Z for UTC
        else {
            greaterThanDateTime = 0;
        }
        long startTime = System.currentTimeMillis();
        List<Transaction> transactions = transactionCache.asMap()
                .values().stream()
                .filter(t -> t.getTransactionDateTime() > greaterThanDateTime)
                .filter(t -> isNull(categoryNameFilter) || t.getCategory().equalsIgnoreCase(categoryNameFilter))
                .sorted(Comparator.comparing(Transaction::getTransactionDateTime).reversed()).collect(Collectors.toList());
        log.info("Successfully retrieved {} transactions in {} milliseconds", transactions.size(), System.currentTimeMillis() - startTime);
        return transactions;
    }

    private List<Transaction> fetchAll() {
        long startTime = System.currentTimeMillis();
        List<Transaction> transactions = transactionRepository.findAll();
        log.info("Successfully retrieved all {} transactions in {} milliseconds", transactions.size(), System.currentTimeMillis() - startTime);
        return transactions;
    }

    @Scheduled(cron = "0 */5 * * * *")
    private void updateTransactionsCache() {
        //Nuke cache
        transactionCache.invalidateAll();
        //Update with new transactions
        fetchAll().forEach(t -> transactionCache.put(t.getTransactionId(), t));
        log.info("Cache updated with {} values", transactionCache.estimatedSize());
    }

    public void deleteTransaction(TransactionDeleteRequest request) {
        long startTime = System.currentTimeMillis();
        Transaction transaction = transactionCache.getIfPresent(request.getTransactionId());
        if (isNull(transaction)) {
            log.error("Transaction with id {} not found in cache", request.getTransactionId());
            throw new IllegalArgumentException("Transaction not found");
        }
        transactionRepository.deleteById(request.getTransactionId());
        transactionCache.invalidate(request.getTransactionId());
        log.info("Deleted transaction with id {} in {} milliseconds", request.getTransactionId(), System.currentTimeMillis() - startTime);
    }

    public ResponseEntity<Flux<SaveTransactionResponse>> handlePredictedTransactions(PredictedTransactionsJson predictedTransactionsJson) {
        TransactionsJson transactionsJson = predictedTransactionsJson.toTransactionsJson();
        return addNewTransactions(transactionsJson);
    }

    //Called for single transaction save check
    private List<Transaction> findPotentialDuplicates(Transaction transaction) {
        return transactionCache.asMap().values().stream()
                .filter(t -> t.getTransactionDate().equals(transaction.getTransactionDate()) &&
                        t.getAmount().compareTo(transaction.getAmount()) == 0 &&
                        (t.getBusinessName() == null && (transaction.getBusinessName() == null || transaction.getBusinessName().isEmpty()) ||
                                t.getBusinessName() != null && transaction.getBusinessName() != null &&
                                        t.getBusinessName().equalsIgnoreCase(transaction.getBusinessName())) &&
                        t.getCategory().equalsIgnoreCase(transaction.getCategory()))
                .collect(Collectors.toList());
    }

    // Called for batch save
    private Map<Transaction, List<Transaction>> findPotentialDuplicates(List<Transaction> transactions) {
        Map<Transaction, List<Transaction>> duplicatesMap = new HashMap<>();

        transactions.forEach(newTransaction -> {
            List<Transaction> duplicates = transactionCache.asMap().values().stream()
                    .filter(t -> t.getTransactionDate().equals(newTransaction.getTransactionDate()) &&
                            t.getAmount().compareTo(newTransaction.getAmount()) == 0 &&
                            (t.getBusinessName() == null && (newTransaction.getBusinessName() == null || newTransaction.getBusinessName().isEmpty()) ||
                                    t.getBusinessName() != null && newTransaction.getBusinessName() != null &&
                                            t.getBusinessName().equalsIgnoreCase(newTransaction.getBusinessName())) &&
                            t.getCategory().equalsIgnoreCase(newTransaction.getCategory()))
                    .collect(Collectors.toList());

            if (!duplicates.isEmpty()) {
                duplicatesMap.put(newTransaction, duplicates);
            }
        });

        return duplicatesMap;
    }

    public TrainingResponse triggerFullModelTraining() {
        log.info("Starting full model training process");

        // Get all transactions from cache
        List<Transaction> allTransactions = getAll();
        log.info("Retrieved {} transactions from cache", allTransactions.size());

        // Filter out transactions with null or empty business names
        List<Transaction> validTransactions = allTransactions.stream()
            .filter(t -> nonNull(t.getBusinessName()) && !t.getBusinessName().trim().isEmpty())
            .collect(Collectors.toList());

        log.info("Filtered to {} transactions with valid business names", validTransactions.size());

        if (validTransactions.isEmpty()) {
            log.warn("No valid transactions found for training (all have null/empty business names)");
            return TrainingResponse.builder()
                .success(false)
                .message("No valid transactions found for training - all transactions have null or empty business names")
                .transactionCount(0)
                .build();
        }

        // Convert transactions to training format and call ML service
        return predictionService.trainModelWithAllTransactions(validTransactions);
    }
}
