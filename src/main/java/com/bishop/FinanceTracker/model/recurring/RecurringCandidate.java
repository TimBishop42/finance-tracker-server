package com.bishop.FinanceTracker.model.recurring;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * A detected recurring merchant, scored and enriched for the UI. This is the
 * public shape returned by {@code GET /api/finance/recurring}; the front-end is
 * a thin consumer of it (feature doc §2.5).
 */
@Data
@Builder
public class RecurringCandidate {

    /** Normalised, upper-cased grouping key (also used to dismiss/exclude). */
    private String key;
    /** Human-friendly merchant name. */
    private String name;
    /** Distinct raw business names that folded into this group. */
    private List<String> rawNames;

    // --- cadence ---
    private String cadence;          // "monthly", "annual", ...
    private double cadenceScore;     // phase-space fit, 0..1
    private double vectorStrength;   // r, 0..1

    // --- confidence ---
    private double confidence;       // overall 0..1

    // --- occurrences / timeline ---
    private int occurrences;
    private int distinctMonths;
    private int monthsRange;
    private String firstDate;        // yyyy-MM-dd
    private String lastDate;         // yyyy-MM-dd
    private Integer predictedDay;    // day-of-month for monthly-ish cadences (null otherwise)
    private String nextPredictedDate;// yyyy-MM-dd
    /** Actual charge dates (yyyy-MM-dd) so the Bill Calendar can mark paid months. */
    private List<String> chargeDates;

    // --- amounts ---
    private BigDecimal averageAmount;
    private BigDecimal expectedAmount; // trimmed mean — feeds the calendar/forecast
    private BigDecimal totalPaid;
    private BigDecimal monthlyEquivalent;
    private double amountCv;
    private boolean amountVariable;    // high variance → "variable amount", not rejected

    // --- classification ---
    private String category;
    private boolean subscription;
    private boolean bill;
    private String knowledgeType;      // subscription | bill | unknown
    private String knowledgeSource;    // custom | seed | statistical

    /** User has dismissed this merchant (matches an excluded_merchants entry). */
    private boolean dismissed;
}
