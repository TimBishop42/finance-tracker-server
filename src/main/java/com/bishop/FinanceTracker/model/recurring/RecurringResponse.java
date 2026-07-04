package com.bishop.FinanceTracker.model.recurring;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/** Top-level payload for {@code GET /api/finance/recurring}. */
@Data
@Builder
public class RecurringResponse {

    private List<RecurringCandidate> candidates;
    /** Epoch millis the detection ran — lets the UI show "as of" and cache-bust. */
    private long generatedAt;
    /** Detector implementation that produced these (e.g. "phase-space"); eases the future ML pivot. */
    private String engine;
}
