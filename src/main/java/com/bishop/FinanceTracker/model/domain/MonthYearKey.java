package com.bishop.FinanceTracker.model.domain;

import lombok.Builder;
import lombok.Data;


@Data
@Builder
public class MonthYearKey {
    private String month;
    private int year;

}
