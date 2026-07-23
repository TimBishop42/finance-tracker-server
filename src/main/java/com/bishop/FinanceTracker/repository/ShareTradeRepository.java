package com.bishop.FinanceTracker.repository;

import com.bishop.FinanceTracker.model.domain.ShareTrade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ShareTradeRepository extends JpaRepository<ShareTrade, Long> {
    // Chronological replay order for cost-basis / realised-P&L computation.
    //
    // Owner-scoped finders come in IsNull/value pairs rather than one method taking
    // a nullable owner: Spring Data does not translate a null bind parameter into
    // "IS NULL" (`owner = ?` with a null argument matches zero rows), so "household"
    // (owner IS NULL) and "a specific kid" need genuinely different queries.
    List<ShareTrade> findAllByOwnerIsNullOrderByTradeDateAscIdAsc();

    List<ShareTrade> findAllByOwnerOrderByTradeDateAscIdAsc(String owner);

    List<ShareTrade> findBySecurityIdAndOwnerIsNullOrderByTradeDateAscIdAsc(Long securityId);

    List<ShareTrade> findBySecurityIdAndOwnerOrderByTradeDateAscIdAsc(Long securityId, String owner);
}
