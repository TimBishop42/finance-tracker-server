package com.bishop.FinanceTracker.util;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.text.SimpleDateFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.Calendar;
import java.util.Date;

import static java.util.Objects.isNull;

@Slf4j
public class DateUtil {

    private static final String DISPLAY_DATE_TIME = "dd-MM-yyyy";

    public static Date getDateFromMillisString(String input) {
        long millisTime = Long.parseLong(input);
        return new Date(millisTime);
    }

    @SneakyThrows
    public static Date getDateFromDateString(String dateString) {
        SimpleDateFormat sdf = new SimpleDateFormat(DISPLAY_DATE_TIME);
        return sdf.parse(dateString);
    }

    public static String getLocalizedDateString(Long epochTime, ZoneId zoneId) {
        if(isNull(epochTime) || isNull(zoneId)) {
            log.error("Unable to parse date with null input or null zoneId");
            return null;
        }
        LocalDateTime ldt = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochTime), zoneId);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DISPLAY_DATE_TIME);
        return formatter.format(ldt);
    }

    public static Month getMonthFromStringDate(String date) {
        Calendar tDate = Calendar.getInstance();
        tDate.setTime(getDateFromDateString(date));
        return Month.of(tDate.get(Calendar.MONTH) + 1);
    }

    public static int getYearFromStringDate(String transactionDate) {
        Calendar tDate = Calendar.getInstance();
        tDate.setTime(getDateFromDateString(transactionDate));
        return tDate.get(Calendar.YEAR);
    }

    public static Long getEpochMilliOfCurrentYear() {

        // Create a LocalDateTime representing the start of the current year
        LocalDateTime startOfYear = LocalDateTime.of
                (LocalDateTime.now().getYear(), 1, 1, 0, 0, 0);

        // Convert the LocalDateTime to ZonedDateTime at UTC
        ZonedDateTime startOfYearUTC = startOfYear.atZone(ZoneOffset.UTC);

        // Get the epoch milliseconds
        return startOfYearUTC.toInstant().toEpochMilli();
    }

    public static long getRecentMonthStartEpochMilli() {
        LocalDateTime now = LocalDateTime.now();

        // Get the first day of the current month at midnight
        LocalDateTime firstDayOfCurrentMonth = now.with(TemporalAdjusters.firstDayOfMonth()).truncatedTo(ChronoUnit.DAYS);

        // If the current date is not in the first month, use the first day of the current month
        LocalDateTime firstDayOfLastMonth;
        if (now.isAfter(firstDayOfCurrentMonth)) {
            firstDayOfLastMonth = firstDayOfCurrentMonth.minusMonths(1);
        } else {
            firstDayOfLastMonth = firstDayOfCurrentMonth;
        }

        // Convert to ZonedDateTime using the system default time zone
        ZonedDateTime zonedDateTime = firstDayOfLastMonth.atZone(ZoneId.systemDefault());

        // Convert to epoch milliseconds
        return zonedDateTime.toInstant().toEpochMilli();
    }
}
