package app.aaps.implementation.pump.alerts

import app.aaps.core.data.model.TE
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.notifications.AapsNotification
import app.aaps.core.interfaces.notifications.AlarmSound
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationLevel
import app.aaps.core.interfaces.notifications.SystemNotificationPlatform
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.PumpWithConcentration
import app.aaps.core.interfaces.pump.alerts.PumpAlert
import app.aaps.core.interfaces.pump.alerts.PumpAlertAction
import app.aaps.core.interfaces.pump.alerts.PumpAlertLifetime
import app.aaps.core.interfaces.pump.alerts.PumpAlertPlatform
import app.aaps.core.interfaces.pump.alerts.PumpAlertSeverity
import app.aaps.core.interfaces.pump.alerts.PumpAlerts
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.Event
import app.aaps.core.interfaces.rx.events.EventShowSnackbar
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.implementation.notifications.CommonNotificationManager
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.anyVararg
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever

/**
 * The shared pump alerts against the real notification list, so the card, its level and its sound are what the
 * user would get. Never `advanceUntilIdle`: the list cleans up in a loop that re-arms itself.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PumpAlertsImplTest : TestBase() {

    /** What the notification list asks of the platform: which card owns the sound now. */
    private class RecordingSystemPlatform : SystemNotificationPlatform {

        var soundingKey: Int? = null
        override fun show(notification: AapsNotification, title: String) {}
        override fun cancel(instanceKey: Int) {}
        override fun cancelAll() {}
        override fun setAudibleAlarm(instanceKey: Int?, sound: AlarmSound?) {
            soundingKey = instanceKey
        }

        override fun onDismissed(callback: (Int) -> Unit) {}
    }

    private class RecordingAlertPlatform : PumpAlertPlatform {

        val screens = mutableListOf<String>()
        val chimes = mutableListOf<String>()
        val snoozes = mutableListOf<Pair<String, Long>>()
        val withdrawals = mutableListOf<String>()
        override fun showAlarmScreen(alert: PumpAlert) {
            screens += alert.key
        }

        override fun chime(alert: PumpAlert) {
            chimes += alert.key
        }

        override fun snoozed(alert: PumpAlert, until: Long) {
            snoozes += alert.key to until
        }

        override fun withdrawn(source: String, key: String) {
            withdrawals += key
        }
    }

    private val scope = TestScope(StandardTestDispatcher())
    private val systemPlatform = RecordingSystemPlatform()
    private val alertPlatform = RecordingAlertPlatform()
    private val rh: TextResolver = mock()
    private val eventBus: RxBus = mock()
    private val dateUtil: DateUtil = mock()
    private val preferences: Preferences = mock()
    private val pumpSync: PumpSync = mock()
    private val activePlugin: ActivePlugin = mock()
    private val pump: PumpWithConcentration = mock()

    private var now = 1_000_000_000L
    private var storedSilences = "[]"

    private lateinit var notificationManager: CommonNotificationManager
    private lateinit var sut: PumpAlertsImpl

    @BeforeEach
    fun setUp() {
        whenever(dateUtil.now()).thenAnswer { now }
        doAnswer { (it.arguments[0] as? TextRef.Literal)?.text ?: "text" }.whenever(rh).gs(any<TextRef>())
        whenever(rh.gs(any<TextRef>(), anyVararg())).thenReturn("text")
        whenever(preferences.get(StringNonKey.PumpAlertSilences)).thenAnswer { storedSilences }
        doAnswer { storedSilences = it.arguments[1] as String; Unit }.whenever(preferences).put(eq(StringNonKey.PumpAlertSilences), any<String>())
        whenever(activePlugin.activePump).thenReturn(pump)
        whenever(pump.model()).thenReturn(PumpType.GENERIC_AAPS)
        whenever(pump.serialNumber()).thenReturn("serial")
        notificationManager = CommonNotificationManager(aapsLogger, rh, systemPlatform, eventBus, scope)
        sut = newPumpAlerts()
    }

    private fun newPumpAlerts() = PumpAlertsImpl(aapsLogger, notificationManager, alertPlatform, preferences, pumpSync, activePlugin, rh, eventBus, dateUtil, scope)

    private fun alert(
        key: String = "occlusion",
        severity: PumpAlertSeverity = PumpAlertSeverity.CRITICAL,
        raisedAt: Long = 500L,
        source: String = "pump",
        action: PumpAlertAction? = null,
        lifetime: PumpAlertLifetime = PumpAlertLifetime.CONDITION
    ) = PumpAlert(
        source = source, key = key, severity = severity, title = TextRef.Literal(key), raisedAt = raisedAt,
        action = action, lifetime = lifetime
    )

    private fun cards(): List<AapsNotification> = notificationManager.notifications.value.filter { it.id == NotificationId.PUMP_ALERT }

    private fun card(): AapsNotification = cards().single()

    @Test
    fun `a critical alert is an urgent card that rings, and opens the alarm screen`() {
        sut.raise(alert())

        assertThat(card().level).isEqualTo(NotificationLevel.URGENT)
        assertThat(card().sound).isEqualTo(AlarmSound.ALARM)
        assertThat(systemPlatform.soundingKey).isEqualTo(card().instanceKey)
        assertThat(alertPlatform.screens).containsExactly("occlusion")
        assertThat(sut.active.value.map { it.key }).containsExactly("occlusion")
    }

    @Test
    fun `a new alert is announced once with the time the pump raised it`() {
        sut.raise(alert(severity = PumpAlertSeverity.WARNING))
        scope.runCurrent()

        verifyBlocking(pumpSync) {
            insertTherapyEventIfNewWithTimestamp(eq(500L), eq(TE.Type.ANNOUNCEMENT), anyOrNull(), anyOrNull(), eq(PumpType.GENERIC_AAPS), eq("serial"))
        }
    }

    @Test
    fun `an info alert is not announced`() {
        sut.raise(alert(severity = PumpAlertSeverity.INFO))
        scope.runCurrent()

        verifyBlocking(pumpSync, never()) { insertTherapyEventIfNewWithTimestamp(any(), any(), anyOrNull(), anyOrNull(), any(), any()) }
    }

    @Test
    fun `the same occurrence raised again does not ring again`() {
        sut.raise(alert())
        sut.raise(alert())

        assertThat(cards()).hasSize(1)
        assertThat(alertPlatform.screens).hasSize(1)
    }

    @Test
    fun `a new occurrence of the same key rings again`() {
        sut.raise(alert(raisedAt = 500L))
        sut.raise(alert(raisedAt = 600L))

        assertThat(cards()).hasSize(1)
        assertThat(alertPlatform.screens).hasSize(2)
    }

    @Test
    fun `silence stops the sound for 30 minutes and keeps the card`() {
        sut.raise(alert())

        sut.silence("pump", "occlusion")

        assertThat(card().sound).isNull()
        assertThat(card().level).isEqualTo(NotificationLevel.URGENT)
        assertThat(systemPlatform.soundingKey).isNull()
        assertThat(alertPlatform.snoozes).containsExactly("occlusion" to now + PumpAlerts.SILENCE_MINUTES * 60_000L)
        assertThat(sut.active.value).hasSize(1)
    }

    @Test
    fun `when the silence ends an alert still active rings again`() {
        sut.raise(alert())
        sut.silence("pump", "occlusion")

        now += PumpAlerts.SILENCE_MINUTES * 60_000L
        scope.advanceTimeBy(PumpAlerts.SILENCE_MINUTES * 60_000L + 1)

        assertThat(card().sound).isEqualTo(AlarmSound.ALARM)
        assertThat(systemPlatform.soundingKey).isEqualTo(card().instanceKey)
        assertThat(alertPlatform.screens).hasSize(2)
        assertThat(alertPlatform.withdrawals).contains("occlusion")
    }

    @Test
    fun `a platform wake ends a silence that is over, also when the timer is late`() {
        sut.raise(alert())
        sut.silence("pump", "occlusion")

        now += PumpAlerts.SILENCE_MINUTES * 60_000L
        sut.checkSilences()

        assertThat(card().sound).isEqualTo(AlarmSound.ALARM)
    }

    @Test
    fun `a silence survives a restart and holds for the same occurrence`() {
        sut.raise(alert(raisedAt = 500L))
        sut.silence("pump", "occlusion")
        notificationManager.dismiss(NotificationId.PUMP_ALERT)

        // A new process: a new instance, the same stored preferences.
        now += 60_000L
        val restarted = newPumpAlerts()
        restarted.raise(alert(raisedAt = 500L))

        assertThat(card().sound).isNull()
        assertThat(alertPlatform.screens).hasSize(1)
    }

    @Test
    fun `a stored silence does not hold for a new occurrence`() {
        sut.raise(alert(raisedAt = 500L))
        sut.silence("pump", "occlusion")
        notificationManager.dismiss(NotificationId.PUMP_ALERT)

        val restarted = newPumpAlerts()
        restarted.raise(alert(raisedAt = 900L))

        assertThat(card().sound).isEqualTo(AlarmSound.ALARM)
        assertThat(alertPlatform.screens).hasSize(2)
    }

    @Test
    fun `resolve removes the card, stops the sound and forgets the silence`() {
        sut.raise(alert())
        sut.silence("pump", "occlusion")

        sut.resolve("pump", "occlusion")

        assertThat(cards()).isEmpty()
        assertThat(systemPlatform.soundingKey).isNull()
        assertThat(sut.active.value).isEmpty()
        assertThat(alertPlatform.withdrawals).contains("occlusion")
        assertThat(storedSilences).isEqualTo("[]")
    }

    @Test
    fun `resolveAll ends only the alerts of that source`() {
        sut.raise(alert(key = "a", source = "one"))
        sut.raise(alert(key = "b", source = "two"))

        sut.resolveAll("one")

        assertThat(sut.active.value.map { it.key }).containsExactly("b")
        assertThat(cards()).hasSize(1)
    }

    @Test
    fun `a card cleared by another alarm screen comes back silenced`() {
        sut.raise(alert())
        scope.runCurrent()

        // OK on the alarm screen of some other alarm clears every ringing card.
        notificationManager.muteAllAlarms()
        scope.runCurrent()

        assertThat(card().sound).isNull()
        assertThat(sut.active.value).hasSize(1)
        assertThat(alertPlatform.snoozes).hasSize(1)
    }

    @Test
    fun `an info alert with no action that the user dismissed stays dismissed`() {
        sut.raise(alert(severity = PumpAlertSeverity.INFO))
        scope.runCurrent()

        notificationManager.dismiss(NotificationId.PUMP_ALERT)
        scope.runCurrent()

        assertThat(cards()).isEmpty()
        assertThat(sut.active.value).hasSize(1)
    }

    @Test
    fun `a warning is an important card with one sound and no alarm screen`() {
        sut.raise(alert(severity = PumpAlertSeverity.WARNING))

        assertThat(card().level).isEqualTo(NotificationLevel.IMPORTANT)
        assertThat(card().sound).isNull()
        assertThat(alertPlatform.chimes).containsExactly("occlusion")
        assertThat(alertPlatform.screens).isEmpty()
    }

    @Test
    fun `an info alert is a normal card with no sound`() {
        sut.raise(alert(severity = PumpAlertSeverity.INFO))

        assertThat(card().level).isEqualTo(NotificationLevel.NORMAL)
        assertThat(alertPlatform.chimes).isEmpty()
        assertThat(alertPlatform.screens).isEmpty()
    }

    @Test
    fun `the card button runs the action and silences the alert`() {
        var runs = 0
        sut.raise(alert(action = PumpAlertAction(TextRef.Literal("Clear")) { runs++; true }))

        card().actions.single().action()
        scope.runCurrent()

        assertThat(runs).isEqualTo(1)
        assertThat(card().sound).isNull()
        verify(eventBus, never()).send(any<EventShowSnackbar>())
    }

    @Test
    fun `an action the pump did not confirm says so`() {
        sut.raise(alert(action = PumpAlertAction(TextRef.Literal("Clear")) { false }))

        sut.act("pump", "occlusion")
        scope.runCurrent()

        // The same bus also carries the watch events of the notification list.
        val sent = argumentCaptor<Event>()
        verify(eventBus, atLeastOnce()).send(sent.capture())
        val snackbars = sent.allValues.filterIsInstance<EventShowSnackbar>()
        assertThat(snackbars).hasSize(1)
        assertThat(snackbars.single().type).isEqualTo(EventShowSnackbar.Type.Error)
    }
    // ---------------------------------------------------------------------------------------------
    // An EVENT alert: the user's answer is the end of it
    // ---------------------------------------------------------------------------------------------

    /**
     * The difference the lifetime makes. A pump's alert is a condition the pump still holds, so silence only quiets
     * it; one of the app's own - a bolus that failed - is over once the user has read it, and bringing it back half
     * an hour later would be telling them about something they already dealt with.
     */
    @Test
    fun `silencing an event alert ends it`() {
        sut.raise(alert(key = "bolus-error", source = "aaps", lifetime = PumpAlertLifetime.EVENT))

        sut.silence("aaps", "bolus-error")

        assertThat(sut.active.value).isEmpty()
        assertThat(cards()).isEmpty()
        assertThat(alertPlatform.withdrawals).containsExactly("bolus-error")
    }

    @Test
    fun `silencing a condition alert keeps it, quieted`() {
        sut.raise(alert())

        sut.silence("pump", "occlusion")

        assertThat(sut.active.value.map { it.key }).containsExactly("occlusion")
        assertThat(card().sound).isNull()
    }

    /** Mute 5 min on an event alert is still a delay, not the end: that is what Silence is for. */
    @Test
    fun `snoozing an event alert keeps it and schedules the re-ring`() {
        sut.raise(alert(key = "bolus-error", source = "aaps", lifetime = PumpAlertLifetime.EVENT))

        sut.snooze("aaps", "bolus-error", 5)

        assertThat(sut.active.value.map { it.key }).containsExactly("bolus-error")
        assertThat(card().sound).isNull()
        assertThat(alertPlatform.snoozes.map { it.second }).containsExactly(now + 5 * 60_000L)
    }

    /**
     * The card going away is the user's answer, however they gave it - a swipe, Mute on the notification, "mute all
     * alarms". For an event that is the end of it; a condition comes back without sound, which the test above this
     * one covers for the pump case.
     */
    @Test
    fun `an event alert whose card is removed is over`() {
        sut.raise(alert(key = "bolus-error", source = "aaps", lifetime = PumpAlertLifetime.EVENT))
        notificationManager.muteAllAlarms()
        scope.runCurrent()

        assertThat(sut.active.value).isEmpty()
        assertThat(cards()).isEmpty()
    }

    /** The pump still holds it, so the card comes back - silenced, not ringing. */
    @Test
    fun `a condition alert whose card is removed comes back without sound`() {
        sut.raise(alert())
        notificationManager.muteAllAlarms()
        scope.runCurrent()

        assertThat(sut.active.value.map { it.key }).containsExactly("occlusion")
        assertThat(card().sound).isNull()
    }

    /**
     * An event's action is the answer, so a success ends it. "+5 min" on the "time to eat" timer moves the timer and
     * the reminder is done; the same button failing has to leave the alarm exactly as it was, because the user still
     * has to eat.
     */
    @Test
    fun `an event action that succeeds ends the alert`() {
        sut.raise(alert(key = "eat-reminder", source = "aaps", lifetime = PumpAlertLifetime.EVENT, action = PumpAlertAction(TextRef.Literal("+5 min")) { true }))

        sut.act("aaps", "eat-reminder")
        scope.runCurrent()

        assertThat(sut.active.value).isEmpty()
    }

    @Test
    fun `an event action that fails leaves the alert ringing`() {
        sut.raise(alert(key = "eat-reminder", source = "aaps", lifetime = PumpAlertLifetime.EVENT, action = PumpAlertAction(TextRef.Literal("+5 min")) { false }))

        sut.act("aaps", "eat-reminder")
        scope.runCurrent()

        assertThat(sut.active.value.map { it.key }).containsExactly("eat-reminder")
        assertThat(card().sound).isEqualTo(AlarmSound.ALARM)
    }

    /** A pump's alert is silenced while its step runs: the pump takes its time, and the driver resolves it. */
    @Test
    fun `a condition action silences the alert while it runs`() {
        sut.raise(alert(action = PumpAlertAction(TextRef.Literal("Deactivate")) { true }))

        sut.act("pump", "occlusion")
        scope.runCurrent()

        assertThat(sut.active.value.map { it.key }).containsExactly("occlusion")
        assertThat(card().sound).isNull()
    }

    // ---------------------------------------------------------------------------------------------
    // Which alert is ringing
    // ---------------------------------------------------------------------------------------------

    /** For the watch's Mute and the Mute action, which cannot name an alert. */
    @Test
    fun `the newest ringing alert is the sounding one`() {
        sut.raise(alert(key = "first", raisedAt = 500L))
        sut.raise(alert(key = "second", raisedAt = 600L))

        assertThat(sut.sounding?.key).isEqualTo("second")
    }

    @Test
    fun `a silenced alert is not the sounding one`() {
        sut.raise(alert(key = "only"))
        sut.snooze("pump", "only", 5)

        assertThat(sut.sounding).isNull()
    }

    @Test
    fun `nothing ringing means no sounding alert`() {
        assertThat(sut.sounding).isNull()
    }

}
