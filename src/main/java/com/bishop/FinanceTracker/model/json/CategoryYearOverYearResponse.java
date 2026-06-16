package com.bishop.FinanceTracker.model.json;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryYearOverYearResponse {

    private List<CategoryRow> categories;
    private double thisYearTotal;
    private double lastYearTotal;
    private String comparisonPeriod;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryRow {
        private String category;
        private double thisYear;
        private double lastYear;
        private double delta;
        private double deltaPercent;
    }
}
