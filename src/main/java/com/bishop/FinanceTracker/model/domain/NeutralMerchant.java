package com.bishop.FinanceTracker.model.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A merchant whose transactions are internal transfers / credit-card payments
 * and should be booked as NEUTRAL on CSV import — recorded for audit but
 * excluded from spend and cashflow. The key is the normalised, upper-cased
 * business name (see MerchantNormalizer), so future imports of the same
 * merchant are auto-classified.
 */
@Entity
@Table(name = "neutral_merchants")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NeutralMerchant {

    @Id
    @Column(name = "merchant_key", length = 200)
    private String merchantKey;
}
