package com.bishop.FinanceTracker.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafeRegexTest {

    // java.util.regex is notably resistant to the textbook (a+)+ style ReDoS examples
    // (its GroupCurly matching avoids the classic exponential blowup), so rather than
    // depend on finding a pattern that actually hangs *this* JVM's engine, these tests
    // drive the budget mechanism directly via the package-private overloads: an
    // already-expired budget forces the same abort path a genuinely pathological
    // pattern would hit, deterministically and without relying on JVM-specific timing.

    @Test
    @Timeout(5)
    void compileIfSafeRejectsWhenBudgetIsAlreadyExpired() {
        assertNull(SafeRegex.compileIfSafe("netflix", -1));
    }

    @Test
    void compileIfSafeAcceptsOrdinaryPatternWithinBudget() {
        assertNotNull(SafeRegex.compileIfSafe("netflix", 100));
    }

    @Test
    void acceptsOrdinaryPartialNamePattern() {
        assertNotNull(SafeRegex.compileIfSafe("netflix"));
    }

    @Test
    void acceptsOrdinaryRegexPattern() {
        assertNotNull(SafeRegex.compileIfSafe("origin energy|\\bagl\\b"));
    }

    @Test
    void rejectsInvalidSyntax() {
        assertNull(SafeRegex.compileIfSafe("("));
    }

    @Test
    void rejectsOversizedPattern() {
        assertNull(SafeRegex.compileIfSafe("a".repeat(201)));
    }

    @Test
    void rejectsNullAndBlank() {
        assertNull(SafeRegex.compileIfSafe(null));
        assertNull(SafeRegex.compileIfSafe("   "));
    }

    @Test
    @Timeout(5)
    void findWithinBudgetAbortsAndReturnsFalseOnceBudgetExpires() {
        Pattern pattern = Pattern.compile("netflix", Pattern.CASE_INSENSITIVE);
        assertFalse(SafeRegex.findWithinBudget(pattern, "NETFLIX.COM", -1));
    }

    @Test
    void findWithinBudgetMatchesNormalInput() {
        Pattern pattern = SafeRegex.compileIfSafe("netflix");
        assertTrue(SafeRegex.findWithinBudget(pattern, "NETFLIX.COM"));
        assertFalse(SafeRegex.findWithinBudget(pattern, "SPOTIFY"));
    }
}
