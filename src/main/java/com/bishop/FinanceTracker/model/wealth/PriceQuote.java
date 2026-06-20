package com.bishop.FinanceTracker.model.wealth;

import java.math.BigDecimal;

/** A price (or FX rate) returned by a price provider, in the instrument's native currency. */
public record PriceQuote(BigDecimal price, String currency, String asOfDate) {}
