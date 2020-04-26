package info.nightscout.androidaps.utils;

import org.junit.Assert;
import org.junit.Test;

public class SntpClientTest {

    @Test
    public void ntpTimeTest() {
        // no internet
        SntpClient.ntpTime(new SntpClient.Callback() {
            @Override
            public void run() {
                Assert.assertFalse(networkConnected);
                Assert.assertFalse(success);
                Assert.assertEquals(0L, time);
            }
        }, false);
        // internet
        SntpClient.doNtpTime(new SntpClient.Callback() {
            @Override
            public void run() {
                Assert.assertTrue(success);
                Assert.assertTrue(Math.abs(time - DateUtil.now()) < 60000);
            }
        });
    }
}