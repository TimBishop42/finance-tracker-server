package com.bishop.FinanceTracker.service;

import com.bishop.FinanceTracker.model.domain.NetWorthSnapshot;
import com.bishop.FinanceTracker.model.wealth.SnapshotView;
import com.bishop.FinanceTracker.model.wealth.TotalWealthResponse;
import com.bishop.FinanceTracker.repository.KidPortfolioSnapshotRepository;
import com.bishop.FinanceTracker.repository.NetWorthSnapshotRepository;
import com.bishop.FinanceTracker.repository.WealthItemRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Covers the per-asset-class snapshot breakdown that the category drill-down
 * charts: currency conversion, and the "unknown" vs "genuinely zero" distinction
 * that decides whether a point is plotted at all.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WealthServiceSnapshotBreakdownTest {

    @Mock private WealthItemRepository wealthItemRepository;
    @Mock private NetWorthSnapshotRepository snapshotRepository;
    @Mock private KidPortfolioSnapshotRepository kidPortfolioSnapshotRepository;
    @Mock private HoldingsService holdingsService;
    @Mock private OptionValuationService optionValuationService;
    @Mock private FxService fxService;

    private WealthService service() {
        when(holdingsService.computeHoldings()).thenReturn(List.of());
        when(optionValuationService.computeGrants()).thenReturn(List.of());
        when(wealthItemRepository.findByArchivedFalseOrderByAssetClassAscNameAsc()).thenReturn(List.of());
        when(fxService.latestUsdAud()).thenReturn(Optional.empty());
        return new WealthService(wealthItemRepository, snapshotRepository, kidPortfolioSnapshotRepository,
                holdingsService, optionValuationService, fxService, new ObjectMapper());
    }

    private NetWorthSnapshot snapshot(String date, String breakdownJson) {
        NetWorthSnapshot s = new NetWorthSnapshot();
        s.setAsOfDate(date);
        s.setBaseCcy("AUD");
        s.setTotalAssets(new BigDecimal("1000"));
        s.setTotalLiabilities(BigDecimal.ZERO);
        s.setNetWorth(new BigDecimal("1000"));
        s.setBreakdownJson(breakdownJson);
        return s;
    }

    private SnapshotView only(WealthService svc, String ccy) {
        TotalWealthResponse r = svc.getSummary(ccy);
        assertEquals(1, r.getSnapshots().size());
        return r.getSnapshots().get(0);
    }

    @Test
    void breakdownIsReturnedPerAssetClass() {
        when(snapshotRepository.findAllByOrderByAsOfDateAsc())
                .thenReturn(List.of(snapshot("2026-05-01", "{\"SHARES\":400,\"OPTIONS\":600}")));

        SnapshotView v = only(service(), "AUD");
        assertEquals(0, new BigDecimal("400.00").compareTo(v.getBreakdown().get("SHARES")));
        assertEquals(0, new BigDecimal("600.00").compareTo(v.getBreakdown().get("OPTIONS")));
    }

    @Test
    void breakdownIsConvertedIntoTheViewCurrency() {
        // Stored in AUD; asking for USD must convert the class totals too, not
        // just the headline figures.
        when(snapshotRepository.findAllByOrderByAsOfDateAsc())
                .thenReturn(List.of(snapshot("2026-05-01", "{\"OPTIONS\":600}")));
        when(fxService.convert(any(), anyString(), anyString()))
                .thenAnswer(inv -> Optional.of(((BigDecimal) inv.getArgument(0)).divide(new BigDecimal("2"))));

        SnapshotView v = only(service(), "USD");
        assertEquals(0, new BigDecimal("300.00").compareTo(v.getBreakdown().get("OPTIONS")));
    }

    @Test
    void absentClassInAPresentBreakdownMeansZeroNotUnknown() {
        // runSnapshot only writes non-zero classes, so a missing key is a real
        // zero — the caller must be able to tell that from "no breakdown at all".
        when(snapshotRepository.findAllByOrderByAsOfDateAsc())
                .thenReturn(List.of(snapshot("2026-05-01", "{\"SHARES\":400}")));

        SnapshotView v = only(service(), "AUD");
        assertTrue(v.getBreakdown().containsKey("SHARES"));
        assertNull(v.getBreakdown().get("OPTIONS"));
    }

    @Test
    void snapshotWithNoBreakdownIsUnknownSoItCanBeSkipped() {
        when(snapshotRepository.findAllByOrderByAsOfDateAsc())
                .thenReturn(List.of(snapshot("2026-05-01", null)));

        assertNull(only(service(), "AUD").getBreakdown());
    }

    @Test
    void unreadableBreakdownIsUnknownRatherThanFatal() {
        when(snapshotRepository.findAllByOrderByAsOfDateAsc())
                .thenReturn(List.of(snapshot("2026-05-01", "not json")));

        SnapshotView v = only(service(), "AUD");
        assertNull(v.getBreakdown());
        // The rest of the point still renders.
        assertEquals(0, new BigDecimal("1000.00").compareTo(v.getNetWorth()));
    }
}
