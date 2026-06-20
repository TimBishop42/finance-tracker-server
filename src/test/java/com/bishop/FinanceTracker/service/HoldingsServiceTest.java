package com.bishop.FinanceTracker.service;

import com.bishop.FinanceTracker.model.domain.Security;
import com.bishop.FinanceTracker.model.domain.SecurityPrice;
import com.bishop.FinanceTracker.model.domain.ShareTrade;
import com.bishop.FinanceTracker.model.domain.StockSplit;
import com.bishop.FinanceTracker.model.wealth.HoldingView;
import com.bishop.FinanceTracker.repository.SecurityPriceRepository;
import com.bishop.FinanceTracker.repository.SecurityRepository;
import com.bishop.FinanceTracker.repository.ShareTradeRepository;
import com.bishop.FinanceTracker.repository.StockSplitRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the average-cost / split / placeholder-price logic in
 * {@link HoldingsService}. Repositories are mocked; holdings are pure functions
 * of the trade + split log.
 */
@ExtendWith(MockitoExtension.class)
class HoldingsServiceTest {

    private static final long SEC_ID = 1L;

    @Mock private SecurityRepository securityRepository;
    @Mock private ShareTradeRepository shareTradeRepository;
    @Mock private SecurityPriceRepository securityPriceRepository;
    @Mock private StockSplitRepository stockSplitRepository;

    @InjectMocks private HoldingsService holdingsService;

    // --- helpers -----------------------------------------------------------

    private Security security() {
        return Security.builder().id(SEC_ID).ticker("VUG").name("Vanguard Growth")
                .exchange("NYSE").currency("USD").priceSource("MANUAL").build();
    }

    private ShareTrade trade(long id, String side, String qty, String price, String fee, String date) {
        return ShareTrade.builder().id(id).securityId(SEC_ID).side(side)
                .quantity(new BigDecimal(qty)).price(new BigDecimal(price)).fee(new BigDecimal(fee))
                .tradeDate(date).createTime(0L).build();
    }

    private StockSplit split(long id, String ratio, String date) {
        return StockSplit.builder().id(id).securityId(SEC_ID).exDate(date)
                .ratio(new BigDecimal(ratio)).createTime(0L).build();
    }

    private void givenTrades(List<ShareTrade> trades) {
        when(securityRepository.findAll()).thenReturn(List.of(security()));
        when(shareTradeRepository.findBySecurityIdOrderByTradeDateAscIdAsc(SEC_ID)).thenReturn(trades);
    }

    private void givenSplits(List<StockSplit> splits) {
        when(stockSplitRepository.findBySecurityIdOrderByExDateAscIdAsc(SEC_ID)).thenReturn(splits);
    }

    private void givenPrice(String price, String date) {
        when(securityPriceRepository.findFirstBySecurityIdOrderByAsOfDateDesc(SEC_ID))
                .thenReturn(Optional.of(SecurityPrice.builder()
                        .securityId(SEC_ID).asOfDate(date).price(new BigDecimal(price)).source("MANUAL").build()));
    }

    private HoldingView only() {
        List<HoldingView> holdings = holdingsService.computeHoldings();
        assertEquals(1, holdings.size(), "expected exactly one holding");
        return holdings.get(0);
    }

    private static void assertBd(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                () -> "expected " + expected + " but was " + actual);
    }

    // --- tests -------------------------------------------------------------

    @Test
    void blendsAverageCostAcrossBuys() {
        givenTrades(List.of(
                trade(1, "BUY", "10", "5", "0", "2024-01-01"),
                trade(2, "BUY", "10", "7", "0", "2024-02-01")));
        givenPrice("8", "2024-03-01");

        HoldingView h = only();
        assertBd("20", h.getQuantity());
        assertBd("6", h.getAvgCost());            // (50 + 70) / 20
        assertBd("160.00", h.getMarketValueNative()); // 8 * 20
        assertBd("40.00", h.getUnrealisedPlNative()); // 160 - 120
        assertBd("0.00", h.getRealisedPlNative());
        assertFalse(h.isPriceIsEstimated());
        assertEquals(33.33, h.getUnrealisedPlPct(), 0.01);
    }

    @Test
    void includesFeesInCostBasis() {
        givenTrades(List.of(trade(1, "BUY", "10", "5", "10", "2024-01-01")));

        HoldingView h = only();
        assertBd("6", h.getAvgCost()); // (10*5 + 10 fee) / 10
    }

    @Test
    void sellRealisesGainAtAverageCost() {
        givenTrades(List.of(
                trade(1, "BUY", "10", "5", "0", "2024-01-01"),
                trade(2, "BUY", "10", "7", "0", "2024-02-01"),  // avg 6, qty 20
                trade(3, "SELL", "5", "10", "0", "2024-03-01")));

        HoldingView h = only();
        assertBd("15", h.getQuantity());
        assertBd("6", h.getAvgCost());                 // average cost unchanged by a sell
        assertBd("20.00", h.getRealisedPlNative());    // (10 - 6) * 5
    }

    @Test
    void forwardSplitScalesQuantityAndPreservesCostBasis() {
        // The VUG case: buy 1 @ $300, 5-for-1 split, then buy 2 @ $62.
        givenTrades(List.of(
                trade(1, "BUY", "1", "300", "0", "2024-01-01"),
                trade(2, "BUY", "2", "62", "0", "2024-07-01")));
        givenSplits(List.of(split(1, "5", "2024-06-01")));
        givenPrice("65", "2024-08-01");

        HoldingView h = only();
        assertBd("7", h.getQuantity());            // 1*5 + 2
        assertBd("60.5714", h.getAvgCost());       // (300 + 124) / 7
        assertBd("455.00", h.getMarketValueNative()); // 65 * 7
        assertBd("31.00", h.getUnrealisedPlNative()); // 455 - 424
    }

    @Test
    void reverseSplitConsolidatesQuantity() {
        // 1-for-5 consolidation: ratio 0.2.
        givenTrades(List.of(trade(1, "BUY", "10", "2", "0", "2024-01-01")));
        givenSplits(List.of(split(1, "0.2", "2024-06-01")));

        HoldingView h = only();
        assertBd("2", h.getQuantity());   // 10 * 0.2
        assertBd("10", h.getAvgCost());   // basis 20 / 2
    }

    @Test
    void oversellIsClampedToHeldQuantity() {
        givenTrades(List.of(
                trade(1, "BUY", "5", "10", "0", "2024-01-01"),
                trade(2, "SELL", "10", "12", "0", "2024-02-01"))); // tries to sell 10, holds 5

        HoldingView h = only();
        assertBd("0", h.getQuantity());
        assertBd("10.00", h.getRealisedPlNative()); // (12 - 10) * 5 — only the 5 held
        assertBd("0.00", h.getMarketValueNative());
    }

    @Test
    void noPriceFallsBackToAverageCost() {
        givenTrades(List.of(trade(1, "BUY", "10", "5", "0", "2024-01-01")));
        // no price stubbed -> repository returns Optional.empty()

        HoldingView h = only();
        assertTrue(h.isPriceIsEstimated());
        assertNull(h.getLastPrice());
        assertBd("50.00", h.getMarketValueNative()); // avg cost 5 * 10
        assertBd("0.00", h.getUnrealisedPlNative()); // placeholder => no gain/loss
    }

    @Test
    void securityWithNoTradesIsSkipped() {
        when(securityRepository.findAll()).thenReturn(List.of(security()));
        when(shareTradeRepository.findBySecurityIdOrderByTradeDateAscIdAsc(SEC_ID)).thenReturn(List.of());

        assertTrue(holdingsService.computeHoldings().isEmpty());
    }
}
