package com.bishop.FinanceTracker.model.wealth;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

/** A net-worth snapshot point converted to the view currency for the over-time chart. */
@Data
@Builder
public class SnapshotView {
    private String asOfDate;
    private BigDecimal totalAssets;
    private BigDecimal totalLiabilities;
    private BigDecimal netWorth;
    /**
     * Per-asset-class totals for this point, converted to the view currency, so a
     * category drill-down can chart its own history.
     * <p>
     * {@code null} means this snapshot predates breakdown capture (or its JSON was
     * unreadable) — the value for any class is unknown, so callers must skip the
     * point rather than plot a zero. A non-null map missing a class means that
     * class was genuinely worth zero on that date: {@code runSnapshot} only writes
     * non-zero classes.
     */
    private Map<String, BigDecimal> breakdown;
}
