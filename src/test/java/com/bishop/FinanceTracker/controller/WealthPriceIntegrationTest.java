package com.bishop.FinanceTracker.controller;

import com.bishop.FinanceTracker.model.domain.Security;
import com.bishop.FinanceTracker.model.wealth.HoldingView;
import com.bishop.FinanceTracker.model.wealth.TotalWealthResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end (controller + JPA + H2) check that a manually-set price actually
 * flows through to the holding in the wealth summary. Mirrors the UI's
 * "Set price" path: addSecurity -> addTrade -> setPrice -> getSummary.
 */
@SpringBootTest
@ActiveProfiles("test")
class WealthPriceIntegrationTest {

    @Autowired
    private WealthController controller;

    private static Map<String, Object> body(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    @Test
    void manualPriceFlowsIntoHolding() {
        Security sec = controller.addSecurity(
                body("ticker", "TSTPX", "exchange", "ASX", "currency", "AUD", "name", "Test")).getBody();
        Long secId = sec.getId();

        controller.addTrade(body(
                "securityId", secId, "side", "BUY", "quantity", "10", "price", "5", "fee", "0",
                "tradeDate", "2024-01-01"));

        // Before any price: valued at average cost (placeholder).
        HoldingView before = onlyHolding(secId);
        assertTrue(before.isPriceIsEstimated());
        assertEquals(0, new BigDecimal("50.00").compareTo(before.getMarketValueNative())); // 5 * 10

        // Set a manual price for today, exactly as the dialog does.
        controller.setPrice(body("securityId", secId, "price", "8", "asOfDate", LocalDate.now().toString()));

        HoldingView after = onlyHolding(secId);
        assertFalse(after.isPriceIsEstimated(), "holding should no longer be price-estimated after a manual price");
        assertEquals(0, new BigDecimal("8").compareTo(after.getLastPrice()));
        assertEquals(0, new BigDecimal("80.00").compareTo(after.getMarketValueNative())); // 8 * 10
    }

    private HoldingView onlyHolding(Long secId) {
        TotalWealthResponse summary = controller.getSummary("AUD").getBody();
        return summary.getHoldings().stream()
                .filter(h -> secId.equals(h.getSecurityId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("holding not found for security " + secId));
    }
}
