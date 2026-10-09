package com.bishop.FinanceTracker.repository;

import com.bishop.FinanceTracker.model.domain.SecurityPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SecurityPriceRepository extends JpaRepository<SecurityPrice, Long> {
    Optional<SecurityPrice> findFirstBySecurityIdOrderByAsOfDateDesc(Long securityId);

    Optional<SecurityPrice> findFirstBySecurityIdAndAsOfDateLessThanEqualOrderByAsOfDateDesc(Long securityId, String asOfDate);

    Optional<SecurityPrice> findBySecurityIdAndAsOfDate(Long securityId, String asOfDate);
}
