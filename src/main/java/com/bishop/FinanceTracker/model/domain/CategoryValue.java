package com.bishop.FinanceTracker.model.domain;

import lombok.Builder;
import lombok.Data;

@Data
public class CategoryValue {

    private String category;
    private Double value;

    public CategoryValue(String category) {
        this.category = category;
        this.value = 0.0;
    }

    public void incrementValue(Double value) {
        this.value += value;
    }
}
