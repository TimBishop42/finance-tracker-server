package com.bishop.FinanceTracker.repository;

import com.bishop.FinanceTracker.model.domain.NeutralMerchant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NeutralMerchantRepository extends JpaRepository<NeutralMerchant, String> {
}
