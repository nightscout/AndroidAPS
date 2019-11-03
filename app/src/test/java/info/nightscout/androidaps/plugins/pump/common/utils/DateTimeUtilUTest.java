package info.nightscout.androidaps.plugins.pump.common.utils;

import android.util.Log;

import junit.framework.Assert;

import org.junit.Test;

import static org.junit.Assert.*;

public class DateTimeUtilUTest {

    @Test
    public void getATechDateDiferenceAsMinutes() {

        long dt1 = 20191001182301L;
        long dt2 = 20191001192805L;

        int aTechDateDiferenceAsMinutes = DateTimeUtil.getATechDateDiferenceAsMinutes(dt1, dt2);

        Assert.assertEquals(65, aTechDateDiferenceAsMinutes);

        Log.d("DateTimeUtilUTest", "Time difference: " + aTechDateDiferenceAsMinutes);

    }
}