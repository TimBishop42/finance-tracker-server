package com.bishop.FinanceTracker.model.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * A price point for a security on a given date. Manual entries today; API-fetched
 * rows later (v2) land in the same table so manual and fetched prices coexist.
 */
@Entity
@Table(name = "security_prices")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecurityPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "security_id", nullable = false)
    private Long securityId;

    // yyyy-MM-dd
    @Column(name = "as_of_date", nullable = false, length = 20)
    private String asOfDate;

    @Column(name = "price", nullable = false, precision = 15, scale = 4)
    private BigDecimal price;

    @Column(name = "source", nullable = false, length = 20)
    private String source;
}
