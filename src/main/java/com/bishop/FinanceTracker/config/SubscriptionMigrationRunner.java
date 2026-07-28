package com.bishop.FinanceTracker.config;

import com.bishop.FinanceTracker.model.domain.ManualBill;
import com.bishop.FinanceTracker.model.domain.Subscription;
import com.bishop.FinanceTracker.model.domain.SubscriptionPriceHistory;
import com.bishop.FinanceTracker.repository.ManualBillRepository;
import com.bishop.FinanceTracker.repository.SubscriptionPriceHistoryRepository;
import com.bishop.FinanceTracker.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * One-time, idempotent migration of the legacy {@code manual_bills} table into the
 * unified {@code subscriptions} table (feature doc §2A.4). Each manual bill becomes
 * a {@code source=manual, kind=bill} subscription; already-migrated rows are skipped
 * via {@code legacy_manual_bill_id}, so this is safe to run on every startup. The
 * original {@code manual_bills} rows are left intact as a backstop.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionMigrationRunner implements ApplicationRunner {

    private static final ZoneId ZONE = ZoneId.of("Australia/Sydney");
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;

    private final ManualBillRepository manualBillRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionPriceHistoryRepository priceHistoryRepository;

    @Override
    public void run(ApplicationArguments args) {
        int migrated = 0;
        for (ManualBill bill : manualBillRepository.findAll()) {
            if (subscriptionRepository.findByLegacyManualBillId(bill.getId()).isPresent()) {
                continue; // already migrated
            }

            Subscription sub = Subscription.builder()
                    .source("manual")
                    .kind("bill")
                    .status("active")
                    .name(bill.getName())
                    .category(bill.getCategory())
                    .billingCycle("monthly")
                    .amount(bill.getAmount())
                    .amountVariable(bill.getAmountVariable())
                    .dueDay(bill.getDueDay())
                    .paidDates(bill.getPaidDates() == null ? "" : bill.getPaidDates())
                    .createTime(bill.getCreateTime() != null ? bill.getCreateTime() : System.currentTimeMillis())
                    .legacyManualBillId(bill.getId())
                    .build();

            Subscription saved = subscriptionRepository.save(sub);
            if (bill.getAmount() != null) {
                priceHistoryRepository.save(SubscriptionPriceHistory.builder()
                        .subscriptionId(saved.getId())
                        .effectiveDate(LocalDate.ofInstant(
                                Instant.ofEpochMilli(saved.getCreateTime()), ZONE).format(ISO))
                        .amount(bill.getAmount())
                        .build());
            }
            migrated++;
        }
        if (migrated > 0) {
            log.info("Migrated {} manual bill(s) into the unified subscriptions table", migrated);
        }
    }
}
