package com.bishop.FinanceTracker.service.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * No-op {@link Notifier} that just logs (feature doc §2A.1). This is the placeholder
 * until an email channel is wired up (pending a domain + mail server). It lets the
 * whole notification engine — triggers, de-dupe, scheduling — run and be verified
 * without any outbound infrastructure.
 */
@Slf4j
@Component
public class LoggingNotifier implements Notifier {

    @Override
    public void send(Type type, String title, String body) {
        log.info("[NOTIFY:{}] {} — {}", type, title, body);
    }
}
