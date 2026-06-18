package com.bishop.FinanceTracker.repository;

import com.bishop.FinanceTracker.model.domain.NetWorthSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NetWorthSnapshotRepository extends JpaRepository<NetWorthSnapshot, Long> {
    List<NetWorthSnapshot> findAllByOrderByAsOfDateAsc();

    Optional<NetWorthSnapshot> findByAsOfDate(String asOfDate);
}
