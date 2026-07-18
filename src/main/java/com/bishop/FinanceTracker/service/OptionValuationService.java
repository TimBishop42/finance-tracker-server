package com.bishop.FinanceTracker.service;

import com.bishop.FinanceTracker.model.domain.OptionGrant;
import com.bishop.FinanceTracker.model.domain.Security;
import com.bishop.FinanceTracker.model.domain.SecurityPrice;
import com.bishop.FinanceTracker.model.wealth.OptionGrantView;
import com.bishop.FinanceTracker.repository.OptionGrantRepository;
import com.bishop.FinanceTracker.repository.SecurityPriceRepository;
import com.bishop.FinanceTracker.repository.SecurityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Values option grants at intrinsic value against the underlying security's
 * latest price, and computes vesting from the recurring schedule. Produces
 * native-currency figures; {@code WealthService} converts them to the view
 * currency. See {@link OptionGrant}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OptionValuationService {

    private static final int MONEY_SCALE = 2;
    private static final int QTY_SCALE = 6;

    private final OptionGrantRepository optionGrantRepository;
    private final SecurityRepository securityRepository;
    private final SecurityPriceRepository securityPriceRepository;

    /** Result of applying a recurring vesting schedule as of a given date. */
    public record Vesting(BigDecimal vestedQty, BigDecimal unvestedQty, int vestedTranches, String nextVestDate) {}

    /**
     * Pure vesting calculation. Tranche i (1-based) vests on
     * {@code start.plusMonths((i-1) * intervalMonths)}; the first tranche vests on
     * {@code start}. Equal tranche sizes; the amount vested is proportional to the
     * number of tranches whose date is on/before {@code today}.
     */
    public static Vesting computeVesting(BigDecimal quantity, LocalDate start, int intervalMonths,
                                         int tranches, LocalDate today) {
        BigDecimal qty = quantity == null ? BigDecimal.ZERO : quantity;
        if (tranches <= 0 || start == null) {
            return new Vesting(qty, BigDecimal.ZERO, tranches, null);
        }
        int interval = Math.max(1, intervalMonths);

        int vested = 0;
        for (int i = 1; i <= tranches; i++) {
            LocalDate vestDate = start.plusMonths((long) (i - 1) * interval);
            if (!vestDate.isAfter(today)) {
                vested++;
            } else {
                break; // dates are increasing
            }
        }

        BigDecimal vestedQty;
        if (vested >= tranches) {
            vestedQty = qty;
        } else {
            vestedQty = qty.multiply(BigDecimal.valueOf(vested))
                    .divide(BigDecimal.valueOf(tranches), QTY_SCALE, RoundingMode.HALF_UP);
        }
        BigDecimal unvestedQty = qty.subtract(vestedQty);

        String nextVestDate = vested >= tranches
                ? null
                : start.plusMonths((long) vested * interval).toString();

        return new Vesting(vestedQty, unvestedQty, vested, nextVestDate);
    }

    /** Value every non-archived grant (native currency; display conversion happens in WealthService). */
    public List<OptionGrantView> computeGrants() {
        return computeGrants(LocalDate.now());
    }

    // Package-visible overload with an explicit "today" for deterministic tests.
    List<OptionGrantView> computeGrants(LocalDate today) {
        List<OptionGrantView> out = new ArrayList<>();
        for (OptionGrant g : optionGrantRepository.findByArchivedFalseOrderByNameAsc()) {
            out.add(valueGrant(g, today));
        }
        return out;
    }

    private OptionGrantView valueGrant(OptionGrant g, LocalDate today) {
        Security sec = securityRepository.findById(g.getUnderlyingSecurityId()).orElse(null);
        String currency = sec != null ? sec.getCurrency() : "AUD";

        SecurityPrice priceRow = securityPriceRepository
                .findFirstBySecurityIdOrderByAsOfDateDesc(g.getUnderlyingSecurityId()).orElse(null);
        BigDecimal price = priceRow != null ? priceRow.getPrice() : null;

        boolean isRsu = "RSU".equalsIgnoreCase(g.getGrantType());

        // Options can expire worthless; RSUs just become shares, so expiry doesn't zero them.
        boolean expired = !isRsu && g.getExpiryDate() != null && !g.getExpiryDate().isBlank()
                && LocalDate.parse(g.getExpiryDate()).isBefore(today);

        BigDecimal multiplier = g.getMultiplier() == null ? BigDecimal.ONE : g.getMultiplier();
        Vesting v = computeVesting(g.getQuantity(), LocalDate.parse(g.getVestStartDate()),
                nz(g.getVestIntervalMonths()), nz(g.getVestTranches()), today);

        // Per-share value: RSUs are worth the full price; options are worth the
        // intrinsic spread max(0, price - strike). 0 if no price or (options) expired.
        BigDecimal intrinsic = BigDecimal.ZERO;
        if (price != null && !expired) {
            BigDecimal strike = g.getStrike() == null ? BigDecimal.ZERO : g.getStrike();
            intrinsic = isRsu ? price : price.subtract(strike).max(BigDecimal.ZERO);
        }

        BigDecimal vestedValue = intrinsic.multiply(v.vestedQty()).multiply(multiplier)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal unvestedValue = intrinsic.multiply(v.unvestedQty()).multiply(multiplier)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        // The next tranche to vest: one equal tranche's worth, at current value.
        int tranches = nz(g.getVestTranches());
        BigDecimal nextVestQty = (v.nextVestDate() != null && tranches > 0)
                ? g.getQuantity().divide(BigDecimal.valueOf(tranches), QTY_SCALE, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal nextVestValue = intrinsic.multiply(nextVestQty).multiply(multiplier)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        return OptionGrantView.builder()
                .id(g.getId())
                .name(g.getName())
                .grantType(isRsu ? "RSU" : "OPTION")
                .underlyingSecurityId(g.getUnderlyingSecurityId())
                .underlyingTicker(sec != null ? sec.getTicker() : null)
                .underlyingName(sec != null ? sec.getName() : null)
                .currency(currency)
                .strike(g.getStrike())
                .quantity(g.getQuantity())
                .multiplier(multiplier)
                .vestStartDate(g.getVestStartDate())
                .vestIntervalMonths(g.getVestIntervalMonths())
                .expiryDate(g.getExpiryDate())
                .vestedQuantity(v.vestedQty())
                .unvestedQuantity(v.unvestedQty())
                .vestedTranches(v.vestedTranches())
                .totalTranches(tranches)
                .nextVestDate(v.nextVestDate())
                .nextVestQuantity(nextVestQty)
                .nextVestValueNative(nextVestValue)
                .underlyingPrice(price)
                .underlyingPriceDate(priceRow != null ? priceRow.getAsOfDate() : null)
                .priceMissing(price == null)
                .expired(expired)
                .intrinsicPerShare(intrinsic.setScale(4, RoundingMode.HALF_UP))
                .vestedValueNative(vestedValue)
                .unvestedValueNative(unvestedValue)
                .build();
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }
}
