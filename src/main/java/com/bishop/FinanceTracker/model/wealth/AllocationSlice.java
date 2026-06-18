package com.bishop.FinanceTracker.model.wealth;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/** One slice of the asset-allocation donut (value in the view currency). */
@Data
@Builder
public class AllocationSlice {
    private String assetClass;
    private BigDecimal value;
    private Double pct;
}
