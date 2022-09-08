package com.bishop.FinanceTracker.model.domain;

import lombok.Data;

@Data
public class CategoryValue {

    private String category;
    private String month;
    private Integer value;

    public CategoryValue(String category, String month) {
        this.category = category;
        this.month = month;
        this.value = 0;
    }

    public void incrementValue(Double value) {
        this.value += value.intValue();
    }
}
