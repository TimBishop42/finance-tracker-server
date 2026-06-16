package com.bishop.FinanceTracker.model.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "custom_merchants")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomMerchant {

    @Id
    @Column(name = "merchant_pattern", length = 200)
    private String merchantPattern;

    @Column(name = "merchant_type", length = 20, nullable = false)
    private String merchantType;
}
