package com.bishop.FinanceTracker.service;

import com.bishop.FinanceTracker.model.domain.NetWorthSnapshot;
import com.bishop.FinanceTracker.model.domain.WealthItem;
import com.bishop.FinanceTracker.model.wealth.AllocationSlice;
import com.bishop.FinanceTracker.model.wealth.HoldingView;
import com.bishop.FinanceTracker.model.wealth.SnapshotView;
import com.bishop.FinanceTracker.model.wealth.TotalWealthResponse;
import com.bishop.FinanceTracker.model.wealth.WealthItemView;
import com.bishop.FinanceTracker.repository.NetWorthSnapshotRepository;
import com.bishop.FinanceTracker.repository.WealthItemRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Assembles the Total Wealth view (net worth, allocation, holdings, items,
 * snapshot series) in a requested display currency, and writes the monthly
 * net-worth snapshot. Net worth = sum(asset values) - sum(liability values),
 * all converted to a single currency via {@link FxService}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WealthService {

    private static final int MONEY_SCALE = 2;
    private static final String SHARES = "SHARES";
    private static final String LIABILITY = "LIABILITY";
    private static final String BASE_CCY = "AUD";

    private final WealthItemRepository wealthItemRepository;
    private final NetWorthSnapshotRepository snapshotRepository;
    private final HoldingsService holdingsService;
    private final FxService fxService;
    private final ObjectMapper objectMapper;

    public TotalWealthResponse getSummary(String displayCurrency) {
        final String ccy = normaliseCcy(displayCurrency);
        final boolean[] fxMissing = {false};

        // --- Holdings (the SHARES asset class) ---
        List<HoldingView> holdings = holdingsService.computeHoldings();
        BigDecimal sharesValue = BigDecimal.ZERO;
        for (HoldingView h : holdings) {
            h.setMarketValueDisplay(scale(convert(h.getMarketValueNative(), h.getCurrency(), ccy, fxMissing)));
            h.setUnrealisedPlDisplay(scale(convert(h.getUnrealisedPlNative(), h.getCurrency(), ccy, fxMissing)));
            h.setRealisedPlDisplay(scale(convert(h.getRealisedPlNative(), h.getCurrency(), ccy, fxMissing)));
            sharesValue = sharesValue.add(h.getMarketValueDisplay());
        }

        // --- Wealth items (everything else) ---
        List<WealthItem> items = wealthItemRepository.findByArchivedFalseOrderByAssetClassAscNameAsc();
        List<WealthItemView> itemViews = new ArrayList<>();
        Map<String, BigDecimal> classTotals = new LinkedHashMap<>();
        BigDecimal totalAssets = sharesValue;
        BigDecimal totalLiabilities = BigDecimal.ZERO;
        if (!holdings.isEmpty()) {
            classTotals.merge(SHARES, sharesValue, BigDecimal::add);
        }

        for (WealthItem it : items) {
            BigDecimal disp = scale(convert(it.getCurrentValue(), it.getCurrency(), ccy, fxMissing));
            itemViews.add(WealthItemView.builder()
                    .id(it.getId()).name(it.getName()).assetClass(it.getAssetClass())
                    .kind(it.getKind()).currency(it.getCurrency())
                    .currentValueNative(it.getCurrentValue()).valueDisplay(disp)
                    .note(it.getNote()).updateTime(it.getUpdateTime())
                    .build());
            if (LIABILITY.equalsIgnoreCase(it.getKind())) {
                totalLiabilities = totalLiabilities.add(disp);
            } else {
                totalAssets = totalAssets.add(disp);
                classTotals.merge(it.getAssetClass(), disp, BigDecimal::add);
            }
        }

        totalAssets = scale(totalAssets);
        totalLiabilities = scale(totalLiabilities);
        BigDecimal netWorth = scale(totalAssets.subtract(totalLiabilities));

        // --- Allocation (assets only) ---
        List<AllocationSlice> allocation = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> e : classTotals.entrySet()) {
            BigDecimal v = scale(e.getValue());
            if (v.signum() == 0) continue;
            Double pct = totalAssets.signum() > 0
                    ? v.divide(totalAssets, 6, RoundingMode.HALF_UP).doubleValue() * 100.0
                    : 0.0;
            allocation.add(AllocationSlice.builder().assetClass(e.getKey()).value(v).pct(pct).build());
        }

        // --- Snapshot series (stored in AUD -> display ccy) ---
        List<SnapshotView> snapViews = new ArrayList<>();
        for (NetWorthSnapshot s : snapshotRepository.findAllByOrderByAsOfDateAsc()) {
            String base = s.getBaseCcy() == null ? BASE_CCY : s.getBaseCcy();
            snapViews.add(SnapshotView.builder()
                    .asOfDate(s.getAsOfDate())
                    .totalAssets(scale(convert(s.getTotalAssets(), base, ccy, fxMissing)))
                    .totalLiabilities(scale(convert(s.getTotalLiabilities(), base, ccy, fxMissing)))
                    .netWorth(scale(convert(s.getNetWorth(), base, ccy, fxMissing)))
                    .build());
        }

        // --- Delta vs the most recent snapshot from a prior month ---
        BigDecimal delta = null;
        Double deltaPct = null;
        String firstOfMonth = LocalDate.now().withDayOfMonth(1).toString();
        for (int i = snapViews.size() - 1; i >= 0; i--) {
            SnapshotView prior = snapViews.get(i);
            if (prior.getAsOfDate().compareTo(firstOfMonth) < 0) {
                delta = scale(netWorth.subtract(prior.getNetWorth()));
                if (prior.getNetWorth().signum() != 0) {
                    deltaPct = delta.divide(prior.getNetWorth().abs(), 6, RoundingMode.HALF_UP)
                            .doubleValue() * 100.0;
                }
                break;
            }
        }

        return TotalWealthResponse.builder()
                .displayCurrency(ccy)
                .netWorth(netWorth)
                .totalAssets(totalAssets)
                .totalLiabilities(totalLiabilities)
                .deltaVsPrevious(delta)
                .deltaPct(deltaPct)
                .fxRateUsdAud(fxService.latestUsdAud().orElse(null))
                .fxMissing(fxMissing[0])
                .holdings(holdings)
                .items(itemViews)
                .allocation(allocation)
                .snapshots(snapViews)
                .asOf(LocalDate.now().toString())
                .build();
    }

    /** Compute current totals in base AUD and upsert this month's snapshot (idempotent). */
    public NetWorthSnapshot runSnapshot() {
        // One row per calendar day (upsert): re-running on the same day overwrites that
        // day's point, but distinct days accumulate so the over-time chart builds up.
        final String asOf = LocalDate.now().toString();
        final boolean[] fxMissing = {false};

        Map<String, BigDecimal> classTotals = new LinkedHashMap<>();
        BigDecimal assets = BigDecimal.ZERO;
        BigDecimal liabilities = BigDecimal.ZERO;

        BigDecimal sharesValue = BigDecimal.ZERO;
        for (HoldingView h : holdingsService.computeHoldings()) {
            sharesValue = sharesValue.add(convert(h.getMarketValueNative(), h.getCurrency(), BASE_CCY, fxMissing));
        }
        if (sharesValue.signum() != 0) classTotals.merge(SHARES, scale(sharesValue), BigDecimal::add);
        assets = assets.add(sharesValue);

        for (WealthItem it : wealthItemRepository.findByArchivedFalseOrderByAssetClassAscNameAsc()) {
            BigDecimal v = convert(it.getCurrentValue(), it.getCurrency(), BASE_CCY, fxMissing);
            if (LIABILITY.equalsIgnoreCase(it.getKind())) {
                liabilities = liabilities.add(v);
            } else {
                assets = assets.add(v);
                classTotals.merge(it.getAssetClass(), scale(v), BigDecimal::add);
            }
        }

        assets = scale(assets);
        liabilities = scale(liabilities);
        BigDecimal net = scale(assets.subtract(liabilities));

        NetWorthSnapshot snap = snapshotRepository.findByAsOfDate(asOf).orElseGet(NetWorthSnapshot::new);
        snap.setAsOfDate(asOf);
        snap.setBaseCcy(BASE_CCY);
        snap.setTotalAssets(assets);
        snap.setTotalLiabilities(liabilities);
        snap.setNetWorth(net);
        snap.setBreakdownJson(writeJson(classTotals));
        NetWorthSnapshot saved = snapshotRepository.save(snap);
        if (fxMissing[0]) {
            log.warn("Net-worth snapshot for {} written with a missing FX rate (USD treated 1:1)", asOf);
        }
        return saved;
    }

    @Scheduled(cron = "0 0 2 1 * *") // 02:00 on the 1st of each month
    public void monthlySnapshot() {
        try {
            runSnapshot();
            log.info("Monthly net-worth snapshot written");
        } catch (Exception e) {
            log.error("Failed to write monthly net-worth snapshot", e);
        }
    }

    private BigDecimal convert(BigDecimal amount, String from, String to, boolean[] fxMissing) {
        if (amount == null) return BigDecimal.ZERO;
        Optional<BigDecimal> r = fxService.convert(amount, from, to);
        if (r.isEmpty()) {
            fxMissing[0] = true; // surfaced to the UI; best-effort 1:1 so the page still renders
            return amount;
        }
        return r.get();
    }

    private static BigDecimal scale(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static String normaliseCcy(String c) {
        if (c == null) return BASE_CCY;
        return "USD".equalsIgnoreCase(c.trim()) ? "USD" : BASE_CCY;
    }

    private String writeJson(Map<String, BigDecimal> m) {
        try {
            return objectMapper.writeValueAsString(m);
        } catch (Exception e) {
            log.warn("Failed to serialise snapshot breakdown", e);
            return "{}";
        }
    }
}
