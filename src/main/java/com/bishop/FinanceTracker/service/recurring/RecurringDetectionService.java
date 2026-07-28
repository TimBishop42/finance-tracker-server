package com.bishop.FinanceTracker.service.recurring;

import com.bishop.FinanceTracker.model.domain.CustomMerchant;
import com.bishop.FinanceTracker.model.domain.ExcludedMerchant;
import com.bishop.FinanceTracker.model.domain.Transaction;
import com.bishop.FinanceTracker.model.recurring.RecurringCandidate;
import com.bishop.FinanceTracker.model.recurring.RecurringResponse;
import com.bishop.FinanceTracker.repository.CustomMerchantRepository;
import com.bishop.FinanceTracker.repository.ExcludedMerchantRepository;
import com.bishop.FinanceTracker.service.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Orchestrates recurring detection: pulls the full transaction history, excludes
 * income (so a salary deposit is never a false-positive subscription, doc §2.2),
 * hands the expenses to the detection engine, and marks user-dismissed merchants.
 *
 * <p>Detection is computed on demand from transactions + the existing
 * {@code custom_merchants}/{@code excluded_merchants} tables — no new tables for
 * this tier. The review queue (persisted confirmed recurrings) is the next tier.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecurringDetectionService {

    private static final String EXPENSE = "EXPENSE";

    private final TransactionService transactionService;
    private final CustomMerchantRepository customMerchantRepository;
    private final ExcludedMerchantRepository excludedMerchantRepository;
    private final RecurringDetectionEngine engine;
    private final MerchantNormalizer normalizer;
    private final com.bishop.FinanceTracker.service.SubscriptionService subscriptionService;

    public RecurringResponse detect() {
        long start = System.currentTimeMillis();

        // Only expenses can be subscriptions/bills. Exclude income and NEUTRAL
        // (internal transfers / credit-card payments); null type = legacy expense.
        List<Transaction> expenses = transactionService.getAll().stream()
                .filter(t -> t.getTransactionType() == null
                        || EXPENSE.equalsIgnoreCase(t.getTransactionType()))
                .collect(Collectors.toList());

        List<CustomMerchant> customRules = customMerchantRepository.findAll();
        List<RecurringCandidate> candidates = engine.detect(expenses, customRules);

        // Excluded keys were historically stored raw (v1); normalise them so old
        // dismissals still line up with the new normalised candidate keys.
        Set<String> dismissedKeys = excludedMerchantRepository.findAll().stream()
                .map(ExcludedMerchant::getMerchantKey)
                .flatMap(k -> java.util.stream.Stream.of(
                        k == null ? "" : k.trim().toUpperCase(),
                        normalizer.normalize(k).key()))
                .filter(k -> !k.isBlank())
                .collect(Collectors.toSet());

        // Annotate detections already promoted into the unified subscriptions table
        // (feature doc §2A.4.2) so the UI can show "tracked" state and avoid dupes.
        java.util.Map<String, Long> confirmed = subscriptionService.confirmedKeyToId();
        candidates.forEach(c -> {
            c.setDismissed(dismissedKeys.contains(c.getKey()));
            Long subId = confirmed.get(c.getKey());
            if (subId != null) {
                c.setConfirmed(true);
                c.setSubscriptionId(subId);
            }
        });

        log.info("Recurring detection ({}) produced {} candidates from {} expenses in {} ms",
                engine.name(), candidates.size(), expenses.size(), System.currentTimeMillis() - start);

        return RecurringResponse.builder()
                .candidates(candidates)
                .generatedAt(System.currentTimeMillis())
                .engine(engine.name())
                .build();
    }
}
