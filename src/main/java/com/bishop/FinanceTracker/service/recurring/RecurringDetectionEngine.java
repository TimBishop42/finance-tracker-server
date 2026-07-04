package com.bishop.FinanceTracker.service.recurring;

import com.bishop.FinanceTracker.model.domain.CustomMerchant;
import com.bishop.FinanceTracker.model.domain.Transaction;
import com.bishop.FinanceTracker.model.recurring.RecurringCandidate;

import java.util.List;

/**
 * The full detect pipeline over a set of transactions: normalise → group →
 * cadence → amount band → score → predict (feature doc §2.1).
 *
 * <p>This is the top-level pivot seam. v1 is {@link StatisticalRecurringEngine}
 * (pure maths, no ML); a supervised/ML engine can implement the same interface
 * and be swapped in by marking it {@code @Primary} — the controller, service and
 * UI are unaffected.
 */
public interface RecurringDetectionEngine {

    /** Short identifier surfaced in the API response (e.g. "phase-space"). */
    String name();

    /**
     * @param transactions expense transactions to analyse (income already excluded)
     * @param customRules  user-defined merchant classification rules
     * @return scored recurring candidates, highest confidence first
     */
    List<RecurringCandidate> detect(List<Transaction> transactions, List<CustomMerchant> customRules);
}
