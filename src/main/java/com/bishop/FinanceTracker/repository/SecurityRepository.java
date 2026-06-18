package com.bishop.FinanceTracker.repository;

import com.bishop.FinanceTracker.model.domain.Security;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SecurityRepository extends JpaRepository<Security, Long> {
    List<Security> findAllByOrderByTickerAsc();

    Optional<Security> findByTickerIgnoreCaseAndExchangeIgnoreCase(String ticker, String exchange);
}
