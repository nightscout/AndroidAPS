package info.nightscout.plugins.constraints.objectives

import com.google.common.truth.Truth.assertThat
import info.nightscout.shared.utils.DateUtil
import info.nightscout.sharedtests.TestBase
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
                assertThat(networkConnected).isFalse()
                assertThat(success).isFalse()
                assertThat(time).isEqualTo(0L)
            }
        }, false)
        // internet
        SntpClient(aapsLogger, dateUtil).doNtpTime(object : SntpClient.Callback() {
            override fun run() {
                assertThat(success).isTrue()
                assertThat(abs(time - System.currentTimeMillis())).isLessThan(60_000L)
            }
        })
    }
}
