package app.aaps.pump.carelevo.presentation.viewmodel

import android.os.Handler
import android.os.Looper
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.ui.R as CoreUiR
import app.aaps.pump.carelevo.R
import app.aaps.pump.carelevo.common.CarelevoAlarmActionHandler
import app.aaps.pump.carelevo.domain.model.alarm.CarelevoAlarmInfo
import app.aaps.pump.carelevo.domain.type.AlarmCause
import app.aaps.pump.carelevo.presentation.model.AlarmEvent
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

/**
 * Unit tests for [CarelevoAlarmViewModel].
 *
 * The ViewModel is a thin UI shell that owns ONLY the alarm-sound lifecycle and forwards clear
 * requests to [CarelevoAlarmActionHandler], so everything asserted here is either a delegation to
 * the mocked handler, a sound side effect on the mocked [UiInteraction], or a scheduling call on the
 * private re-arm [Handler].
 *
 * ## Why plain JVM (JUnit5) and not Robolectric
 * The VM never uses `viewModelScope`; its only Android surface is the dedicated
 * `HandlerThread`/[Handler] it builds in its initializer for the "mute 5 min" re-arm. That
 * initializer runs fine on the JVM because the module sets `unitTests.isReturnDefaultValues = true`
 * — the mockable android.jar rewrites `HandlerThread`/[Handler] bodies to return defaults, so
 * construction is a no-op rather than a `Stub!` throw.
 *
 * Running on the JVM is deliberate and load-bearing for coverage: classes loaded through
 * Robolectric's instrumenting sandbox classloader are NOT recorded by the JaCoCo agent in this
 * build, so a Robolectric suite reports 0% no matter how much it exercises (the same VM previously
 * had a green Robolectric suite and still measured 0/37 lines).
 *
 * ## How the re-arm Handler is driven
 * `handler.looper.quitSafely()` in `onCleared` would NPE against the defaulted stub (`getLooper()`
 * returns null), and a stubbed `postDelayed` never runs its [Runnable]. So after construction the
 * private `handler` field is swapped for a Mockito mock whose looper is also mocked. That makes the
 * scheduling observable (`postDelayed` args are captured and the [Runnable] invoked directly, which
 * covers the re-arm lambda body) and lets `onCleared` be verified precisely — strictly more of the
 * VM's own logic than waiting on a real looper would exercise.
 */
class CarelevoAlarmViewModelTest {

    private lateinit var aapsLogger: AAPSLogger
    private lateinit var uiInteraction: UiInteraction
    private lateinit var rh: ResourceHelper
    private lateinit var alarmActionHandler: CarelevoAlarmActionHandler

    // Stand-ins for the dedicated re-arm thread the VM builds in its initializer.
    private lateinit var handler: Handler
    private lateinit var looper: Looper

    // Backing flows the mocked handler hands out; the shell must re-expose these very instances.
    private val alarmQueueFlow: MutableStateFlow<List<CarelevoAlarmInfo>> = MutableStateFlow(emptyList())
    private val emptyEventFlow: MutableSharedFlow<Unit> = MutableSharedFlow()
    private val uiRequestsFlow: MutableSharedFlow<AlarmEvent> = MutableSharedFlow()

    private lateinit var sut: CarelevoAlarmViewModel

    /** The delay the VM posts the "mute 5 min" re-arm with — `T.mins(5).msecs()`, spelled out here. */
    private val reArmDelayMs = 300_000L

    private fun alarm(
        cause: AlarmCause = AlarmCause.ALARM_NOTICE_LGS_START,
        id: String = "alarm-1"
    ): CarelevoAlarmInfo =
        CarelevoAlarmInfo(
            alarmId = id,
            alarmType = cause.alarmType,
            cause = cause,
            value = null,
            createdAt = "2026-07-16T10:00:00",
            updatedAt = "2026-07-16T10:00:00",
            isAcknowledged = false
        )

    @BeforeEach
    fun setUp() {
        aapsLogger = mock()
        uiInteraction = mock()
        rh = mock()
        alarmActionHandler = mock()

        // Read once at construction time (they become `val`s on the shell).
        whenever(alarmActionHandler.alarmQueue).thenReturn(alarmQueueFlow)
        whenever(alarmActionHandler.alarmQueueEmptyEvent).thenReturn(emptyEventFlow)
        whenever(alarmActionHandler.uiRequests).thenReturn(uiRequestsFlow)

        sut = CarelevoAlarmViewModel(aapsLogger, uiInteraction, rh, alarmActionHandler)

        // Build the looper mock BEFORE the whenever() that hands it out (avoids UnfinishedStubbing).
        looper = mock()
        handler = mock()
        whenever(handler.looper).thenReturn(looper)
        replaceHandler(sut, handler)
    }

    // ---- reflection helpers (private members the shell owns) -----------------------------------

    /** Swap the VM's real (defaulted-stub) re-arm Handler for an observable mock. */
    private fun replaceHandler(vm: CarelevoAlarmViewModel, replacement: Handler) {
        val field = CarelevoAlarmViewModel::class.java.getDeclaredField("handler")
        field.isAccessible = true
        field.set(vm, replacement)
    }

    /** `onCleared` is protected on ViewModel and the VM is final, so drive it reflectively. */
    private fun invokeOnCleared(vm: CarelevoAlarmViewModel) {
        val method = CarelevoAlarmViewModel::class.java.getDeclaredMethod("onCleared")
        method.isAccessible = true
        method.invoke(vm)
    }

    /** The re-arm [Runnable] the VM posted for the 5-minute mute, so it can be fired on demand. */
    private fun capturePostedReArm(): Runnable {
        val captor = argumentCaptor<Runnable>()
        verify(handler).postDelayed(captor.capture(), eq(reArmDelayMs))
        return captor.firstValue
    }

    // ---- delegation / exposed state -----------------------------------------------------------

    @Test
    fun `re-exposes the handler queue empty-event and ui-request flows unchanged`() {
        assertThat(sut.alarmQueue).isSameInstanceAs(alarmQueueFlow)
        assertThat(sut.alarmQueueEmptyEvent).isSameInstanceAs(emptyEventFlow)
        assertThat(sut.event).isSameInstanceAs(uiRequestsFlow)
    }

    @Test
    fun `alarmQueue reflects values pushed by the action handler`() {
        val queued = listOf(alarm())

        alarmQueueFlow.value = queued

        assertThat(sut.alarmQueue.value).isEqualTo(queued)
    }

    @Test
    fun `alarmInfo defaults to null`() {
        assertThat(sut.alarmInfo).isNull()
    }

    @Test
    fun `alarmInfo is settable`() {
        val info = alarm()

        sut.alarmInfo = info

        assertThat(sut.alarmInfo).isSameInstanceAs(info)
    }

    @Test
    fun `loadActiveAlarms delegates to the action handler`() {
        sut.loadActiveAlarms()

        verify(alarmActionHandler).loadActiveAlarms()
    }

    // ---- triggerEvent: ClearAlarm -------------------------------------------------------------

    @Test
    fun `triggerEvent ClearAlarm stops the alarm stores the info and forwards to the handler`() {
        val info = alarm()
        val event = AlarmEvent.ClearAlarm(info)

        sut.triggerEvent(event)

        verify(uiInteraction).stopAlarm("Confirm Click")
        assertThat(sut.alarmInfo).isSameInstanceAs(info)
        verify(alarmActionHandler).triggerEvent(event)
    }

    @Test
    fun `triggerEvent ClearAlarm neither sounds the alarm nor schedules a re-arm`() {
        sut.triggerEvent(AlarmEvent.ClearAlarm(alarm()))

        verify(uiInteraction, never()).runAlarm(any<String>(), any<String>(), any<Int>())
        verify(handler, never()).postDelayed(any<Runnable>(), any<Long>())
    }

    @Test
    fun `triggerEvent ClearAlarm overwrites a previously stored alarmInfo`() {
        sut.alarmInfo = alarm(id = "old")
        val current = alarm(id = "new")

        sut.triggerEvent(AlarmEvent.ClearAlarm(current))

        assertThat(sut.alarmInfo).isSameInstanceAs(current)
    }

    // ---- triggerEvent: Mute -------------------------------------------------------------------

    @Test
    fun `triggerEvent Mute only stops the alarm and does not touch the handler`() {
        sut.triggerEvent(AlarmEvent.Mute)

        verify(uiInteraction).stopAlarm("Mute Click")
        verify(uiInteraction, never()).runAlarm(any<String>(), any<String>(), any<Int>())
        verify(alarmActionHandler, never()).triggerEvent(any())
        verify(handler, never()).postDelayed(any<Runnable>(), any<Long>())
    }

    @Test
    fun `triggerEvent Mute leaves alarmInfo untouched`() {
        val info = alarm()
        sut.alarmInfo = info

        sut.triggerEvent(AlarmEvent.Mute)

        assertThat(sut.alarmInfo).isSameInstanceAs(info)
    }

    // ---- triggerEvent: Mute5min ---------------------------------------------------------------

    @Test
    fun `triggerEvent Mute5min stops the alarm and schedules the re-arm five minutes out`() {
        sut.triggerEvent(AlarmEvent.Mute5min)

        verify(uiInteraction).stopAlarm("Mute5min Click")
        verify(handler).postDelayed(any<Runnable>(), eq(reArmDelayMs))
    }

    @Test
    fun `triggerEvent Mute5min does not sound the alarm before the delay elapses`() {
        sut.triggerEvent(AlarmEvent.Mute5min)

        // The re-arm is a delayed post; until that Runnable runs, nothing may sound.
        verify(uiInteraction, never()).runAlarm(any<String>(), any<String>(), any<Int>())
    }

    @Test
    fun `the Mute5min re-arm runs the alarm using the app title when no alarm info is set`() {
        whenever(rh.gs(R.string.carelevo)).thenReturn("Carelevo")
        sut.triggerEvent(AlarmEvent.Mute5min)

        capturePostedReArm().run()

        // alarmInfo is null -> status falls back to the app title.
        verify(uiInteraction).runAlarm(eq("Carelevo"), eq("Carelevo"), eq(CoreUiR.raw.error))
    }

    @Test
    fun `the Mute5min re-arm runs the alarm using the cause title when alarm info is set`() {
        whenever(rh.gs(R.string.carelevo)).thenReturn("Carelevo")
        whenever(rh.gs(R.string.alarm_feat_title_notice_lgs_started)).thenReturn("LGS Started")
        sut.alarmInfo = alarm(AlarmCause.ALARM_NOTICE_LGS_START)
        sut.triggerEvent(AlarmEvent.Mute5min)

        capturePostedReArm().run()

        verify(uiInteraction).runAlarm(eq("LGS Started"), eq("Carelevo"), eq(CoreUiR.raw.error))
    }

    // ---- triggerEvent: StartAlarm -------------------------------------------------------------

    @Test
    fun `triggerEvent StartAlarm with no alarm info runs the alarm using the app title as status`() {
        whenever(rh.gs(R.string.carelevo)).thenReturn("Carelevo")

        sut.triggerEvent(AlarmEvent.StartAlarm)

        verify(uiInteraction).runAlarm(eq("Carelevo"), eq("Carelevo"), eq(CoreUiR.raw.error))
    }

    @Test
    fun `triggerEvent StartAlarm with alarm info runs the alarm using the notice cause title`() {
        whenever(rh.gs(R.string.carelevo)).thenReturn("Carelevo")
        whenever(rh.gs(R.string.alarm_feat_title_notice_lgs_started)).thenReturn("LGS Started")
        sut.alarmInfo = alarm(AlarmCause.ALARM_NOTICE_LGS_START)

        sut.triggerEvent(AlarmEvent.StartAlarm)

        verify(uiInteraction).runAlarm(eq("LGS Started"), eq("Carelevo"), eq(CoreUiR.raw.error))
    }

    @Test
    fun `triggerEvent StartAlarm with a warning alarm info runs the alarm using the warning title`() {
        whenever(rh.gs(R.string.carelevo)).thenReturn("Carelevo")
        whenever(rh.gs(R.string.alarm_feat_title_warning_infusion_clogged)).thenReturn("Infusion clogged")
        sut.alarmInfo = alarm(AlarmCause.ALARM_WARNING_PUMP_CLOGGED)

        sut.triggerEvent(AlarmEvent.StartAlarm)

        verify(uiInteraction).runAlarm(eq("Infusion clogged"), eq("Carelevo"), eq(CoreUiR.raw.error))
    }

    @Test
    fun `triggerEvent StartAlarm does not stop the alarm schedule a re-arm or reach the handler`() {
        whenever(rh.gs(R.string.carelevo)).thenReturn("Carelevo")

        sut.triggerEvent(AlarmEvent.StartAlarm)

        verify(uiInteraction, never()).stopAlarm(any<String>())
        verify(handler, never()).postDelayed(any<Runnable>(), any<Long>())
        verify(alarmActionHandler, never()).triggerEvent(any())
    }

    @Test
    fun `triggerEvent StartAlarm logs the resolved status on the pump-comm tag`() {
        whenever(rh.gs(R.string.carelevo)).thenReturn("Carelevo")

        sut.triggerEvent(AlarmEvent.StartAlarm)

        verify(aapsLogger).debug(eq(LTag.PUMPCOMM), eq("startAlarm reason=start status=Carelevo"))
    }

    // ---- triggerEvent: ignored events ---------------------------------------------------------

    @Test
    fun `triggerEvent NoAction does nothing`() {
        sut.triggerEvent(AlarmEvent.NoAction)

        verifyNoInteractions(uiInteraction)
        verify(alarmActionHandler, never()).triggerEvent(any())
        assertThat(sut.alarmInfo).isNull()
    }

    @Test
    fun `triggerEvent RequestBluetoothEnable is ignored by the shell`() {
        // Emitted by the action handler for the host to act on; the shell itself must stay inert.
        sut.triggerEvent(AlarmEvent.RequestBluetoothEnable)

        verifyNoInteractions(uiInteraction)
        verify(alarmActionHandler, never()).triggerEvent(any())
    }

    @Test
    fun `triggerEvent ShowToastMessage is ignored by the shell`() {
        sut.triggerEvent(AlarmEvent.ShowToastMessage(R.string.carelevo))

        verifyNoInteractions(uiInteraction)
        verify(alarmActionHandler, never()).triggerEvent(any())
    }

    // ---- onCleared ----------------------------------------------------------------------------

    @Test
    fun `onCleared drops pending callbacks and quits the handler looper`() {
        invokeOnCleared(sut)

        verify(handler).removeCallbacksAndMessages(null)
        verify(looper).quitSafely()
    }

    @Test
    fun `onCleared drops a pending Mute5min re-arm so the alarm never fires`() {
        sut.triggerEvent(AlarmEvent.Mute5min)

        invokeOnCleared(sut)

        // Without this the HandlerThread leaks and the delayed re-arm could sound against a cleared VM.
        verify(handler).removeCallbacksAndMessages(null)
        verify(looper).quitSafely()
        verify(uiInteraction, never()).runAlarm(any<String>(), any<String>(), any<Int>())
    }

    @Test
    fun `onCleared is idempotent`() {
        invokeOnCleared(sut)
        invokeOnCleared(sut)

        verify(handler, times(2)).removeCallbacksAndMessages(null)
        verify(looper, times(2)).quitSafely()
    }
}
