package com.bishop.FinanceTracker.model.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Unified recurring commitment — the single canonical store for both user-entered
 * bills and confirmed statistically-detected recurrings (feature doc §2A.4). The
 * Bill Calendar (filters {@code kind=bill}) and the Recurring tab both read/edit
 * this one table; supersedes the older {@code manual_bills} table (migrated in on
 * startup, see {@code SubscriptionMigrationRunner}).
 */
@Data
@Entity
@Table(name = "subscriptions")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** {@code manual} (user-entered) or {@code detected} (promoted from the recurring engine). */
    @Column(nullable = false, length = 20)
    private String source;

    /** Detection confidence 0..1, retained when promoted from a detected candidate; null for manual. */
    @Column(name = "confidence")
    private Double confidence;

    /** {@code bill} (essential/hard-to-cancel), {@code subscription} (discretionary), or {@code other}. */
    @Column(nullable = false, length = 20)
    private String kind;

    /** Lifecycle: trial | active | price_changed | at_risk | paused | cancelled. */
    @Column(nullable = false, length = 20)
    private String status;

    @Column(nullable = false, length = 200)
    private String name;

    /** Normalised, upper-cased merchant key — links back to the recurring engine + dedupes detections. */
    @Column(name = "normalized_key", length = 200)
    private String normalizedKey;

    @Column(length = 50)
    private String category;

    /** Who pays it (household member); groundwork for multi-user. */
    @Column(length = 100)
    private String payer;

    @Column(name = "payment_method", length = 100)
    private String paymentMethod;

    @Column(length = 500)
    private String url;

    @Column(length = 500)
    private String logo;

    /** daily | weekly | fortnightly | monthly | quarterly | semiannual | annual | lifetime. */
    @Column(name = "billing_cycle", nullable = false, length = 20)
    private String billingCycle;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    /** When true, payment matching skips the amount check (name match only) — e.g. rent. */
    @Column(name = "amount_variable")
    private Boolean amountVariable;

    /** Day of month (1-31) the charge is due, for monthly-ish cadences; null otherwise. */
    @Column(name = "due_day")
    private Integer dueDay;

    /** yyyy-MM-dd of the next expected charge (kept fresh by the detector/matcher). */
    @Column(name = "next_charge_date", length = 20)
    private String nextChargeDate;

    /** yyyy-MM-dd a free trial converts to paid; drives the trial→paid alert. */
    @Column(name = "trial_end_date", length = 20)
    private String trialEndDate;

    /** Comma-separated yyyy-MM-dd dates of transactions matched as payments. */
    @Column(name = "paid_dates", columnDefinition = "TEXT")
    private String paidDates;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "create_time", nullable = false)
    private Long createTime;

    /** Set for rows migrated from the legacy {@code manual_bills} table; makes migration idempotent. */
    @Column(name = "legacy_manual_bill_id")
    private Long legacyManualBillId;

    /** Comma-separated transaction ids the user has manually linked as this commitment's charges (§2A.5). */
    @Column(name = "linked_transaction_ids", columnDefinition = "TEXT")
    private String linkedTransactionIds;
}
