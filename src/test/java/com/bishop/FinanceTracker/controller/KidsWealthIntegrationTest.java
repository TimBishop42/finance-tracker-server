package com.bishop.FinanceTracker.controller;

import com.bishop.FinanceTracker.model.domain.Security;
import com.bishop.FinanceTracker.model.domain.ShareTrade;
import com.bishop.FinanceTracker.model.wealth.KidSnapshotView;
import com.bishop.FinanceTracker.model.wealth.KidsWealthResponse;
import com.bishop.FinanceTracker.model.wealth.TotalWealthResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end (controller + JPA + H2) regression test for the core requirement of
 * the Kids' Portfolios feature: a kid's trade must never count toward the
 * household's own Total Wealth (net worth / holdings / trade blotter), and each
 * kid's trades must stay separate from the other's.
 */
@SpringBootTest
@ActiveProfiles("test")
class KidsWealthIntegrationTest {

    @Autowired
    private WealthController controller;

    private static Map<String, Object> body(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    @Test
    void kidsTradesAreExcludedFromHouseholdAndSeparateFromEachOther() {
        Security sec = controller.addSecurity(
                body("ticker", "KIDTST", "exchange", "ASX", "currency", "AUD", "name", "Kid Test")).getBody();
        Long secId = sec.getId();

        controller.addTrade(body(
                "securityId", secId, "side", "BUY", "quantity", "10", "price", "5", "fee", "0",
                "tradeDate", "2024-01-01")); // household — no owner
        controller.addTrade(body(
                "securityId", secId, "side", "BUY", "quantity", "3", "price", "5", "fee", "0",
                "tradeDate", "2024-01-02", "owner", "CHLOE"));

        // Household view: only the un-owned trade counts.
        TotalWealthResponse summary = controller.getSummary("AUD").getBody();
        assertEquals(0, new BigDecimal("10").compareTo(onlyHoldingQty(summary.getHoldings(), secId)));

        List<ShareTrade> householdTrades = controller.getTrades(null).getBody();
        assertTrue(householdTrades.stream().noneMatch(t -> "CHLOE".equals(t.getOwner())),
                "household trade blotter must not include Chloe's trade");

        // Kids view: Chloe sees only her trade; Millie sees none for this security.
        KidsWealthResponse kids = controller.getKidsSummary("AUD").getBody();
        assertEquals(0, new BigDecimal("3").compareTo(onlyHoldingQty(kids.getChloe().getHoldings(), secId)));
        assertTrue(kids.getMillie().getHoldings().stream().noneMatch(h -> secId.equals(h.getSecurityId())));

        List<ShareTrade> chloeTrades = controller.getTrades("CHLOE").getBody();
        assertTrue(chloeTrades.stream().allMatch(t -> "CHLOE".equals(t.getOwner())));
        assertTrue(controller.getTrades("MILLIE").getBody().isEmpty());
    }

    @Test
    void unknownOwnerIsRejected() {
        assertEquals(400, controller.getTrades("SOMEONE_ELSE").getStatusCode().value());
    }

    @Test
    void kidsSnapshotUpsertsTodayAndMatchesLivePortfolioValue() {
        Security sec = controller.addSecurity(
                body("ticker", "KIDSNAP", "exchange", "ASX", "currency", "AUD", "name", "Kid Snapshot Test"))
                .getBody();
        controller.addTrade(body(
                "securityId", sec.getId(), "side", "BUY", "quantity", "1", "price", "100", "fee", "0",
                "tradeDate", "2024-01-01", "owner", "CHLOE"));

        controller.runKidsSnapshot();
        controller.runKidsSnapshot(); // same-day re-run must upsert, not duplicate

        KidsWealthResponse kids = controller.getKidsSummary("AUD").getBody();
        String today = LocalDate.now().toString();
        List<KidSnapshotView> chloeSnapshots = kids.getChloe().getSnapshots();

        long todaysRows = chloeSnapshots.stream().filter(s -> today.equals(s.getAsOfDate())).count();
        assertEquals(1, todaysRows, "expected exactly one upserted snapshot row for today");

        BigDecimal todaysValue = chloeSnapshots.stream()
                .filter(s -> today.equals(s.getAsOfDate()))
                .findFirst().orElseThrow()
                .getValueDisplay();
        assertEquals(0, todaysValue.compareTo(kids.getChloe().getPortfolioValueDisplay()),
                "today's snapshot should match the live portfolio value at capture time");
    }

    private static BigDecimal onlyHoldingQty(List<com.bishop.FinanceTracker.model.wealth.HoldingView> holdings, Long secId) {
        return holdings.stream()
                .filter(h -> secId.equals(h.getSecurityId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("holding not found for security " + secId))
                .getQuantity();
    }
}
