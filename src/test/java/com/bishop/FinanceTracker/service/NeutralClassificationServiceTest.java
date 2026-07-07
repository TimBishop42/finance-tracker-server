package com.bishop.FinanceTracker.service;

import com.bishop.FinanceTracker.model.domain.NeutralMerchant;
import com.bishop.FinanceTracker.repository.NeutralMerchantRepository;
import com.bishop.FinanceTracker.service.recurring.HeuristicMerchantNormalizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NeutralClassificationServiceTest {

    @Mock
    private NeutralMerchantRepository repository;

    private final HeuristicMerchantNormalizer normalizer = new HeuristicMerchantNormalizer();

    private NeutralClassificationService service() {
        return new NeutralClassificationService(repository, normalizer);
    }

    @Test
    void seedPatternsCatchCardPaymentsAndTransfers() {
        when(repository.findAll()).thenReturn(List.of());
        Set<String> neutral = service().neutralNames(
                List.of("PAYMENT - THANK YOU", "TRANSFER TO SAVINGS", "NETFLIX.COM", "WOOLWORTHS"));
        assertTrue(neutral.contains("PAYMENT - THANK YOU"));
        assertTrue(neutral.contains("TRANSFER TO SAVINGS"));
        assertFalse(neutral.contains("NETFLIX.COM"));
        assertFalse(neutral.contains("WOOLWORTHS"));
    }

    @Test
    void savedRuleMatchesSameMerchantAcrossImportVariants() {
        // A rule saved from one statement (with a date) should match the same
        // merchant on a later statement (different trailing store number).
        String key = normalizer.normalize("My Offset Transfer 12/03").key();
        when(repository.findAll()).thenReturn(List.of(NeutralMerchant.builder().merchantKey(key).build()));

        Set<String> neutral = service().neutralNames(List.of("MY OFFSET TRANSFER 4021", "COLES 555"));
        assertTrue(neutral.contains("MY OFFSET TRANSFER 4021"));
        assertFalse(neutral.contains("COLES 555"));
    }

    @Test
    void genuineBillsPaidByDirectDebitAreNotNeutralised() {
        when(repository.findAll()).thenReturn(List.of());
        // "direct debit" / "bpay" wording is intentionally NOT seeded, so real
        // bills aren't wrongly treated as transfers.
        Set<String> neutral = service().neutralNames(List.of("ORIGIN ENERGY DIRECT DEBIT", "BPAY TELSTRA"));
        assertTrue(neutral.isEmpty());
    }
}
