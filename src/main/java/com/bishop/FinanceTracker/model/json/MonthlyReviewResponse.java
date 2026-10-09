package com.bishop.FinanceTracker.model.json;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Everything the month-in-review walkthrough shows for one calendar month.
 * Averages are over up to the 12 months before {@code month} (fewer when history
 * is shorter; null when there is none). Money is AUD, rounded to cents.
 */
public record MonthlyReviewResponse(
        String month,
        /* Latest transaction date on file — tells the reader whether the month is fully imported. */
        LocalDate dataThrough,
        Totals totals,
        /* Oldest first: the averaged months, then {@code month} itself — for sparklines. */
        List<MonthPoint> history,
        List<CategoryRow> categories,
        List<MerchantRow> topMerchants,
        List<MerchantRow> newMerchants,
        List<TransactionRow> biggestTransactions,
        SubscriptionChanges subscriptionChanges,
        Cumulative cumulative,
        WealthReview wealth) {

    public record Totals(BigDecimal spend, BigDecimal income, BigDecimal net, BigDecimal savingsRatePercent,
                         BigDecimal spendTarget, BigDecimal lastMonthSpend, BigDecimal lastMonthIncome,
                         BigDecimal averageSpend, BigDecimal averageIncome, int averageMonths) {
    }

    public record MonthPoint(String month, BigDecimal spend, BigDecimal income) {
    }

    /** {@code history} lines up with the response's {@code history}; trend is null with < 3 months of data. */
    public record CategoryRow(String category, BigDecimal spend, BigDecimal budget, BigDecimal lastMonth,
                              BigDecimal average, Trend trend, List<BigDecimal> history) {
    }

    public enum Trend { UP, DOWN, FLAT }

    public record MerchantRow(String merchant, BigDecimal amount, int count) {
    }

    public record TransactionRow(LocalDate date, String merchant, String category, BigDecimal amount) {
    }

    public record SubscriptionChanges(List<CommitmentRow> started, List<PriceChangeRow> priceChanges,
                                      List<CommitmentRow> trialsEnded) {
    }

    public record CommitmentRow(String name, BigDecimal amount, String billingCycle, LocalDate date) {
    }

    public record PriceChangeRow(String name, BigDecimal oldAmount, BigDecimal newAmount, LocalDate date) {
    }

    /** Running spend per day; {@code thisMonth} stops at today while the month is in progress. */
    public record Cumulative(List<BigDecimal> thisMonth, List<BigDecimal> lastMonth) {
    }
}
