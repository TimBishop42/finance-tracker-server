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

    /** Low-confidence "looks repeated" match (lenient fallback), surfaced as a suggestion to confirm. */
    private boolean suggestion;

    /** User has dismissed this merchant (matches an excluded_merchants entry). */
    private boolean dismissed;

    /** True for a user-entered bill (Bill Calendar "Add Bill"), as opposed to statistically detected. */
    private boolean manual;
    /** Backing {@code ManualBill} id, for edit/delete calls; null for detected candidates. @deprecated use subscriptionId. */
    @Deprecated
    private Long manualBillId;

    // --- unified subscription fields (feature doc §2A) ---
    /** Persisted {@link com.bishop.FinanceTracker.model.domain.Subscription} id; null for an unconfirmed detection. */
    private Long subscriptionId;
    /** True when a detected candidate has already been promoted to a persisted subscription. */
    private boolean confirmed;
    /** bill | subscription | other. */
    private String kind;
    /** trial | active | price_changed | at_risk | paused | cancelled. */
    private String status;
    private String paymentMethod;
    private String payer;
    private String url;
    private String logo;
    /** daily | weekly | fortnightly | monthly | quarterly | semiannual | annual | lifetime. */
    private String billingCycle;
    private String trialEndDate;      // yyyy-MM-dd
    private String notes;
    /** Dated amounts for price-creep display (chronological). */
    private List<PricePoint> priceHistory;
    /** True when the automatic signals flag this as a cancel candidate (§2A.4.3). */
    private boolean cancelCandidate;
    /** Human-readable reasons behind {@link #cancelCandidate}. */
    private List<String> cancelReasons;
    /** Transaction ids manually linked as this commitment's charges (§2A.5), for the link picker. */
    private List<Long> linkedTransactionIds;

    @Data
    @Builder
    public static class PricePoint {
        private String date;         // yyyy-MM-dd
        private BigDecimal amount;
    }
}
