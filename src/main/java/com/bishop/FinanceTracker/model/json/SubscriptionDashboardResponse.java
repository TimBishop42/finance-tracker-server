package com.bishop.FinanceTracker.model.json;

import com.bishop.FinanceTracker.model.recurring.RecurringCandidate;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * Aggregated view for the Subscription dashboard (feature doc §2A.3): normalised
 * monthly/yearly totals, budget vs remaining, breakdowns, and the automatic
 * cancel-candidate list.
 */
@Data
@Builder
public class SubscriptionDashboardResponse {

    private BigDecimal monthlyTotal;      // active subs normalised to monthly
    private BigDecimal yearlyTotal;       // active subs normalised to yearly
    private BigDecimal monthlyBudget;     // subscription budget target (0 = unset)
    private BigDecimal monthlyRemaining;  // budget − monthlyTotal (null when unset)

    private int activeCount;
    private int cancelledCount;

    private List<Breakdown> byCategory;
    private List<Breakdown> byPaymentMethod;

    /** Renewals due in the next 30 days (includes lumpy annual charges). */
    private List<RecurringCandidate> upcoming;
    /** Subscriptions the automatic signals flag as cancel candidates. */
    private List<RecurringCandidate> cancelCandidates;

    @Data
    @Builder
    public static class Breakdown {
        private String label;
        private BigDecimal monthly;   // normalised to monthly
        private int count;
    }
}
