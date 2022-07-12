package com.bishop.FinanceTracker.util;

import java.util.Date;

public class DateUtil {

    public static Date getDateFromString(String input) {
        long millisTime = Long.parseLong(input);
        return new Date(millisTime);
    }
}
