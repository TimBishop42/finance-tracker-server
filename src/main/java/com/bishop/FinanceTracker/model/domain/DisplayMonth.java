package com.bishop.FinanceTracker.model.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.Month;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
public class DisplayMonth {

    private Month month;
    private Integer year;

    private Collection<CategoryValue> categoryValues;

    public static DisplayMonth to(SummarizingMonth summarizingMonth) {
        return DisplayMonth.builder()
                .month(summarizingMonth.getMonth())
                .year(summarizingMonth.getYear())
                .categoryValues(summarizingMonth.getCategoryValues().values())
                .build();
    }
}
