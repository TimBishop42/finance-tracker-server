package com.bishop.FinanceTracker.controller;

import com.bishop.FinanceTracker.model.json.SubscriptionDashboardResponse;
import com.bishop.FinanceTracker.model.json.SubscriptionRequest;
import com.bishop.FinanceTracker.model.recurring.RecurringCandidate;
import com.bishop.FinanceTracker.service.SubscriptionService;
import com.bishop.FinanceTracker.service.TransactionService;
import com.bishop.FinanceTracker.service.UserSettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Unified subscriptions/bills API (feature doc §2A.4/§2A.5). The Bill Calendar
 * (kind=bill) and the Recurring/Subscription views all read and edit this single
 * datasource. Replaces the old {@code /manual-bills} endpoints.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/finance/subscriptions")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final TransactionService transactionService;
    private final UserSettingsService userSettingsService;

    @GetMapping
    public ResponseEntity<List<RecurringCandidate>> getAll() {
        return ResponseEntity.ok(subscriptionService.getAll());
    }

    @GetMapping("/dashboard")
    public ResponseEntity<SubscriptionDashboardResponse> dashboard() {
        return ResponseEntity.ok(subscriptionService.dashboard());
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody SubscriptionRequest request) {
        RecurringCandidate created;
        try {
            created = subscriptionService.create(request);
        } catch (IllegalArgumentException e) {
            log.warn("Rejected subscription create: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        }
        // Backfill against existing history so already-paid periods show immediately.
        try {
            return ResponseEntity.ok(
                    subscriptionService.backfillFromHistory(created.getSubscriptionId(), transactionService.getAll()));
        } catch (Exception e) {
            log.error("Backfill failed for subscription {}", created.getSubscriptionId(), e);
            return ResponseEntity.ok(created);
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody SubscriptionRequest request) {
        try {
            return ResponseEntity.ok(subscriptionService.update(id, request));
        } catch (IllegalArgumentException e) {
            log.warn("Rejected subscription update {}: {}", id, e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        try {
            subscriptionService.delete(id);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /** Mark a period paid/unpaid manually (calendar override). */
    @PostMapping("/{id}/paid")
    public ResponseEntity<?> setPaid(@PathVariable Long id,
                                     @RequestParam String date,
                                     @RequestParam(defaultValue = "true") boolean paid) {
        try {
            return ResponseEntity.ok(subscriptionService.setPaid(id, date, paid));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /** Replace the set of transactions manually linked to a commitment (§2A.5). */
    @PutMapping("/{id}/links")
    public ResponseEntity<?> setLinks(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Object ids = body.get("transactionIds");
        Set<Long> desired = new HashSet<>();
        if (ids instanceof List) {
            for (Object o : (List<?>) ids) {
                try {
                    desired.add(Long.valueOf(String.valueOf(o)));
                } catch (NumberFormatException ignored) {
                    // skip malformed id
                }
            }
        }
        try {
            return ResponseEntity.ok(subscriptionService.setLinkedTransactions(id, desired, transactionService.getAll()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /** Promote a detected recurring candidate into the unified table (§2A.4.2). */
    @PostMapping("/confirm")
    public ResponseEntity<?> confirm(@RequestBody RecurringCandidate candidate) {
        RecurringCandidate created;
        try {
            created = subscriptionService.confirmFromDetection(candidate);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
        try {
            return ResponseEntity.ok(
                    subscriptionService.backfillFromHistory(created.getSubscriptionId(), transactionService.getAll()));
        } catch (Exception e) {
            log.error("Backfill failed for confirmed subscription {}", created.getSubscriptionId(), e);
            return ResponseEntity.ok(created);
        }
    }

    @GetMapping("/budget")
    public ResponseEntity<BigDecimal> getBudget() {
        return ResponseEntity.ok(userSettingsService.getSubscriptionBudget());
    }

    @PutMapping("/budget")
    public ResponseEntity<?> setBudget(@RequestBody Map<String, Object> body) {
        Object value = body.get("budget");
        if (value == null) return ResponseEntity.badRequest().body("budget is required");
        try {
            userSettingsService.setSubscriptionBudget(new BigDecimal(String.valueOf(value)));
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
