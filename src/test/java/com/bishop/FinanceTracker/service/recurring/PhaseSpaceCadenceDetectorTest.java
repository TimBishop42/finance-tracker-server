package com.bishop.FinanceTracker.service.recurring;

import com.bishop.FinanceTracker.model.recurring.Cadence;
import com.bishop.FinanceTracker.model.recurring.CadenceResult;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhaseSpaceCadenceDetectorTest {

    private final PhaseSpaceCadenceDetector detector = new PhaseSpaceCadenceDetector();

    private List<LocalDate> series(LocalDate start, int count, java.util.function.IntFunction<LocalDate> step) {
        List<LocalDate> dates = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            dates.add(step.apply(i));
        }
        return dates;
    }

    @Test
    void detectsMonthlyCadence() {
        LocalDate anchor = LocalDate.of(2025, 1, 15);
        List<LocalDate> dates = series(anchor, 12, i -> anchor.plusMonths(i));
        CadenceResult r = detector.detect(dates).orElseThrow();
        assertEquals(Cadence.MONTHLY, r.cadence());
        assertTrue(r.vectorStrength() > 0.9, "monthly series should be tightly periodic");
    }

    @Test
    void detectsWeeklyCadence() {
        LocalDate anchor = LocalDate.of(2025, 1, 6);
        List<LocalDate> dates = series(anchor, 12, i -> anchor.plusWeeks(i));
        assertEquals(Cadence.WEEKLY, detector.detect(dates).orElseThrow().cadence());
    }

    @Test
    void detectsAnnualCadence() {
        LocalDate anchor = LocalDate.of(2021, 3, 20);
        List<LocalDate> dates = series(anchor, 4, i -> anchor.plusYears(i));
        assertEquals(Cadence.ANNUAL, detector.detect(dates).orElseThrow().cadence());
    }

    @Test
    void toleratesDayOfMonthDrift() {
        // Monthly bill whose day wanders +/- a few days (weekend/holiday shifts).
        LocalDate anchor = LocalDate.of(2025, 1, 10);
        int[] drift = {0, 2, -1, 1, 3, -2, 0, 1, -1, 2, 0, 1};
        List<LocalDate> dates = series(anchor, 12, i -> anchor.plusMonths(i).plusDays(drift[i]));
        assertEquals(Cadence.MONTHLY, detector.detect(dates).orElseThrow().cadence());
    }

    @Test
    void survivesOneMissedPayment() {
        LocalDate anchor = LocalDate.of(2025, 1, 15);
        List<LocalDate> dates = new ArrayList<>(series(anchor, 12, i -> anchor.plusMonths(i)));
        dates.remove(5); // skip one month
        assertEquals(Cadence.MONTHLY, detector.detect(dates).orElseThrow().cadence());
    }

    @Test
    void rejectsScatteredDates() {
        List<LocalDate> dates = List.of(
                LocalDate.of(2025, 1, 3),
                LocalDate.of(2025, 1, 21),
                LocalDate.of(2025, 3, 14),
                LocalDate.of(2025, 3, 30),
                LocalDate.of(2025, 7, 2),
                LocalDate.of(2025, 11, 8));
        assertTrue(detector.detect(dates).isEmpty(), "irregular spend should not be periodic");
    }

    @Test
    void requiresMinimumOccurrences() {
        List<LocalDate> two = List.of(LocalDate.of(2025, 1, 15), LocalDate.of(2025, 2, 15));
        assertEquals(Optional.empty(), detector.detect(two));
    }
}
