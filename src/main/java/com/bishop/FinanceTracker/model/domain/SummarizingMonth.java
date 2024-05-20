package com.bishop.FinanceTracker.model.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.Month;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
public class SummarizingMonth {

    private Month month;
    private Integer year;

    private Double totalSpend;

    private Map<String, CategoryValue> categoryValues;
}
