package com.bishop.FinanceTracker.model.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * A stock split / consolidation, applied during the trade replay. {@code ratio}
 * is new shares per old share — 5 for a 5-for-1 split, 0.2 for a 1-for-5
 * consolidation. On the ex-date the running quantity is multiplied by the ratio
 * while the cost basis is unchanged, so the per-unit average cost adjusts cleanly.
 */
@Entity
@Table(name = "stock_splits")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockSplit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "security_id", nullable = false)
    private Long securityId;

    // yyyy-MM-dd
    @Column(name = "ex_date", nullable = false, length = 20)
    private String exDate;

    @Column(name = "ratio", nullable = false, precision = 18, scale = 6)
    private BigDecimal ratio;

    @Column(name = "note", length = 200)
    private String note;

    @Column(name = "create_time", nullable = false)
    private Long createTime;
}
