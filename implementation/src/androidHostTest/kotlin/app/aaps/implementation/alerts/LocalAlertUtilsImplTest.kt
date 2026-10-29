package app.aaps.implementation.alerts

import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.notifications.AlarmSound
import app.aaps.core.interfaces.notifications.NotificationAction
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationLevel
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.pump.PumpWithConcentration
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.smsCommunicator.SmsCommunicator
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.implementation.alerts.keys.LocalAlertLongKey
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyVararg
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.never
import org.mockito.kotlin.timeout
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class LocalAlertUtilsImplTest : TestBase() {

    @Mock lateinit var preferences: Preferences
    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var activePlugin: ActivePlugin
    @Mock lateinit var profileFunction: ProfileFunction
    @Mock lateinit var profileUtil: ProfileUtil
    @Mock lateinit var smsCommunicator: SmsCommunicator
    @Mock lateinit var config: Config
    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var dateUtil: DateUtil
    @Mock lateinit var notificationManager: NotificationManager
    @Mock lateinit var pump: PumpWithConcentration
    @Mock lateinit var pumpDescription: PumpDescription

    private val testScope = CoroutineScope(Dispatchers.Unconfined)
    private lateinit var localAlertUtils: LocalAlertUtilsImpl

    private val now = 100000000L

    companion object {

        @JvmStatic
        @BeforeAll
        fun initializeEnums() {
            // Force enum initialization before any tests run to avoid circular dependency
            // This must happen at class load time, before test methods try to use IntKey
            try {
                // Initialize in dependency order: BooleanKey and StringKey first, then IntKey
                Class.forName("app.aaps.core.keys.BooleanKey")
                Class.forName("app.aaps.core.keys.StringKey")
                Class.forName("app.aaps.core.keys.IntKey")
            } catch (e: Throwable) {
                // Swallow initialization errors - they'll surface in actual test execution
                System.err.println("Warning: Enum initialization failed in test setup: ${e.message}")
            }
        }
    }

    @BeforeEach
    fun setup() {
        localAlertUtils = LocalAlertUtilsImpl(
            aapsLogger,
            preferences,
            rh,
            activePlugin,
            profileFunction,
            profileUtil,
            smsCommunicator,
            config,
            persistenceLayer,
            dateUtil,
            notificationManager,
            testScope
        )
        whenever(dateUtil.now()).thenReturn(now)
        // The real rule of ProfileUtil: below 36 the value is mmol/L, otherwise mg/dL.
        whenever(profileUtil.convertToMgdlDetect(any())).thenAnswer {
            val value = it.getArgument<Double>(0)
            if (value < 36.0) value * Constants.MMOLL_TO_MGDL else value
        }
        whenever(profileUtil.fromMgdlToStringWithUnits(any())).thenReturn("x")
        whenever(activePlugin.activePump).thenReturn(pump)
        whenever(pump.pumpDescription).thenReturn(pumpDescription)
        whenever(pumpDescription.hasCustomUnreachableAlertCheck).thenReturn(false)
        runTest {
            whenever(persistenceLayer.insertPumpTherapyEventIfNewByTimestamp(any(), any(), any(), any(), any(), any()))
                .thenReturn(PersistenceLayer.TransactionResult())
        }
    }

    @Test
    fun `preSnoozeAlarms sets next missed readings alarm when expired`() {
        whenever(preferences.get(LocalAlertLongKey.NextMissedReadingsAlarm)).thenReturn(now - 1000)

        localAlertUtils.preSnoozeAlarms()

        verify(preferences).put(LocalAlertLongKey.NextMissedReadingsAlarm, now + 5 * 60 * 1000)
    }

    @Test
    fun `preSnoozeAlarms does not update next missed readings alarm when not expired`() {
        val futureTime = now + 10000
        whenever(preferences.get(LocalAlertLongKey.NextMissedReadingsAlarm)).thenReturn(futureTime)

        localAlertUtils.preSnoozeAlarms()

        verify(preferences, never()).put(eq(LocalAlertLongKey.NextMissedReadingsAlarm), any())
    }

    @Test
    fun `preSnoozeAlarms sets next pump disconnected alarm when expired`() {
        whenever(preferences.get(LocalAlertLongKey.NextMissedReadingsAlarm)).thenReturn(now + 1000)
        whenever(preferences.get(LocalAlertLongKey.NextPumpDisconnectedAlarm)).thenReturn(now - 1000)

        localAlertUtils.preSnoozeAlarms()

        verify(preferences).put(LocalAlertLongKey.NextPumpDisconnectedAlarm, now + 5 * 60 * 1000)
    }

    @Test
    fun `preSnoozeAlarms does not update pump disconnected alarm when not expired`() {
        val futureTime = now + 10000
        whenever(preferences.get(LocalAlertLongKey.NextMissedReadingsAlarm)).thenReturn(futureTime)
        whenever(preferences.get(LocalAlertLongKey.NextPumpDisconnectedAlarm)).thenReturn(futureTime)

        localAlertUtils.preSnoozeAlarms()

        verify(preferences, never()).put(eq(LocalAlertLongKey.NextPumpDisconnectedAlarm), any())
    }

    @Test
    fun `preSnoozeAlarms updates both alarms when both expired`() {
        whenever(preferences.get(LocalAlertLongKey.NextMissedReadingsAlarm)).thenReturn(now - 1000)
        whenever(preferences.get(LocalAlertLongKey.NextPumpDisconnectedAlarm)).thenReturn(now - 2000)

        localAlertUtils.preSnoozeAlarms()

        verify(preferences).put(LocalAlertLongKey.NextMissedReadingsAlarm, now + 5 * 60 * 1000)
        verify(preferences).put(LocalAlertLongKey.NextPumpDisconnectedAlarm, now + 5 * 60 * 1000)
    }

    @Test
    fun `shortenSnoozeInterval limits missed readings alarm to threshold`() {
        val thresholdMinutes = 30
        val farFutureAlarm = now + T.hours(5).msecs()

        whenever(preferences.get(IntKey.AlertsStaleDataThreshold)).thenReturn(thresholdMinutes)
        whenever(preferences.get(LocalAlertLongKey.NextMissedReadingsAlarm)).thenReturn(farFutureAlarm)
        whenever(preferences.get(LocalAlertLongKey.NextPumpDisconnectedAlarm)).thenReturn(now + 1000)
        whenever(preferences.get(IntKey.AlertsPumpUnreachableThreshold)).thenReturn(30)

        localAlertUtils.shortenSnoozeInterval()

        verify(preferences).put(LocalAlertLongKey.NextMissedReadingsAlarm, now + T.mins(thresholdMinutes.toLong()).msecs())
    }

    @Test
    fun `shortenSnoozeInterval does not change alarm if already within threshold`() {
        val thresholdMinutes = 30
        val alarmTime = now + T.mins(10).msecs()

        whenever(preferences.get(IntKey.AlertsStaleDataThreshold)).thenReturn(thresholdMinutes)
        whenever(preferences.get(LocalAlertLongKey.NextMissedReadingsAlarm)).thenReturn(alarmTime)
        whenever(preferences.get(IntKey.AlertsPumpUnreachableThreshold)).thenReturn(30)
        whenever(preferences.get(LocalAlertLongKey.NextPumpDisconnectedAlarm)).thenReturn(alarmTime)

        localAlertUtils.shortenSnoozeInterval()

        verify(preferences).put(LocalAlertLongKey.NextMissedReadingsAlarm, alarmTime)
    }

    @Test
    fun `shortenSnoozeInterval limits pump disconnected alarm to threshold`() {
        val thresholdMinutes = 20
        val farFutureAlarm = now + T.hours(10).msecs()

        whenever(preferences.get(IntKey.AlertsPumpUnreachableThreshold)).thenReturn(thresholdMinutes)
        whenever(preferences.get(IntKey.AlertsStaleDataThreshold)).thenReturn(30)
        whenever(preferences.get(LocalAlertLongKey.NextMissedReadingsAlarm)).thenReturn(now + 1000)
        whenever(preferences.get(LocalAlertLongKey.NextPumpDisconnectedAlarm)).thenReturn(farFutureAlarm)

        localAlertUtils.shortenSnoozeInterval()

        verify(preferences).put(LocalAlertLongKey.NextPumpDisconnectedAlarm, now + T.mins(thresholdMinutes.toLong()).msecs())
    }

    @Test
    fun `shortenSnoozeInterval handles both alarms correctly`() {
        val missedReadingsThreshold = 25
        val pumpUnreachableThreshold = 35

        whenever(preferences.get(IntKey.AlertsStaleDataThreshold)).thenReturn(missedReadingsThreshold)
        whenever(preferences.get(IntKey.AlertsPumpUnreachableThreshold)).thenReturn(pumpUnreachableThreshold)
        whenever(preferences.get(LocalAlertLongKey.NextMissedReadingsAlarm)).thenReturn(now + T.hours(1).msecs())
        whenever(preferences.get(LocalAlertLongKey.NextPumpDisconnectedAlarm)).thenReturn(now + T.hours(2).msecs())

        localAlertUtils.shortenSnoozeInterval()

        verify(preferences).put(LocalAlertLongKey.NextMissedReadingsAlarm, now + T.mins(missedReadingsThreshold.toLong()).msecs())
        verify(preferences).put(LocalAlertLongKey.NextPumpDisconnectedAlarm, now + T.mins(pumpUnreachableThreshold.toLong()).msecs())
    }

    @Test
    fun `reportPumpStatusRead updates alarm when profile is available`() = runTest {
        val lastDataTime = now - T.mins(5).msecs()
        val thresholdMinutes = 30
        val profile = org.mockito.kotlin.mock<app.aaps.core.interfaces.profile.EffectiveProfile>()

        whenever(profileFunction.getProfile()).thenReturn(profile)
        whenever(pump.lastDataTime).thenReturn(MutableStateFlow(lastDataTime))
        whenever(preferences.get(IntKey.AlertsPumpUnreachableThreshold)).thenReturn(thresholdMinutes)
        whenever(preferences.get(LocalAlertLongKey.NextPumpDisconnectedAlarm)).thenReturn(now - 1000)

        localAlertUtils.reportPumpStatusRead()

        val expectedAlarmTime = lastDataTime + T.mins(thresholdMinutes.toLong()).msecs()
        verify(preferences).put(LocalAlertLongKey.NextPumpDisconnectedAlarm, expectedAlarmTime)
    }

    @Test
    fun `reportPumpStatusRead does not update alarm when profile is null`() = runTest {
        whenever(profileFunction.getProfile()).thenReturn(null)

        localAlertUtils.reportPumpStatusRead()

        verify(preferences, never()).put(any<LocalAlertLongKey>(), any<Long>())
    }

    @Test
    fun `reportPumpStatusRead does not decrease alarm time`() = runTest {
        val lastDataTime = now - T.mins(5).msecs()
        val thresholdMinutes = 30
        val futureAlarmTime = now + T.hours(2).msecs()
        val profile = org.mockito.kotlin.mock<app.aaps.core.interfaces.profile.EffectiveProfile>()

        whenever(profileFunction.getProfile()).thenReturn(profile)
        whenever(pump.lastDataTime).thenReturn(MutableStateFlow(lastDataTime))
        whenever(preferences.get(IntKey.AlertsPumpUnreachableThreshold)).thenReturn(thresholdMinutes)
        whenever(preferences.get(LocalAlertLongKey.NextPumpDisconnectedAlarm)).thenReturn(futureAlarmTime)

        localAlertUtils.reportPumpStatusRead()

        // Should not update because futureAlarmTime is already later than earliestAlarmTime
        verify(preferences, never()).put(eq(LocalAlertLongKey.NextPumpDisconnectedAlarm), any())
    }

    @Test
    fun `preSnoozeAlarms uses exact 5 minute snooze`() {
        whenever(preferences.get(LocalAlertLongKey.NextMissedReadingsAlarm)).thenReturn(now - 1000)
        whenever(preferences.get(LocalAlertLongKey.NextPumpDisconnectedAlarm)).thenReturn(now - 1000)

        localAlertUtils.preSnoozeAlarms()

        val expectedSnooze = now + 5 * 60 * 1000
        verify(preferences).put(LocalAlertLongKey.NextMissedReadingsAlarm, expectedSnooze)
        verify(preferences).put(LocalAlertLongKey.NextPumpDisconnectedAlarm, expectedSnooze)
    }

    @Test
    fun `shortenSnoozeInterval handles edge case with zero threshold`() {
        whenever(preferences.get(IntKey.AlertsStaleDataThreshold)).thenReturn(0)
        whenever(preferences.get(IntKey.AlertsPumpUnreachableThreshold)).thenReturn(0)
        whenever(preferences.get(LocalAlertLongKey.NextMissedReadingsAlarm)).thenReturn(now + 1000)
        whenever(preferences.get(LocalAlertLongKey.NextPumpDisconnectedAlarm)).thenReturn(now + 1000)

        localAlertUtils.shortenSnoozeInterval()

        verify(preferences).put(LocalAlertLongKey.NextMissedReadingsAlarm, now)
        verify(preferences).put(LocalAlertLongKey.NextPumpDisconnectedAlarm, now)
    }

    @Test
    fun `shortenSnoozeInterval uses minimum of current alarm and threshold`() {
        val thresholdMinutes = 30
        val currentAlarmClose = now + T.mins(10).msecs()
        val currentAlarmFar = now + T.mins(50).msecs()

        whenever(preferences.get(IntKey.AlertsStaleDataThreshold)).thenReturn(thresholdMinutes)
        whenever(preferences.get(IntKey.AlertsPumpUnreachableThreshold)).thenReturn(thresholdMinutes)

        // Test with close alarm - should keep it
        whenever(preferences.get(LocalAlertLongKey.NextMissedReadingsAlarm)).thenReturn(currentAlarmClose)
        whenever(preferences.get(LocalAlertLongKey.NextPumpDisconnectedAlarm)).thenReturn(currentAlarmClose)
        localAlertUtils.shortenSnoozeInterval()
        verify(preferences).put(LocalAlertLongKey.NextMissedReadingsAlarm, currentAlarmClose)
        verify(preferences).put(LocalAlertLongKey.NextPumpDisconnectedAlarm, currentAlarmClose)

        // Test with far alarm - should shorten it
        whenever(preferences.get(LocalAlertLongKey.NextMissedReadingsAlarm)).thenReturn(currentAlarmFar)
        whenever(preferences.get(LocalAlertLongKey.NextPumpDisconnectedAlarm)).thenReturn(currentAlarmFar)
        localAlertUtils.shortenSnoozeInterval()
        verify(preferences).put(LocalAlertLongKey.NextMissedReadingsAlarm, now + T.mins(thresholdMinutes.toLong()).msecs())
        verify(preferences).put(LocalAlertLongKey.NextPumpDisconnectedAlarm, now + T.mins(thresholdMinutes.toLong()).msecs())
    }

    @Test
    fun `reportPumpStatusRead calculates earliest alarm time correctly`() = runTest {
        val lastDataTime = now - T.mins(10).msecs()
        val thresholdMinutes = 40
        val currentAlarmTime = now + T.mins(5).msecs()
        val profile = org.mockito.kotlin.mock<app.aaps.core.interfaces.profile.EffectiveProfile>()

        whenever(profileFunction.getProfile()).thenReturn(profile)
        whenever(pump.lastDataTime).thenReturn(MutableStateFlow(lastDataTime))
        whenever(preferences.get(IntKey.AlertsPumpUnreachableThreshold)).thenReturn(thresholdMinutes)
        whenever(preferences.get(LocalAlertLongKey.NextPumpDisconnectedAlarm)).thenReturn(currentAlarmTime)

        localAlertUtils.reportPumpStatusRead()

        val expectedEarliestAlarm = lastDataTime + T.mins(thresholdMinutes.toLong()).msecs()
        verify(preferences).put(LocalAlertLongKey.NextPumpDisconnectedAlarm, expectedEarliestAlarm)
    }

    // region checkGlucoseAlerts: hypo, hyper and rapid fall (they only notify, they never dose)

    private fun gv(value: Double, timestamp: Long = now) = GV(
        timestamp = timestamp, raw = null, value = value,
        trendArrow = TrendArrow.NONE, noise = null, sourceSensor = SourceSensor.UNKNOWN
    )

    /** Fresh last reading and a 30 min stale threshold, so the freshness guard passes. */
    private suspend fun freshLast(value: Double) {
        whenever(preferences.get(IntKey.AlertsStaleDataThreshold)).thenReturn(30)
        whenever(persistenceLayer.getLastGlucoseValue()).thenReturn(gv(value, timestamp = now))
    }

    private fun hypoOn(thresholdStored: Double = 70.0) {
        whenever(preferences.get(BooleanKey.AlertHypo)).thenReturn(true)
        whenever(preferences.getRaw(UnitDoubleKey.AlertHypoThreshold)).thenReturn(thresholdStored)
    }

    private fun hyperOn(thresholdStored: Double = 250.0) {
        whenever(preferences.get(BooleanKey.AlertHyper)).thenReturn(true)
        whenever(preferences.getRaw(UnitDoubleKey.AlertHyperThreshold)).thenReturn(thresholdStored)
    }

    private fun rapidFallOn(drop: Int) {
        whenever(preferences.get(BooleanKey.AlertRapidFall)).thenReturn(true)
        whenever(preferences.get(IntKey.AlertRapidFallWindow)).thenReturn(15)
        whenever(preferences.get(IntKey.AlertRapidFallDrop)).thenReturn(drop)
    }

    private fun verifyPosted(id: NotificationId, sound: AlarmSound?): List<NotificationAction> {
        val actions = argumentCaptor<List<NotificationAction>>()
        verify(notificationManager).post(eq(id), any<TextRef>(), any<NotificationLevel>(), any(), any(), any(), eq(sound), actions.capture(), anyOrNull())
        return actions.firstValue
    }

    private fun verifyNotPosted(id: NotificationId) {
        verify(notificationManager, never()).post(eq(id), any<TextRef>(), any<NotificationLevel>(), any(), any(), any(), anyOrNull(), any(), anyOrNull())
    }

    @Test
    fun `hypo alarm fires below threshold`() = runTest {
        freshLast(68.0)
        hypoOn()
        whenever(preferences.get(LocalAlertLongKey.NextHypoAlarm)).thenReturn(now - 1)

        localAlertUtils.checkGlucoseAlerts()

        verify(preferences).put(LocalAlertLongKey.NextHypoAlarm, now + T.mins(15).msecs())
        verifyPosted(NotificationId.BG_HYPO, AlarmSound.ALARM)
    }

    @Test
    fun `hypo alarm fires at exactly the threshold`() = runTest {
        freshLast(70.0)
        hypoOn()
        whenever(preferences.get(LocalAlertLongKey.NextHypoAlarm)).thenReturn(now - 1)

        localAlertUtils.checkGlucoseAlerts()

        verifyPosted(NotificationId.BG_HYPO, AlarmSound.ALARM)
    }

    @Test
    fun `hypo alarm does not fire just above the threshold`() = runTest {
        freshLast(71.0)
        hypoOn()
        whenever(preferences.get(LocalAlertLongKey.NextHypoAlarm)).thenReturn(now - 1)

        localAlertUtils.checkGlucoseAlerts()

        verifyNotPosted(NotificationId.BG_HYPO)
    }

    @Test
    fun `hypo alarm is urgent with sound so mute all alarms silences it`() = runTest {
        // muteAllAlarms() in the notification manager drops every URGENT notification that has a
        // sound. The low alarm must be one of them, and NotificationId gives it its level.
        assertThat(NotificationId.BG_HYPO.defaultLevel).isEqualTo(NotificationLevel.URGENT)
        assertThat(NotificationId.BG_RAPID_FALL.defaultLevel).isEqualTo(NotificationLevel.URGENT)
        // High glucose is not urgent and has no alarm sound.
        assertThat(NotificationId.BG_HYPER.defaultLevel).isEqualTo(NotificationLevel.IMPORTANT)
    }

    @Test
    fun `hypo alarm offers hypo treated and snooze`() = runTest {
        freshLast(60.0)
        hypoOn()
        whenever(preferences.get(LocalAlertLongKey.NextHypoAlarm)).thenReturn(now - 1)

        localAlertUtils.checkGlucoseAlerts()

        val actions = verifyPosted(NotificationId.BG_HYPO, AlarmSound.ALARM)
        assertThat(actions).hasSize(2)

        // Snooze writes nothing: the alarm comes back after the 15 min written above.
        actions[1].action()
        verify(preferences, never()).put(LocalAlertLongKey.NextHypoAlarm, now + T.mins(20).msecs())

        // Hypo treated holds the alarm for 20 min.
        actions[0].action()
        verify(preferences, timeout(1_000)).put(LocalAlertLongKey.NextHypoAlarm, now + T.mins(20).msecs())
    }

    @Test
    fun `hypo alarm does not fire when disabled`() = runTest {
        freshLast(50.0)
        whenever(preferences.get(BooleanKey.AlertHypo)).thenReturn(false)

        localAlertUtils.checkGlucoseAlerts()

        verify(preferences, never()).put(eq(LocalAlertLongKey.NextHypoAlarm), any())
        verify(notificationManager, never()).dismiss(NotificationId.BG_HYPO)
        verifyNotPosted(NotificationId.BG_HYPO)
    }

    @Test
    fun `hypo alarm is not posted again while snoozed`() = runTest {
        freshLast(68.0)
        hypoOn()
        whenever(preferences.get(LocalAlertLongKey.NextHypoAlarm)).thenReturn(now + T.mins(5).msecs())

        localAlertUtils.checkGlucoseAlerts()

        verify(preferences, never()).put(eq(LocalAlertLongKey.NextHypoAlarm), any())
        verifyNotPosted(NotificationId.BG_HYPO)
    }

    @Test
    fun `hypo alarm comes back when the snooze has ended and glucose is still low`() = runTest {
        freshLast(68.0)
        hypoOn()
        // The snooze ended exactly now: "next alarm" must be in the past to fire, so not yet.
        whenever(preferences.get(LocalAlertLongKey.NextHypoAlarm)).thenReturn(now)
        localAlertUtils.checkGlucoseAlerts()
        verifyNotPosted(NotificationId.BG_HYPO)

        whenever(preferences.get(LocalAlertLongKey.NextHypoAlarm)).thenReturn(now - 1)
        localAlertUtils.checkGlucoseAlerts()
        verifyPosted(NotificationId.BG_HYPO, AlarmSound.ALARM)
    }

    @Test
    fun `hypo alarm dismissed when recovered beyond hysteresis`() = runTest {
        freshLast(75.0) // threshold 70 + 5 hysteresis = 75
        hypoOn()

        localAlertUtils.checkGlucoseAlerts()

        verify(notificationManager).dismiss(NotificationId.BG_HYPO)
        verify(preferences).put(LocalAlertLongKey.NextHypoAlarm, 0L)
    }

    @Test
    fun `hypo alarm held within hysteresis band`() = runTest {
        freshLast(74.0) // above 70 but below 75
        hypoOn()

        localAlertUtils.checkGlucoseAlerts()

        verify(preferences, never()).put(eq(LocalAlertLongKey.NextHypoAlarm), any())
        verify(notificationManager, never()).dismiss(NotificationId.BG_HYPO)
    }

    @Test
    fun `hyper alarm fires at exactly the threshold without sound`() = runTest {
        freshLast(250.0)
        whenever(preferences.get(BooleanKey.AlertHyper)).thenReturn(true)
        whenever(preferences.getRaw(UnitDoubleKey.AlertHyperThreshold)).thenReturn(250.0)
        whenever(preferences.get(LocalAlertLongKey.NextHyperAlarm)).thenReturn(now - 1)

        localAlertUtils.checkGlucoseAlerts()

        verify(preferences).put(LocalAlertLongKey.NextHyperAlarm, now + T.mins(30).msecs())
        verifyPosted(NotificationId.BG_HYPER, null)
    }

    @Test
    fun `hyper alarm does not fire just below the threshold`() = runTest {
        freshLast(249.0)
        whenever(preferences.get(BooleanKey.AlertHyper)).thenReturn(true)
        whenever(preferences.getRaw(UnitDoubleKey.AlertHyperThreshold)).thenReturn(250.0)
        whenever(preferences.get(LocalAlertLongKey.NextHyperAlarm)).thenReturn(now - 1)

        localAlertUtils.checkGlucoseAlerts()

        verifyNotPosted(NotificationId.BG_HYPER)
    }

    @Test
    fun `hyper alarm dismissed when recovered beyond hysteresis`() = runTest {
        freshLast(240.0) // threshold 250 - 10 = 240
        whenever(preferences.get(BooleanKey.AlertHyper)).thenReturn(true)
        whenever(preferences.getRaw(UnitDoubleKey.AlertHyperThreshold)).thenReturn(250.0)

        localAlertUtils.checkGlucoseAlerts()

        verify(notificationManager).dismiss(NotificationId.BG_HYPER)
        verify(preferences).put(LocalAlertLongKey.NextHyperAlarm, 0L)
    }

    @Test
    fun `rapid fall alarm fires when drop exceeds threshold`() = runTest {
        freshLast(115.0)
        rapidFallOn(drop = 30)
        whenever(preferences.get(LocalAlertLongKey.NextRapidFallAlarm)).thenReturn(now - 1)
        whenever(persistenceLayer.getBgReadingsDataFromTime(any(), eq(true)))
            .thenReturn(listOf(gv(150.0, now - T.mins(15).msecs()), gv(115.0, now)))

        localAlertUtils.checkGlucoseAlerts()

        verify(preferences).put(LocalAlertLongKey.NextRapidFallAlarm, now + T.mins(15).msecs())
        verifyPosted(NotificationId.BG_RAPID_FALL, AlarmSound.ALARM)
    }

    @Test
    fun `rapid fall alarm fires at exactly the drop threshold`() = runTest {
        freshLast(120.0)
        rapidFallOn(drop = 30)
        whenever(preferences.get(LocalAlertLongKey.NextRapidFallAlarm)).thenReturn(now - 1)
        whenever(persistenceLayer.getBgReadingsDataFromTime(any(), eq(true)))
            .thenReturn(listOf(gv(150.0, now - T.mins(15).msecs()), gv(120.0, now))) // drop 30

        localAlertUtils.checkGlucoseAlerts()

        verifyPosted(NotificationId.BG_RAPID_FALL, AlarmSound.ALARM)
    }

    @Test
    fun `rapid fall alarm does not fire for a shallow drop`() = runTest {
        freshLast(130.0)
        rapidFallOn(drop = 30)
        whenever(persistenceLayer.getBgReadingsDataFromTime(any(), eq(true)))
            .thenReturn(listOf(gv(150.0, now - T.mins(15).msecs()), gv(130.0, now))) // drop 20

        localAlertUtils.checkGlucoseAlerts()

        verify(preferences, never()).put(eq(LocalAlertLongKey.NextRapidFallAlarm), any())
    }

    @Test
    fun `rapid fall alarm dismissed when the fall has flattened`() = runTest {
        freshLast(140.0)
        rapidFallOn(drop = 30)
        whenever(persistenceLayer.getBgReadingsDataFromTime(any(), eq(true)))
            .thenReturn(listOf(gv(150.0, now - T.mins(15).msecs()), gv(140.0, now))) // drop 10 <= 15

        localAlertUtils.checkGlucoseAlerts()

        verify(notificationManager).dismiss(NotificationId.BG_RAPID_FALL)
        verify(preferences).put(LocalAlertLongKey.NextRapidFallAlarm, 0L)
    }

    @Test
    fun `rapid fall alarm uses a 20 mg per dL drop on an mmol phone`() = runTest {
        whenever(profileUtil.units).thenReturn(GlucoseUnit.MMOL)
        freshLast(128.0)
        rapidFallOn(drop = 20)
        whenever(preferences.get(LocalAlertLongKey.NextRapidFallAlarm)).thenReturn(now - 1)
        whenever(persistenceLayer.getBgReadingsDataFromTime(any(), eq(true)))
            .thenReturn(listOf(gv(150.0, now - T.mins(15).msecs()), gv(128.0, now))) // drop 22

        localAlertUtils.checkGlucoseAlerts()

        verify(preferences).put(LocalAlertLongKey.NextRapidFallAlarm, now + T.mins(15).msecs())
    }

    @Test
    fun `rapid fall alarm skips on data gap`() = runTest {
        freshLast(115.0)
        rapidFallOn(drop = 30)
        whenever(persistenceLayer.getBgReadingsDataFromTime(any(), eq(true)))
            .thenReturn(listOf(gv(115.0, now)))

        localAlertUtils.checkGlucoseAlerts()

        verify(preferences, never()).put(eq(LocalAlertLongKey.NextRapidFallAlarm), any())
        verify(notificationManager, never()).dismiss(NotificationId.BG_RAPID_FALL)
    }

    @Test // The display getter converts to mmol/L: reading it would make 100 mg/dL look "high"
    fun `mmol phone does not raise a hyper alarm at normal glucose`() = runTest {
        freshLast(100.0) // 5.5 mmol/L
        whenever(preferences.get(BooleanKey.AlertHyper)).thenReturn(true)
        whenever(preferences.getRaw(UnitDoubleKey.AlertHyperThreshold)).thenReturn(250.0)
        whenever(preferences.get(UnitDoubleKey.AlertHyperThreshold)).thenReturn(13.9)
        whenever(preferences.get(LocalAlertLongKey.NextHyperAlarm)).thenReturn(now - 1)

        localAlertUtils.checkGlucoseAlerts()

        verifyNotPosted(NotificationId.BG_HYPER)
    }

    @Test
    fun `mmol phone still raises the hypo alarm below threshold`() = runTest {
        freshLast(68.0) // 3.8 mmol/L
        hypoOn(thresholdStored = 70.0)
        whenever(preferences.get(UnitDoubleKey.AlertHypoThreshold)).thenReturn(3.9)
        whenever(preferences.get(LocalAlertLongKey.NextHypoAlarm)).thenReturn(now - 1)

        localAlertUtils.checkGlucoseAlerts()

        verifyPosted(NotificationId.BG_HYPO, AlarmSound.ALARM)
    }

    @Test // A threshold that was stored in mmol/L (3.9 = 70.3 mg/dL) still compares in mg/dL
    fun `a threshold stored in mmol per L is converted to mg per dL`() = runTest {
        freshLast(70.0)
        hypoOn(thresholdStored = 3.9)
        whenever(preferences.get(LocalAlertLongKey.NextHypoAlarm)).thenReturn(now - 1)

        localAlertUtils.checkGlucoseAlerts()

        verifyPosted(NotificationId.BG_HYPO, AlarmSound.ALARM)
    }

    @Test
    fun `hypo treated action holds the alarm for 20 minutes and clears it`() {
        localAlertUtils.snoozeHypoAfterTreatment()

        verify(preferences).put(LocalAlertLongKey.NextHypoAlarm, now + T.mins(20).msecs())
        verify(notificationManager).dismiss(NotificationId.BG_HYPO)
    }

    @Test // A tap during a running check must not be overwritten by that check
    fun `hypo treated tap waits for a running check and is written last`() = runTest {
        val readingGate = CompletableDeferred<Unit>()
        whenever(preferences.get(IntKey.AlertsStaleDataThreshold)).thenReturn(30)
        // The check stops inside the lock until the gate opens.
        whenever(persistenceLayer.getLastGlucoseValue()).doSuspendableAnswer { readingGate.await(); gv(60.0) }
        hypoOn()
        whenever(preferences.get(LocalAlertLongKey.NextHypoAlarm)).thenReturn(now - 1)

        val check = testScope.launch { localAlertUtils.checkGlucoseAlerts() }
        localAlertUtils.snoozeHypoAfterTreatment()
        // The tap is waiting for the check: nothing written yet.
        verify(preferences, never()).put(eq(LocalAlertLongKey.NextHypoAlarm), any())

        readingGate.complete(Unit)
        check.join()

        val order = inOrder(preferences)
        order.verify(preferences, timeout(1_000)).put(LocalAlertLongKey.NextHypoAlarm, now + T.mins(15).msecs())
        order.verify(preferences, timeout(1_000)).put(LocalAlertLongKey.NextHypoAlarm, now + T.mins(20).msecs())
    }

    @Test
    fun `no glucose alarm fires on stale data`() = runTest {
        whenever(preferences.get(IntKey.AlertsStaleDataThreshold)).thenReturn(30)
        whenever(persistenceLayer.getLastGlucoseValue())
            .thenReturn(gv(50.0, timestamp = now - T.mins(30).msecs() - 1)) // 1 ms older than 30 min
        hypoOn()
        whenever(preferences.get(BooleanKey.AlertHyper)).thenReturn(true)
        whenever(preferences.get(BooleanKey.AlertRapidFall)).thenReturn(true)

        localAlertUtils.checkGlucoseAlerts()

        verify(preferences, never()).put(eq(LocalAlertLongKey.NextHypoAlarm), any())
        verify(preferences, never()).put(eq(LocalAlertLongKey.NextHyperAlarm), any())
        verify(preferences, never()).put(eq(LocalAlertLongKey.NextRapidFallAlarm), any())
        verify(persistenceLayer, never()).getBgReadingsDataFromTime(any(), any())
        verifyNotPosted(NotificationId.BG_HYPO)
    }

    @Test // The high alarm keeps the missed readings limit; the low alarm has its own 15 min limit
    fun `a reading exactly at the stale limit still counts as fresh`() = runTest {
        whenever(preferences.get(IntKey.AlertsStaleDataThreshold)).thenReturn(30)
        whenever(persistenceLayer.getLastGlucoseValue()).thenReturn(gv(300.0, timestamp = now - T.mins(30).msecs()))
        hyperOn()
        whenever(preferences.get(LocalAlertLongKey.NextHyperAlarm)).thenReturn(now - 1)

        localAlertUtils.checkGlucoseAlerts()

        verifyPosted(NotificationId.BG_HYPER, null)
    }

    // region missed readings alarm follows the glucose alarms

    private suspend fun staleLast() {
        whenever(preferences.get(IntKey.AlertsStaleDataThreshold)).thenReturn(30)
        whenever(persistenceLayer.getLastGlucoseValue()).thenReturn(gv(120.0, timestamp = now - T.mins(31).msecs()))
        whenever(preferences.get(LocalAlertLongKey.NextMissedReadingsAlarm)).thenReturn(now - 1)
    }

    @Test // A glucose alarm the user relies on says nothing when readings stop: this one must
    fun `missed readings alarm fires when only the low alarm is on`() = runTest {
        staleLast()
        whenever(preferences.get(BooleanKey.AlertMissedBgReading)).thenReturn(false)
        whenever(preferences.get(BooleanKey.AlertHypo)).thenReturn(true)

        localAlertUtils.checkStaleBGAlert()

        verify(notificationManager).post(eq(NotificationId.BG_READINGS_MISSED), any<TextRef>(), any<NotificationLevel>(), any(), any(), any(), eq(AlarmSound.ALARM), any(), anyOrNull())
        verify(preferences).put(LocalAlertLongKey.NextMissedReadingsAlarm, now + T.mins(30).msecs())
    }

    @Test
    fun `missed readings alarm fires when only the high or rapid fall alarm is on`() = runTest {
        staleLast()
        whenever(preferences.get(BooleanKey.AlertRapidFall)).thenReturn(true)

        localAlertUtils.checkStaleBGAlert()

        verify(notificationManager).post(eq(NotificationId.BG_READINGS_MISSED), any<TextRef>(), any<NotificationLevel>(), any(), any(), any(), eq(AlarmSound.ALARM), any(), anyOrNull())
    }

    @Test
    fun `missed readings alarm stays silent when every alarm is off`() = runTest {
        staleLast()

        localAlertUtils.checkStaleBGAlert()

        verify(notificationManager, never()).post(eq(NotificationId.BG_READINGS_MISSED), any<TextRef>(), any<NotificationLevel>(), any(), any(), any(), anyOrNull(), any(), anyOrNull())
    }

    @Test // The glucose alarms do not change its snooze: it still waits for its own repeat time
    fun `missed readings alarm forced by a glucose alarm keeps its snooze`() = runTest {
        staleLast()
        whenever(preferences.get(BooleanKey.AlertHypo)).thenReturn(true)
        whenever(preferences.get(LocalAlertLongKey.NextMissedReadingsAlarm)).thenReturn(now + 1)

        localAlertUtils.checkStaleBGAlert()

        verify(notificationManager, never()).post(eq(NotificationId.BG_READINGS_MISSED), any<TextRef>(), any<NotificationLevel>(), any(), any(), any(), anyOrNull(), any(), anyOrNull())
    }

    // endregion

    // region the low and rapid fall alarms use no reading older than 15 min

    private suspend fun lastAt(value: Double, ageMs: Long) {
        whenever(preferences.get(IntKey.AlertsStaleDataThreshold)).thenReturn(30)
        whenever(persistenceLayer.getLastGlucoseValue()).thenReturn(gv(value, timestamp = now - ageMs))
    }

    @Test
    fun `low alarm fires on a reading exactly 15 min old`() = runTest {
        lastAt(60.0, T.mins(15).msecs())
        hypoOn()
        whenever(preferences.get(LocalAlertLongKey.NextHypoAlarm)).thenReturn(now - 1)

        localAlertUtils.checkGlucoseAlerts()

        verifyPosted(NotificationId.BG_HYPO, AlarmSound.ALARM)
    }

    @Test
    fun `low alarm does not fire on a reading older than 15 min`() = runTest {
        lastAt(60.0, T.mins(15).msecs() + 1)
        hypoOn()
        whenever(preferences.get(LocalAlertLongKey.NextHypoAlarm)).thenReturn(now - 1)

        localAlertUtils.checkGlucoseAlerts()

        verifyNotPosted(NotificationId.BG_HYPO)
        verify(preferences, never()).put(eq(LocalAlertLongKey.NextHypoAlarm), any())
    }

    @Test
    fun `low alarm uses the missed readings limit when it is shorter than 15 min`() = runTest {
        whenever(preferences.get(IntKey.AlertsStaleDataThreshold)).thenReturn(10)
        whenever(persistenceLayer.getLastGlucoseValue()).thenReturn(gv(60.0, timestamp = now - T.mins(10).msecs() - 1))
        hypoOn()
        whenever(preferences.get(LocalAlertLongKey.NextHypoAlarm)).thenReturn(now - 1)

        localAlertUtils.checkGlucoseAlerts()

        verifyNotPosted(NotificationId.BG_HYPO)
    }

    @Test
    fun `rapid fall alarm does not fire on a reading older than 15 min`() = runTest {
        lastAt(115.0, T.mins(15).msecs() + 1)
        rapidFallOn(drop = 30)
        whenever(preferences.get(LocalAlertLongKey.NextRapidFallAlarm)).thenReturn(now - 1)
        whenever(persistenceLayer.getBgReadingsDataFromTime(any(), eq(true)))
            .thenReturn(listOf(gv(150.0, now - T.mins(30).msecs()), gv(115.0, now - T.mins(15).msecs() - 1)))

        localAlertUtils.checkGlucoseAlerts()

        verifyNotPosted(NotificationId.BG_RAPID_FALL)
    }

    @Test
    fun `rapid fall alarm fires on a reading exactly 15 min old`() = runTest {
        lastAt(115.0, T.mins(15).msecs())
        rapidFallOn(drop = 30)
        whenever(preferences.get(LocalAlertLongKey.NextRapidFallAlarm)).thenReturn(now - 1)
        whenever(persistenceLayer.getBgReadingsDataFromTime(any(), eq(true)))
            .thenReturn(listOf(gv(150.0, now - T.mins(30).msecs()), gv(115.0, now - T.mins(15).msecs())))

        localAlertUtils.checkGlucoseAlerts()

        verifyPosted(NotificationId.BG_RAPID_FALL, AlarmSound.ALARM)
    }

    @Test
    fun `high alarm still fires on a reading older than 15 min`() = runTest {
        lastAt(300.0, T.mins(20).msecs())
        hyperOn()
        whenever(preferences.get(LocalAlertLongKey.NextHyperAlarm)).thenReturn(now - 1)

        localAlertUtils.checkGlucoseAlerts()

        verifyPosted(NotificationId.BG_HYPER, null)
    }

    @Test
    fun `every glucose alarm message shows how old the reading is`() = runTest {
        whenever(rh.gs(any<TextRef>(), anyVararg())).thenAnswer { "${it.getArgument<Any>(1)} min" }
        lastAt(60.0, T.mins(4).msecs())
        hypoOn()
        whenever(preferences.get(LocalAlertLongKey.NextHypoAlarm)).thenReturn(now - 1)
        localAlertUtils.checkGlucoseAlerts()
        assertThat(argsOf(postedText(NotificationId.BG_HYPO))).containsExactly("x", "4 min").inOrder()

        lastAt(300.0, T.mins(7).msecs())
        whenever(preferences.get(BooleanKey.AlertHypo)).thenReturn(false)
        hyperOn()
        whenever(preferences.get(LocalAlertLongKey.NextHyperAlarm)).thenReturn(now - 1)
        localAlertUtils.checkGlucoseAlerts()
        assertThat(argsOf(postedText(NotificationId.BG_HYPER))).containsExactly("x", "7 min").inOrder()

        lastAt(115.0, T.mins(2).msecs())
        whenever(preferences.get(BooleanKey.AlertHyper)).thenReturn(false)
        rapidFallOn(drop = 30)
        whenever(preferences.get(LocalAlertLongKey.NextRapidFallAlarm)).thenReturn(now - 1)
        whenever(persistenceLayer.getBgReadingsDataFromTime(any(), eq(true)))
            .thenReturn(listOf(gv(150.0, now - T.mins(15).msecs()), gv(115.0, now - T.mins(2).msecs())))
        localAlertUtils.checkGlucoseAlerts()
        assertThat(argsOf(postedText(NotificationId.BG_RAPID_FALL))).containsExactly("x", 15, "2 min").inOrder()
    }

    private fun postedText(id: NotificationId): TextRef {
        val text = argumentCaptor<TextRef>()
        verify(notificationManager).post(eq(id), text.capture(), any<NotificationLevel>(), any(), any(), any(), anyOrNull(), any(), anyOrNull())
        return text.firstValue
    }

    private fun argsOf(ref: TextRef): List<Any?> = when (ref) {
        is TextRef.AndroidRes -> ref.args
        is TextRef.Named      -> ref.args
        else                  -> emptyList()
    }

    // endregion

    // region the "hypo treated" hold breaks when glucose keeps falling

    @Test
    fun `hypo treated tap keeps the glucose of that moment with the hold`() = runTest {
        whenever(persistenceLayer.getLastGlucoseValue()).thenReturn(gv(65.0))

        localAlertUtils.snoozeHypoAfterTreatment()

        verify(preferences, timeout(1_000)).put(LocalAlertLongKey.HypoTreatedGlucose, 65L)
        verify(preferences, timeout(1_000)).put(LocalAlertLongKey.NextHypoAlarm, now + T.mins(20).msecs())
    }

    @Test
    fun `after hypo treated at 65 a reading of 60 stays silent`() = runTest {
        freshLast(60.0)
        hypoOn()
        whenever(preferences.get(LocalAlertLongKey.HypoTreatedGlucose)).thenReturn(65L)
        whenever(preferences.get(LocalAlertLongKey.NextHypoAlarm)).thenReturn(now + T.mins(15).msecs())

        localAlertUtils.checkGlucoseAlerts()

        verifyNotPosted(NotificationId.BG_HYPO)
    }

    @Test
    fun `after hypo treated at 65 a reading of 54 breaks the hold`() = runTest {
        freshLast(54.0)
        hypoOn()
        whenever(preferences.get(LocalAlertLongKey.HypoTreatedGlucose)).thenReturn(65L)
        whenever(preferences.get(LocalAlertLongKey.NextHypoAlarm)).thenReturn(now + T.mins(15).msecs())

        localAlertUtils.checkGlucoseAlerts()

        verifyPosted(NotificationId.BG_HYPO, AlarmSound.ALARM)
        verify(preferences).put(LocalAlertLongKey.NextHypoAlarm, now + T.mins(15).msecs())
        // The hold is used up: the normal repeat time applies again from here.
        verify(preferences).put(LocalAlertLongKey.HypoTreatedGlucose, 0L)
    }

    @Test // Without a "hypo treated" tap the plain 15 min repeat is never broken early
    fun `a normal hold is not broken by a falling reading`() = runTest {
        freshLast(40.0)
        hypoOn()
        whenever(preferences.get(LocalAlertLongKey.HypoTreatedGlucose)).thenReturn(0L)
        whenever(preferences.get(LocalAlertLongKey.NextHypoAlarm)).thenReturn(now + T.mins(10).msecs())

        localAlertUtils.checkGlucoseAlerts()

        verifyNotPosted(NotificationId.BG_HYPO)
    }

    // endregion

    // region a clock that went back cannot hold a glucose alarm for long

    @Test
    fun `shortenSnoozeInterval cuts glucose alarm holds that are too far ahead`() {
        whenever(preferences.get(IntKey.AlertsStaleDataThreshold)).thenReturn(30)
        whenever(preferences.get(IntKey.AlertsPumpUnreachableThreshold)).thenReturn(30)
        whenever(preferences.get(LocalAlertLongKey.NextMissedReadingsAlarm)).thenReturn(now)
        whenever(preferences.get(LocalAlertLongKey.NextPumpDisconnectedAlarm)).thenReturn(now)
        whenever(preferences.get(LocalAlertLongKey.NextHypoAlarm)).thenReturn(now + T.hours(5).msecs())
        whenever(preferences.get(LocalAlertLongKey.NextHyperAlarm)).thenReturn(now + T.hours(5).msecs())
        whenever(preferences.get(LocalAlertLongKey.NextRapidFallAlarm)).thenReturn(now + T.hours(5).msecs())

        localAlertUtils.shortenSnoozeInterval()

        verify(preferences).put(LocalAlertLongKey.NextHypoAlarm, now + T.mins(20).msecs())
        verify(preferences).put(LocalAlertLongKey.NextHyperAlarm, now + T.mins(30).msecs())
        verify(preferences).put(LocalAlertLongKey.NextRapidFallAlarm, now + T.mins(15).msecs())
    }

    @Test
    fun `shortenSnoozeInterval keeps glucose alarm holds that are already short`() {
        whenever(preferences.get(IntKey.AlertsStaleDataThreshold)).thenReturn(30)
        whenever(preferences.get(IntKey.AlertsPumpUnreachableThreshold)).thenReturn(30)
        whenever(preferences.get(LocalAlertLongKey.NextHypoAlarm)).thenReturn(now + T.mins(20).msecs())
        whenever(preferences.get(LocalAlertLongKey.NextHyperAlarm)).thenReturn(now + T.mins(10).msecs())

        localAlertUtils.shortenSnoozeInterval()

        verify(preferences, never()).put(eq(LocalAlertLongKey.NextHypoAlarm), any())
        verify(preferences, never()).put(eq(LocalAlertLongKey.NextHyperAlarm), any())
        verify(preferences, never()).put(eq(LocalAlertLongKey.NextRapidFallAlarm), any())
    }

    // endregion

    @Test
    fun `no glucose alarm fires when there is no reading`() = runTest {
        whenever(persistenceLayer.getLastGlucoseValue()).thenReturn(null)

        localAlertUtils.checkGlucoseAlerts()

        verify(preferences, never()).put(eq(LocalAlertLongKey.NextHypoAlarm), any())
    }

    @Test // The periodic pass and the new reading event can both run: the second must not post again
    fun `a second check right after a hypo alarm does not post again`() = runTest {
        freshLast(68.0)
        hypoOn()
        // First read: due. Second read: the time the first check just stored.
        whenever(preferences.get(LocalAlertLongKey.NextHypoAlarm)).thenReturn(now - 1, now + T.mins(15).msecs())

        localAlertUtils.checkGlucoseAlerts()
        localAlertUtils.checkGlucoseAlerts()

        verify(preferences, times(1)).put(eq(LocalAlertLongKey.NextHypoAlarm), any())
    }

    // endregion
}
