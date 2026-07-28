package com.bishop.FinanceTracker.repository;

import com.bishop.FinanceTracker.model.domain.ManualBill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ManualBillRepository extends JpaRepository<ManualBill, Long> {
}
