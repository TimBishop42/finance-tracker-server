package com.bishop.FinanceTracker.controller;

import com.bishop.FinanceTracker.service.NeutralClassificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Manage "neutral" merchant rules (internal transfers / credit-card payments)
 * and classify a batch of business names on CSV import. Mirrors the
 * excluded/custom-merchant endpoints on {@code TrackerController}.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/finance")
public class NeutralMerchantController {

    private final NeutralClassificationService neutralService;

    @GetMapping("/neutral-merchants")
    public ResponseEntity<List<String>> list() {
        return ResponseEntity.ok(neutralService.listKeys());
    }

    /** Add a rule from a raw business name (server normalises it to a key). */
    @PostMapping("/neutral-merchants")
    public ResponseEntity<Map<String, String>> add(@RequestBody Map<String, String> body) {
        String name = body.getOrDefault("businessName", body.get("merchantKey"));
        if (name == null || name.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        String key = neutralService.addRule(name);
        if (key == null || key.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(Map.of("merchantKey", key));
    }

    @DeleteMapping("/neutral-merchants/{key}")
    public ResponseEntity<Void> remove(@PathVariable String key) {
        neutralService.removeRule(key);
        return ResponseEntity.ok().build();
    }

    /** Given business names from an import, return those that should be NEUTRAL. */
    @PostMapping("/neutral-merchants/match")
    public ResponseEntity<List<String>> match(@RequestBody Map<String, List<String>> body) {
        List<String> names = body.getOrDefault("names", List.of());
        return ResponseEntity.ok(List.copyOf(neutralService.neutralNames(names)));
    }
}
