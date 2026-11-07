package app.aaps.wear.watchfaces

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventWearToMobile
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.keys.PushedWatchfaceId
import app.aaps.wear.AAPSLoggerTest
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Covers the safety contract of [WatchFacePushHelper] below Wear OS 6 (API 36): every CWF user on
 * an older watch runs [WatchFacePushHelper.syncOnStartup] on each app start, so on unsupported
 * devices the helper must be a complete no-op — never binding the Watch Face Push service, never
 * touching state, and above all never throwing.
 *
 * The one exception is the face selection: the phone's choice is stored on every watch, so a watch
 * that gets Wear OS 6 later installs the chosen face on its next start.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
internal class WatchFacePushHelperTest {

    private val sp: SP = mock()
    private val rxBus: RxBus = mock()
    private val aapsLogger: AAPSLogger = AAPSLoggerTest()

    private fun sut(): WatchFacePushHelper =
        WatchFacePushHelper(RuntimeEnvironment.getApplication(), sp, rxBus, aapsLogger)

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

    @Test
    fun `below API 36 the watch still tells the phone that Watch Face Push is missing`() = runTest {
        // Without this the phone would show the face choice to a watch that cannot act on it
        sut().syncOnStartup()
        verify(rxBus).send(EventWearToMobile(EventData.WatchFacePushStatus(supported = false)))
    }

    /** An SP that hands back the default it is asked for, like the real one */
    private fun spWithDefaults(): SP = mock<SP>().also {
        whenever(it.getString(any<String>(), any())).thenAnswer { call -> call.arguments[1] }
    }

    private fun sut(sp: SP): WatchFacePushHelper =
        WatchFacePushHelper(RuntimeEnvironment.getApplication(), sp, rxBus, aapsLogger)

    @Test
    fun `the selected face is the Custom watchface until the phone chooses`() {
        assertThat(sut(spWithDefaults()).selectedFace).isEqualTo(PushedFace.CWF)
    }

    @Test
    fun `an unknown face id falls back to the Custom watchface`() {
        assertThat(PushedFace.fromId("something-newer")).isEqualTo(PushedFace.CWF)
        assertThat(PushedFace.fromId(null)).isEqualTo(PushedFace.CWF)
    }

    @Test
    fun `selecting the other face is stored below API 36 too and reports the change`() {
        val sp = spWithDefaults()
        assertThat(sut(sp).selectFace(PushedWatchfaceId.WFS)).isTrue()
        verify(sp).putString(any<String>(), eq(PushedWatchfaceId.WFS))
    }

    @Test
    fun `selecting the face already selected changes nothing`() {
        val sp = spWithDefaults()
        assertThat(sut(sp).selectFace(PushedWatchfaceId.CWF)).isFalse()
        verify(sp, never()).putString(any<String>(), any())
    }
}
