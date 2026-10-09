package com.bishop.FinanceTracker.util;

import lombok.extern.slf4j.Slf4j;

import java.time.*;
import java.time.format.DateTimeFormatter;

import static java.util.Objects.isNull;

@Slf4j
public class DateUtil {

    /** {@link #APP_ZONE} as an ID, for annotations such as {@code @Scheduled(zone = ...)} that need a constant. */
    public static final String APP_ZONE_ID = "Australia/Sydney";
    /** The household's timezone: transaction calendar dates and "today" are both in it. */
    public static final ZoneId APP_ZONE = ZoneId.of(APP_ZONE_ID);

    private static final String DISPLAY_DATE_TIME = "dd-MM-yyyy";
    // Lenient on zero-padding; thread-safe.
    private static final DateTimeFormatter TRANSACTION_DATE = DateTimeFormatter.ofPattern("d-M-yyyy");

    /**
     * A transaction's calendar date (its dd-MM-yyyy string, written in APP_ZONE) —
     * the source of truth for which day/month a transaction belongs to. Bucketing
     * by transactionDateTime instead shifts dates: the UI sends local midnight,
     * which is the previous day in UTC.
     */
    public static LocalDate parseTransactionDate(String date) {
        return LocalDate.parse(date, TRANSACTION_DATE);
    }

    /** As {@link #parseTransactionDate}, but null (and logged) for a malformed value. */
    public static LocalDate tryParseTransactionDate(String date) {
        try {
            return parseTransactionDate(date);
        } catch (RuntimeException e) {
            log.warn("Unparseable transaction date '{}'", date);
            return null;
        }
    }

    public static String getLocalizedDateString(Long epochTime, ZoneId zoneId) {
        if (isNull(epochTime) || isNull(zoneId)) {
            log.error("Unable to parse date with null input or null zoneId");
            return null;
        }
        LocalDateTime ldt = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochTime), zoneId);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DISPLAY_DATE_TIME);
        return formatter.format(ldt);
    }

    public static long getRecentMonthStartEpochMilli() {
        return getNMonthsAgoStartEpochMilli(0);
    }

    /** Epoch of APP_ZONE midnight on the 1st, {@code months} months ago — matches how the UI stamps dates. */
    public static long getNMonthsAgoStartEpochMilli(int months) {
        LocalDate cutoff = LocalDate.now(APP_ZONE).minusMonths(months).withDayOfMonth(1);
        return cutoff.atStartOfDay(APP_ZONE).toInstant().toEpochMilli();
    }
}
