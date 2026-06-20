package com.bishop.FinanceTracker.service;

import com.bishop.FinanceTracker.model.wealth.PriceQuote;

import java.util.Optional;

/**
 * A market-data source. v1 has a single manual table; this is the seam for live
 * feeds (Yahoo today). Implementations fetch a quote for a provider symbol and
 * return empty on any failure (never throw to the caller).
 */
public interface PriceProvider {
    Optional<PriceQuote> fetch(String symbol);

    /** Source tag stored on fetched price/FX rows (e.g. "YAHOO"). */
    String name();
}
