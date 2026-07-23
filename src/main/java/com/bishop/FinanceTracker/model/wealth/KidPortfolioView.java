package com.bishop.FinanceTracker.model.wealth;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * One kid's stock portfolio: totals in the requested display currency plus the
 * per-security holdings backing them. Reuses {@link HoldingView} as-is — the
 * average-cost/valuation logic is identical to the household portfolio, just
 * scoped to that kid's trades.
 */
@Data
@Builder
public class KidPortfolioView {
    private String owner; // CHLOE | MILLIE
    private BigDecimal portfolioValueDisplay;
    private BigDecimal unrealisedPlDisplay;
    private BigDecimal realisedPlDisplay;
    private List<HoldingView> holdings;
    private List<KidSnapshotView> snapshots;
}
