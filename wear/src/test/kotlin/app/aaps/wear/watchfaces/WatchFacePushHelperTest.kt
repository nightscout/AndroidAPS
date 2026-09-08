package app.aaps.wear.watchfaces

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.wear.AAPSLoggerTest
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Covers the safety contract of [WatchFacePushHelper] below Wear OS 6 (API 36): every CWF user on
 * an older watch runs [WatchFacePushHelper.syncOnStartup] on each app start, so on unsupported
 * devices the helper must be a complete no-op — never binding the Watch Face Push service, never
 * touching state, and above all never throwing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
internal class WatchFacePushHelperTest {

    private val sp: SP = mock()
    private val aapsLogger: AAPSLogger = AAPSLoggerTest()

    private fun sut(): WatchFacePushHelper =
        WatchFacePushHelper(RuntimeEnvironment.getApplication(), sp, aapsLogger)

    @Test
    fun `below API 36 the feature reports unsupported`() {
        assertThat(sut().isSupported()).isFalse()
    }

    @Test
    fun `below API 36 the install state is not installed and SP is untouched`() = runTest {
        assertThat(sut().isFaceInstalled()).isFalse()
        verifyNoInteractions(sp)
    }

    @Test
    fun `below API 36 install does nothing and reports failure`() = runTest {
        assertThat(sut().installOrUpdate(activate = true)).isFalse()
        verifyNoInteractions(sp)
    }

    @Test
    fun `below API 36 the face is never reported active`() = runTest {
        assertThat(sut().isFaceActive()).isFalse()
    }

    @Test
    fun `below API 36 the startup sync completes quietly`() = runTest {
        sut().syncOnStartup()
        verifyNoInteractions(sp)
    }
}
