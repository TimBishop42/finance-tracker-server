package com.bishop.FinanceTracker.model.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "salary_history")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalaryEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ISO date string (yyyy-MM-dd) — consistent with how transaction dates are stored.
    @Column(name = "effective_date", nullable = false, length = 20)
    private String effectiveDate;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "note", length = 200)
    private String note;
}
