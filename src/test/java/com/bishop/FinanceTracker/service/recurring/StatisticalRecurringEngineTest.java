package com.bishop.FinanceTracker.service.recurring;

import com.bishop.FinanceTracker.model.domain.Transaction;
import com.bishop.FinanceTracker.model.recurring.RecurringCandidate;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatisticalRecurringEngineTest {

    private static final ZoneId ZONE = ZoneId.of("Australia/Sydney");

    private final StatisticalRecurringEngine engine = new StatisticalRecurringEngine(
            new HeuristicMerchantNormalizer(),
            new PhaseSpaceCadenceDetector(),
            new MerchantKnowledgeBase());

    private Transaction txn(String name, double amount, LocalDate date) {
        return Transaction.builder()
                .businessName(name)
                .amount(BigDecimal.valueOf(amount))
                .transactionDateTime(date.atStartOfDay(ZONE).toInstant().toEpochMilli())
                .transactionDate(date.toString())
                .category("Bills")
                .transactionType("EXPENSE")
                .build();
    }

    /** {@code count} monthly charges of {@code amount}, most recent one {@code endMonthsAgo} months back. */
    private List<Transaction> monthly(String name, double amount, int count, int endMonthsAgo) {
        List<Transaction> list = new ArrayList<>();
        LocalDate today = LocalDate.now(ZONE);
        for (int i = 0; i < count; i++) {
            list.add(txn(name, amount, today.minusMonths((long) endMonthsAgo + i)));
        }
        return list;
    }

    private Optional<RecurringCandidate> find(List<RecurringCandidate> candidates, String key) {
        return candidates.stream().filter(c -> c.getKey().equals(key)).findFirst();
    }

    @Test
    void detectsMonthlySubscription() {
        List<RecurringCandidate> out = engine.detect(monthly("NETFLIX.COM", 19.99, 12, 0), List.of());
        RecurringCandidate c = find(out, "NETFLIX").orElseThrow();
        assertEquals("monthly", c.getCadence());
        assertTrue(c.isSubscription());
        assertEquals("seed", c.getKnowledgeSource());
        assertEquals(new BigDecimal("19.99"), c.getExpectedAmount());
        assertTrue(c.getConfidence() > 0.7);
        assertTrue(LocalDate.parse(c.getNextPredictedDate()).isAfter(LocalDate.now(ZONE).minusDays(1)));
    }

    @Test
    void excludesEverydayNoiseMerchant() {
        // Woolworths is in the noise list — must never be recurring, even monthly.
        assertTrue(find(engine.detect(monthly("WOOLWORTHS", 120.0, 12, 0), List.of()), "WOOLWORTHS").isEmpty());
    }

    @Test
    void ignoresUnknownIrregularSpend() {
        List<Transaction> irregular = List.of(
                txn("RANDOM SHOP", 30, LocalDate.now(ZONE).minusDays(2)),
                txn("RANDOM SHOP", 65, LocalDate.now(ZONE).minusDays(19)),
                txn("RANDOM SHOP", 12, LocalDate.now(ZONE).minusDays(51)));
        assertTrue(find(engine.detect(irregular, List.of()), "RANDOM SHOP").isEmpty());
    }

    @Test
    void flagsVariableAmountButStillDetects() {
        double[] amts = {90, 130, 95, 145, 88, 160, 92, 150, 100, 140, 85, 155};
        List<Transaction> txns = new ArrayList<>();
        LocalDate today = LocalDate.now(ZONE);
        for (int i = 0; i < amts.length; i++) {
            txns.add(txn("AGL", amts[i], today.minusMonths(i)));
        }
        RecurringCandidate c = find(engine.detect(txns, List.of()), "AGL").orElseThrow();
        assertTrue(c.isAmountVariable());
        assertTrue(c.isBill());
    }

    @Test
    void dropsCancelledSubscriptionViaRecency() {
        // 12 monthly charges that stopped ~18 months ago — should not linger.
        assertTrue(find(engine.detect(monthly("NETFLIX.COM", 19.99, 12, 18), List.of()), "NETFLIX").isEmpty());
    }

    @Test
    void predictsNextChargeInFuture() {
        RecurringCandidate c = find(engine.detect(monthly("SPOTIFY", 12.0, 8, 0), List.of()), "SPOTIFY").orElseThrow();
        assertFalse(LocalDate.parse(c.getNextPredictedDate()).isBefore(LocalDate.now(ZONE)));
    }

    @Test
    void surfacesThinHistoryUnknownAsSuggestion() {
        // Two same-priced charges ~1 month apart, merchant not in the KB — too thin for
        // the phase-space detector, but surfaced as a low-confidence suggestion to confirm.
        LocalDate today = LocalDate.now(ZONE);
        List<Transaction> twoCharges = List.of(
                txn("BREAKING POINTS", 10.0, today.minusMonths(1)),
                txn("BREAKING POINTS", 10.0, today));
        RecurringCandidate c = find(engine.detect(twoCharges, List.of()), "BREAKING POINTS").orElseThrow();
        assertTrue(c.isSuggestion());
    }

    @Test
    void groupsNoisyBankDescriptorAcrossVaryingReferences() {
        // Same payee, different per-payment reference codes each month — must still group.
        LocalDate today = LocalDate.now(ZONE);
        List<Transaction> rent = List.of(
                txn("HUDSON CBD FLATB WEB PMTS QKX1V1 WEB ID: 9001904314", 6249.41, today.minusMonths(2)),
                txn("HUDSON CBD FLATB WEB PMTS ZZ9K2P WEB ID: 9001904314", 6300.00, today.minusMonths(1)),
                txn("HUDSON CBD FLATB WEB PMTS AA1B2C WEB ID: 9001904314", 6200.00, today));
        List<RecurringCandidate> out = engine.detect(rent, List.of());
        assertEquals(1, out.stream().filter(c -> c.getName().toLowerCase().contains("hudson")).count(),
                "varying references must collapse to one group");
    }
}
