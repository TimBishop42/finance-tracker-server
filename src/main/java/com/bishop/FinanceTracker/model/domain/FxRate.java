package com.bishop.FinanceTracker.model.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * An FX rate for a date: 1 {@code baseCcy} = {@code rate} {@code quoteCcy}.
 * v1 only needs the USD/AUD pair, entered manually.
 */
@Entity
@Table(name = "fx_rates")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FxRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "base_ccy", nullable = false, length = 3)
    private String baseCcy;

    @Column(name = "quote_ccy", nullable = false, length = 3)
    private String quoteCcy;

    // yyyy-MM-dd
    @Column(name = "as_of_date", nullable = false, length = 20)
    private String asOfDate;

    @Column(name = "rate", nullable = false, precision = 15, scale = 6)
    private BigDecimal rate;

    @Column(name = "source", nullable = false, length = 20)
    private String source;
}
