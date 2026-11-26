package app.aaps.implementation.ui

import app.aaps.core.interfaces.alerts.Alert
import app.aaps.core.interfaces.alerts.AlertChoice
import app.aaps.core.interfaces.alerts.SharedSilence
import app.aaps.core.interfaces.alerts.AlertKind
import app.aaps.core.interfaces.alerts.AlertSound
import app.aaps.core.interfaces.alerts.AlertLifetime
import app.aaps.core.interfaces.alerts.AlertOrigin
import app.aaps.core.interfaces.alerts.AlertSetting
import app.aaps.core.interfaces.alerts.AlertSeverity
import app.aaps.core.interfaces.alerts.Alerts
import app.aaps.core.interfaces.alerts.CoreAlert
import app.aaps.core.interfaces.alerts.EatReminder
import app.aaps.core.interfaces.notifications.AlarmSound
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventAlarmDismissed
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * The app's own alarms, as alerts.
 *
 * What is worth pinning here is the shape of the alert `runAlarm` builds, because everything the user sees follows
 * from it: the lifetime decides whether answering the alarm ends it, the title and the text decide what the alarm
 * screen and the notification say, and the two watch flags decide what the watch shows. All of it used to be spread
 * across 150 lines of Android-only code in `:app`; see `_docs/PUMP_DESIGN.md`, section 8.
 */
class UiInteractionImplTest : TestBase() {

    /** Records what was raised and what was asked of the alerts, as the core would. */
    private class RecordingAlerts : Alerts {

        private val _active = MutableStateFlow<List<Alert>>(emptyList())
        override val active: StateFlow<List<Alert>> = _active
        override var sounding: Alert? = null
        override val choices: StateFlow<Map<String, AlertChoice>> = MutableStateFlow(emptyMap())
        override fun setChoice(setting: AlertSetting, choice: AlertChoice) {}
        override fun clearChoice(kind: AlertKind) {}
        override val pauses: StateFlow<Map<String, Long>> = MutableStateFlow(emptyMap())
        override fun pause(kinds: Collection<AlertKind>, until: Long) {}
        override fun resume(kinds: Collection<AlertKind>) {}
        override val sounds: StateFlow<Map<String, AlertSound>> = MutableStateFlow(emptyMap())
        override fun setSound(setting: AlertSetting, sound: AlertSound?) {}
        override fun test(kind: AlertKind, severity: AlertSeverity, vibrate: Boolean): Boolean = true
        override fun silenceEverywhere(source: String, key: String, minutes: Int) {}
        override fun canSilenceEverywhere(alert: Alert): Boolean = false
        override fun applySharedSilence(silence: SharedSilence) {}
        override fun silencedUntil(source: String, key: String): Long = 0L
        val raised = mutableListOf<Alert>()
        val silenced = mutableListOf<Pair<String, String>>()
        val snoozed = mutableListOf<Triple<String, String, Int>>()

        override fun raise(alert: Alert) {
            raised += alert
            _active.value = _active.value.filterNot { it.source == alert.source && it.key == alert.key } + alert
        }

        override fun resolve(source: String, key: String) {
            _active.value = _active.value.filterNot { it.source == source && it.key == key }
        }

        override fun resolveAll(source: String) {
            _active.value = _active.value.filterNot { it.source == source }
        }

        override fun silence(source: String, key: String, minutes: Int?) {
            silenced += source to key
        }

        override fun snooze(source: String, key: String, minutes: Int) {
            snoozed += Triple(source, key, minutes)
        }

        override fun act(source: String, key: String) {}

        val booked = mutableListOf<Pair<Alert, Long>>()
        val unbooked = mutableListOf<Pair<String, String>>()
        var canBook = true

        override fun schedule(alert: Alert, at: Long): Boolean {
            if (canBook) booked += alert to at
            return canBook
        }

        override fun unschedule(source: String, key: String) {
            unbooked += source to key
        }
    }

    private val alerts = RecordingAlerts()
    private val notificationManager: NotificationManager = mock()
    private val dateUtil: DateUtil = mock()
    private val eventBus: RxBus = mock()
    private val eatReminder: EatReminder = mock()

    private lateinit var sut: UiInteractionImpl

    @BeforeEach
    fun setUp() {
        whenever(dateUtil.now()).thenReturn(1_000L)
        sut = UiInteractionImpl(aapsLogger, alerts, { notificationManager }, dateUtil, eventBus, { eatReminder })
    }

    private fun raised(): Alert = alerts.raised.single()

    // ---------------------------------------------------------------------------------------------
    // The alert an alarm becomes
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `an alarm is a critical event alert under the app's own source`() {
        sut.runAlarm("bolus", "Bolus failed", "AAPS", AlarmSound.BOLUS_ERROR)

        assertThat(raised().source).isEqualTo(UiInteraction.SOURCE)
        assertThat(raised().key).isEqualTo("bolus")
        assertThat(raised().severity).isEqualTo(AlertSeverity.CRITICAL)
        assertThat(raised().lifetime).isEqualTo(AlertLifetime.EVENT)
    }

    /**
     * The title is the headline and the status is the message under it, which is the way round the alarm screen and
     * the notification have always shown them.
     */
    @Test
    fun `the title heads the alarm and the status is its message`() {
        sut.runAlarm("bolus", "Bolus failed", "AAPS", AlarmSound.BOLUS_ERROR)

        assertThat(raised().title).isEqualTo(TextRef.Literal("AAPS"))
        assertThat(raised().text).isEqualTo(TextRef.Literal("Bolus failed"))
        assertThat(raised().notificationTitle).isEqualTo("AAPS")
    }

    @Test
    fun `an alarm keeps the sound its caller asked for`() {
        sut.runAlarm("bolus", "Bolus failed", "AAPS", AlarmSound.BOLUS_ERROR)

        assertThat(raised().sound).isEqualTo(AlarmSound.BOLUS_ERROR)
    }

    /** Null has always meant the default alarm sound here, not a silent alarm. */
    @Test
    fun `an alarm with no sound named rings the default one`() {
        sut.runAlarm("bolus", "Bolus failed", "AAPS", null)

        assertThat(raised().sound).isEqualTo(AlarmSound.ERROR)
    }

    @Test
    fun `an alarm can be muted and is shown on the watch`() {
        sut.runAlarm("bolus", "Bolus failed", "AAPS")

        assertThat(raised().canMute).isTrue()
        assertThat(raised().tellWatch).isTrue()
    }

    /** The app's own alarm: [Alerts] writes its announcement as the app's, not under the pump (see `AlertsImplTest`). */
    @Test
    fun `an alarm is an alert of the app, of the kind its key names`() {
        sut.runAlarm(UiInteraction.KEY_COMMAND_FAILED, "Bolus failed", "AAPS")

        assertThat(raised().kind).isEqualTo(CoreAlert.COMMAND_FAILED)
        assertThat(raised().source).isEqualTo(UiInteraction.SOURCE)
        assertThat(raised().origin).isEqualTo(AlertOrigin.APP)
    }

    /** The user can silence a failed command, but not an insulin change that left the wrong basal running. */
    @Test
    fun `an insulin change that failed is a protected kind`() {
        sut.runAlarm(UiInteraction.KEY_INSULIN_CHANGE_FAILED, "Insulin change failed", "AAPS")

        assertThat(raised().kind).isEqualTo(CoreAlert.INSULIN_CHANGE_FAILED)
        assertThat(raised().kind.isProtected).isTrue()
    }

    /** A pump driver that has no kinds of its own yet: the app cannot tell which of its alarms are safe to silence. */
    @Test
    fun `an alarm of a driver without kinds is protected`() {
        sut.runAlarm("danars-time-diff", "Time differs", "Dana-i/RS")

        assertThat(raised().kind).isEqualTo(CoreAlert.PUMP_DRIVER_ALARM)
        assertThat(raised().kind.isProtected).isTrue()
    }

    // ---------------------------------------------------------------------------------------------
    // The "time to eat" timer
    // ---------------------------------------------------------------------------------------------

    /**
     * Its "+5 min" is the alert's own action, so the screen and the card offer it in place of a mute: that button
     * moves the timer, while a mute would only ring the same reminder again five minutes later.
     */
    @Test
    fun `the eat reminder carries plus five minutes as its action`() {
        sut.runEatReminderAlarm("Time to eat", "AAPS")

        assertThat(raised().key).isEqualTo(UiInteraction.KEY_EAT_REMINDER)
        assertThat(raised().sound).isEqualTo(AlarmSound.EAT_REMINDER)
        assertThat(raised().action).isNotNull()
        assertThat(raised().canMute).isFalse()
    }

    /** Booked, it is the same alert as raised now - with its +5 min - at the booked time (`_docs/ALARMS.md`, step 1e). */
    @Test
    fun `a booked eat reminder is the same alert at its time`() {
        assertThat(sut.scheduleEatReminderAlarm(at = 60_000L, status = "Time to eat", title = "AAPS")).isTrue()

        val (alert, at) = alerts.booked.single()
        assertThat(at).isEqualTo(60_000L)
        assertThat(alert.key).isEqualTo(UiInteraction.KEY_EAT_REMINDER)
        assertThat(alert.action).isNotNull()
        assertThat(alert.canMute).isFalse()
        assertThat(alerts.raised).isEmpty()
    }

    @Test
    fun `a booked alarm that the phone cannot book reports it`() {
        alerts.canBook = false

        assertThat(sut.scheduleAlarm(at = 60_000L, key = UiInteraction.KEY_AUTOMATION_ALARM, status = "Check BG", title = "AAPS")).isFalse()
    }

    @Test
    fun `taking back a booked alarm reaches only its key`() {
        sut.cancelScheduledAlarm(UiInteraction.KEY_EAT_REMINDER)

        assertThat(alerts.unbooked).containsExactly(UiInteraction.SOURCE to UiInteraction.KEY_EAT_REMINDER)
    }

    /** The watch rings this one by itself, so telling it as well would show two reminders for one timer. */
    @Test
    fun `the eat reminder is not sent to the watch`() {
        sut.runEatReminderAlarm("Time to eat", "AAPS")

        assertThat(raised().tellWatch).isFalse()
    }

    @Test
    fun `stopping the eat alarm silences that alert and nothing else`() {
        sut.stopEatReminderAlarm()

        assertThat(alerts.silenced).containsExactly(UiInteraction.SOURCE to UiInteraction.KEY_EAT_REMINDER)
        verify(notificationManager, never()).muteAllAlarms()
    }

    // ---------------------------------------------------------------------------------------------
    // Answering an alarm
    // ---------------------------------------------------------------------------------------------

    /** The watch, the Mute action and a swipe all reach here, and none of them can name an alarm. */
    @Test
    fun `stopping silences everything that is ringing`() {
        sut.stopAlarm("Muted from wear")

        verify(notificationManager).muteAllAlarms()
    }

    @Test
    fun `silence and close from the watch also closes the alarm screen`() {
        sut.dismissAlarm("Dismissed from wear")

        verify(notificationManager).muteAllAlarms()
        verify(eventBus).send(any<EventAlarmDismissed>())
    }

    /** Mute is a snooze of the one alert that is ringing, so a second alarm goes on ringing. */
    @Test
    fun `muting snoozes the alert that is ringing`() {
        alerts.sounding = Alert(
            kind = CoreAlert.PUMP_DRIVER_ALARM, key = "occlusion", severity = AlertSeverity.CRITICAL,
            title = TextRef.Literal("Occlusion"), raisedAt = 500L
        )

        sut.muteAlarm(5, "Mute 5 min")

        assertThat(alerts.snoozed).containsExactly(Triple(UiInteraction.SOURCE, "occlusion", 5))
        verify(notificationManager, never()).muteAllAlarms()
    }

    /**
     * A driver that still posts its own sounding card, until pump alerts reach it, leaves nothing to snooze. The
     * user gets quiet either way; what they do not get is the alarm coming back, and the log says so.
     */
    @Test
    fun `muting with no alert ringing silences everything instead`() {
        alerts.sounding = null

        sut.muteAlarm(5, "Mute 5 min")

        assertThat(alerts.snoozed).isEmpty()
        verify(notificationManager).muteAllAlarms()
    }
}
