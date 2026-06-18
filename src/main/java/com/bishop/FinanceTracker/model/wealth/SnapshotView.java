package com.bishop.FinanceTracker.model.wealth;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/** A net-worth snapshot point converted to the view currency for the over-time chart. */
@Data
@Builder
public class SnapshotView {
    private String asOfDate;
    private BigDecimal totalAssets;
    private BigDecimal totalLiabilities;
    private BigDecimal netWorth;
}
