package com.bishop.FinanceTracker.repository;

import com.bishop.FinanceTracker.model.domain.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    /** Used by the migration to skip manual_bills rows already copied across. */
    Optional<Subscription> findByLegacyManualBillId(Long legacyManualBillId);

    /** Detection dedupe: promote-once by normalised merchant key. */
    List<Subscription> findByNormalizedKey(String normalizedKey);
}
