package com.bishop.FinanceTracker.model.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * A single buy or sell trade. The holding position, average cost and realised
 * gain/loss for a security are derived by replaying these in date order.
 */
@Entity
@Table(name = "share_trades")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShareTrade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "security_id", nullable = false)
    private Long securityId;

    // BUY | SELL
    @Column(name = "side", nullable = false, length = 4)
    private String side;

    @Column(name = "quantity", nullable = false, precision = 18, scale = 6)
    private BigDecimal quantity;

    // Per-unit price in the security's currency.
    @Column(name = "price", nullable = false, precision = 15, scale = 4)
    private BigDecimal price;

    @Column(name = "fee", nullable = false, precision = 10, scale = 2)
    private BigDecimal fee;

    // yyyy-MM-dd
    @Column(name = "trade_date", nullable = false, length = 20)
    private String tradeDate;

    @Column(name = "note", length = 200)
    private String note;

    @Column(name = "create_time", nullable = false)
    private Long createTime;
}
