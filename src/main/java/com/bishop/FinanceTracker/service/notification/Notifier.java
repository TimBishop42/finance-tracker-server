package com.bishop.FinanceTracker.service.notification;

/**
 * Delivery seam for user notifications (feature doc §2A.1). Kept as an interface so
 * channels are additive. <b>Delivery is deferred</b> — the only implementation today
 * is {@link LoggingNotifier} (a no-op that logs), pending a domain + mail server;
 * an email/webhook {@code Notifier} drops in later without touching the trigger logic.
 */
public interface Notifier {

    /** Notification categories, so a future channel can route/format per type. */
    enum Type { UPCOMING_CHARGE, BUDGET_OVERRUN, PRICE_CHANGE, TRIAL_ENDING }

    void send(Type type, String title, String body);
}
