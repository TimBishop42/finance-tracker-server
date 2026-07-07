package com.bishop.FinanceTracker.service;

import com.bishop.FinanceTracker.model.domain.OptionGrant;
import com.bishop.FinanceTracker.model.domain.Security;
import com.bishop.FinanceTracker.model.domain.SecurityPrice;
import com.bishop.FinanceTracker.model.wealth.OptionGrantView;
import com.bishop.FinanceTracker.repository.OptionGrantRepository;
import com.bishop.FinanceTracker.repository.SecurityPriceRepository;
import com.bishop.FinanceTracker.repository.SecurityRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OptionValuationServiceTest {

    @Mock
    private OptionGrantRepository optionGrantRepository;
    @Mock
    private SecurityRepository securityRepository;
    @Mock
    private SecurityPriceRepository securityPriceRepository;

    private OptionValuationService service() {
        return new OptionValuationService(optionGrantRepository, securityRepository, securityPriceRepository);
    }

    // ── pure vesting math ───────────────────────────────────────────────────

    @Test
    void vestingCountsTranchesOnOrBeforeToday() {
        LocalDate start = LocalDate.of(2025, 1, 1); // quarterly: Jan, Apr, Jul, Oct
        // Two tranches vested by mid-May (Jan + Apr), two still to come.
        OptionValuationService.Vesting v = OptionValuationService.computeVesting(
                new BigDecimal("400"), start, 3, 4, LocalDate.of(2025, 5, 15));
        assertEquals(2, v.vestedTranches());
        assertEquals(0, new BigDecimal("200").compareTo(v.vestedQty()));
        assertEquals(0, new BigDecimal("200").compareTo(v.unvestedQty()));
        assertEquals("2025-07-01", v.nextVestDate());
    }

    @Test
    void vestingBeforeFirstDateIsZero() {
        OptionValuationService.Vesting v = OptionValuationService.computeVesting(
                new BigDecimal("400"), LocalDate.of(2025, 1, 1), 3, 4, LocalDate.of(2024, 12, 31));
        assertEquals(0, v.vestedTranches());
        assertEquals(0, BigDecimal.ZERO.compareTo(v.vestedQty()));
        assertEquals("2025-01-01", v.nextVestDate());
    }

    @Test
    void vestingFullyVestedHasNoNextDate() {
        OptionValuationService.Vesting v = OptionValuationService.computeVesting(
                new BigDecimal("400"), LocalDate.of(2025, 1, 1), 3, 4, LocalDate.of(2026, 6, 1));
        assertEquals(4, v.vestedTranches());
        assertEquals(0, new BigDecimal("400").compareTo(v.vestedQty()));
        assertNull(v.nextVestDate());
    }

    // ── valuation ───────────────────────────────────────────────────────────

    private OptionGrant grant(BigDecimal strike, BigDecimal qty, String start, int tranches, String expiry) {
        return OptionGrant.builder()
                .id(1L).name("Grant").underlyingSecurityId(10L)
                .strike(strike).quantity(qty).multiplier(BigDecimal.ONE)
                .vestStartDate(start).vestIntervalMonths(3).vestTranches(tranches)
                .expiryDate(expiry).archived(false).build();
    }

    private void stubSecurity(BigDecimal price) {
        when(securityRepository.findById(10L)).thenReturn(Optional.of(
                Security.builder().id(10L).ticker("ACME").currency("USD").build()));
        if (price != null) {
            when(securityPriceRepository.findFirstBySecurityIdOrderByAsOfDateDesc(anyLong()))
                    .thenReturn(Optional.of(SecurityPrice.builder().securityId(10L)
                            .asOfDate("2026-06-01").price(price).source("MANUAL").build()));
        } else {
            when(securityPriceRepository.findFirstBySecurityIdOrderByAsOfDateDesc(anyLong()))
                    .thenReturn(Optional.empty());
        }
    }

    @Test
    void intrinsicValueOnlyCountsVestedQuantity() {
        // price 100, strike 60 -> $40 intrinsic; 200 of 400 vested -> vested value 8000.
        stubSecurity(new BigDecimal("100"));
        when(optionGrantRepository.findByArchivedFalseOrderByNameAsc())
                .thenReturn(List.of(grant(new BigDecimal("60"), new BigDecimal("400"), "2025-01-01", 4, null)));

        OptionGrantView v = service().computeGrants(LocalDate.of(2025, 5, 15)).get(0);
        assertEquals(0, new BigDecimal("8000.00").compareTo(v.getVestedValueNative()));
        assertEquals(0, new BigDecimal("8000.00").compareTo(v.getUnvestedValueNative()));
        assertEquals("USD", v.getCurrency());
    }

    @Test
    void rsuGrantsAreValuedAtFullPriceNotIntrinsic() {
        // RSU: strike is irrelevant; vested value = price × vestedQty.
        stubSecurity(new BigDecimal("100"));
        OptionGrant rsu = OptionGrant.builder()
                .id(2L).name("RSU").grantType("RSU").underlyingSecurityId(10L)
                .strike(BigDecimal.ZERO).quantity(new BigDecimal("400")).multiplier(BigDecimal.ONE)
                .vestStartDate("2025-01-01").vestIntervalMonths(3).vestTranches(4)
                .archived(false).build();
        when(optionGrantRepository.findByArchivedFalseOrderByNameAsc()).thenReturn(List.of(rsu));

        OptionGrantView v = service().computeGrants(LocalDate.of(2025, 5, 15)).get(0);
        // 200 of 400 vested × $100 = 20,000 (full price, not a spread).
        assertEquals(0, new BigDecimal("20000.00").compareTo(v.getVestedValueNative()));
        assertEquals("RSU", v.getGrantType());
    }

    @Test
    void underwaterOptionsAreWorthZeroNotNegative() {
        stubSecurity(new BigDecimal("50")); // below the 60 strike
        when(optionGrantRepository.findByArchivedFalseOrderByNameAsc())
                .thenReturn(List.of(grant(new BigDecimal("60"), new BigDecimal("400"), "2025-01-01", 4, null)));

        OptionGrantView v = service().computeGrants(LocalDate.of(2026, 6, 1)).get(0);
        assertEquals(0, BigDecimal.ZERO.compareTo(v.getVestedValueNative().stripTrailingZeros()));
        assertEquals(0, BigDecimal.ZERO.compareTo(v.getIntrinsicPerShare().stripTrailingZeros()));
    }

    @Test
    void expiredGrantsAreWorthZero() {
        stubSecurity(new BigDecimal("100"));
        when(optionGrantRepository.findByArchivedFalseOrderByNameAsc())
                .thenReturn(List.of(grant(new BigDecimal("60"), new BigDecimal("400"), "2025-01-01", 4, "2026-01-01")));

        OptionGrantView v = service().computeGrants(LocalDate.of(2026, 6, 1)).get(0);
        assertTrue(v.isExpired());
        assertEquals(0, BigDecimal.ZERO.compareTo(v.getVestedValueNative().stripTrailingZeros()));
    }

    @Test
    void missingPriceIsFlaggedAndValuedZero() {
        stubSecurity(null);
        when(optionGrantRepository.findByArchivedFalseOrderByNameAsc())
                .thenReturn(List.of(grant(new BigDecimal("60"), new BigDecimal("400"), "2025-01-01", 4, null)));

        OptionGrantView v = service().computeGrants(LocalDate.of(2026, 6, 1)).get(0);
        assertTrue(v.isPriceMissing());
        assertFalse(v.isExpired());
        assertEquals(0, BigDecimal.ZERO.compareTo(v.getVestedValueNative().stripTrailingZeros()));
    }
}
