package com.bishop.FinanceTracker.util;

import lombok.extern.slf4j.Slf4j;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Bounds regex match time so a pathological user-supplied pattern (catastrophic
 * backtracking) can't hang the calling thread. A length cap alone doesn't help here
 * — short patterns can still be pathological against the right input (UI-N8 / ReDoS)
 * — so matching is done against a {@link CharSequence} that aborts once a wall-clock
 * budget is exceeded, regardless of the pattern's shape or how it was constructed.
 */
@Slf4j
public final class SafeRegex {

    public static final int MAX_PATTERN_LENGTH = 200;
    private static final long MATCH_BUDGET_MILLIS = 100;

    /** Probe string run against a freshly-compiled pattern before it's persisted. */
    private static final String CANARY = "a".repeat(40) + "!";

    private SafeRegex() {
    }

    /**
     * Compiles a user-supplied pattern for use at write time, returning {@code null}
     * if it's invalid, oversized, or can't match a canary probe string within budget.
     */
    public static Pattern compileIfSafe(String pattern) {
        return compileIfSafe(pattern, MATCH_BUDGET_MILLIS);
    }

    static Pattern compileIfSafe(String pattern, long budgetMillis) {
        if (pattern == null || pattern.isBlank() || pattern.length() > MAX_PATTERN_LENGTH) {
            return null;
        }
        Pattern compiled;
        try {
            compiled = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
        } catch (PatternSyntaxException e) {
            log.warn("Rejecting invalid custom merchant pattern '{}': {}", pattern, e.getMessage());
            return null;
        }
        try {
            matchWithinBudget(compiled, CANARY, budgetMillis);
        } catch (RegexBudgetExceededException e) {
            log.warn("Rejecting custom merchant pattern '{}': exceeded {}ms match budget", pattern, budgetMillis);
            return null;
        }
        return compiled;
    }

    /**
     * Time-bounded match, used as defense-in-depth at classification time for
     * patterns that were already persisted before this validation existed.
     */
    public static boolean findWithinBudget(Pattern pattern, String input) {
        return findWithinBudget(pattern, input, MATCH_BUDGET_MILLIS);
    }

    static boolean findWithinBudget(Pattern pattern, String input, long budgetMillis) {
        try {
            return matchWithinBudget(pattern, input, budgetMillis);
        } catch (RegexBudgetExceededException e) {
            log.warn("Custom merchant pattern '{}' exceeded {}ms match budget; treating as no match",
                    pattern.pattern(), budgetMillis);
            return false;
        }
    }

    private static boolean matchWithinBudget(Pattern pattern, CharSequence input, long budgetMillis) {
        long deadlineNanos = System.nanoTime() + budgetMillis * 1_000_000L;
        return pattern.matcher(new DeadlineCharSequence(input, deadlineNanos)).find();
    }

    private static final class DeadlineCharSequence implements CharSequence {
        private final CharSequence inner;
        private final long deadlineNanos;

        DeadlineCharSequence(CharSequence inner, long deadlineNanos) {
            this.inner = inner;
            this.deadlineNanos = deadlineNanos;
        }

        @Override
        public int length() {
            return inner.length();
        }

        @Override
        public char charAt(int index) {
            if (System.nanoTime() > deadlineNanos) {
                throw new RegexBudgetExceededException();
            }
            return inner.charAt(index);
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            return new DeadlineCharSequence(inner.subSequence(start, end), deadlineNanos);
        }

        @Override
        public String toString() {
            return inner.toString();
        }
    }

    private static final class RegexBudgetExceededException extends RuntimeException {
        RegexBudgetExceededException() {
            super(null, null, false, false);
        }
    }
}
