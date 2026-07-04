package com.bishop.FinanceTracker.model.recurring;

/**
 * Candidate payment cadences for recurring detection. Period lengths are average
 * calendar days (so MONTHLY = 365.2425 / 12 etc.) which the phase-space detector
 * uses to fold transaction dates onto a cycle. See feature doc §2.1.
 */
public enum Cadence {
    WEEKLY(7.0, "weekly"),
    FORTNIGHTLY(14.0, "fortnightly"),
    MONTHLY(30.436875, "monthly"),
    QUARTERLY(91.310625, "quarterly"),
    SEMI_ANNUAL(182.62125, "semi-annual"),
    ANNUAL(365.2425, "annual");

    private final double periodDays;
    private final String label;

    Cadence(double periodDays, String label) {
        this.periodDays = periodDays;
        this.label = label;
    }

    public double periodDays() {
        return periodDays;
    }

    public String label() {
        return label;
    }

    /** Rough count of these periods in a month — used for a monthly-equivalent cost. */
    public double perMonth() {
        return 30.436875 / periodDays;
    }
}
