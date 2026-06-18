package com.bishop.FinanceTracker.model.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A tradeable security (share/ETF). Holdings, average cost and P/L are derived
 * from {@link ShareTrade} rows; the latest price comes from {@link SecurityPrice}.
 */
@Entity
@Table(name = "securities")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Security {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // e.g. "CBA.AX", "VAS.AX", "AAPL"
    @Column(name = "ticker", nullable = false, length = 20)
    private String ticker;

    @Column(name = "name", length = 120)
    private String name;

    // e.g. ASX, NASDAQ
    @Column(name = "exchange", length = 20)
    private String exchange;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    // MANUAL today; an API source name later (v2).
    @Column(name = "price_source", nullable = false, length = 20)
    private String priceSource;
}
