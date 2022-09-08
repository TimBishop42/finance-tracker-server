package com.bishop.FinanceTracker.model.domain;

import lombok.Data;

@Data
public class CategoryValue {

    private String category;
    private String month;
    private Double value;
    private Integer intValue;

    public CategoryValue(String category, String month) {
        this.category = category;
        this.month = month;
        this.value = 0.0;
        this.intValue = 0;
    }

    public void incrementValue(Double value) {
        this.value += value;
        this.intValue = this.value.intValue();
    }
}
