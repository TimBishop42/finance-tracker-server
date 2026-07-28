package com.bishop.FinanceTracker.repository;

import com.bishop.FinanceTracker.model.domain.NotifiedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NotifiedEventRepository extends JpaRepository<NotifiedEvent, String> {
    boolean existsByEventKey(String eventKey);
}
