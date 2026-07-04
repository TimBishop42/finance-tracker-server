package com.bishop.FinanceTracker.model.recurring;

/**
 * Prior classification of a merchant from the knowledge base or a user rule.
 * Acts as a Bayesian-ish prior that <em>augments</em> the statistical score — it
 * never bypasses cadence detection (fixes the v1 KB-bypass weakness, doc §2.1).
 *
 * @param type   subscription | bill | noise | unknown
 * @param source custom (user rule) | seed (built-in KB) | statistical (no prior)
 */
public record MerchantClass(Type type, String source) {

    public enum Type {
        SUBSCRIPTION,
        BILL,
        NOISE,
        UNKNOWN
    }

    public static MerchantClass unknown() {
        return new MerchantClass(Type.UNKNOWN, "statistical");
    }

    public boolean isKnown() {
        return type == Type.SUBSCRIPTION || type == Type.BILL;
    }
}
