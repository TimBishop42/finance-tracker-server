package com.bishop.FinanceTracker.repository;

import com.bishop.FinanceTracker.model.domain.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import reactor.core.publisher.Flux;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

}
