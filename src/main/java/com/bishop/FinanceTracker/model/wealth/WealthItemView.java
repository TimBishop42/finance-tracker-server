package com.bishop.FinanceTracker.model.wealth;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/** A wealth item (asset or liability) with its value converted to the view currency. */
@Data
@Builder
public class WealthItemView {
    private Long id;
    private String name;
    private String assetClass;
    private String kind;            // ASSET | LIABILITY
    private String currency;
    private BigDecimal currentValueNative;
    private BigDecimal valueDisplay;
    private String note;
    private Long updateTime;
}
