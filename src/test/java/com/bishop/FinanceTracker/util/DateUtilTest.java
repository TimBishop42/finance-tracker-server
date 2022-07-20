package com.bishop.FinanceTracker.util;


import org.junit.jupiter.api.Test;

import java.time.ZoneId;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class DateUtilTest {

    @Test
    public void getLocalizedDateStringStringTest() {
        Long epochMillis = 1658294807952L;  //20 Jul 2022, 03:26pm-ish
        String expectedResult = "20-07-2022";
        String dateString = DateUtil.getLocalizedDateString(epochMillis, ZoneId.of("Australia/Sydney"));
        assertThat(dateString).isEqualTo(expectedResult);
    }
}
