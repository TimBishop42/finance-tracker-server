package com.bishop.FinanceTracker.repository;

import com.bishop.FinanceTracker.model.domain.WealthItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WealthItemRepository extends JpaRepository<WealthItem, Long> {
    List<WealthItem> findByArchivedFalseOrderByAssetClassAscNameAsc();

    Optional<WealthItem> findFirstByAssetClassAndArchivedFalse(String assetClass);
}
