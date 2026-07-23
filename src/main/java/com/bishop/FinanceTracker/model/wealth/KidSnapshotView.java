package com.bishop.FinanceTracker.model.wealth;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/** One point-in-time portfolio value, converted to the view currency for the over-time chart. */
@Data
@Builder
public class KidSnapshotView {
    private String asOfDate;
    private BigDecimal valueDisplay;
}
