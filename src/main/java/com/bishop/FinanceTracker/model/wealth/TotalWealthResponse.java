package com.bishop.FinanceTracker.model.wealth;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** The assembled Total Wealth view in a single requested display currency. */
@Data
@Builder
public class TotalWealthResponse {
    private String displayCurrency;   // AUD | USD

    private BigDecimal netWorth;
    private BigDecimal totalAssets;
    private BigDecimal totalLiabilities;

    private BigDecimal deltaVsPrevious;   // vs the most recent prior snapshot (display ccy), nullable
    private Double deltaPct;              // nullable

    private BigDecimal fxRateUsdAud;      // latest USD->AUD rate used (null if none set)
    private boolean fxMissing;            // true if a conversion was needed but no rate exists

    private List<HoldingView> holdings;
    private List<WealthItemView> items;
    private List<AllocationSlice> allocation;
    private List<SnapshotView> snapshots;

    private String asOf;                  // yyyy-MM-dd
}
