package info.nightscout.core.pump.common.utils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import info.nightscout.core.utils.DateTimeUtil;

class DateTimeUtilUTest {

    @Test
    void getATechDateDifferenceAsMinutes() {

        long dt1 = 20191001182301L;
        long dt2 = 20191001192805L;

        int aTechDateDifferenceAsMinutes = DateTimeUtil.getATechDateDifferenceAsMinutes(dt1, dt2);

        Assertions.assertEquals(65, aTechDateDifferenceAsMinutes);

        //Log.d("DateTimeUtilUTest", "Time difference: " + aTechDateDifferenceAsMinutes);

    }
}