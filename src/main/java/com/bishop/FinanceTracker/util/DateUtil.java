package com.bishop.FinanceTracker.util;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
}
