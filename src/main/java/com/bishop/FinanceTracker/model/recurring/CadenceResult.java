package com.bishop.FinanceTracker.model.recurring;

/**
 * The outcome of phase-space cadence detection for the best-fitting period.
 *
 * @param cadence      the winning period
 * @param vectorStrength r in [0,1] — 0 = dates scattered, 1 = perfectly periodic
 * @param coverage     fraction of cycles (first→last) that contain ≥1 transaction
 * @param redundancy   fraction of cycles that contain >1 transaction (noise signal)
 * @param fitScore     overall closeness to the ideal (1,1,0), mapped to [0,1]
 */
public record CadenceResult(
        Cadence cadence,
        double vectorStrength,
        double coverage,
        double redundancy,
        double fitScore) {
}
