package com.bishop.FinanceTracker.repository;

import com.bishop.FinanceTracker.model.domain.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

}
