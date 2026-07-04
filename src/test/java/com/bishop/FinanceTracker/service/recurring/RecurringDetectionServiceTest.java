package com.bishop.FinanceTracker.service.recurring;

import com.bishop.FinanceTracker.model.domain.ExcludedMerchant;
import com.bishop.FinanceTracker.model.domain.Transaction;
import com.bishop.FinanceTracker.model.recurring.RecurringCandidate;
import com.bishop.FinanceTracker.model.recurring.RecurringResponse;
import com.bishop.FinanceTracker.repository.CustomMerchantRepository;
import com.bishop.FinanceTracker.repository.ExcludedMerchantRepository;
import com.bishop.FinanceTracker.service.TransactionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecurringDetectionServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Australia/Sydney");

    @Mock
    private TransactionService transactionService;
    @Mock
    private CustomMerchantRepository customMerchantRepository;
    @Mock
    private ExcludedMerchantRepository excludedMerchantRepository;

    private final HeuristicMerchantNormalizer normalizer = new HeuristicMerchantNormalizer();

    private RecurringDetectionService service() {
        StatisticalRecurringEngine engine = new StatisticalRecurringEngine(
                normalizer, new PhaseSpaceCadenceDetector(), new MerchantKnowledgeBase());
        return new RecurringDetectionService(
                transactionService, customMerchantRepository, excludedMerchantRepository, engine, normalizer);
    }

    private Transaction txn(String name, double amount, LocalDate date, String type) {
        return Transaction.builder()
                .businessName(name)
                .amount(BigDecimal.valueOf(amount))
                .transactionDateTime(date.atStartOfDay(ZONE).toInstant().toEpochMilli())
                .transactionDate(date.toString())
                .category("Bills")
                .transactionType(type)
                .build();
    }

    private List<Transaction> monthly(String name, double amount, String type) {
        List<Transaction> list = new ArrayList<>();
        LocalDate today = LocalDate.now(ZONE);
        for (int i = 0; i < 12; i++) {
            list.add(txn(name, amount, today.minusMonths(i), type));
        }
        return list;
    }

    @Test
    void excludesIncomeSoSalaryIsNotASubscription() {
        List<Transaction> all = new ArrayList<>();
        all.addAll(monthly("EMPLOYER PAYROLL", 5000, "INCOME")); // perfectly monthly income
        all.addAll(monthly("NETFLIX.COM", 19.99, "EXPENSE"));
        when(transactionService.getAll()).thenReturn(all);
        when(customMerchantRepository.findAll()).thenReturn(List.of());
        when(excludedMerchantRepository.findAll()).thenReturn(List.of());

        RecurringResponse resp = service().detect();
        assertTrue(resp.getCandidates().stream().anyMatch(c -> c.getKey().equals("NETFLIX")));
        assertFalse(resp.getCandidates().stream().anyMatch(c -> c.getKey().contains("EMPLOYER")),
                "income must be excluded from recurring detection");
    }

    @Test
    void marksDismissedFromExcludedMerchants() {
        when(transactionService.getAll()).thenReturn(monthly("NETFLIX.COM", 19.99, "EXPENSE"));
        when(customMerchantRepository.findAll()).thenReturn(List.of());
        when(excludedMerchantRepository.findAll()).thenReturn(List.of(new ExcludedMerchant("NETFLIX")));

        RecurringCandidate netflix = service().detect().getCandidates().stream()
                .filter(c -> c.getKey().equals("NETFLIX")).findFirst().orElseThrow();
        assertTrue(netflix.isDismissed());
    }

    @Test
    void normalisesLegacyRawExcludedKeys() {
        when(transactionService.getAll()).thenReturn(monthly("NETFLIX.COM", 19.99, "EXPENSE"));
        when(customMerchantRepository.findAll()).thenReturn(List.of());
        // A v1-era raw excluded key still dismisses the normalised candidate.
        when(excludedMerchantRepository.findAll()).thenReturn(List.of(new ExcludedMerchant("SQ *NETFLIX.COM")));

        RecurringCandidate netflix = service().detect().getCandidates().stream()
                .filter(c -> c.getKey().equals("NETFLIX")).findFirst().orElseThrow();
        assertTrue(netflix.isDismissed());
    }
}
