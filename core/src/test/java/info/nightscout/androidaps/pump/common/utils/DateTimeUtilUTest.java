package info.nightscout.androidaps.pump.common.utils;

import android.util.Log;

import org.junit.Assert;
import org.junit.Test;

import info.nightscout.androidaps.plugins.pump.common.utils.DateTimeUtil;

public class DateTimeUtilUTest {

    @Test
    public void getATechDateDifferenceAsMinutes() {

        long dt1 = 20191001182301L;
        long dt2 = 20191001192805L;

        int aTechDateDifferenceAsMinutes = DateTimeUtil.getATechDateDiferenceAsMinutes(dt1, dt2);

        Assert.assertEquals(65, aTechDateDifferenceAsMinutes);

        Log.d("DateTimeUtilUTest", "Time difference: " + aTechDateDifferenceAsMinutes);

    }
}