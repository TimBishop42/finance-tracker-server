package com.bishop.FinanceTracker.repository;

import com.bishop.FinanceTracker.model.domain.FxRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FxRateRepository extends JpaRepository<FxRate, Long> {
    Optional<FxRate> findFirstByBaseCcyAndQuoteCcyOrderByAsOfDateDesc(String baseCcy, String quoteCcy);

    Optional<FxRate> findByBaseCcyAndQuoteCcyAndAsOfDate(String baseCcy, String quoteCcy, String asOfDate);
}
