package app.aaps.plugins.constraints.objectives

import app.aaps.core.interfaces.local.LocaleDependentSetting
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.mockito.Mock
import kotlin.math.abs

class SntpClientTest : TestBase() {

    @Mock lateinit var dateUtil: DateUtil

    @Test
    fun ntpTimeTest() = runTest {
        val localeDependentSetting = object : LocaleDependentSetting {
            override val ntpServer: String
                get() = "time.google.com"
        }

        // no internet
        val noNetResult = SntpClient(aapsLogger, dateUtil, localeDependentSetting).ntpTime(isConnected = false)
        assertThat(noNetResult.networkConnected).isFalse()
        assertThat(noNetResult.success).isFalse()
        assertThat(noNetResult.time).isEqualTo(0L)

        // internet
        val result = SntpClient(aapsLogger, dateUtil, localeDependentSetting).ntpTime(isConnected = true)
        assertThat(result.success).isTrue()
        assertThat(abs(result.time - System.currentTimeMillis())).isLessThan(60_000L)
    }
}
