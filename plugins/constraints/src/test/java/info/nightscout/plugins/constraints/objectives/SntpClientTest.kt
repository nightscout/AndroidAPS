package info.nightscout.plugins.constraints.objectives

import info.nightscout.shared.utils.DateUtil
import info.nightscout.sharedtests.TestBase
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.Mock
import kotlin.math.abs

class SntpClientTest : TestBase() {

    @Mock lateinit var dateUtil: DateUtil

    @Test
    fun ntpTimeTest() {
        // no internet
        SntpClient(aapsLogger, dateUtil).ntpTime(object : SntpClient.Callback() {
            override fun run() {
                Assertions.assertFalse(networkConnected)
                Assertions.assertFalse(success)
                Assertions.assertEquals(0L, time)
            }
        }, false)
        // internet
        SntpClient(aapsLogger, dateUtil).doNtpTime(object : SntpClient.Callback() {
            override fun run() {
                Assertions.assertTrue(success)
                Assertions.assertTrue(abs(time - System.currentTimeMillis()) < 60000)
            }
        })
    }
}