package info.nightscout.androidaps.pump.common.utils;

import org.junit.Assert;
import org.junit.jupiter.api.Test;

import info.nightscout.core.utils.DateTimeUtil;

public class DateTimeUtilUTest {

    @Test
    public void getATechDateDifferenceAsMinutes() {

        long dt1 = 20191001182301L;
        long dt2 = 20191001192805L;

        int aTechDateDifferenceAsMinutes = DateTimeUtil.getATechDateDifferenceAsMinutes(dt1, dt2);

        Assert.assertEquals(65, aTechDateDifferenceAsMinutes);

        //Log.d("DateTimeUtilUTest", "Time difference: " + aTechDateDifferenceAsMinutes);

    }
}