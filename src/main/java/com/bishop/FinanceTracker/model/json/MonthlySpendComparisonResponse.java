package com.bishop.FinanceTracker.model.json;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class MonthlySpendComparisonResponse {
    @JsonProperty("currentMonthSpend")
    private String currentMonthSpend;

    @JsonProperty("priorMonthSpend")
    private String priorMonthSpend;

    @JsonProperty("comparisonToPriorMonth")
    private String comparisonToPriorMonth;
} 