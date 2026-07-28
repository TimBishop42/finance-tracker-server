package com.bishop.FinanceTracker.service.notification;

import com.bishop.FinanceTracker.model.domain.NotifiedEvent;
import com.bishop.FinanceTracker.model.domain.Subscription;
import com.bishop.FinanceTracker.model.domain.SubscriptionPriceHistory;
import com.bishop.FinanceTracker.repository.NotifiedEventRepository;
import com.bishop.FinanceTracker.repository.SubscriptionPriceHistoryRepository;
import com.bishop.FinanceTracker.repository.SubscriptionRepository;
import com.bishop.FinanceTracker.service.AggregationService;
import com.bishop.FinanceTracker.service.UserSettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * The notification engine (feature doc §2A.1): a daily sweep that evaluates
 * triggers — upcoming charges, budget overrun, price changes, trial→paid — and
 * dispatches through the {@link Notifier} seam, de-duped via {@code notified_events}.
 *
 * <p><b>Delivery is deferred:</b> the wired {@link Notifier} is {@link LoggingNotifier}
 * (no-op/log) until an email channel exists. The engine itself is complete and runs.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final ZoneId ZONE = ZoneId.of("Australia/Sydney");
    private static final String CANCELLED = "cancelled";
    private static final String PAUSED = "paused";
    private static final String TRIAL = "trial";

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionPriceHistoryRepository priceHistoryRepository;
    private final NotifiedEventRepository notifiedEventRepository;
    private final UserSettingsService userSettingsService;
    private final AggregationService aggregationService;
    private final Notifier notifier;

    @Value("${app.notifications.enabled:true}")
    private boolean enabled;

    @Value("${app.notifications.lead-days:3}")
    private int leadDays;

    /** Budget-overrun alert thresholds, as percentages of the monthly target. */
    @Value("${app.notifications.budget-thresholds:90,100}")
    private String budgetThresholds;

    /** Daily sweep (feature doc §2A.1). Scheduling is already enabled app-wide. */
    @Scheduled(cron = "${app.notifications.cron:0 0 8 * * *}", zone = "Australia/Sydney")
    public void scheduledSweep() {
        runSweep();
    }

    /** Evaluate all triggers once. Safe to call ad hoc (idempotent via de-dupe). */
    public void runSweep() {
        if (!enabled) {
            return;
        }
        LocalDate today = LocalDate.now(ZONE);
        try {
            upcomingCharges(today);
            budgetOverrun(today);
            priceChanges(today);
            trialEnding(today);
        } catch (Exception e) {
            log.error("Notification sweep failed", e);
        }
    }

    private void upcomingCharges(LocalDate today) {
        for (Subscription s : subscriptionRepository.findAll()) {
            if (isInactive(s) || s.getNextChargeDate() == null) {
                continue;
            }
            LocalDate next = tryParse(s.getNextChargeDate());
            if (next == null) {
                continue;
            }
            long days = ChronoUnit.DAYS.between(today, next);
            if (days < 0 || days > leadDays) {
                continue;
            }
            String key = "upcoming:" + s.getId() + ":" + s.getNextChargeDate();
            if (fresh(key)) {
                notifier.send(Notifier.Type.UPCOMING_CHARGE,
                        s.getName() + " due " + s.getNextChargeDate(),
                        money(s.getAmount()) + " for " + s.getName() + " is due on " + s.getNextChargeDate());
                record(key);
            }
        }
    }

    private void budgetOverrun(LocalDate today) {
        BigDecimal max = userSettingsService.getMaxSpendValue();
        if (max == null || max.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        Double currentMonth = aggregationService.homeData().getCurrentMonth();
        if (currentMonth == null) {
            return;
        }
        double pct = currentMonth / max.doubleValue() * 100.0;
        YearMonth month = YearMonth.from(today);
        for (int threshold : parseThresholds()) {
            if (pct >= threshold) {
                String key = "budget:" + month + ":" + threshold;
                if (fresh(key)) {
                    notifier.send(Notifier.Type.BUDGET_OVERRUN,
                            "Spending at " + Math.round(pct) + "% of budget",
                            "This month's spend " + money(BigDecimal.valueOf(currentMonth))
                                    + " has reached " + threshold + "% of your "
                                    + money(max) + " budget.");
                    record(key);
                }
            }
        }
    }

    private void priceChanges(LocalDate today) {
        for (Subscription s : subscriptionRepository.findAll()) {
            List<SubscriptionPriceHistory> hist =
                    priceHistoryRepository.findBySubscriptionIdOrderByEffectiveDateAsc(s.getId());
            if (hist.size() < 2) {
                continue;
            }
            SubscriptionPriceHistory last = hist.get(hist.size() - 1);
            SubscriptionPriceHistory prev = hist.get(hist.size() - 2);
            if (last.getAmount().compareTo(prev.getAmount()) == 0) {
                continue;
            }
            LocalDate eff = tryParse(last.getEffectiveDate());
            // Only alert on recent changes so a first run doesn't replay old history.
            if (eff == null || ChronoUnit.DAYS.between(eff, today) > 35) {
                continue;
            }
            String key = "price:" + s.getId() + ":" + last.getEffectiveDate();
            if (fresh(key)) {
                String dir = last.getAmount().compareTo(prev.getAmount()) > 0 ? "increased" : "decreased";
                notifier.send(Notifier.Type.PRICE_CHANGE,
                        s.getName() + " price " + dir,
                        s.getName() + " changed from " + money(prev.getAmount())
                                + " to " + money(last.getAmount()) + " on " + last.getEffectiveDate() + ".");
                record(key);
            }
        }
    }

    private void trialEnding(LocalDate today) {
        for (Subscription s : subscriptionRepository.findAll()) {
            if (!TRIAL.equals(s.getStatus()) || s.getTrialEndDate() == null) {
                continue;
            }
            LocalDate end = tryParse(s.getTrialEndDate());
            if (end == null) {
                continue;
            }
            long days = ChronoUnit.DAYS.between(today, end);
            if (days < 0 || days > leadDays) {
                continue;
            }
            String key = "trial:" + s.getId() + ":" + s.getTrialEndDate();
            if (fresh(key)) {
                notifier.send(Notifier.Type.TRIAL_ENDING,
                        s.getName() + " trial ends " + s.getTrialEndDate(),
                        s.getName() + " converts to a paid charge of " + money(s.getAmount())
                                + " on " + s.getTrialEndDate() + ".");
                record(key);
            }
        }
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private boolean isInactive(Subscription s) {
        return CANCELLED.equals(s.getStatus()) || PAUSED.equals(s.getStatus());
    }

    private boolean fresh(String key) {
        return !notifiedEventRepository.existsByEventKey(key);
    }

    private void record(String key) {
        notifiedEventRepository.save(NotifiedEvent.builder()
                .eventKey(key)
                .createdAt(System.currentTimeMillis())
                .build());
    }

    private int[] parseThresholds() {
        try {
            String[] parts = budgetThresholds.split(",");
            int[] out = new int[parts.length];
            for (int i = 0; i < parts.length; i++) {
                out[i] = Integer.parseInt(parts[i].trim());
            }
            return out;
        } catch (NumberFormatException e) {
            return new int[]{90, 100};
        }
    }

    private static LocalDate tryParse(String raw) {
        try {
            return raw == null || raw.isBlank() ? null : LocalDate.parse(raw.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String money(BigDecimal v) {
        return v == null ? "$0" : "$" + v.setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
