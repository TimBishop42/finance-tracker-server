package com.bishop.FinanceTracker.model.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * A point-in-time snapshot of one kid's stock portfolio value (written monthly +
 * on demand), mirroring {@link NetWorthSnapshot} but deliberately flat — a kid's
 * portfolio is stock-only, so there's no asset-class breakdown or P/L to carry,
 * just the value in the base currency (AUD) for the over-time chart.
 */
@Entity
@Table(name = "kid_portfolio_snapshots",
        uniqueConstraints = @UniqueConstraint(columnNames = {"owner", "as_of_date"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KidPortfolioSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // CHLOE | MILLIE
    @Column(name = "owner", nullable = false, length = 20)
    private String owner;

    // yyyy-MM-dd (one row per owner per day; upserted)
    @Column(name = "as_of_date", nullable = false, length = 20)
    private String asOfDate;

    @Column(name = "base_ccy", nullable = false, length = 3)
    private String baseCcy;

    @Column(name = "portfolio_value", nullable = false, precision = 15, scale = 2)
    private BigDecimal portfolioValue;
}
