package com.bishop.FinanceTracker.model.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Entity
@Table(name = "manual_bills")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManualBill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 50)
    private String category;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    /** Day of month (1-31) the bill is due; the bill is treated as monthly-recurring. */
    @Column(name = "due_day", nullable = false)
    private Integer dueDay;

    /** When true, payment matching skips the amount check (name match only) — for
     *  bills like rent that vary by more than the standard tolerance month to month. */
    @Column(name = "amount_variable")
    private Boolean amountVariable;

    /** Comma-separated yyyy-MM-dd dates of transactions matched as payments. */
    @Column(name = "paid_dates", columnDefinition = "TEXT")
    private String paidDates;

    @Column(name = "create_time", nullable = false)
    private Long createTime;
}
