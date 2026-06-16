package com.bishop.FinanceTracker.model.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "excluded_merchants")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExcludedMerchant {

    @Id
    @Column(name = "merchant_key", length = 200)
    private String merchantKey;
}
