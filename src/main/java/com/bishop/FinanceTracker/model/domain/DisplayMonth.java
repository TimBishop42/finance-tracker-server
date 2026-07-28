package com.bishop.FinanceTracker.model.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.Month;
import java.util.Collection;

@Data
@Builder
@AllArgsConstructor
public class DisplayMonth {

    private Month month;
    private Integer year;

    private Double totalMonthlySpend;

    private Double totalMonthlyIncome;

    private Collection<CategoryValue> categoryValues;

    public static DisplayMonth to(SummarizingMonth summarizingMonth) {
        DisplayMonth month = DisplayMonth.builder()
                .month(summarizingMonth.getMonth())
                .year(summarizingMonth.getYear())
                .totalMonthlySpend(0.0)
                .totalMonthlyIncome(summarizingMonth.getTotalIncome() == null ? 0.0 : summarizingMonth.getTotalIncome())
                .categoryValues(summarizingMonth.getCategoryValues().values())
                .build();
        month.calcMonthlySpend();
        return month;
    }

    public void calcMonthlySpend() {
        categoryValues
                .forEach(c -> incSpend(c.getValue()));
    }

    private void incSpend(Double val) {
        totalMonthlySpend += val;
    }
}
