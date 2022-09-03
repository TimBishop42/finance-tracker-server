package com.bishop.FinanceTracker.model.domain;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Month;
import java.util.List;

@Data
@AllArgsConstructor
public class SummarizingMonth {

    private Month month;
    private Integer year;

    private List<CategoryValue> categoryValues;
}
