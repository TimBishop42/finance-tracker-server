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
        return withBackfill(subscriptionService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody SubscriptionRequest request) {
        return ResponseEntity.ok(subscriptionService.update(id, request));
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
        return ResponseEntity.ok(subscriptionService.setPaid(id, date, paid));
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
        return ResponseEntity.ok(subscriptionService.setLinkedTransactions(id, desired, transactionService.getAll()));
    }

    /** Promote a detected recurring candidate into the unified table (§2A.4.2). */
    @PostMapping("/confirm")
    public ResponseEntity<?> confirm(@RequestBody RecurringCandidate candidate) {
        return withBackfill(subscriptionService.confirmFromDetection(candidate));
    }

    @GetMapping("/budget")
    public ResponseEntity<BigDecimal> getBudget() {
        return ResponseEntity.ok(userSettingsService.getSubscriptionBudget());
    }

    @PutMapping("/budget")
    public ResponseEntity<?> setBudget(@RequestBody Map<String, Object> body) {
        Object value = body.get("budget");
        if (value == null) return ResponseEntity.badRequest().body("budget is required");
        userSettingsService.setSubscriptionBudget(new BigDecimal(String.valueOf(value)));
        return ResponseEntity.ok().build();
    }

    /**
     * Backfill a new commitment against existing history so already-paid periods
     * show immediately. Best-effort: on failure the commitment is still returned.
     */
    private ResponseEntity<RecurringCandidate> withBackfill(RecurringCandidate created) {
        try {
            return ResponseEntity.ok(
                    subscriptionService.backfillFromHistory(created.getSubscriptionId(), transactionService.getAll()));
        } catch (Exception e) {
            log.error("Backfill failed for subscription {}", created.getSubscriptionId(), e);
            return ResponseEntity.ok(created);
        }
    }
}
