package com.bishop.FinanceTracker.repository;

import com.bishop.FinanceTracker.model.domain.OptionGrant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OptionGrantRepository extends JpaRepository<OptionGrant, Long> {
    List<OptionGrant> findByArchivedFalseOrderByNameAsc();
}
