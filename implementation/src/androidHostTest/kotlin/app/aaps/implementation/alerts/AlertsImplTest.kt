package app.aaps.implementation.alerts

import app.aaps.core.data.model.TE
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.alerts.Alert
import app.aaps.core.interfaces.alerts.AlertAction
import app.aaps.core.interfaces.alerts.AlertChoice
import app.aaps.core.interfaces.alerts.AlertKind
import app.aaps.core.interfaces.alerts.AlertKindCatalogue
import app.aaps.core.interfaces.alerts.AlertLifetime
import app.aaps.core.interfaces.alerts.AlertOrigin
import app.aaps.core.interfaces.alerts.AlertPlatform
import app.aaps.core.interfaces.alerts.AlertSeverity
import app.aaps.core.interfaces.alerts.AlertSound
import app.aaps.core.interfaces.alerts.BgAlert
import app.aaps.core.interfaces.alerts.NightscoutAlert
import app.aaps.core.interfaces.alerts.SharedSilence
import app.aaps.core.interfaces.alerts.SharedSilenceLink
import app.aaps.core.interfaces.alerts.Alerts
import app.aaps.core.interfaces.alerts.CoreAlert
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.notifications.AapsNotification
import app.aaps.core.interfaces.notifications.AlarmSound
import app.aaps.core.interfaces.notifications.AlarmSoundPlayer
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationLevel
import app.aaps.core.interfaces.notifications.SystemNotificationPlatform
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.PumpWithConcentration
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.Event
import app.aaps.core.interfaces.rx.events.EventShowSnackbar
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.BooleanKey
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
class AlertsImplTest : TestBase() {

    /** What the notification list asks of the platform: which card owns the sound now. */
    private class RecordingSystemPlatform : SystemNotificationPlatform {

        var soundingKey: Int? = null
        override fun show(notification: AapsNotification, title: String) {}
        override fun cancel(instanceKey: Int) {}
        override fun cancelAll() {}
        var breaksThrough = false
        override fun setAudibleAlarm(instanceKey: Int?, sound: AlarmSound?, breakThroughDnd: Boolean, ringtoneUri: String?) {
            soundingKey = instanceKey
            breaksThrough = breakThroughDnd
        }

        override fun onDismissed(callback: (Int) -> Unit) {}
        override fun clearLeftovers(isCurrent: (Int) -> Boolean) {}
    }

    private class RecordingAlertPlatform : AlertPlatform {

        val screens = mutableListOf<String>()
        val chimes = mutableListOf<String>()
        val chimeSounds = mutableListOf<AlarmSound?>()
        val snoozes = mutableListOf<Pair<String, Long>>()
        val withdrawals = mutableListOf<String>()
        override fun showAlarmScreen(alert: Alert) {
            screens += alert.key
        }

        val chimeRingtones = mutableListOf<String?>()
        override fun chime(alert: Alert, sound: AlarmSound, ringtoneUri: String?) {
            chimes += alert.key
            chimeSounds += sound
            chimeRingtones += ringtoneUri
        }

        override fun snoozed(alert: Alert, until: Long) {
            snoozes += alert.key to until
        }

        override fun withdrawn(source: String, key: String) {
            withdrawals += key
        }

        val bookings = mutableMapOf<String, Pair<Long, Boolean>>()
        var canBook = true

        override fun book(alert: Alert, at: Long, ring: Boolean): Boolean {
            if (canBook) bookings[alert.key] = at to ring
            return canBook
        }

        override fun unbook(source: String, key: String) {
            bookings.remove(key)
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
    private val persistenceLayer: PersistenceLayer = mock()
    private val config: Config = mock()
    private val activePlugin: ActivePlugin = mock()
    private val pump: PumpWithConcentration = mock()
    private val soundPlayer: AlarmSoundPlayer = mock()

    /** The client-control link as the test sets it, with what was sent through it. */
    private val link = object : SharedSilenceLink {
        var on = true
        val sent = mutableListOf<SharedSilence>()
        override fun available(): Boolean = on
        override fun send(silence: SharedSilence) {
            sent += silence
        }
    }
    private var storedShared = "[]"

    private var now = 1_000_000_000L
    private var storedSilences = "[]"
    private var storedChoices = "{}"
    private var storedBookings = "[]"
    private var storedPauses = "{}"
    private var storedSounds = "{}"

    private lateinit var notificationManager: CommonNotificationManager
    private lateinit var sut: AlertsImpl

    @BeforeEach
    fun setUp() {
        whenever(dateUtil.now()).thenAnswer { now }
        doAnswer { (it.arguments[0] as? TextRef.Literal)?.text ?: "text" }.whenever(rh).gs(any<TextRef>())
        whenever(rh.gs(any<TextRef>(), anyVararg())).thenReturn("text")
        whenever(preferences.get(StringNonKey.AlertSilences)).thenAnswer { storedSilences }
        doAnswer { storedSilences = it.arguments[1] as String; Unit }.whenever(preferences).put(eq(StringNonKey.AlertSilences), any<String>())
        whenever(preferences.get(StringNonKey.AlertChoices)).thenAnswer { storedChoices }
        doAnswer { storedChoices = it.arguments[1] as String; Unit }.whenever(preferences).put(eq(StringNonKey.AlertChoices), any<String>())
        whenever(preferences.get(StringNonKey.AlertBookings)).thenAnswer { storedBookings }
        doAnswer { storedBookings = it.arguments[1] as String; Unit }.whenever(preferences).put(eq(StringNonKey.AlertBookings), any<String>())
        whenever(preferences.get(StringNonKey.AlertPauses)).thenAnswer { storedPauses }
        whenever(preferences.get(StringNonKey.AlertSounds)).thenAnswer { storedSounds }
        whenever(preferences.get(StringNonKey.AlertSharedSilences)).thenAnswer { storedShared }
        doAnswer { storedShared = it.arguments[1] as String; Unit }.whenever(preferences).put(eq(StringNonKey.AlertSharedSilences), any<String>())
        doAnswer { storedSounds = it.arguments[1] as String; Unit }.whenever(preferences).put(eq(StringNonKey.AlertSounds), any<String>())
        doAnswer { storedPauses = it.arguments[1] as String; Unit }.whenever(preferences).put(eq(StringNonKey.AlertPauses), any<String>())
        // A booking is raised only after the app start
        whenever(config.appInitialized).thenReturn(true)
        whenever(activePlugin.activePump).thenReturn(pump)
        whenever(pump.model()).thenReturn(PumpType.GENERIC_AAPS)
        whenever(pump.serialNumber()).thenReturn("serial")
        // The phone that runs the loop; a client is the exception, set in its own test
        whenever(config.APS).thenReturn(true)
        // Its default on the phone that runs the loop
        whenever(preferences.get(BooleanKey.NsClientCreateAnnouncementsFromErrors)).thenReturn(true)
        notificationManager = CommonNotificationManager(aapsLogger, rh, systemPlatform, eventBus, scope)
        sut = newAlerts()
    }

    private val catalogue = object : AlertKindCatalogue {
        override val title: TextRef = TextRef.Literal("AAPS")
        override val kinds: List<AlertKind> = CoreAlert.entries
        override fun inUse(): Boolean = true
    }

    private fun newAlerts() =
        AlertsImpl(aapsLogger, notificationManager, alertPlatform, preferences, pumpSync, persistenceLayer, config, activePlugin, rh, eventBus, dateUtil, soundPlayer, { link }, scope, { setOf(catalogue) })

    /** A kind for the tests, named after the alert's key unless a test gives its own. */
    private data class TestKind(
        override val source: String,
        override val origin: AlertOrigin,
        val name: String,
        override val isProtected: Boolean = false
    ) : AlertKind {

        override val id: String get() = "$source.$name"
        override val title: TextRef get() = TextRef.Literal(name)
    }

    private fun alert(
        key: String = "occlusion",
        severity: AlertSeverity = AlertSeverity.CRITICAL,
        raisedAt: Long = 500L,
        source: String = "pump",
        origin: AlertOrigin = AlertOrigin.PUMP,
        action: AlertAction? = null,
        lifetime: AlertLifetime = AlertLifetime.CONDITION,
        kind: AlertKind = TestKind(source, origin, key)
    ) = Alert(
        kind = kind, key = key, severity = severity, title = TextRef.Literal(key), raisedAt = raisedAt,
        action = action, lifetime = lifetime
    )

    private fun verifyNoAppAnnouncement() = verifyBlocking(persistenceLayer, never()) {
        insertPumpTherapyEventIfNewByTimestamp(any(), any(), any(), any(), anyOrNull(), any())
    }

    private fun verifyNoPumpAnnouncement() = verifyBlocking(pumpSync, never()) {
        insertTherapyEventIfNewWithTimestamp(any(), any(), anyOrNull(), anyOrNull(), any(), any())
    }

    private fun cards(): List<AapsNotification> = notificationManager.notifications.value.filter { it.id == NotificationId.ALERT }

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
        sut.raise(alert(severity = AlertSeverity.WARNING))
        scope.runCurrent()

        verifyBlocking(pumpSync) {
            insertTherapyEventIfNewWithTimestamp(eq(500L), eq(TE.Type.ANNOUNCEMENT), anyOrNull(), anyOrNull(), eq(PumpType.GENERIC_AAPS), eq("serial"))
        }
        verifyNoAppAnnouncement()
    }

    /** The app's own alarm is the app's record, with its own time: not filed under the pump (`_docs/ALARMS.md`). */
    @Test
    fun `an alert of the app is announced as the app's own, not under the pump`() {
        sut.raise(alert(source = "aaps", origin = AlertOrigin.APP, key = "bolus", lifetime = AlertLifetime.EVENT))
        scope.runCurrent()

        val written = argumentCaptor<TE>()
        verifyBlocking(persistenceLayer) {
            insertPumpTherapyEventIfNewByTimestamp(written.capture(), any(), any(), eq(Sources.Aaps), anyOrNull(), any())
        }
        assertThat(written.firstValue.type).isEqualTo(TE.Type.ANNOUNCEMENT)
        assertThat(written.firstValue.timestamp).isEqualTo(500L)
        verifyNoPumpAnnouncement()
    }

    /** A client writes none: it would reach Nightscout and come back to the master as a second announcement. */
    @Test
    fun `a client writes no announcement`() {
        whenever(config.APS).thenReturn(false)

        sut.raise(alert(severity = AlertSeverity.WARNING))
        sut.raise(alert(source = "aaps", origin = AlertOrigin.APP, key = "bolus", lifetime = AlertLifetime.EVENT))
        scope.runCurrent()

        verifyNoPumpAnnouncement()
        verifyNoAppAnnouncement()
    }

    /** "Create announcements from errors" off: the user wants no announcements in Nightscout, from any alert. */
    @Test
    fun `no announcement when the user turned announcements from errors off`() {
        whenever(preferences.get(BooleanKey.NsClientCreateAnnouncementsFromErrors)).thenReturn(false)

        sut.raise(alert(severity = AlertSeverity.WARNING))
        sut.raise(alert(source = "aaps", origin = AlertOrigin.APP, key = "bolus", lifetime = AlertLifetime.EVENT))
        scope.runCurrent()

        verifyNoPumpAnnouncement()
        verifyNoAppAnnouncement()
        // The alert itself is not changed by it
        assertThat(sut.active.value.map { it.key }).containsExactly("occlusion", "bolus")
    }

    /** The loop writes the announcement of carbs required itself, under its own setting: the core writes none. */
    @Test
    fun `carbs required writes no announcement of the core`() {
        sut.raise(alert(key = CoreAlert.CARBS_REQUIRED.key, severity = AlertSeverity.WARNING, kind = CoreAlert.CARBS_REQUIRED))
        scope.runCurrent()

        verifyNoPumpAnnouncement()
        verifyNoAppAnnouncement()
    }

    /** Every phone raises its own BG alarm from the same readings: an announcement would make a client hear it twice. */
    @Test
    fun `a BG alarm writes no announcement`() {
        sut.raise(alert(key = "low", severity = AlertSeverity.WARNING, kind = BgAlert.LOW))
        scope.runCurrent()

        verifyNoPumpAnnouncement()
        verifyNoAppAnnouncement()
    }

    /** Its record is already in Nightscout: written again, it would come back as a new announcement. */
    @Test
    fun `an alert from Nightscout writes no announcement`() {
        sut.raise(alert(key = "announcement.1", severity = AlertSeverity.WARNING, lifetime = AlertLifetime.EVENT, kind = NightscoutAlert.ANNOUNCEMENT))
        scope.runCurrent()

        verifyNoPumpAnnouncement()
        verifyNoAppAnnouncement()
    }

    // ---- the user's choice per kind (`_docs/ALARMS.md`, section 6) ------------------------------

    private val occlusion = TestKind("pump", AlertOrigin.PUMP, "occlusion")

    private fun verifyPumpAnnouncement() = verifyBlocking(pumpSync) {
        insertTherapyEventIfNewWithTimestamp(eq(500L), eq(TE.Type.ANNOUNCEMENT), anyOrNull(), anyOrNull(), any(), any())
    }

    @Test
    fun `a silent kind shows a card with no sound and no alarm screen, and is still announced`() {
        sut.setChoice(occlusion, AlertChoice.SILENT)

        sut.raise(alert(kind = occlusion))
        scope.runCurrent()

        // Not the alarm tier: an URGENT card is only the card of an alert that rings
        assertThat(card().level).isEqualTo(NotificationLevel.IMPORTANT)
        assertThat(card().sound).isNull()
        assertThat(alertPlatform.screens).isEmpty()
        assertThat(sut.sounding).isNull()
        verifyPumpAnnouncement()
    }

    @Test
    fun `an alert of a kind that is off shows nothing, but stays active and is announced`() {
        sut.setChoice(occlusion, AlertChoice.OFF)

        sut.raise(alert(kind = occlusion))
        scope.runCurrent()

        assertThat(cards()).isEmpty()
        assertThat(alertPlatform.screens).isEmpty()
        // Its raiser still sees it, and resolves it as any other
        assertThat(sut.active.value.map { it.key }).containsExactly("occlusion")
        verifyPumpAnnouncement()
    }

    @Test
    fun `a protected kind rings whatever was chosen`() {
        val stopped = TestKind("pump", AlertOrigin.PUMP, "occlusion", isProtected = true)
        sut.setChoice(stopped, AlertChoice.OFF)

        sut.raise(alert(kind = stopped))

        assertThat(sut.choices.value).isEmpty()
        assertThat(card().level).isEqualTo(NotificationLevel.URGENT)
        assertThat(card().sound).isEqualTo(AlarmSound.ALARM)
        assertThat(alertPlatform.screens).containsExactly("occlusion")
    }

    @Test
    fun `a choice changed while the alert is active applies at once`() {
        sut.raise(alert(kind = occlusion))
        assertThat(card().sound).isEqualTo(AlarmSound.ALARM)

        sut.setChoice(occlusion, AlertChoice.OFF)
        assertThat(cards()).isEmpty()

        // Rings as a new alert does: sound with nothing on the screen to answer it would be worse
        sut.setChoice(occlusion, AlertChoice.RING)
        assertThat(card().sound).isEqualTo(AlarmSound.ALARM)
        assertThat(alertPlatform.screens).containsExactly("occlusion", "occlusion")
        assertThat(sut.choices.value).isEmpty()
    }

    /** As "Silence this kind" on the alarm screen does it: the alarm is answered, then the kind goes quiet. */
    @Test
    fun `an answered alert stays quiet when its kind is set back to ring`() {
        sut.raise(alert(kind = occlusion))
        sut.silence("pump", "occlusion")
        sut.setChoice(occlusion, AlertChoice.SILENT)

        sut.setChoice(occlusion, AlertChoice.RING)

        assertThat(card().sound).isNull()
        assertThat(sut.sounding).isNull()
        assertThat(alertPlatform.screens).containsExactly("occlusion")
    }

    @Test
    fun `the same choice again does not ring again`() {
        sut.raise(alert(kind = occlusion))

        sut.setChoice(occlusion, AlertChoice.RING)

        assertThat(alertPlatform.screens).containsExactly("occlusion")
    }

    @Test
    fun `the choices are kept for the next start`() {
        sut.setChoice(occlusion, AlertChoice.SILENT)

        val restarted = newAlerts()
        assertThat(restarted.choices.value).containsExactly(occlusion.id, AlertChoice.SILENT)
        restarted.raise(alert(kind = occlusion))
        assertThat(card().sound).isNull()
    }

    @Test
    fun `an info alert is not announced`() {
        sut.raise(alert(severity = AlertSeverity.INFO))
        scope.runCurrent()

        verifyNoPumpAnnouncement()
        verifyNoAppAnnouncement()
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
        assertThat(alertPlatform.snoozes).containsExactly("occlusion" to now + Alerts.SILENCE_MINUTES * 60_000L)
        assertThat(sut.active.value).hasSize(1)
    }

    /** The length the user picked on the alarm screen (`_docs/ALARMS.md`, step 1d). */
    @Test
    fun `silence lasts the length the user picked`() {
        sut.raise(alert(kind = occlusion))

        sut.silence("pump", "occlusion", 120)

        assertThat(alertPlatform.snoozes).containsExactly("occlusion" to now + 120 * 60_000L)
    }

    @Test
    fun `a protected kind is silenced for 30 minutes at most`() {
        val stopped = TestKind("pump", AlertOrigin.PUMP, "occlusion", isProtected = true)
        sut.raise(alert(kind = stopped))

        sut.silence("pump", "occlusion", 240)

        assertThat(alertPlatform.snoozes).containsExactly("occlusion" to now + Alerts.PROTECTED_SILENCE_MAX_MINUTES * 60_000L)
    }

    @Test
    fun `the alarm screen offers a protected kind only the short silences`() {
        val stopped = TestKind("pump", AlertOrigin.PUMP, "occlusion", isProtected = true)

        assertThat(Alerts.silenceLengths(occlusion)).containsExactly(15, 30, 60, 120, 240).inOrder()
        assertThat(Alerts.silenceLengths(stopped)).containsExactly(15, 30).inOrder()
        assertThat(Alerts.silenceLengths(occlusion)).contains(Alerts.SILENCE_MINUTES)
    }

    @Test
    fun `when the silence ends an alert still active rings again`() {
        sut.raise(alert())
        sut.silence("pump", "occlusion")

        now += Alerts.SILENCE_MINUTES * 60_000L
        scope.advanceTimeBy(Alerts.SILENCE_MINUTES * 60_000L + 1)

        assertThat(card().sound).isEqualTo(AlarmSound.ALARM)
        assertThat(systemPlatform.soundingKey).isEqualTo(card().instanceKey)
        assertThat(alertPlatform.screens).hasSize(2)
        assertThat(alertPlatform.withdrawals).contains("occlusion")
    }

    @Test
    fun `a platform wake ends a silence that is over, also when the timer is late`() {
        sut.raise(alert())
        sut.silence("pump", "occlusion")

        now += Alerts.SILENCE_MINUTES * 60_000L
        sut.checkSilences()

        assertThat(card().sound).isEqualTo(AlarmSound.ALARM)
    }

    @Test
    fun `a silence survives a restart and holds for the same occurrence`() {
        sut.raise(alert(raisedAt = 500L))
        sut.silence("pump", "occlusion")
        notificationManager.dismiss(NotificationId.ALERT)

        // A new process: a new instance, the same stored preferences.
        now += 60_000L
        val restarted = newAlerts()
        restarted.raise(alert(raisedAt = 500L))

        assertThat(card().sound).isNull()
        assertThat(alertPlatform.screens).hasSize(1)
    }

    @Test
    fun `a stored silence does not hold for a new occurrence`() {
        sut.raise(alert(raisedAt = 500L))
        sut.silence("pump", "occlusion")
        notificationManager.dismiss(NotificationId.ALERT)

        val restarted = newAlerts()
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
        sut.raise(alert(severity = AlertSeverity.INFO))
        scope.runCurrent()

        notificationManager.dismiss(NotificationId.ALERT)
        scope.runCurrent()

        assertThat(cards()).isEmpty()
        assertThat(sut.active.value).hasSize(1)
    }

    @Test
    fun `a warning is an important card with one sound and no alarm screen`() {
        sut.raise(alert(severity = AlertSeverity.WARNING))

        assertThat(card().level).isEqualTo(NotificationLevel.IMPORTANT)
        assertThat(card().sound).isNull()
        assertThat(alertPlatform.chimes).containsExactly("occlusion")
        assertThat(alertPlatform.screens).isEmpty()
    }

    /** On iOS the removal of each old card lost the race with the new one, and the old cards stayed in the tray. */
    @Test
    fun `the same occurrence again with nothing new does not post its card again`() {
        sut.raise(alert(severity = AlertSeverity.WARNING))
        val first = card().instanceKey

        repeat(4) { sut.raise(alert(severity = AlertSeverity.WARNING)) }

        assertThat(card().instanceKey).isEqualTo(first)
        assertThat(alertPlatform.chimes).containsExactly("occlusion")
    }

    @Test
    fun `the same occurrence with a new text posts its card again without ringing`() {
        sut.raise(alert(severity = AlertSeverity.WARNING))
        val first = card().instanceKey

        sut.raise(Alert(kind = TestKind("pump", AlertOrigin.PUMP, "occlusion"), key = "occlusion", severity = AlertSeverity.WARNING,
                        title = TextRef.Literal("occlusion"), text = TextRef.Literal("now 5 g"), raisedAt = 500L))

        assertThat(card().instanceKey).isNotEqualTo(first)
        assertThat(alertPlatform.chimes).containsExactly("occlusion")
    }

    /** The re-ring of a silenced alert is booked with its text; a new text must reach the booking (iOS showed an old BG). */
    @Test
    fun `a silenced alert with a new text books its re-ring again`() {
        val kind = TestKind("pump", AlertOrigin.PUMP, "occlusion")
        sut.raise(Alert(kind = kind, key = "occlusion", severity = AlertSeverity.WARNING, title = TextRef.Literal("occlusion"),
                        text = TextRef.Literal("BG 136"), raisedAt = 500L))
        sut.silence("pump", "occlusion", 15)
        val booked = alertPlatform.snoozes.size

        sut.raise(Alert(kind = kind, key = "occlusion", severity = AlertSeverity.WARNING, title = TextRef.Literal("occlusion"),
                        text = TextRef.Literal("BG 170"), raisedAt = 500L))

        assertThat(alertPlatform.snoozes).hasSize(booked + 1)
        assertThat(alertPlatform.snoozes.last()).isEqualTo("occlusion" to now + 15 * 60_000L)
        assertThat(alertPlatform.chimes).containsExactly("occlusion")
    }

    @Test
    fun `a silenced alert raised again with nothing new is not booked again`() {
        sut.raise(alert(severity = AlertSeverity.WARNING))
        sut.silence("pump", "occlusion", 15)
        val booked = alertPlatform.snoozes.size

        sut.raise(alert(severity = AlertSeverity.WARNING))

        assertThat(alertPlatform.snoozes).hasSize(booked)
    }

    /** iOS starts the app for a tap on a notification left from before; the tap comes before the raiser raises again. */
    @Test
    fun `a silence tapped before the alert is raised again holds when it is raised`() {
        sut.silence("pump", "occlusion", 15)

        sut.raise(alert(severity = AlertSeverity.WARNING))

        assertThat(alertPlatform.chimes).isEmpty()
        assertThat(card().sound).isNull()
        assertThat(sut.silencedUntil("pump", "occlusion")).isEqualTo(now + 15 * 60_000L)
    }

    @Test
    fun `a silence tapped long before the raise is not used`() {
        sut.silence("pump", "occlusion", 15)
        now += 3 * 60_000L

        sut.raise(alert(severity = AlertSeverity.WARNING))

        assertThat(alertPlatform.chimes).containsExactly("occlusion")
        assertThat(sut.silencedUntil("pump", "occlusion")).isEqualTo(0L)
    }

    @Test
    fun `a silence tapped before the raise is used once`() {
        sut.silence("pump", "occlusion", 15)
        sut.raise(alert(severity = AlertSeverity.WARNING))
        sut.resolve("pump", "occlusion")

        sut.raise(alert(severity = AlertSeverity.WARNING, raisedAt = 900L))

        assertThat(alertPlatform.chimes).containsExactly("occlusion")
    }

    @Test
    fun `a silence tapped before the raise of a protected kind is short`() {
        sut.silence("pump", "occlusion", 240)

        sut.raise(alert(severity = AlertSeverity.WARNING, kind = TestKind("pump", AlertOrigin.PUMP, "occlusion", isProtected = true)))

        assertThat(sut.silencedUntil("pump", "occlusion")).isEqualTo(now + Alerts.PROTECTED_SILENCE_MAX_MINUTES * 60_000L)
    }

    /** Named by its level, a warning reached the phone's tray and the watch as "Info". */
    @Test
    fun `the card is announced with the alert's own title`() {
        sut.raise(alert(key = "low", severity = AlertSeverity.WARNING))

        assertThat(card().alarmTitle).isEqualTo("low")
    }

    /** The system notification shows the title above its text: the text must not start with it again. */
    @Test
    fun `the system notification gets the alert's text alone`() {
        sut.raise(Alert(kind = TestKind("pump", AlertOrigin.PUMP, "low"), key = "low", severity = AlertSeverity.WARNING,
                        title = TextRef.Literal("Low"), text = TextRef.Literal("BG 67 mg/dL"), raisedAt = 500L))

        assertThat(card().systemBody).isEqualTo("BG 67 mg/dL")
        assertThat(card().text).isNotEqualTo("BG 67 mg/dL")
    }

    @Test
    fun `an alert with no text has an empty system notification text`() {
        sut.raise(alert(key = "low", severity = AlertSeverity.WARNING))

        assertThat(card().systemBody).isEmpty()
    }

    @Test
    fun `a title the caller gave is kept`() {
        sut.raise(
            Alert(
                kind = TestKind("aaps", AlertOrigin.APP, "bolus"), key = "bolus", severity = AlertSeverity.CRITICAL,
                title = TextRef.Literal("bolus failed"), raisedAt = 500L, notificationTitle = "AAPS"
            )
        )

        assertThat(card().alarmTitle).isEqualTo("AAPS")
    }

    @Test
    fun `an info alert is a normal card with no sound`() {
        sut.raise(alert(severity = AlertSeverity.INFO))

        assertThat(card().level).isEqualTo(NotificationLevel.NORMAL)
        assertThat(alertPlatform.chimes).isEmpty()
        assertThat(alertPlatform.screens).isEmpty()
    }

    @Test
    fun `the card button runs the action and silences the alert`() {
        var runs = 0
        sut.raise(alert(action = AlertAction(TextRef.Literal("Clear")) { runs++; true }))

        card().actions.single().action()
        scope.runCurrent()

        assertThat(runs).isEqualTo(1)
        assertThat(card().sound).isNull()
        verify(eventBus, never()).send(any<EventShowSnackbar>())
    }

    @Test
    fun `an action the pump did not confirm says so`() {
        sut.raise(alert(action = AlertAction(TextRef.Literal("Clear")) { false }))

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
        sut.raise(alert(key = "bolus-error", source = "aaps", lifetime = AlertLifetime.EVENT))

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
        sut.raise(alert(key = "bolus-error", source = "aaps", lifetime = AlertLifetime.EVENT))

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
        sut.raise(alert(key = "bolus-error", source = "aaps", lifetime = AlertLifetime.EVENT))
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
        sut.raise(alert(key = "eat-reminder", source = "aaps", lifetime = AlertLifetime.EVENT, action = AlertAction(TextRef.Literal("+5 min")) { true }))

        sut.act("aaps", "eat-reminder")
        scope.runCurrent()

        assertThat(sut.active.value).isEmpty()
    }

    @Test
    fun `an event action that fails leaves the alert ringing`() {
        sut.raise(alert(key = "eat-reminder", source = "aaps", lifetime = AlertLifetime.EVENT, action = AlertAction(TextRef.Literal("+5 min")) { false }))

        sut.act("aaps", "eat-reminder")
        scope.runCurrent()

        assertThat(sut.active.value.map { it.key }).containsExactly("eat-reminder")
        assertThat(card().sound).isEqualTo(AlarmSound.ALARM)
    }

    /** A pump's alert is silenced while its step runs: the pump takes its time, and the driver resolves it. */
    @Test
    fun `a condition action silences the alert while it runs`() {
        sut.raise(alert(action = AlertAction(TextRef.Literal("Deactivate")) { true }))

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

    /** A BG alarm takes its silence from its rule: a silence with no length picked uses it (_docs/ALARMS.md, step 2). */
    @Test
    fun `a silence with no length picked uses the alert's own`() {
        sut.raise(Alert(kind = occlusion, key = "occlusion", severity = AlertSeverity.CRITICAL, title = TextRef.Literal("low"), raisedAt = 500L, silenceMinutes = 15))

        sut.silence("pump", "occlusion")

        assertThat(alertPlatform.snoozes).containsExactly("occlusion" to now + 15 * 60_000L)
    }

    /** Urgent low rings in Do Not Disturb whatever the user's override says: the platform is told so. */
    @Test
    fun `an alert that breaks through Do Not Disturb tells the sound owner`() {
        sut.raise(Alert(kind = occlusion, key = "occlusion", severity = AlertSeverity.CRITICAL, title = TextRef.Literal("low"), raisedAt = 500L, breakThroughDnd = true))

        assertThat(systemPlatform.breaksThrough).isTrue()
    }

    // ---- booked alerts (`_docs/ALARMS.md`, step 1e) ---------------------------------------------

    /** An automation reminder: a kind the catalogue knows, so a booking read back finds it again. */
    private fun reminder() = alert(
        key = "automation-timer", source = "aaps", origin = AlertOrigin.APP, lifetime = AlertLifetime.EVENT, kind = CoreAlert.AUTOMATION_ALARM
    )

    private val minute = 60_000L

    @Test
    fun `a booked alert rings at its time and not before`() {
        assertThat(sut.schedule(reminder(), now + minute)).isTrue()
        assertThat(alertPlatform.bookings).containsKey("automation-timer")

        scope.advanceTimeBy(minute / 2)
        scope.runCurrent()
        assertThat(cards()).isEmpty()

        now += minute
        scope.advanceTimeBy(minute / 2 + 1)
        scope.runCurrent()

        assertThat(card().sound).isEqualTo(AlarmSound.ALARM)
        assertThat(alertPlatform.screens).containsExactly("automation-timer")
        // The time of the booking is the time of the occurrence, also for its announcement
        assertThat(sut.active.value.single().raisedAt).isEqualTo(now)
        assertThat(alertPlatform.bookings).isEmpty()
    }

    @Test
    fun `a booking taken back does not ring`() {
        sut.schedule(reminder(), now + minute)

        sut.unschedule("aaps", "automation-timer")
        now += minute
        scope.advanceTimeBy(minute + 1)
        scope.runCurrent()

        assertThat(cards()).isEmpty()
        assertThat(alertPlatform.bookings).isEmpty()
        assertThat(storedBookings).isEqualTo("[]")
    }

    /** The process ended before the time; the platform's alarm starts a new one, which finds the saved booking. */
    @Test
    fun `a booking rings also after the process ended`() {
        sut.schedule(reminder(), now + minute)

        val restarted = newAlerts()
        now += minute
        restarted.fireDue()
        scope.runCurrent()

        assertThat(card().level).isEqualTo(NotificationLevel.URGENT)
        assertThat(restarted.active.value.single().key).isEqualTo("automation-timer")
    }

    /** The phone was off: a reminder for something long past is no use. */
    @Test
    fun `a booking found long after its time is dropped`() {
        sut.schedule(reminder(), now + minute)

        val restarted = newAlerts()
        now += minute + (Alerts.LATE_LIMIT_MINUTES + 1) * minute
        restarted.fireDue()
        scope.runCurrent()

        assertThat(cards()).isEmpty()
        assertThat(storedBookings).isEqualTo("[]")
    }

    @Test
    fun `a booking the phone cannot book is not kept`() {
        alertPlatform.canBook = false

        assertThat(sut.schedule(reminder(), now + minute)).isFalse()

        assertThat(storedBookings).isEqualTo("[]")
    }

    /** iOS rings a booking by itself; a kind the user made silent must not. */
    @Test
    fun `a booking of a silent kind books no sound`() {
        sut.setChoice(CoreAlert.AUTOMATION_ALARM, AlertChoice.SILENT)

        sut.schedule(reminder(), now + minute)

        assertThat(alertPlatform.bookings["automation-timer"]).isEqualTo((now + minute) to false)
    }

    // ---- pauses (step 2d) ---------------------------------------------------------------------------

    @Test
    fun `a paused kind shows its card with no sound and no alarm screen`() {
        val a = alert()
        sut.pause(listOf(a.kind), now + 60 * 60_000L)

        sut.raise(a)

        assertThat(card().sound).isNull()
        assertThat(card().level).isEqualTo(NotificationLevel.IMPORTANT)
        assertThat(alertPlatform.screens).isEmpty()
        // The end of the pause is booked: it rings then, also when the app does not run
        assertThat(alertPlatform.snoozes).containsExactly("occlusion" to now + 60 * 60_000L)
    }

    @Test
    fun `pausing a ringing kind quiets it at once`() {
        val a = alert()
        sut.raise(a)

        sut.pause(listOf(a.kind), now + 60 * 60_000L)

        assertThat(card().sound).isNull()
        assertThat(sut.sounding).isNull()
    }

    @Test
    fun `when the pause ends what is still on rings`() {
        val a = alert()
        sut.pause(listOf(a.kind), now + 60 * 60_000L)
        sut.raise(a)

        now += 60 * 60_000L
        sut.checkSilences()

        assertThat(card().sound).isEqualTo(AlarmSound.ALARM)
        assertThat(alertPlatform.screens).containsExactly("occlusion")
        assertThat(sut.pauses.value).isEmpty()
    }

    @Test
    fun `resume rings at once`() {
        val a = alert()
        sut.pause(listOf(a.kind), now + 60 * 60_000L)
        sut.raise(a)

        sut.resume(listOf(a.kind))

        assertThat(alertPlatform.screens).containsExactly("occlusion")
        assertThat(sut.pauses.value).isEmpty()
    }

    /** A silence the user gave holds after the pause: the pause end does not ring over it. */
    @Test
    fun `a silence longer than the pause still holds when the pause ends`() {
        val a = alert()
        sut.raise(a)
        sut.silence("pump", "occlusion", 120)
        sut.pause(listOf(a.kind), now + 60 * 60_000L)

        now += 60 * 60_000L
        sut.checkSilences()

        assertThat(card().sound).isNull()
        assertThat(alertPlatform.screens).containsExactly("occlusion")
    }

    @Test
    fun `a protected kind is never paused`() {
        val a = alert(kind = TestKind("pump", AlertOrigin.PUMP, "occlusion", isProtected = true))
        sut.pause(listOf(a.kind), now + 60 * 60_000L)

        sut.raise(a)

        assertThat(sut.pauses.value).isEmpty()
        assertThat(card().sound).isEqualTo(AlarmSound.ALARM)
    }

    @Test
    fun `a pause survives a restart, an ended one does not`() {
        val a = alert()
        sut.pause(listOf(a.kind), now + 60 * 60_000L)

        val restarted = newAlerts()
        assertThat(restarted.pauses.value).containsExactly(a.kind.id, now + 60 * 60_000L)

        now += 61 * 60_000L
        assertThat(newAlerts().pauses.value).isEmpty()
    }

    @Test
    fun `silencedUntil tells the end of a silence, and 0 when there is none`() {
        sut.raise(alert())
        assertThat(sut.silencedUntil("pump", "occlusion")).isEqualTo(0L)

        sut.silence("pump", "occlusion", 60)

        assertThat(sut.silencedUntil("pump", "occlusion")).isEqualTo(now + 60 * 60_000L)
        assertThat(sut.silencedUntil("pump", "other")).isEqualTo(0L)
    }

    // ---- sounds and the test (step 2e) --------------------------------------------------------------

    @Test
    fun `a kind's chosen sound rings instead of the alert's own`() {
        val a = alert()
        sut.setSound(a.kind, AlertSound.Bundled(AlarmSound.URGENT_ALARM))

        sut.raise(a)

        assertThat(card().sound).isEqualTo(AlarmSound.URGENT_ALARM)
    }

    @Test
    fun `a warning chimes with the chosen sound`() {
        val a = alert(severity = AlertSeverity.WARNING)
        sut.setSound(a.kind, AlertSound.Bundled(AlarmSound.ERROR))

        sut.raise(a)

        assertThat(alertPlatform.chimeSounds).containsExactly(AlarmSound.ERROR)
    }

    @Test
    fun `the sound choice survives a restart, and null brings the alert's own back`() {
        val a = alert()
        sut.setSound(a.kind, AlertSound.Bundled(AlarmSound.ERROR))
        assertThat(newAlerts().sounds.value).containsExactly(a.kind.id, AlertSound.Bundled(AlarmSound.ERROR))

        sut.setSound(a.kind, null)
        sut.raise(a)

        assertThat(card().sound).isEqualTo(AlarmSound.ALARM)
    }

    @Test
    fun `the test plays the alarm sound for a few seconds`() {
        val kind = TestKind("pump", AlertOrigin.PUMP, "occlusion")
        sut.setSound(kind, AlertSound.Bundled(AlarmSound.BOLUS_ERROR))

        assertThat(sut.test(kind, AlertSeverity.CRITICAL)).isTrue()

        verify(soundPlayer).play(eq(AlarmSound.BOLUS_ERROR), any(), eq(false), eq(null))
        scope.advanceTimeBy(Alerts.TEST_SECONDS * 1000L + 1)
        verify(soundPlayer).stop(any())
        // A test is not an alert: no card, no announcement
        assertThat(cards()).isEmpty()
        verifyNoPumpAnnouncement()
    }

    @Test
    fun `the test of a warning chimes`() {
        val kind = TestKind("pump", AlertOrigin.PUMP, "occlusion")

        sut.test(kind, AlertSeverity.WARNING)

        assertThat(alertPlatform.chimeSounds).containsExactly(AlarmSound.ALARM)
    }

    /** The player plays one sound at a time: a test while an alarm rings would stop the alarm. */
    @Test
    fun `no test while an alarm is sounding`() {
        sut.raise(alert())

        assertThat(sut.test(TestKind("pump", AlertOrigin.PUMP, "other"), AlertSeverity.CRITICAL)).isFalse()

        verify(soundPlayer, never()).play(any(), any(), any(), anyOrNull())
    }

    // ---- a phone ringtone (step 2e-2) ---------------------------------------------------------------

    private val ringtone = AlertSound.Ringtone("content://media/internal/audio/media/42", "Beep")

    @Test
    fun `a chosen ringtone rings, with the alert's own sound as its fallback`() {
        val a = alert()
        sut.setSound(a.kind, ringtone)

        sut.raise(a)

        assertThat(card().ringtoneUri).isEqualTo(ringtone.uri)
        assertThat(card().sound).isEqualTo(AlarmSound.ALARM)
    }

    @Test
    fun `a warning chimes with the chosen ringtone`() {
        val a = alert(severity = AlertSeverity.WARNING)
        sut.setSound(a.kind, ringtone)

        sut.raise(a)

        assertThat(alertPlatform.chimeRingtones).containsExactly(ringtone.uri)
    }

    @Test
    fun `a ringtone choice survives a restart`() {
        val a = alert()
        sut.setSound(a.kind, ringtone)

        assertThat(newAlerts().sounds.value).containsExactly(a.kind.id, ringtone)
    }

    @Test
    fun `the test plays the chosen ringtone`() {
        val kind = TestKind("pump", AlertOrigin.PUMP, "occlusion")
        sut.setSound(kind, ringtone)

        sut.test(kind, AlertSeverity.CRITICAL)

        verify(soundPlayer).play(eq(AlarmSound.ALARM), any(), eq(false), eq(ringtone.uri))
    }

    @Test
    fun `an unreadable stored sound is left out, the others stay`() {
        storedSounds = """{"a":"NO_SUCH_SOUND","b":"ERROR","c":{"uri":"u"}}"""

        assertThat(newAlerts().sounds.value).containsExactly("b", AlertSound.Bundled(AlarmSound.ERROR))
    }

    // ---- the silence everywhere (step 2f) -----------------------------------------------------------

    private fun bgLow(raisedAt: Long = 500L) = alert(key = "low", source = BgAlert.SOURCE, origin = AlertOrigin.APP, raisedAt = raisedAt, kind = BgAlert.LOW)

    @Test
    fun `on the master a silence everywhere silences here and is kept for the clients`() {
        sut.raise(bgLow())

        sut.silenceEverywhere(BgAlert.SOURCE, "low", 60)

        assertThat(card().sound).isNull()
        val shared = SharedSilence.parse(storedShared).single()
        assertThat(shared.until).isEqualTo(now + 60 * 60_000L)
        assertThat(shared.raisedAt).isEqualTo(500L)
        assertThat(link.sent).isEmpty()
    }

    /** Here first, never waiting for the way round: a client out of reach must still stop ringing. */
    @Test
    fun `on a client a silence everywhere silences here at once and is sent to the master`() {
        whenever(config.AAPSCLIENT).thenReturn(true)
        sut.raise(bgLow())

        sut.silenceEverywhere(BgAlert.SOURCE, "low", 60)

        assertThat(card().sound).isNull()
        assertThat(link.sent.single().until).isEqualTo(now + 60 * 60_000L)
        assertThat(storedShared).isEqualTo("[]")
    }

    @Test
    fun `only a BG alarm can be silenced everywhere, and only with the link on`() {
        assertThat(sut.canSilenceEverywhere(bgLow())).isTrue()
        assertThat(sut.canSilenceEverywhere(alert())).isFalse()

        link.on = false

        assertThat(sut.canSilenceEverywhere(bgLow())).isFalse()
    }

    @Test
    fun `a silence from another phone silences the same alarm here`() {
        sut.raise(bgLow())

        sut.applySharedSilence(SharedSilence(BgAlert.SOURCE, "low", raisedAt = 500L, until = now + 30 * 60_000L, sentAt = now))

        assertThat(card().sound).isNull()
        assertThat(sut.silencedUntil(BgAlert.SOURCE, "low")).isEqualTo(now + 30 * 60_000L)
    }

    /** The master sends every silence back in its doc, also the one this phone gave: that echo changes nothing. */
    @Test
    fun `the echo of this phone's own silence changes nothing`() {
        sut.raise(bgLow())
        sut.silence(BgAlert.SOURCE, "low", 60)
        val snoozes = alertPlatform.snoozes.size

        sut.applySharedSilence(SharedSilence(BgAlert.SOURCE, "low", raisedAt = 500L, until = now + 30 * 60_000L, sentAt = now))

        assertThat(sut.silencedUntil(BgAlert.SOURCE, "low")).isEqualTo(now + 60 * 60_000L)
        assertThat(alertPlatform.snoozes).hasSize(snoozes)
    }

    @Test
    fun `a silence from a phone whose clock is far ahead is refused`() {
        sut.raise(bgLow())

        sut.applySharedSilence(SharedSilence(BgAlert.SOURCE, "low", raisedAt = 500L, until = now + 30 * 60_000L, sentAt = now + 10 * 60_000L))

        assertThat(sut.silencedUntil(BgAlert.SOURCE, "low")).isEqualTo(0L)
    }

    /** The readings may come to this phone a moment later: the silence waits for the alarm it is for. */
    @Test
    fun `a silence for an alarm not raised here yet holds when it is raised`() {
        sut.applySharedSilence(SharedSilence(BgAlert.SOURCE, "low", raisedAt = 500L, until = now + 30 * 60_000L, sentAt = now))

        sut.raise(bgLow())

        assertThat(card().sound).isNull()
        assertThat(alertPlatform.screens).isEmpty()
    }

    @Test
    fun `a waiting silence does not silence a later occurrence`() {
        sut.applySharedSilence(SharedSilence(BgAlert.SOURCE, "low", raisedAt = 500L, until = now + 30 * 60_000L, sentAt = now))

        sut.raise(bgLow(raisedAt = 500L + 60 * 60_000L))

        assertThat(alertPlatform.screens).containsExactly("low")
    }

    @Test
    fun `urgent low is silenced from another phone only briefly`() {
        sut.raise(alert(key = "low", source = BgAlert.SOURCE, origin = AlertOrigin.APP, kind = BgAlert.URGENT_LOW))

        sut.applySharedSilence(SharedSilence(BgAlert.SOURCE, "low", raisedAt = 500L, until = now + 120 * 60_000L, sentAt = now))

        assertThat(sut.silencedUntil(BgAlert.SOURCE, "low")).isEqualTo(now + Alerts.PROTECTED_SILENCE_MAX_MINUTES * 60_000L)
    }

    @Test
    fun `the master's list keeps one silence per alarm and drops the ones that are over`() {
        val first = SharedSilence(BgAlert.SOURCE, "low", raisedAt = 1L, until = now + 60_000L, sentAt = now)
        val over = SharedSilence(BgAlert.SOURCE, "high", raisedAt = 1L, until = now - 1L, sentAt = now)
        val second = first.copy(until = now + 120_000L)

        assertThat(SharedSilence.merge(listOf(first, over), second, now)).containsExactly(second)
    }
}