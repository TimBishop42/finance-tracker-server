package com.bishop.FinanceTracker.model.json;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Wealth half of the month-in-review, all in AUD. Month-end values come from the
 * net-worth snapshot taken on (or before) the 1st of the next month, or the live
 * figures for the in-progress month; {@code opening} is the prior month's close.
 * Either end is null when no snapshot covers it.
 */
public record WealthReview(
        Point opening,
        Point closing,
        BigDecimal netWorthChange,
        BigDecimal netWorthChangePercent,
        /* Null unless both ends carry an asset-class breakdown. */
        Waterfall waterfall,
        List<ClassChange> allocation,
        /* Household holdings, largest gain first. */
        List<HoldingMove> holdings,
        /* Month-end net worth for up to 24 months, oldest first; months without a snapshot are omitted. */
        List<TrendPoint> trend,
        List<KidReview> kids,
        /* A USD conversion was needed but no FX rate is set (USD treated 1:1). */
        boolean fxMissing) {

    public record Point(String asOf, BigDecimal netWorth, BigDecimal totalAssets, BigDecimal totalLiabilities,
                        Map<String, BigDecimal> breakdown) {
    }

    /**
     * opening + savings + investmentGrowth + equityComp + other = closing, exactly.
     * savings = the month's income - spend; investmentGrowth = change in SHARES less
     * net money put in via trades; equityComp = change in vested OPTIONS; other =
     * everything else (property/super revaluations, liabilities, cash not matching
     * cashflow).
     */
    public record Waterfall(BigDecimal opening, BigDecimal savings, BigDecimal investmentGrowth,
                            BigDecimal equityComp, BigDecimal other, BigDecimal closing) {
    }

    public record ClassChange(String assetClass, BigDecimal opening, BigDecimal closing, BigDecimal change) {
    }

    /**
     * gain = closing value - opening value - net money put in during the month;
     * returnPercent is gain over (opening value + money put in). Converted at the
     * latest FX rate, so FX moves don't show as gains.
     */
    public record HoldingMove(String ticker, String name, BigDecimal value, BigDecimal gain,
                              BigDecimal returnPercent, boolean priceEstimated) {
    }

    public record TrendPoint(String month, BigDecimal value, Map<String, BigDecimal> breakdown) {
    }

    /** growth = closing - opening - contributions; values from trades + prices, not snapshots. */
    public record KidReview(String owner, BigDecimal openingValue, BigDecimal closingValue,
                            BigDecimal contributions, BigDecimal growth, List<HoldingMove> holdings,
                            List<TrendPoint> trend) {
    }
}
