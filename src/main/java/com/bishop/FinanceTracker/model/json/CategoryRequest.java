package com.bishop.FinanceTracker.model.json;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Data
public class CategoryRequest {

    @NotBlank(message = "Category name cannot be empty")
    @Size(min = 1, max = 50, message = "Category name must be between 1 and 50 characters")
    private String categoryName;
}