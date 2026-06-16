package com.bishop.FinanceTracker.repository;

import com.bishop.FinanceTracker.model.domain.ExcludedMerchant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ExcludedMerchantRepository extends JpaRepository<ExcludedMerchant, String> {
}
