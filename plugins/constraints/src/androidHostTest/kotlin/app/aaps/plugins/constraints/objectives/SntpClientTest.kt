package app.aaps.plugins.constraints.objectives

import app.aaps.core.interfaces.local.LocaleDependentSetting
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.mockito.Mock
import kotlin.math.abs

class SntpClientTest : TestBase() {

    @Mock lateinit var dateUtil: DateUtil

    private val localeDependentSetting = object : LocaleDependentSetting {
        override val ntpServer: String
            get() = "test.invalid"
    }

    private class FakeSntpClient(
        aapsLogger: AAPSLogger,
        dateUtil: DateUtil,
        localeDependentSetting: LocaleDependentSetting,
        private val fakeNtpTime: Long,
        private val succeed: Boolean
    ) : JvmSntpClient(aapsLogger, dateUtil, localeDependentSetting) {

        override fun requestTime(host: String, timeout: Int): Boolean {
            ntpTime = fakeNtpTime
            // Read from the same monotonic clock the class does, which is what the real
            // requestTime records. Zero only worked while that clock was SystemClock.elapsedRealtime(),
            // which a unit test starts near zero; System.nanoTime() has an arbitrary origin, so a
            // hardcoded reference makes the delta meaningless.
            ntpTimeReference = elapsedRealtime()
            return succeed
        }
    }

    @Test
    fun ntpTimeTest() = runTest {
        // no internet
        val noNetResult = FakeSntpClient(aapsLogger, dateUtil, localeDependentSetting, fakeNtpTime = 0L, succeed = true)
            .ntpTime(isConnected = false)
        assertThat(noNetResult.networkConnected).isFalse()
        assertThat(noNetResult.success).isFalse()
        assertThat(noNetResult.time).isEqualTo(0L)

        // internet, mocked NTP response
        val now = System.currentTimeMillis()
        val okResult = FakeSntpClient(aapsLogger, dateUtil, localeDependentSetting, fakeNtpTime = now, succeed = true)
            .ntpTime(isConnected = true)
        assertThat(okResult.networkConnected).isTrue()
        assertThat(okResult.success).isTrue()
        assertThat(abs(okResult.time - System.currentTimeMillis())).isLessThan(60_000L)

        // internet, but NTP request fails
        val failResult = FakeSntpClient(aapsLogger, dateUtil, localeDependentSetting, fakeNtpTime = 0L, succeed = false)
            .ntpTime(isConnected = true)
        assertThat(failResult.networkConnected).isTrue()
        assertThat(failResult.success).isFalse()
    }
}
