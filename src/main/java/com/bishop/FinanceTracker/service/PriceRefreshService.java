package com.bishop.FinanceTracker.service;

import com.bishop.FinanceTracker.model.domain.FxRate;
import com.bishop.FinanceTracker.model.domain.Security;
import com.bishop.FinanceTracker.model.domain.SecurityPrice;
import com.bishop.FinanceTracker.model.wealth.PriceQuote;
import com.bishop.FinanceTracker.repository.FxRateRepository;
import com.bishop.FinanceTracker.repository.SecurityPriceRepository;
import com.bishop.FinanceTracker.repository.SecurityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Pulls live prices (and the USD/AUD rate) from a {@link PriceProvider} into the
 * same {@code security_prices} / {@code fx_rates} tables manual entry uses, so
 * holdings recompute automatically. Runs on a configurable schedule and on demand.
 *
 * Manual wins: a hand-entered (MANUAL) price/rate for a given day is never
 * overwritten by the feed — so user corrections stick.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PriceRefreshService {

    /** Per-symbol outcome of a refresh run (surfaced to the UI). */
    public record RefreshResult(String ticker, String symbol, boolean ok, String price, String message) {}

    private final SecurityRepository securityRepository;
    private final SecurityPriceRepository securityPriceRepository;
    private final FxRateRepository fxRateRepository;
    private final PriceProvider priceProvider;

    @Value("${app.price-refresh.enabled:true}")
    private boolean enabled;

    @Value("${app.price-refresh.zone:Australia/Sydney}")
    private String zone;

    @Scheduled(
            cron = "${app.price-refresh.cron:0 0 18 * * *}",
            zone = "${app.price-refresh.zone:Australia/Sydney}")
    public void scheduledRefresh() {
        if (!enabled) {
            log.info("Scheduled price refresh is disabled (app.price-refresh.enabled=false)");
            return;
        }
        try {
            List<RefreshResult> results = refreshNow();
            long ok = results.stream().filter(RefreshResult::ok).count();
            log.info("Scheduled price refresh complete: {}/{} symbols updated", ok, results.size());
        } catch (Exception e) {
            log.error("Scheduled price refresh failed", e);
        }
    }

    /** Fetch every security's price (and the FX rate) now; returns a per-symbol report. */
    public List<RefreshResult> refreshNow() {
        String today = LocalDate.now(ZoneId.of(zone)).toString();
        List<RefreshResult> results = new ArrayList<>();
        for (Security sec : securityRepository.findAllByOrderByTickerAsc()) {
            String symbol = resolveSymbol(sec);
            Optional<PriceQuote> quote = priceProvider.fetch(symbol);
            if (quote.isEmpty()) {
                results.add(new RefreshResult(sec.getTicker(), symbol, false, null, "no price returned"));
                continue;
            }
            BigDecimal price = quote.get().price();
            Optional<SecurityPrice> existing =
                    securityPriceRepository.findBySecurityIdAndAsOfDate(sec.getId(), today);
            if (existing.isPresent() && "MANUAL".equalsIgnoreCase(existing.get().getSource())) {
                results.add(new RefreshResult(sec.getTicker(), symbol, true, price.toPlainString(),
                        "kept manual price for today"));
                continue;
            }
            SecurityPrice row = existing.orElseGet(SecurityPrice::new);
            row.setSecurityId(sec.getId());
            row.setAsOfDate(today);
            row.setPrice(price);
            row.setSource(priceProvider.name());
            securityPriceRepository.save(row);
            results.add(new RefreshResult(sec.getTicker(), symbol, true, price.toPlainString(), "updated"));
        }
        refreshFx(today);
        return results;
    }

    /** Live quote for one security without persisting — used to validate a symbol. */
    public Optional<PriceQuote> quote(Security sec) {
        return priceProvider.fetch(resolveSymbol(sec));
    }

    private void refreshFx(String today) {
        try {
            Optional<PriceQuote> quote = priceProvider.fetch("USDAUD=X");
            if (quote.isEmpty()) return;
            Optional<FxRate> existing =
                    fxRateRepository.findByBaseCcyAndQuoteCcyAndAsOfDate("USD", "AUD", today);
            if (existing.isPresent() && "MANUAL".equalsIgnoreCase(existing.get().getSource())) return;
            FxRate row = existing.orElseGet(FxRate::new);
            row.setBaseCcy("USD");
            row.setQuoteCcy("AUD");
            row.setAsOfDate(today);
            row.setRate(quote.get().price());
            row.setSource(priceProvider.name());
            fxRateRepository.save(row);
        } catch (Exception e) {
            log.warn("FX refresh failed: {}", e.getMessage());
        }
    }

    /**
     * Resolve the provider lookup symbol. Prefers an explicit override; otherwise
     * keeps an already-suffixed ticker, appends ".AX" for ASX/AUD securities, and
     * uses the bare ticker for US listings (NASDAQ and NYSE share the bare symbol).
     */
    static String resolveSymbol(Security sec) {
        if (sec.getPriceSymbol() != null && !sec.getPriceSymbol().isBlank()) {
            return sec.getPriceSymbol().trim();
        }
        String ticker = sec.getTicker() == null ? "" : sec.getTicker().trim();
        if (ticker.contains(".")) return ticker;
        boolean au = "ASX".equalsIgnoreCase(sec.getExchange()) || "AUD".equalsIgnoreCase(sec.getCurrency());
        return au ? ticker + ".AX" : ticker;
    }
}
