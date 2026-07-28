package com.bishop.FinanceTracker.model.json;

import lombok.Data;

/**
 * Create/update payload for a unified subscription/bill (feature doc §2A.2).
 * Used by both the Bill Calendar (kind defaults to {@code bill}) and the
 * Subscription dashboard (kind {@code subscription}).
 */
@Data
public class SubscriptionRequest {

    private String name;
    private String category;
    /** Decimal string, e.g. "42.50". */
    private String amount;

    /** yyyy-MM-dd — day-of-month is derived for the calendar; optional for non-dated cycles. */
    private String dueDate;

    /** bill | subscription | other. Defaults to bill when omitted (calendar add). */
    private String kind;
    /** daily | weekly | fortnightly | monthly | quarterly | semiannual | annual | lifetime. Defaults monthly. */
    private String billingCycle;
    /** trial | active | ... Defaults active (or trial when a trialEndDate is given). */
    private String status;

    private boolean amountVariable;
    private String paymentMethod;
    private String payer;
    private String url;
    private String logo;
    private String trialEndDate;   // yyyy-MM-dd
    private String notes;
}
