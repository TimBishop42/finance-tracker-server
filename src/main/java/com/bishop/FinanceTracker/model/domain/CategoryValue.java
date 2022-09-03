package com.bishop.FinanceTracker.model.domain;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CategoryValue {

    private String category;
    private Double value;

    public CategoryValue(String category) {
        value = 0.0;
    }
}
