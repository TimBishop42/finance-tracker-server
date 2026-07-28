package com.bishop.FinanceTracker.model.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * A dated amount observed for a {@link Subscription} — powers price-creep detection
 * (a cancel-candidate signal) and the price-change alert (feature doc §2A.4.1).
 * One row is appended whenever a matched payment's amount differs from the last
 * recorded amount.
 */
@Data
@Entity
@Table(name = "subscription_price_history")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionPriceHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "subscription_id", nullable = false)
    private Long subscriptionId;

    @Column(name = "effective_date", nullable = false, length = 20)
    private String effectiveDate;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;
}
