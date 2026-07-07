package com.bishop.FinanceTracker.model.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * An employee/stock option grant valued at intrinsic value against an underlying
 * {@link Security} (so it reuses that security's live price, currency and FX).
 * Vesting is a simple recurring schedule: {@code vestTranches} tranches of equal
 * size, the first on {@code vestStartDate}, then every {@code vestIntervalMonths}.
 * Value = max(0, underlyingPrice - strike) × vestedQty × multiplier.
 */
@Entity
@Table(name = "option_grants")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OptionGrant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    // OPTION (valued at intrinsic max(0, price-strike)) | RSU (valued at full price).
    // Nullable so ddl-auto can add it to an existing populated table; a null value
    // (pre-existing rows) is treated as OPTION. New rows always set it.
    @Column(name = "grant_type", length = 10)
    private String grantType;

    // The underlying share/ETF (securities.id) — supplies price, currency, symbol.
    @Column(name = "underlying_security_id", nullable = false)
    private Long underlyingSecurityId;

    // Per-share exercise price, in the underlying's currency. 0 / ignored for RSUs.
    @Column(name = "strike", nullable = false, precision = 15, scale = 4)
    private BigDecimal strike;

    // Total options granted.
    @Column(name = "quantity", nullable = false, precision = 18, scale = 6)
    private BigDecimal quantity;

    // Shares per option (1 for ESOs; 100 for exchange-traded contracts).
    @Column(name = "multiplier", nullable = false, precision = 10, scale = 4)
    private BigDecimal multiplier;

    // Date of the FIRST vest tranche (yyyy-MM-dd).
    @Column(name = "vest_start_date", nullable = false, length = 20)
    private String vestStartDate;

    @Column(name = "vest_interval_months", nullable = false)
    private Integer vestIntervalMonths;

    @Column(name = "vest_tranches", nullable = false)
    private Integer vestTranches;

    // Optional expiry (yyyy-MM-dd) — value floors to 0 once past.
    @Column(name = "expiry_date", length = 20)
    private String expiryDate;

    @Column(name = "note", length = 200)
    private String note;

    @Column(name = "archived", nullable = false)
    private boolean archived;

    @Column(name = "create_time", nullable = false)
    private Long createTime;

    @Column(name = "update_time")
    private Long updateTime;
}
