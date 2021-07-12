package info.nightscout.androidaps.utils

import info.nightscout.androidaps.TestBase
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner


@RunWith(PowerMockRunner::class)
@PrepareForTest(DateUtil::class)
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
                Assert.assertTrue(Math.abs(time - System.currentTimeMillis()) < 60000)
            }
        })
    }
}