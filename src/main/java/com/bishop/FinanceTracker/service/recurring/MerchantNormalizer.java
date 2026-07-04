package com.bishop.FinanceTracker.service.recurring;

import com.bishop.FinanceTracker.model.recurring.NormalizedMerchant;

/**
 * Reduces a raw bank/card business name to a stable grouping key.
 *
 * <p>This is a deliberate seam: the v1 implementation is heuristic/regex-based
 * ({@link HeuristicMerchantNormalizer}), but the feature doc (§2.1) calls for
 * reusing the ML service's normalised merchant when available. An ML-backed
 * implementation can be dropped in as a {@code @Primary} bean without touching
 * the detection engine.
 */
public interface MerchantNormalizer {

    NormalizedMerchant normalize(String rawBusinessName);
}
