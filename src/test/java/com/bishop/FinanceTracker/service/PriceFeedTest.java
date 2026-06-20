package com.bishop.FinanceTracker.service;

import com.bishop.FinanceTracker.model.domain.Security;
import com.bishop.FinanceTracker.model.wealth.PriceQuote;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the Yahoo response parsing and the price-feed symbol resolution.
 * No network — {@code parseQuote} is fed canned JSON and {@code resolveSymbol} is pure.
 */
class PriceFeedTest {

    private final YahooPriceProvider provider =
            new YahooPriceProvider("https://example.test", new ObjectMapper());

    // --- parseQuote --------------------------------------------------------

    @Test
    void parsesValidQuote() {
        String json = "{\"chart\":{\"result\":[{\"meta\":{\"currency\":\"USD\","
                + "\"regularMarketPrice\":123.45,\"regularMarketTime\":1718000000}}],\"error\":null}}";

        Optional<PriceQuote> q = provider.parseQuote(json);
        assertTrue(q.isPresent());
        assertEquals(0, new BigDecimal("123.45").compareTo(q.get().price()));
        assertEquals("USD", q.get().currency());
        assertEquals("2024-06-10", q.get().asOfDate()); // epoch 1718000000 UTC
    }

    @Test
    void returnsEmptyWhenPriceMissing() {
        String json = "{\"chart\":{\"result\":[{\"meta\":{\"currency\":\"USD\"}}],\"error\":null}}";
        assertFalse(provider.parseQuote(json).isPresent());
    }

    @Test
    void returnsEmptyOnErrorShape() {
        String json = "{\"chart\":{\"result\":[],\"error\":{\"code\":\"Not Found\"}}}";
        assertFalse(provider.parseQuote(json).isPresent());
    }

    @Test
    void returnsEmptyOnGarbage() {
        assertFalse(provider.parseQuote("not json").isPresent());
    }

    // --- resolveSymbol -----------------------------------------------------

    private Security sec(String ticker, String exchange, String currency, String priceSymbol) {
        return Security.builder().ticker(ticker).exchange(exchange).currency(currency)
                .priceSymbol(priceSymbol).build();
    }

    @Test
    void explicitOverrideWins() {
        assertEquals("BRK-B", PriceRefreshService.resolveSymbol(sec("BRK.B", "NYSE", "USD", "BRK-B")));
    }

    @Test
    void ausSecurityGetsAxSuffix() {
        assertEquals("CBA.AX", PriceRefreshService.resolveSymbol(sec("CBA", "ASX", "AUD", null)));
        assertEquals("VAS.AX", PriceRefreshService.resolveSymbol(sec("VAS", null, "AUD", null)));
    }

    @Test
    void usSecurityUsesBareTicker() {
        // NASDAQ and NYSE share the bare symbol on Yahoo.
        assertEquals("AAPL", PriceRefreshService.resolveSymbol(sec("AAPL", "NASDAQ", "USD", null)));
        assertEquals("VUG", PriceRefreshService.resolveSymbol(sec("VUG", "NYSE", "USD", null)));
    }

    @Test
    void alreadySuffixedTickerIsLeftAlone() {
        assertEquals("CBA.AX", PriceRefreshService.resolveSymbol(sec("CBA.AX", "ASX", "AUD", null)));
    }
}
