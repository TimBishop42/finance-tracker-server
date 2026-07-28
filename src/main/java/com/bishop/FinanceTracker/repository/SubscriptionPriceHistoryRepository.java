package com.bishop.FinanceTracker.repository;

import com.bishop.FinanceTracker.model.domain.SubscriptionPriceHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SubscriptionPriceHistoryRepository extends JpaRepository<SubscriptionPriceHistory, Long> {

    List<SubscriptionPriceHistory> findBySubscriptionIdOrderByEffectiveDateAsc(Long subscriptionId);

    void deleteBySubscriptionId(Long subscriptionId);
}
