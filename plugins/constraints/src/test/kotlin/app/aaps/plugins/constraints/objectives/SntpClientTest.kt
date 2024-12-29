package app.aaps.plugins.constraints.objectives

import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
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
