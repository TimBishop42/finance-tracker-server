package com.bishop.FinanceTracker.repository;

import com.bishop.FinanceTracker.model.domain.ShareTrade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ShareTradeRepository extends JpaRepository<ShareTrade, Long> {
    // Chronological replay order for cost-basis / realised-P&L computation.
    List<ShareTrade> findAllByOrderByTradeDateAscIdAsc();

    List<ShareTrade> findBySecurityIdOrderByTradeDateAscIdAsc(Long securityId);
}
