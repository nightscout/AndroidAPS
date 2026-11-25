package app.aaps.pump.carelevo.common

import android.content.Context
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.alerts.Alert
import app.aaps.core.interfaces.alerts.AlertChoice
import app.aaps.core.interfaces.alerts.SharedSilence
import app.aaps.core.interfaces.alerts.AlertKind
import app.aaps.core.interfaces.alerts.AlertLifetime
import app.aaps.core.interfaces.alerts.AlertSound
import app.aaps.core.interfaces.alerts.AlertSeverity
import app.aaps.core.interfaces.alerts.Alerts
import app.aaps.core.keys.interfaces.IntPreferenceKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.carelevo.CarelevoStrings
import app.aaps.pump.carelevo.ContextResourceHelper
import app.aaps.pump.carelevo.common.keys.CarelevoIntPreferenceKey
import app.aaps.pump.carelevo.domain.model.alarm.CarelevoAlarmInfo
import app.aaps.pump.carelevo.domain.type.AlarmCause
import app.aaps.pump.carelevo.presentation.model.AlarmEvent
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Robolectric unit tests for [CarelevoAlarmNotifier]: how each stored alarm becomes a pump alert.
 *
 * The strings come from a REAL application [Context] ([ContextResourceHelper]), so the texts are the real formatted
 * ones, with the pump values put in through the alert's text arguments. The alerts go to [RecordingAlerts]; the
 * core side of pump alerts has its own tests (`AlertsImplTest`).
 *
 * The notifier's dispatcher is set to [Dispatchers.Unconfined], so every emission is handled synchronously on the
 * test thread.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CarelevoAlarmNotifierTest {

    /** Keeps what was raised and resolved, as the core would. */
    private class RecordingAlerts : Alerts {

        private val _active = MutableStateFlow<List<Alert>>(emptyList())
        override val active: StateFlow<List<Alert>> = _active
        override val sounding: Alert? get() = _active.value.lastOrNull()
        override val choices: StateFlow<Map<String, AlertChoice>> = MutableStateFlow(emptyMap())
        override fun setChoice(kind: AlertKind, choice: AlertChoice) {}
        override val pauses: StateFlow<Map<String, Long>> = MutableStateFlow(emptyMap())
        override fun pause(kinds: Collection<AlertKind>, until: Long) {}
        override fun resume(kinds: Collection<AlertKind>) {}
        override val sounds: StateFlow<Map<String, AlertSound>> = MutableStateFlow(emptyMap())
        override fun setSound(kind: AlertKind, sound: AlertSound?) {}
        override fun test(kind: AlertKind, severity: AlertSeverity): Boolean = true
        override fun silenceEverywhere(source: String, key: String, minutes: Int) {}
        override fun canSilenceEverywhere(alert: Alert): Boolean = false
        override fun applySharedSilence(silence: SharedSilence) {}
        override fun silencedUntil(source: String, key: String): Long = 0L
        val raised = mutableListOf<Alert>()
        val resolved = mutableListOf<String>()

        override fun raise(alert: Alert) {
            raised += alert
            _active.value = _active.value.filterNot { it.source == alert.source && it.key == alert.key } + alert
        }

        override fun resolve(source: String, key: String) {
            resolved += key
            _active.value = _active.value.filterNot { it.source == source && it.key == key }
        }

        override fun resolveAll(source: String) {
            _active.value.filter { it.source == source }.forEach { resolve(source, it.key) }
        }

        override fun silence(source: String, key: String, minutes: Int?) {}
        override fun snooze(source: String, key: String, minutes: Int) {}
        override fun schedule(alert: Alert, at: Long): Boolean = true
        override fun unschedule(source: String, key: String) {}
        override fun act(source: String, key: String) {}
    }

    // REAL application context, for the real strings.
    private val context: Context = RuntimeEnvironment.getApplication()
    private val rh = ContextResourceHelper(context)

    private lateinit var aapsLogger: AAPSLogger
    private lateinit var preferences: Preferences
    private lateinit var alarmActionHandler: CarelevoAlarmActionHandler
    private val alerts = RecordingAlerts()

    private lateinit var sut: CarelevoAlarmNotifier

    private fun alarm(
        cause: AlarmCause,
        value: Int? = null,
        id: String = "alarm-1",
        createdAt: String = "2026-07-16T10:00:00"
    ): CarelevoAlarmInfo =
        CarelevoAlarmInfo(
            alarmId = id,
            alarmType = cause.alarmType,
            cause = cause,
            value = value,
            createdAt = createdAt,
            updatedAt = createdAt,
            isAcknowledged = false
        )

    @Before
    fun setUp() {
        aapsLogger = mock()
        preferences = mock()
        alarmActionHandler = mock()

        sut = CarelevoAlarmNotifier(
            aapsLogger = aapsLogger,
            alerts = alerts,
            preferences = preferences,
            alarmActionHandler = alarmActionHandler,
            rh = rh
        )
        sut.dispatcher = Dispatchers.Unconfined
    }

    private fun shown(alarm: CarelevoAlarmInfo): Alert {
        sut.showAlerts(listOf(alarm))
        return alerts.raised.last()
    }

    private fun Alert.textShown(): String = text?.let { rh.gs(it) } ?: ""

    // ---- simple state / accessors -------------------------------------------------------------

    @Test
    fun `alarms state flow starts empty`() {
        assertThat(sut.alarms.value).isEmpty()
    }

    // ---- showAlerts ---------------------------------------------------------------------------

    @Test
    fun `each stored alarm is one pump alert keyed by its id`() {
        sut.showAlerts(listOf(alarm(AlarmCause.ALARM_NOTICE_LGS_START, id = "a"), alarm(AlarmCause.ALARM_WARNING_PUMP_CLOGGED, id = "b")))

        assertThat(alerts.active.value.map { it.key }).containsExactly("a", "b")
        assertThat(alerts.active.value.map { it.source }.toSet()).containsExactly(CarelevoAlarmNotifier.SOURCE)
    }

    @Test
    fun `an alarm no longer stored is resolved`() {
        sut.showAlerts(listOf(alarm(AlarmCause.ALARM_NOTICE_LGS_START, id = "a"), alarm(AlarmCause.ALARM_WARNING_PUMP_CLOGGED, id = "b")))

        sut.showAlerts(listOf(alarm(AlarmCause.ALARM_WARNING_PUMP_CLOGGED, id = "b")))

        assertThat(alerts.resolved).containsExactly("a")
        assertThat(alerts.active.value.map { it.key }).containsExactly("b")
    }

    @Test
    fun `clearAlerts ends every Carelevo alert`() {
        sut.showAlerts(listOf(alarm(AlarmCause.ALARM_NOTICE_LGS_START, id = "a")))

        sut.clearAlerts()

        assertThat(alerts.active.value).isEmpty()
    }

    @Test
    fun `an alarm that stopped the insulin rings`() {
        assertThat(shown(alarm(AlarmCause.ALARM_WARNING_PUMP_CLOGGED)).severity).isEqualTo(AlertSeverity.CRITICAL)
    }

    /** An ALERT is not always the step before the stop: a pause for the temperature has stopped the insulin already. */
    @Test
    fun `an alert that paused the insulin rings`() {
        assertThat(shown(alarm(AlarmCause.ALARM_ALERT_INVALID_TEMPERATURE)).severity).isEqualTo(AlertSeverity.CRITICAL)
    }

    @Test
    fun `an alarm before the insulin stops chimes once`() {
        whenever(preferences.get(CarelevoIntPreferenceKey.CARELEVO_LOW_INSULIN_REMINDER_UNITS)).thenReturn(25)
        assertThat(shown(alarm(AlarmCause.ALARM_ALERT_OUT_OF_INSULIN)).severity).isEqualTo(AlertSeverity.WARNING)
    }

    @Test
    fun `a reminder the user set chimes once`() {
        assertThat(shown(alarm(AlarmCause.ALARM_NOTICE_BG_CHECK, value = 120)).severity).isEqualTo(AlertSeverity.WARNING)
    }

    @Test
    fun `an LGS notice is only shown`() {
        assertThat(shown(alarm(AlarmCause.ALARM_NOTICE_LGS_START)).severity).isEqualTo(AlertSeverity.INFO)
    }

    @Test
    fun `an unknown alarm rings, because it may have stopped the insulin`() {
        assertThat(shown(alarm(AlarmCause.ALARM_UNKNOWN)).severity).isEqualTo(AlertSeverity.CRITICAL)
    }

    // ---- the patch that was not stopped -------------------------------------------------------

    @Test
    fun `a patch removed without being stopped rings until the user answers`() {
        sut.patchNotStopped(1_000L)

        val alert = alerts.raised.single()
        assertThat(alert.kind).isSameInstanceAs(CarelevoAlertKind.NOT_STOPPED)
        assertThat(alert.key).isEqualTo(CarelevoAlarmNotifier.NOT_STOPPED_KEY)
        assertThat(alert.severity).isEqualTo(AlertSeverity.CRITICAL)
        assertThat(alert.lifetime).isEqualTo(AlertLifetime.EVENT)
        assertThat(alert.raisedAt).isEqualTo(1_000L)
        assertThat(alert.text).isEqualTo(CarelevoStrings.carelevo_discarded_not_reached)
        assertThat(alert.textShown()).contains("Take it off")
    }

    /** It is not one of the stored alarms, so the store emptying after the removal must not end it. */
    @Test
    fun `the stored alarms do not end the patch that was not stopped`() {
        sut.showAlerts(listOf(alarm(AlarmCause.ALARM_WARNING_PUMP_CLOGGED, id = "b")))
        sut.patchNotStopped(1_000L)

        sut.showAlerts(emptyList())

        assertThat(alerts.resolved).containsExactly("b")
        assertThat(alerts.active.value.map { it.key }).containsExactly(CarelevoAlarmNotifier.NOT_STOPPED_KEY)
    }

    /** The silence of the core holds for one occurrence, so the time must be the stored one, the same after a restart. */
    @Test
    fun `the alert is raised at the stored creation time of the alarm`() {
        val alert = shown(alarm(AlarmCause.ALARM_NOTICE_LGS_START, createdAt = "2026-07-16T10:00:00"))

        val expected = LocalDateTime.parse("2026-07-16T10:00:00").toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
        assertThat(alert.raisedAt).isEqualTo(expected)
    }

    @Test
    fun `the title and text come from real resources`() {
        val alert = shown(alarm(AlarmCause.ALARM_NOTICE_LGS_START))

        assertThat(rh.gs(alert.title)).isNotEmpty()
        assertThat(alert.textShown()).isNotEmpty()
    }

    @Test
    fun `the action clears the alarm through the action handler`() = runBlocking {
        val alert = shown(alarm(AlarmCause.ALARM_NOTICE_LGS_START))

        assertThat(alert.action!!.run()).isTrue()

        verify(alarmActionHandler).triggerEvent(any<AlarmEvent.ClearAlarm>())
    }

    @Test
    fun `the low-insulin notice reads the low-insulin reminder preference into its text`() {
        whenever(preferences.get(CarelevoIntPreferenceKey.CARELEVO_LOW_INSULIN_REMINDER_UNITS)).thenReturn(25)

        val alert = shown(alarm(AlarmCause.ALARM_NOTICE_LOW_INSULIN))

        verify(preferences).get(CarelevoIntPreferenceKey.CARELEVO_LOW_INSULIN_REMINDER_UNITS)
        assertThat(alert.textShown()).contains("25")
    }

    @Test
    fun `the out-of-insulin alert reads the low-insulin reminder preference`() {
        whenever(preferences.get(CarelevoIntPreferenceKey.CARELEVO_LOW_INSULIN_REMINDER_UNITS)).thenReturn(25)

        shown(alarm(AlarmCause.ALARM_ALERT_OUT_OF_INSULIN))

        verify(preferences).get(CarelevoIntPreferenceKey.CARELEVO_LOW_INSULIN_REMINDER_UNITS)
    }

    @Test
    fun `the patch-expired notice splits the reminder hours into days and hours`() {
        whenever(preferences.get(CarelevoIntPreferenceKey.CARELEVO_PATCH_EXPIRATION_REMINDER_HOURS)).thenReturn(50)

        val alert = shown(alarm(AlarmCause.ALARM_NOTICE_PATCH_EXPIRED))

        // 50 h -> 2 days 2 hours. Locale-independent: the template embeds the digits.
        assertThat(alert.textShown()).contains("2")
    }

    @Test
    fun `the bg-check notice formats hours and minutes`() {
        val text = shown(alarm(AlarmCause.ALARM_NOTICE_BG_CHECK, value = 125)).textShown()

        assertThat(text).contains("2")
        assertThat(text).contains("5")
    }

    @Test
    fun `the bg-check notice formats whole hours`() {
        assertThat(shown(alarm(AlarmCause.ALARM_NOTICE_BG_CHECK, value = 120)).textShown()).contains("2")
    }

    @Test
    fun `the bg-check notice formats minutes only`() {
        assertThat(shown(alarm(AlarmCause.ALARM_NOTICE_BG_CHECK, value = 45)).textShown()).contains("45")
    }

    @Test
    fun `a cause with no description arguments reads no preference`() {
        shown(alarm(AlarmCause.ALARM_WARNING_NEEDLE_INSERTION_ERROR))

        verify(preferences, never()).get(any<IntPreferenceKey>())
    }

    // ---- startObserving / stopObserving -------------------------------------------------------

    @Test
    fun `startObserving forwards emitted alarms to the callback and the state flow`() {
        val emitted = listOf(alarm(AlarmCause.ALARM_NOTICE_LGS_START))
        whenever(alarmActionHandler.observeAlarms()).thenReturn(flowOf(emitted))
        var received: List<CarelevoAlarmInfo>? = null

        sut.startObserving { received = it }

        assertThat(received).isEqualTo(emitted)
        assertThat(sut.alarms.value).isEqualTo(emitted)
    }

    @Test
    fun `startObserving logs and does not crash when the alarm stream errors`() {
        whenever(alarmActionHandler.observeAlarms())
            .thenReturn(flow<List<CarelevoAlarmInfo>> { throw RuntimeException("boom") })
        var callbackInvoked = false

        sut.startObserving { callbackInvoked = true }

        assertThat(callbackInvoked).isFalse()
        assertThat(sut.alarms.value).isEmpty()
        verify(aapsLogger).error(eq(LTag.PUMPCOMM), any<String>())
    }

    @Test
    fun `stopObserving cancels the collection so later emissions are ignored`() {
        // A hot stream with no replay, as the PublishSubject was. One buffer slot lets tryEmit hand a value to the collector.
        val subject = MutableSharedFlow<List<CarelevoAlarmInfo>>(extraBufferCapacity = 1)
        whenever(alarmActionHandler.observeAlarms()).thenReturn(subject)
        var callbackCount = 0

        sut.startObserving { callbackCount++ }
        subject.tryEmit(emptyList())
        assertThat(callbackCount).isEqualTo(1)

        sut.stopObserving()
        subject.tryEmit(emptyList())

        // No further delivery after cancellation.
        assertThat(callbackCount).isEqualTo(1)
    }

    // ---- refreshAlarms ------------------------------------------------------------------------

    @Test
    fun `refreshAlarms updates the state flow from a one-shot load`() {
        val loaded = listOf(alarm(AlarmCause.ALARM_NOTICE_LGS_START))
        whenever { alarmActionHandler.getAlarmsOnce() }.thenReturn(loaded)

        sut.refreshAlarms()

        assertThat(sut.alarms.value).isEqualTo(loaded)
    }

    @Test
    fun `refreshAlarms logs and does not crash when the load errors`() {
        whenever { alarmActionHandler.getAlarmsOnce() }
            .thenAnswer { throw RuntimeException("boom") }

        sut.refreshAlarms()

        assertThat(sut.alarms.value).isEmpty()
        verify(aapsLogger).error(eq(LTag.PUMPCOMM), any<String>())
    }
}
