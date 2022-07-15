package com.bishop.FinanceTracker.service;

import com.bishop.FinanceTracker.model.domain.Transaction;
import com.bishop.FinanceTracker.model.json.TransactionJson;
import com.bishop.FinanceTracker.repository.TransactionRepository;
import com.bishop.FinanceTracker.util.JsonValidator;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import javax.validation.ConstraintViolation;
import java.util.Set;


@Data
@Slf4j
@Service
public class TransactionService {

    private final JsonValidator jsonValidator;
    private final TransactionRepository transactionRepository;

    public ResponseEntity addNewTransaction(TransactionJson transactionJson) {
        Set<ConstraintViolation<TransactionJson>> validationResult = jsonValidator.validateJson(transactionJson);
        if(validationResult.size() > 0) {
            log.error("Failed to save new transaction, constraint violation on input: {}", validationResult);
            return new ResponseEntity(validationResult.toString(), HttpStatus.BAD_REQUEST);
        }
        Transaction newTransaction = Transaction.from(transactionJson);
        try {
            transactionRepository.save(newTransaction);
        }
        catch(Exception e) {
            log.error("Failed saving new transaction");
            return new ResponseEntity(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
        log.info("Saved new transaction: {}", newTransaction);
        return ResponseEntity.ok().build();
    }

    public Flux<Transaction> getAll() {
        return Flux.fromIterable(transactionRepository.findAll());
    }
}
