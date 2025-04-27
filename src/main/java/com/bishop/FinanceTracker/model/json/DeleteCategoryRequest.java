package com.bishop.FinanceTracker.model.json;

import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

@Data
public class DeleteCategoryRequest {
    
    @NotBlank(message = "Category name cannot be empty")
    @Size(min = 1, max = 50, message = "Category name must be between 1 and 50 characters")
    @Pattern(regexp = "^[^\"']*$", message = "Category name cannot contain quotes")
    private String categoryName;
} 