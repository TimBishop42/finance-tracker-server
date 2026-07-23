package com.bishop.FinanceTracker.model.wealth;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * The simplified "Kids' Portfolios" view — Chloe's and Millie's stock portfolios,
 * fully separate from the household {@link TotalWealthResponse}. Deliberately
 * minimal (no allocation, no snapshots, no wealth items/options) since these are
 * stock-only accounts.
 */
@Data
@Builder
public class KidsWealthResponse {
    private String displayCurrency; // AUD | USD
    private BigDecimal fxRateUsdAud;
    private boolean fxMissing;
    private KidPortfolioView chloe;
    private KidPortfolioView millie;
    private String asOf;
}
