package com.bishop.FinanceTracker.service;

import com.bishop.FinanceTracker.model.domain.KidPortfolioSnapshot;
import com.bishop.FinanceTracker.model.json.WealthReview;
import com.bishop.FinanceTracker.model.json.WealthReview.ClassChange;
import com.bishop.FinanceTracker.model.json.WealthReview.TrendPoint;
import com.bishop.FinanceTracker.model.wealth.AllocationSlice;
import com.bishop.FinanceTracker.model.wealth.HoldingView;
import com.bishop.FinanceTracker.model.wealth.SnapshotView;
import com.bishop.FinanceTracker.model.wealth.TotalWealthResponse;
import com.bishop.FinanceTracker.repository.KidPortfolioSnapshotRepository;
import com.bishop.FinanceTracker.util.DateUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WealthReviewServiceTest {

    private static final YearMonth AUG = YearMonth.of(2026, 8);
    private static final BigDecimal SAVINGS = new BigDecimal("20.00");

    private WealthService wealthService;
    private HoldingsService holdingsService;
    private KidPortfolioSnapshotRepository kidSnapshots;
    private WealthReviewService service;

    @BeforeEach
    void setup() {
        wealthService = mock(WealthService.class);
        holdingsService = mock(HoldingsService.class);
        kidSnapshots = mock(KidPortfolioSnapshotRepository.class);
        // AUD-only portfolio: conversion is the identity.
        when(wealthService.convert(any(), any(), any(), any())).thenAnswer(inv -> inv.getArgument(0));
        when(wealthService.getSummary("AUD")).thenReturn(TotalWealthResponse.builder()
                .netWorth(money("1500")).totalAssets(money("1600")).totalLiabilities(money("100"))
                .allocation(List.of(AllocationSlice.builder().assetClass("SHARES").value(money("800")).build()))
                .asOf("2026-10-09")
                .snapshots(List.of(
                        snap("2026-07-15", "900", Map.of("SHARES", "450", "CASH", "450")),
                        // The 1 Aug cron snapshot closes July...
                        snap("2026-08-01", "1000", Map.of("SHARES", "500", "CASH", "500")),
                        // ...and a manual mid-August one is superseded by the 1 Sep cron snapshot.
                        snap("2026-08-20", "1100", Map.of("SHARES", "600", "CASH", "500")),
                        snap("2026-09-01", "1300", Map.of("SHARES", "700", "CASH", "520", "OPTIONS", "80"))))
                .build());

        // Household: worth 500 at the end of July, 700 at the end of August, after buying 150 more
        // in August (cost basis 450 -> 600).
        givenHousehold(holding("500", "450", "0"), holding("700", "600", "0"));
        when(holdingsService.computeHoldings(org.mockito.ArgumentMatchers.eq("CHLOE"), any())).thenReturn(List.of());
        when(holdingsService.computeHoldings(org.mockito.ArgumentMatchers.eq("MILLIE"), any())).thenReturn(List.of());
        when(kidSnapshots.findAllByOwnerOrderByAsOfDateAsc("CHLOE")).thenReturn(List.of(
                KidPortfolioSnapshot.builder().owner("CHLOE").asOfDate("2026-08-01").baseCcy("AUD")
                        .portfolioValue(money("40")).build()));

        service = new WealthReviewService(wealthService, holdingsService, kidSnapshots);
    }

    @Test
    void monthEndsComeFromTheSnapshotsClosingEachMonth() {
        WealthReview review = service.review(AUG, SAVINGS);

        assertEquals("2026-08-01", review.opening().asOf());
        assertEquals("2026-09-01", review.closing().asOf());
        assertMoney("300", review.netWorthChange());
        assertMoney("30.0", review.netWorthChangePercent());
    }

    @Test
    void waterfallSeparatesSavingsGrowthEquityAndOtherAndSumsExactly() {
        WealthReview.Waterfall w = service.review(AUG, SAVINGS).waterfall();

        assertMoney("20", w.savings());
        // SHARES rose 200, of which 150 was money put in.
        assertMoney("50", w.investmentGrowth());
        assertMoney("80", w.equityComp());
        assertMoney("150", w.other());
        assertMoney(w.closing().toPlainString(), w.opening().add(w.savings()).add(w.investmentGrowth())
                .add(w.equityComp()).add(w.other()));
    }

    @Test
    void holdingGainExcludesMoneyPutIn() {
        WealthReview.HoldingMove move = service.review(AUG, SAVINGS).holdings().get(0);

        assertEquals("VAS", move.ticker());
        assertMoney("700", move.value());
        assertMoney("50", move.gain());
        assertMoney("7.7", move.returnPercent()); // 50 / (500 + 150)
    }

    @Test
    void sellProceedsCountAsMoneyTakenOut() {
        // Sold half (basis 225) for 300 net of fees, booking 75 realised; the rest rose from 250 to 275.
        givenHousehold(holding("500", "450", "0"), holding("275", "225", "75"));

        WealthReview.HoldingMove move = service.review(AUG, SAVINGS).holdings().get(0);

        // money in = (225 - 450) - (75 - 0) = -300; gain = 275 - 500 + 300 = 75
        assertMoney("75", move.gain());
        assertMoney("15.0", move.returnPercent()); // 75 / 500
    }

    @Test
    void allocationListsEveryClassAtBothEndsLargestFirst() {
        List<ClassChange> allocation = service.review(AUG, SAVINGS).allocation();

        assertEquals(List.of("SHARES", "CASH", "OPTIONS"), allocation.stream().map(ClassChange::assetClass).toList());
        assertMoney("0", allocation.get(2).opening());
        assertMoney("80", allocation.get(2).change());
    }

    @Test
    void trendHasOnePointPerMonthWithAClosingSnapshot() {
        List<TrendPoint> trend = service.review(AUG, SAVINGS).trend();

        assertEquals(List.of("2026-07", "2026-08"), trend.stream().map(TrendPoint::month).toList());
        assertMoney("1300", trend.get(1).value());
    }

    @Test
    void kidsAreReportedSeparatelyWithTheirOwnTrend() {
        WealthReview review = service.review(AUG, SAVINGS);

        assertEquals(List.of("CHLOE", "MILLIE"), review.kids().stream().map(WealthReview.KidReview::owner).toList());
        WealthReview.KidReview chloe = review.kids().get(0);
        assertMoney("0", chloe.growth());
        // Earlier months come from snapshots; the review month from the replay (no holdings here).
        assertEquals(List.of("2026-07", "2026-08"), chloe.trend().stream().map(TrendPoint::month).toList());
        assertMoney("40", chloe.trend().get(0).value());
        assertMoney("0", chloe.trend().get(1).value());
        assertEquals(List.of("2026-08"), review.kids().get(1).trend().stream().map(TrendPoint::month).toList());
    }

    @Test
    void inProgressMonthClosesOnLiveFigures() {
        YearMonth current = YearMonth.now(DateUtil.APP_ZONE);

        WealthReview review = service.review(current, SAVINGS);

        assertEquals("2026-10-09", review.closing().asOf());
        assertMoney("1500", review.closing().netWorth());
        assertMoney("800", review.closing().breakdown().get("SHARES"));
    }

    @Test
    void noWaterfallWithoutSnapshotsAtBothEnds() {
        WealthReview review = service.review(YearMonth.of(2026, 6), SAVINGS);

        assertNull(review.opening());
        assertNull(review.waterfall());
        assertNull(review.netWorthChange());
    }

    private static SnapshotView snap(String date, String netWorth, Map<String, String> breakdown) {
        Map<String, BigDecimal> parsed = new java.util.LinkedHashMap<>();
        breakdown.forEach((k, v) -> parsed.put(k, money(v)));
        return SnapshotView.builder().asOfDate(date).netWorth(money(netWorth))
                .totalAssets(money(netWorth)).totalLiabilities(money("0")).breakdown(parsed).build();
    }

    private void givenHousehold(HoldingView endOfJuly, HoldingView endOfAugust) {
        when(holdingsService.computeHoldings(isNull(), any(LocalDate.class))).thenAnswer(inv ->
                inv.getArgument(1, LocalDate.class).getMonthValue() == 7 ? List.of(endOfJuly) : List.of(endOfAugust));
    }

    private static HoldingView holding(String value, String costBasis, String realised) {
        return HoldingView.builder().securityId(1L).ticker("VAS").name("Vanguard Aus Shares").currency("AUD")
                .marketValueNative(money(value)).costBasisNative(money(costBasis)).realisedPlNative(money(realised))
                .build();
    }

    private static BigDecimal money(String v) {
        return new BigDecimal(v);
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), () -> "expected " + expected + " but was " + actual);
    }
}
