package com.bishop.FinanceTracker.model.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * A generic asset or liability line item in the Total Wealth tracker — everything
 * that isn't a traded share holding (cash/savings, super, property, vehicles,
 * mortgages, loans). Traded holdings are derived from {@link ShareTrade} instead.
 */
@Entity
@Table(name = "wealth_items")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WealthItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    // CASH | SHARES | SUPER | PROPERTY | OTHER_ASSET | LIABILITY
    @Column(name = "asset_class", nullable = false, length = 30)
    private String assetClass;

    // ASSET | LIABILITY
    @Column(name = "kind", nullable = false, length = 10)
    private String kind;

    // ISO 4217 — AUD or USD in v1.
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "current_value", precision = 15, scale = 2)
    private BigDecimal currentValue;

    @Column(name = "note", length = 200)
    private String note;

    @Column(name = "archived", nullable = false)
    private boolean archived;

    @Column(name = "create_time", nullable = false)
    private Long createTime;

    @Column(name = "update_time")
    private Long updateTime;
}
