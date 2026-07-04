package com.bishop.FinanceTracker.service.recurring;

import com.bishop.FinanceTracker.model.recurring.CadenceResult;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Finds the best-fitting payment period for a series of transaction dates.
 *
 * <p>A seam like {@link MerchantNormalizer}: the v1 implementation is the
 * phase-space / circular-statistics method ({@link PhaseSpaceCadenceDetector}),
 * but a future learned detector can implement the same contract.
 */
public interface CadenceDetector {

    /**
     * @param dates transaction dates for a single merchant group (any order)
     * @return the winning cadence + fit statistics, or empty if none is periodic
     */
    Optional<CadenceResult> detect(List<LocalDate> dates);
}
