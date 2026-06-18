package com.bishop.FinanceTracker.model.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * A point-in-time net-worth snapshot (written monthly + on demand). Totals are
 * stored in the base currency (AUD); the per-asset-class breakdown is JSON.
 */
@Entity
@Table(name = "net_worth_snapshots")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NetWorthSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // yyyy-MM-dd (one row per month)
    @Column(name = "as_of_date", nullable = false, unique = true, length = 20)
    private String asOfDate;

    @Column(name = "base_ccy", nullable = false, length = 3)
    private String baseCcy;

    @Column(name = "total_assets", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAssets;

    @Column(name = "total_liabilities", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalLiabilities;

    @Column(name = "net_worth", nullable = false, precision = 15, scale = 2)
    private BigDecimal netWorth;

    // Per-asset-class totals (base ccy) as JSON, for the stacked chart.
    @Column(name = "breakdown_json", columnDefinition = "TEXT")
    private String breakdownJson;
}
