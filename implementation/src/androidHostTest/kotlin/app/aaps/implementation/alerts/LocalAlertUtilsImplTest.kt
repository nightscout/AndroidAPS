package app.aaps.implementation.alerts

import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.alerts.Alert
import app.aaps.core.interfaces.alerts.AlertLifetime
import app.aaps.core.interfaces.alerts.AlertSeverity
import app.aaps.core.interfaces.alerts.Alerts
import app.aaps.core.interfaces.alerts.CoreAlert
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.PumpWithConcentration
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.smsCommunicator.SmsCommunicator
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.implementation.alerts.keys.LocalAlertLongKey
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class LocalAlertUtilsImplTest : TestBase() {

    @Mock lateinit var preferences: Preferences
    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var activePlugin: ActivePlugin
    @Mock lateinit var profileFunction: ProfileFunction
    @Mock lateinit var smsCommunicator: SmsCommunicator
    @Mock lateinit var config: Config
    @Mock lateinit var dateUtil: DateUtil
    @Mock lateinit var alerts: Alerts
    @Mock lateinit var pump: PumpWithConcentration
    @Mock lateinit var pumpDescription: PumpDescription

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
            smsCommunicator,
            config,
            dateUtil,
            { alerts }
        )
        whenever(dateUtil.now()).thenReturn(now)
        whenever(activePlugin.activePump).thenReturn(pump)
        whenever(pump.pumpDescription).thenReturn(pumpDescription)
        whenever(pumpDescription.hasCustomUnreachableAlertCheck).thenReturn(false)
        whenever(alerts.active).thenReturn(MutableStateFlow(emptyList<Alert>()))
    }

    /** The pump was not reached for longer than the threshold: the alarm rings until it is answered. */
    @Test
    fun `an unreachable pump raises the pump unreachable alarm`() {
        givenUnreachableAlarmOn()

        localAlertUtils.checkPumpUnreachableAlarm(lastConnection = now - T.mins(60).msecs(), isStatusOutdated = true, isDisconnected = false)

        val captor = argumentCaptor<Alert>()
        verify(alerts).raise(captor.capture())
        assertThat(captor.firstValue.kind).isEqualTo(CoreAlert.PUMP_UNREACHABLE)
        assertThat(captor.firstValue.severity).isEqualTo(AlertSeverity.CRITICAL)
        assertThat(captor.firstValue.lifetime).isEqualTo(AlertLifetime.CONDITION)
    }

    /** Found again at the next threshold, the same condition keeps its time, so it does not ring again. */
    @Test
    fun `a pump that stays unreachable keeps the time of its alarm`() {
        givenUnreachableAlarmOn()
        val since = now - T.mins(30).msecs()
        whenever(alerts.active).thenReturn(
            MutableStateFlow(listOf(CoreAlert.PUMP_UNREACHABLE.alert(AlertSeverity.CRITICAL, AlertLifetime.CONDITION, null, since)))
        )

        localAlertUtils.checkPumpUnreachableAlarm(lastConnection = now - T.mins(60).msecs(), isStatusOutdated = true, isDisconnected = false)

        val captor = argumentCaptor<Alert>()
        verify(alerts).raise(captor.capture())
        assertThat(captor.firstValue.raisedAt).isEqualTo(since)
    }

    @Test
    fun `a pump that is reached again ends the alarm`() {
        givenUnreachableAlarmOn()

        localAlertUtils.checkPumpUnreachableAlarm(lastConnection = now - T.mins(1).msecs(), isStatusOutdated = false, isDisconnected = false)

        verify(alerts, never()).raise(any())
        verify(alerts).resolve(UiInteraction.SOURCE, CoreAlert.PUMP_UNREACHABLE.key)
    }

    private fun givenUnreachableAlarmOn() {
        whenever(config.APS).thenReturn(true)
        whenever(preferences.get(BooleanKey.AlertPumpUnreachable)).thenReturn(true)
        whenever(preferences.get(IntKey.AlertsPumpUnreachableThreshold)).thenReturn(30)
        whenever(preferences.get(LocalAlertLongKey.NextPumpDisconnectedAlarm)).thenReturn(now - 1000)
    }

    @Test
    fun `preSnoozeAlarms sets next pump disconnected alarm when expired`() {
        whenever(preferences.get(LocalAlertLongKey.NextPumpDisconnectedAlarm)).thenReturn(now - 1000)

        localAlertUtils.preSnoozeAlarms()

        verify(preferences).put(LocalAlertLongKey.NextPumpDisconnectedAlarm, now + 5 * 60 * 1000)
    }

    @Test
    fun `preSnoozeAlarms does not update pump disconnected alarm when not expired`() {
        val futureTime = now + 10000
        whenever(preferences.get(LocalAlertLongKey.NextPumpDisconnectedAlarm)).thenReturn(futureTime)

        localAlertUtils.preSnoozeAlarms()

        verify(preferences, never()).put(eq(LocalAlertLongKey.NextPumpDisconnectedAlarm), any())
    }

    @Test
    fun `preSnoozeAlarms snoozes an expired pump alarm by five minutes`() {
        whenever(preferences.get(LocalAlertLongKey.NextPumpDisconnectedAlarm)).thenReturn(now - 2000)

        localAlertUtils.preSnoozeAlarms()

        verify(preferences).put(LocalAlertLongKey.NextPumpDisconnectedAlarm, now + 5 * 60 * 1000)
    }

    @Test
    fun `shortenSnoozeInterval limits pump disconnected alarm to threshold`() {
        val thresholdMinutes = 20
        val farFutureAlarm = now + T.hours(10).msecs()

        whenever(preferences.get(IntKey.AlertsPumpUnreachableThreshold)).thenReturn(thresholdMinutes)
        whenever(preferences.get(LocalAlertLongKey.NextPumpDisconnectedAlarm)).thenReturn(farFutureAlarm)

        localAlertUtils.shortenSnoozeInterval()

        verify(preferences).put(LocalAlertLongKey.NextPumpDisconnectedAlarm, now + T.mins(thresholdMinutes.toLong()).msecs())
    }

    @Test
    fun `shortenSnoozeInterval brings a far pump alarm back to its threshold`() {
        val missedReadingsThreshold = 25
        val pumpUnreachableThreshold = 35

        whenever(preferences.get(IntKey.AlertsPumpUnreachableThreshold)).thenReturn(pumpUnreachableThreshold)
        whenever(preferences.get(LocalAlertLongKey.NextPumpDisconnectedAlarm)).thenReturn(now + T.hours(2).msecs())

        localAlertUtils.shortenSnoozeInterval()

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
        whenever(preferences.get(LocalAlertLongKey.NextPumpDisconnectedAlarm)).thenReturn(now - 1000)

        localAlertUtils.preSnoozeAlarms()

        val expectedSnooze = now + 5 * 60 * 1000
        verify(preferences).put(LocalAlertLongKey.NextPumpDisconnectedAlarm, expectedSnooze)
    }

    @Test
    fun `shortenSnoozeInterval handles edge case with zero threshold`() {
        whenever(preferences.get(IntKey.AlertsPumpUnreachableThreshold)).thenReturn(0)
        whenever(preferences.get(LocalAlertLongKey.NextPumpDisconnectedAlarm)).thenReturn(now + 1000)

        localAlertUtils.shortenSnoozeInterval()

        verify(preferences).put(LocalAlertLongKey.NextPumpDisconnectedAlarm, now)
    }

    @Test
    fun `shortenSnoozeInterval uses minimum of current alarm and threshold`() {
        val thresholdMinutes = 30
        val currentAlarmClose = now + T.mins(10).msecs()
        val currentAlarmFar = now + T.mins(50).msecs()

        whenever(preferences.get(IntKey.AlertsPumpUnreachableThreshold)).thenReturn(thresholdMinutes)

        // Test with close alarm - should keep it
        whenever(preferences.get(LocalAlertLongKey.NextPumpDisconnectedAlarm)).thenReturn(currentAlarmClose)
        localAlertUtils.shortenSnoozeInterval()
        verify(preferences).put(LocalAlertLongKey.NextPumpDisconnectedAlarm, currentAlarmClose)

        // Test with far alarm - should shorten it
        whenever(preferences.get(LocalAlertLongKey.NextPumpDisconnectedAlarm)).thenReturn(currentAlarmFar)
        localAlertUtils.shortenSnoozeInterval()
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

}
