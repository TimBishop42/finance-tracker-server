package com.bishop.FinanceTracker.service;

import com.bishop.FinanceTracker.model.domain.FxRate;
import com.bishop.FinanceTracker.repository.FxRateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * Currency conversion for the Total Wealth tracker. v1 supports AUD and USD only,
 * driven by the latest manually-entered USD/AUD rate (1 USD = rate AUD).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FxService {

    public static final String BASE = "USD";
    public static final String QUOTE = "AUD";
    private static final int SCALE = 6;

    private final FxRateRepository fxRateRepository;

    /** Latest USD->AUD rate (AUD per 1 USD), or empty if none is set. */
    public Optional<BigDecimal> latestUsdAud() {
        return fxRateRepository.findFirstByBaseCcyAndQuoteCcyOrderByAsOfDateDesc(BASE, QUOTE)
                .map(FxRate::getRate);
    }

    /**
     * Convert between AUD and USD. Returns empty when a rate is required but none
     * exists (callers surface this as {@code fxMissing} rather than silently using 1.0).
     */
    public Optional<BigDecimal> convert(BigDecimal amount, String from, String to) {
        if (amount == null) return Optional.of(BigDecimal.ZERO);
        if (from == null || to == null || from.equalsIgnoreCase(to)) return Optional.of(amount);

        Optional<BigDecimal> rate = latestUsdAud();
        if (rate.isEmpty()) return Optional.empty();
        BigDecimal r = rate.get();

        if ("USD".equalsIgnoreCase(from) && "AUD".equalsIgnoreCase(to)) {
            return Optional.of(amount.multiply(r));
        }
        if ("AUD".equalsIgnoreCase(from) && "USD".equalsIgnoreCase(to)) {
            if (r.signum() == 0) return Optional.empty();
            return Optional.of(amount.divide(r, SCALE, RoundingMode.HALF_UP));
        }
        // Any other pair is out of scope for v1; pass through unchanged.
        return Optional.of(amount);
    }
}
