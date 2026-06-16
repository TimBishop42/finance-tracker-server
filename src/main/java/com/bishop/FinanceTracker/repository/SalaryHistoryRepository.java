package com.bishop.FinanceTracker.repository;

import com.bishop.FinanceTracker.model.domain.SalaryEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SalaryHistoryRepository extends JpaRepository<SalaryEntry, Long> {
    List<SalaryEntry> findAllByOrderByEffectiveDateAsc();
}
