package com.bishop.FinanceTracker.repository;

import com.bishop.FinanceTracker.model.domain.StockSplit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StockSplitRepository extends JpaRepository<StockSplit, Long> {
    List<StockSplit> findAllByOrderByExDateAscIdAsc();

    List<StockSplit> findBySecurityIdOrderByExDateAscIdAsc(Long securityId);
}
