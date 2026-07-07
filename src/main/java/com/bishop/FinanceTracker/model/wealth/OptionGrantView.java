package com.bishop.FinanceTracker.model.wealth;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * A valued option grant for the Total Wealth view. Native figures are in the
 * underlying's currency; {@code *Display} figures are converted to the view
 * currency by {@code WealthService}. Only {@code vestedValue*} counts toward net
 * worth; {@code unvestedValue*} is informational.
 */
@Data
@Builder
public class OptionGrantView {
    private Long id;
    private String name;
    private String grantType;            // OPTION | RSU
    private Long underlyingSecurityId;
    private String underlyingTicker;
    private String underlyingName;
    private String currency;

    private BigDecimal strike;
    private BigDecimal quantity;         // total granted
    private BigDecimal multiplier;

    // Raw schedule (so the UI can prefill the edit form).
    private String vestStartDate;        // yyyy-MM-dd
    private Integer vestIntervalMonths;
    private String expiryDate;           // yyyy-MM-dd, nullable

    private BigDecimal vestedQuantity;
    private BigDecimal unvestedQuantity;
    private int vestedTranches;
    private int totalTranches;
    private String nextVestDate;         // yyyy-MM-dd, null if fully vested

    private BigDecimal underlyingPrice;  // native, null if no price
    private String underlyingPriceDate;
    private boolean priceMissing;
    private boolean expired;

    private BigDecimal intrinsicPerShare;   // native, max(0, price - strike)
    private BigDecimal vestedValueNative;
    private BigDecimal unvestedValueNative;
    private BigDecimal vestedValueDisplay;
    private BigDecimal unvestedValueDisplay;
}
