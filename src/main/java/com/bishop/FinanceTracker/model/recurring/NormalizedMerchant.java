package com.bishop.FinanceTracker.model.recurring;

/**
 * A raw business name reduced to a stable grouping key. {@code key} is the
 * normalised, upper-cased token sequence used to group transactions; {@code
 * displayName} is a human-friendly title-cased version for the UI.
 */
public record NormalizedMerchant(String key, String displayName) {
}
