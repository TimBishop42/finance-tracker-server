package com.bishop.FinanceTracker.repository;

import com.bishop.FinanceTracker.model.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface CategoryRepository extends JpaRepository<Category, String> {
    
    @Modifying
    @Transactional
    @Query("DELETE FROM Category c WHERE c.categoryName = :categoryName")
    void deleteByCategoryName(@Param("categoryName") String categoryName);

    default void deleteByCategoryNameWithQuotes(String categoryName) {
        // Remove surrounding quotes if they exist
        String cleanName = categoryName.replaceAll("^\"|\"$", "");
        deleteByCategoryName(cleanName);
    }
}
