package com.bishop.FinanceTracker.repository;

import com.bishop.FinanceTracker.model.domain.KidPortfolioSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface KidPortfolioSnapshotRepository extends JpaRepository<KidPortfolioSnapshot, Long> {
    List<KidPortfolioSnapshot> findAllByOwnerOrderByAsOfDateAsc(String owner);

    Optional<KidPortfolioSnapshot> findByOwnerAndAsOfDate(String owner, String asOfDate);
}
