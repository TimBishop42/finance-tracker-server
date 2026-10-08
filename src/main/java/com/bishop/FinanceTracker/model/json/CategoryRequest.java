package com.bishop.FinanceTracker.model.json;

import lombok.Data;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Data
public class CategoryRequest {

    @NotBlank(message = "Category name cannot be empty")
    @Size(min = 1, max = 50, message = "Category name must be between 1 and 50 characters")
    private String categoryName;

    /** Only read by set-category-budget; null (or omitted) deliberately clears the budget. */
    @Digits(integer = 8, fraction = 2, message = "Monthly budget must have at most 8 digits and 2 decimals")
    @PositiveOrZero(message = "Monthly budget cannot be negative")
    private BigDecimal monthlyBudget;
}