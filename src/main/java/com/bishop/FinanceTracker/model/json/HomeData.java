package com.bishop.FinanceTracker.model.json;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class HomeData {
    private String status;
    private Integer currentMonth;
    private Integer priorMonth;
}
