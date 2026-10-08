package com.bishop.FinanceTracker.controller;

import com.bishop.FinanceTracker.model.json.MonthlyReviewResponse;
import com.bishop.FinanceTracker.service.MonthlyReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;

/** Month-in-review walkthrough data (and, later, the monthly close). */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/finance/review")
public class ReviewController {

    private final MonthlyReviewService reviewService;

    /** @param month yyyy-MM, e.g. 2026-09 */
    @GetMapping("/{month}")
    public ResponseEntity<MonthlyReviewResponse> review(@PathVariable String month) {
        return ResponseEntity.ok(reviewService.review(parseMonth(month)));
    }

    private static YearMonth parseMonth(String month) {
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Month must be yyyy-MM, got: " + month);
        }
    }
}
