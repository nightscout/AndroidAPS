package app.aaps.implementation.ui

import app.aaps.core.interfaces.alerts.EatReminder
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.notifications.AlarmSound
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.pump.alerts.PumpAlert
import app.aaps.core.interfaces.pump.alerts.PumpAlertLifetime
import app.aaps.core.interfaces.pump.alerts.PumpAlertSeverity
import app.aaps.core.interfaces.pump.alerts.PumpAlerts
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventAlarmDismissed
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever

/**
 * The app's own alarms, as alerts.
 *
 * What is worth pinning here is the shape of the alert `runAlarm` builds, because everything the user sees follows
 * from it: the lifetime decides whether answering the alarm ends it, the title and the text decide what the alarm
 * screen and the notification say, and the two watch flags decide what the watch shows. All of it used to be spread
 * across 150 lines of Android-only code in `:app`; see `_docs/PUMP_DESIGN.md`, section 8.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UiInteractionImplTest : TestBase() {

    /** Records what was raised and what was asked of the alerts, as the core would. */
    private class RecordingAlerts : PumpAlerts {

        private val _active = MutableStateFlow<List<PumpAlert>>(emptyList())
        override val active: StateFlow<List<PumpAlert>> = _active
        override var sounding: PumpAlert? = null
        val raised = mutableListOf<PumpAlert>()
        val silenced = mutableListOf<Pair<String, String>>()
        val snoozed = mutableListOf<Triple<String, String, Int>>()

        override fun raise(alert: PumpAlert) {
            raised += alert
            _active.value = _active.value.filterNot { it.source == alert.source && it.key == alert.key } + alert
        }

        override fun resolve(source: String, key: String) {
            _active.value = _active.value.filterNot { it.source == source && it.key == key }
        }

        override fun resolveAll(source: String) {
            _active.value = _active.value.filterNot { it.source == source }
        }

        override fun silence(source: String, key: String) {
            silenced += source to key
        }

        override fun snooze(source: String, key: String, minutes: Int) {
            snoozed += Triple(source, key, minutes)
        }

        override fun act(source: String, key: String) {}
    }

    private val scope = TestScope(StandardTestDispatcher())
    private val alerts = RecordingAlerts()
    private val notificationManager: NotificationManager = mock()
    private val persistenceLayer: PersistenceLayer = mock()
    private val config: Config = mock()
    private val dateUtil: DateUtil = mock()
    private val eventBus: RxBus = mock()
    private val eatReminder: EatReminder = mock()

    private lateinit var sut: UiInteractionImpl

    @BeforeEach
    fun setUp() {
        whenever(dateUtil.now()).thenReturn(1_000L)
        whenever(config.APS).thenReturn(true)
        sut = UiInteractionImpl(aapsLogger, alerts, { notificationManager }, persistenceLayer, config, dateUtil, eventBus, { eatReminder }, scope)
    }

    private fun raised(): PumpAlert = alerts.raised.single()

    // ---------------------------------------------------------------------------------------------
    // The alert an alarm becomes
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `an alarm is a critical event alert under the app's own source`() {
        sut.runAlarm("bolus", "Bolus failed", "AAPS", AlarmSound.BOLUS_ERROR)

        assertThat(raised().source).isEqualTo(UiInteraction.SOURCE)
        assertThat(raised().key).isEqualTo("bolus")
        assertThat(raised().severity).isEqualTo(PumpAlertSeverity.CRITICAL)
        assertThat(raised().lifetime).isEqualTo(PumpAlertLifetime.EVENT)
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

    @Test
    fun `an alarm is written as an announcement on a loop build`() {
        sut.runAlarm("bolus", "Bolus failed", "AAPS")
        scope.runCurrent()

        verifyBlocking(persistenceLayer) {
            insertPumpTherapyEventIfNewByTimestamp(any(), any(), any(), anyOrNull(), anyOrNull(), any())
        }
    }

    /** A follower records nothing: the announcement belongs to the phone that runs the loop. */
    @Test
    fun `a follower writes no announcement`() {
        whenever(config.APS).thenReturn(false)

        sut.runAlarm("bolus", "Bolus failed", "AAPS")
        scope.runCurrent()

        verifyBlocking(persistenceLayer, never()) {
            insertPumpTherapyEventIfNewByTimestamp(any(), any(), any(), anyOrNull(), anyOrNull(), any())
        }
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
        alerts.sounding = PumpAlert(
            source = "pump", key = "occlusion", severity = PumpAlertSeverity.CRITICAL,
            title = TextRef.Literal("Occlusion"), raisedAt = 500L
        )

        sut.muteAlarm(5, "Mute 5 min")

        assertThat(alerts.snoozed).containsExactly(Triple("pump", "occlusion", 5))
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
