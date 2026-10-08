package com.bishop.FinanceTracker.repository;

import com.bishop.FinanceTracker.model.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, String> {
}
