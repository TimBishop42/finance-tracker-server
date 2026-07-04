package com.bishop.FinanceTracker.controller;

import com.bishop.FinanceTracker.model.recurring.RecurringResponse;
import com.bishop.FinanceTracker.service.recurring.RecurringDetectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Subscription & Recurring Detection v2 API (feature doc §2). The engine runs
 * server-side over the full transaction history; the UI is a thin consumer.
 * Merchant classification rules and dismissals reuse the existing
 * {@code /custom-merchants} and {@code /excluded-merchants} endpoints.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/finance")
public class RecurringController {

    private final RecurringDetectionService recurringDetectionService;

    @GetMapping("/recurring")
    public ResponseEntity<RecurringResponse> getRecurring() {
        log.info("Received request to detect recurring transactions");
        return ResponseEntity.ok(recurringDetectionService.detect());
    }
}
