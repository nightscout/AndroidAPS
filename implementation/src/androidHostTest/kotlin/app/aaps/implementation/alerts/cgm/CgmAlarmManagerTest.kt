package app.aaps.implementation.alerts.cgm

import app.aaps.core.data.model.GV
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.alerts.CgmAlarmGroup
import app.aaps.core.interfaces.alerts.CgmAlarmType
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.notifications.AlarmAction
import app.aaps.core.interfaces.notifications.AlarmSound
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.rx.events.EventMobileToWear
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.core.interfaces.source.BgSource
import app.aaps.core.interfaces.source.CgmSensorAlertProvider
import app.aaps.core.interfaces.source.CgmSensorAlertState
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.keys.interfaces.AppPlatform
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.implementation.alerts.keys.LocalAlertLongKey
import app.aaps.implementation.maintenance.migration.FileKeyValueStore
import app.aaps.implementation.sharedPreferences.PreferencesImpl
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito.mockingDetails
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * [CgmAlarmManager] with real preferences (in memory), so the stored alarm state, the acknowledge
 * and the mute are the real ones. The rules themselves are in `CgmAlarmEngineTest`.
 *
 * The glucose alarms read mg/dL levels also on an mmol phone, and stale data raises no glucose alarm.
 */
class CgmAlarmManagerTest : TestBase() {

    @Mock lateinit var rh: TextResolver
    @Mock lateinit var activePlugin: ActivePlugin
    @Mock lateinit var profileUtil: ProfileUtil
    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var dateUtil: DateUtil
    @Mock lateinit var notificationManager: NotificationManager
    @Mock lateinit var bgSource: BgSource

    private lateinit var preferences: PreferencesImpl
    private lateinit var scope: CoroutineScope
    private lateinit var sut: CgmAlarmManager
    private val toWatch = mutableListOf<EventData>()

    private var now = 100_000_000_000L

    @BeforeEach
    fun setup() {
        val config = mock<Config>()
        whenever(config.APS).thenReturn(true)
        whenever(config.platform).thenReturn(AppPlatform.Android)
        preferences = PreferencesImpl(FileKeyValueStore(), { profileUtil }, { mock() }, { mock() }, mock(), config, mock())
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        whenever(dateUtil.now()).thenAnswer { now }
        whenever(dateUtil.timeString(any())).thenReturn("12:00")
        whenever(dateUtil.dateAndTimeString(any())).thenReturn("1.1. 12:00")
        whenever(rh.gs(any<TextRef>())).thenReturn("text")
        whenever(profileUtil.fromMgdlToStringWithUnits(anyOrNull())).thenReturn("value")
        whenever(activePlugin.activeBgSource).thenReturn(bgSource)
        scope.launch { rxBus.toFlow(EventMobileToWear::class).collect { toWatch += it.payload } }
        sut = newManager()
    }

    @AfterEach
    fun tearDown() {
        scope.cancel()
    }

    private fun newManager() = CgmAlarmManager(
        aapsLogger, preferences, rh, activePlugin, profileUtil, persistenceLayer, dateUtil, notificationManager, rxBus, scope
    )

    private fun gv(value: Double, timestamp: Long = now) =
        GV(timestamp = timestamp, raw = null, value = value, trendArrow = TrendArrow.NONE, noise = null, sourceSensor = SourceSensor.UNKNOWN)

    private suspend fun lastReading(value: Double, timestamp: Long = now) {
        whenever(persistenceLayer.getLastGlucoseValue()).thenReturn(gv(value, timestamp))
        whenever(persistenceLayer.getBgReadingsDataFromTime(any(), eq(true))).thenReturn(listOf(gv(value, timestamp)))
    }

    /** How often [id] was posted. Counts every `post` overload, so the test does not depend on which one. */
    private fun posts(id: NotificationId): Int =
        mockingDetails(notificationManager).invocations.count { it.method.name == "post" && it.arguments[0] == id }

    private fun dismisses(id: NotificationId): Int =
        mockingDetails(notificationManager).invocations.count { it.method.name == "dismiss" && it.arguments[0] == id }

    /** The sound the last post of [id] carried. */
    private fun lastSound(id: NotificationId): AlarmSound? =
        mockingDetails(notificationManager).invocations.last { it.method.name == "post" && it.arguments[0] == id }
            .arguments.filterIsInstance<AlarmSound>().firstOrNull()

    @Test
    fun theNewGlucoseAlarmsAreOffByDefault() {
        assertThat(BooleanKey.AlertUrgentLow.defaultValue).isFalse()
        assertThat(BooleanKey.AlertUrgentLowSoon.defaultValue).isFalse()
        assertThat(BooleanKey.AlertRapidRise.defaultValue).isFalse()
        assertThat(BooleanKey.AlertHypo.defaultValue).isFalse()
        assertThat(BooleanKey.AlertHyper.defaultValue).isFalse()
        assertThat(BooleanKey.AlertRapidFall.defaultValue).isFalse()
    }

    @Test
    fun nothingRingsWithTheDefaults() = runTest {
        lastReading(40.0)
        sut.check()
        assertThat(mockingDetails(notificationManager).invocations.count { it.method.name == "post" }).isEqualTo(0)
    }

    @Test
    fun urgentLowRingsWithItsSoundAndTellsTheWatch() = runTest {
        preferences.put(BooleanKey.AlertUrgentLow, true)
        lastReading(52.0)
        sut.check()
        assertThat(posts(NotificationId.CGM_URGENT_LOW)).isEqualTo(1)
        assertThat(lastSound(NotificationId.CGM_URGENT_LOW)).isEqualTo(AlarmSound.URGENT_ALARM)
        val watch = toWatch.filterIsInstance<EventData.CgmAlarm>().single()
        assertThat(watch.id).isEqualTo("urgent_low")
        assertThat(watch.urgent).isTrue()
    }

    @Test
    fun aChosenTrioSoundIsUsed() = runTest {
        preferences.put(BooleanKey.AlertHypo, true)
        preferences.put(StringKey.AlertHypoSound, "trio_electronic_lg_medium")
        lastReading(65.0)
        sut.check()
        assertThat(lastSound(NotificationId.BG_HYPO)).isEqualTo(AlarmSound.TRIO_ELECTRONIC_LOW_MEDIUM)
    }

    @Test
    fun theStoredValueRaisesTheLowAlarm() = runTest {
        preferences.put(BooleanKey.AlertHypo, true)
        preferences.put(UnitDoubleKey.AlertHypoThreshold, 60.0)
        lastReading(56.0)
        sut.check()
        assertThat(posts(NotificationId.BG_HYPO)).isEqualTo(1)
    }

    @Test // Field report 2026-08-06: "high glucose" at 5.5 mmol/L on an mmol phone.
    fun theLevelsAreMgdlAlsoOnAnMmolPhone() = runTest {
        preferences.put(BooleanKey.AlertHyper, true)
        preferences.put(BooleanKey.AlertHypo, true)
        preferences.put(UnitDoubleKey.AlertHyperThreshold, 250.0)
        preferences.put(UnitDoubleKey.AlertHypoThreshold, 70.0)
        // The display getter would turn 250 into 13.9 and 70 into 3.9.
        whenever(profileUtil.valueInCurrentUnitsDetect(any())).thenAnswer { (it.arguments[0] as Double) / 18.0 }
        lastReading(100.0)
        sut.check()
        assertThat(posts(NotificationId.BG_HYPER)).isEqualTo(0)
        lastReading(68.0)
        sut.check()
        assertThat(posts(NotificationId.BG_HYPO)).isEqualTo(1)
    }

    @Test
    fun theFallIsMeasuredOverTheWindow() = runTest {
        preferences.put(BooleanKey.AlertRapidFall, true)
        preferences.put(IntKey.AlertRapidFallDrop, 30)
        preferences.put(IntKey.AlertRapidFallWindow, 15)
        val start = now - T.mins(15).msecs()
        whenever(persistenceLayer.getLastGlucoseValue()).thenReturn(gv(125.0, now))
        whenever(persistenceLayer.getBgReadingsDataFromTime(any(), eq(true))).thenReturn(listOf(gv(150.0, start), gv(125.0, now)))
        sut.check()
        // A drop of 25 is less than 30.
        assertThat(posts(NotificationId.BG_RAPID_FALL)).isEqualTo(0)
        whenever(persistenceLayer.getLastGlucoseValue()).thenReturn(gv(115.0, now))
        whenever(persistenceLayer.getBgReadingsDataFromTime(any(), eq(true))).thenReturn(listOf(gv(150.0, start), gv(115.0, now)))
        sut.check()
        // A drop of 35.
        assertThat(posts(NotificationId.BG_RAPID_FALL)).isEqualTo(1)
    }

    @Test
    fun anOldReadingRaisesNoGlucoseAlarm() = runTest {
        preferences.put(BooleanKey.AlertUrgentLow, true)
        preferences.put(BooleanKey.AlertHypo, true)
        lastReading(40.0, timestamp = now - T.mins(45).msecs())
        sut.check()
        assertThat(posts(NotificationId.CGM_URGENT_LOW)).isEqualTo(0)
        assertThat(posts(NotificationId.BG_HYPO)).isEqualTo(0)
    }

    @Test
    fun aSecondCheckRightAfterDoesNotPostAgain() = runTest {
        preferences.put(BooleanKey.AlertHypo, true)
        lastReading(60.0)
        sut.check()
        sut.check()
        assertThat(posts(NotificationId.BG_HYPO)).isEqualTo(1)
    }

    @Test
    fun withoutAcknowledgeTheAlarmComesBack() = runTest {
        preferences.put(BooleanKey.AlertHypo, true)
        lastReading(60.0)
        sut.check()
        now += CgmAlarmEngine.REMINDER_MS
        lastReading(60.0)
        sut.check()
        assertThat(posts(NotificationId.BG_HYPO)).isEqualTo(2)
    }

    @Test
    fun acknowledgeStopsItForItsRepeatTimeAndCancelsTheWatch() = runTest {
        preferences.put(BooleanKey.AlertHypo, true)
        preferences.put(IntKey.AlertHypoRepeat, 15)
        lastReading(60.0)
        sut.check()
        sut.acknowledge(CgmAlarmType.LOW)
        assertThat(dismisses(NotificationId.BG_HYPO)).isEqualTo(1)
        assertThat(toWatch.filterIsInstance<EventData.CgmAlarmCancel>().map { it.id }).containsExactly("low")
        now += T.mins(10).msecs()
        lastReading(60.0)
        sut.check()
        assertThat(posts(NotificationId.BG_HYPO)).isEqualTo(1)
        now += T.mins(5).msecs()
        lastReading(60.0)
        sut.check()
        assertThat(posts(NotificationId.BG_HYPO)).isEqualTo(2)
    }

    @Test
    fun theAcknowledgeSurvivesARestart() = runTest {
        preferences.put(BooleanKey.AlertHypo, true)
        lastReading(60.0)
        sut.check()
        sut.acknowledge(null)
        val restarted = newManager()
        now += T.mins(1).msecs()
        lastReading(60.0)
        restarted.check()
        assertThat(posts(NotificationId.BG_HYPO)).isEqualTo(1)
    }

    @Test
    fun anAcknowledgeFromTheWatchActsOnThePhone() = runTest {
        preferences.put(BooleanKey.AlertHypo, true)
        lastReading(60.0)
        sut.check()
        rxBus.send(EventData.CgmAlarmAcknowledge("low"))
        // The scope is Unconfined, so the bus delivers the message and the acknowledge runs before send() returns.
        assertThat(dismisses(NotificationId.BG_HYPO)).isEqualTo(1)
        assertThat(toWatch.filterIsInstance<EventData.CgmAlarmCancel>().map { it.id }).containsExactly("low")
    }

    @Test
    fun theMuteNeverSilencesUrgentLow() = runTest {
        preferences.put(BooleanKey.AlertUrgentLow, true)
        preferences.put(BooleanKey.AlertHyper, true)
        sut.mute(240)
        assertThat(sut.mutedUntil.value).isEqualTo(now + T.mins(240).msecs())
        assertThat(preferences.get(LocalAlertLongKey.CgmAlarmsMutedUntil)).isEqualTo(now + T.mins(240).msecs())
        lastReading(300.0)
        sut.check()
        assertThat(posts(NotificationId.BG_HYPER)).isEqualTo(0)
        lastReading(50.0)
        sut.check()
        assertThat(posts(NotificationId.CGM_URGENT_LOW)).isEqualTo(1)
        sut.mute(0)
        assertThat(sut.mutedUntil.value).isEqualTo(0L)
    }

    @Test
    fun hypoTreatedHoldsTheLowAlarmsFor20Minutes() = runTest {
        preferences.put(BooleanKey.AlertHypo, true)
        lastReading(60.0)
        sut.check()
        sut.acknowledgeHypoTreated(CgmAlarmManager.HYPO_TREATED_SNOOZE_MS)
        now += T.mins(19).msecs()
        lastReading(60.0)
        sut.check()
        assertThat(posts(NotificationId.BG_HYPO)).isEqualTo(1)
        now += T.mins(1).msecs()
        lastReading(60.0)
        sut.check()
        assertThat(posts(NotificationId.BG_HYPO)).isEqualTo(2)
    }

    @Test
    fun theSensorStateOfTheSourceRaisesTheSensorAlarms() = runTest {
        val provider = mock<BgSource>(extraInterfaces = arrayOf(CgmSensorAlertProvider::class))
        whenever(activePlugin.activeBgSource).thenReturn(provider)
        whenever((provider as CgmSensorAlertProvider).sensorAlertState(any(), any())).thenReturn(CgmSensorAlertState.Expired(now - 1_000L))
        whenever(persistenceLayer.getLastGlucoseValue()).thenReturn(null)
        sut.check()
        assertThat(posts(NotificationId.CGM_SENSOR_EXPIRED)).isEqualTo(1)
    }

    @Test
    fun aBrokenSourceDoesNotStopTheGlucoseAlarms() = runTest {
        val provider = mock<BgSource>(extraInterfaces = arrayOf(CgmSensorAlertProvider::class))
        whenever(activePlugin.activeBgSource).thenReturn(provider)
        whenever((provider as CgmSensorAlertProvider).sensorAlertState(any(), any())).thenThrow(IllegalStateException("broken"))
        preferences.put(BooleanKey.AlertHypo, true)
        lastReading(60.0)
        sut.check()
        assertThat(posts(NotificationId.BG_HYPO)).isEqualTo(1)
    }

    @Test
    fun theTestButtonPlaysTheChosenSound() {
        preferences.put(StringKey.AlertHyperSound, "trio_tone_harp")
        sut.test(CgmAlarmType.HIGH)
        assertThat(lastSound(NotificationId.CGM_ALARM_TEST)).isEqualTo(AlarmSound.TRIO_TONE_HARP)
    }

    @Test
    fun theRingingAlarmsDeclareAnAcknowledgeForTheLockScreen() {
        CgmAlarmType.entries.filter { it.reminds && it.group != CgmAlarmGroup.LOW }.forEach {
            assertThat(it.notificationId.alarmAction).isEqualTo(AlarmAction.CGM_ACKNOWLEDGE)
        }
        assertThat(NotificationId.BG_HYPO.alarmAction).isEqualTo(AlarmAction.HYPO_TREATED)
    }
}
