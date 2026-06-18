package com.bishop.FinanceTracker.model.wealth;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * A derived share/ETF holding: position, average cost and gain/loss for one
 * security. Native-currency figures come from {@code HoldingsService}; the
 * {@code *Display} figures are converted to the requested view currency.
 */
@Data
@Builder
public class HoldingView {
    private Long securityId;
    private String ticker;
    private String name;
    private String exchange;
    private String currency;

    private BigDecimal quantity;
    private BigDecimal avgCost;          // native, per unit
    private BigDecimal lastPrice;        // native, per unit (null if no price set)
    private String lastPriceDate;        // yyyy-MM-dd (null if no price set)

    private BigDecimal marketValueNative;
    private BigDecimal unrealisedPlNative;
    private BigDecimal realisedPlNative;

    private BigDecimal marketValueDisplay;
    private BigDecimal unrealisedPlDisplay;
    private BigDecimal realisedPlDisplay;
    private Double unrealisedPlPct;
}
