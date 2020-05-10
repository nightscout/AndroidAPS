package info.nightscout.androidaps.utils

import info.nightscout.androidaps.TestBase
import org.junit.Assert
import org.junit.Test
import org.mockito.Mock

class SntpClientTest : TestBase() {
    @Mock lateinit var dateUtil: DateUtil

    @Test
    fun ntpTimeTest() {
        // no internet
        SntpClient(aapsLogger, dateUtil).ntpTime(object : SntpClient.Callback() {
            override fun run() {
                Assert.assertFalse(networkConnected)
                Assert.assertFalse(success)
                Assert.assertEquals(0L, time)
            }
        }, false)
        // internet
        SntpClient(aapsLogger, dateUtil).doNtpTime(object : SntpClient.Callback() {
            override fun run() {
                Assert.assertTrue(success)
                Assert.assertTrue(Math.abs(time - DateUtil.now()) < 60000)
            }
        })
    }
}