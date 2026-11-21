package app.aaps.pump.aaruy.services

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.TemporaryBasalStorage
import app.aaps.core.interfaces.pump.alerts.PumpAlert
import app.aaps.core.interfaces.pump.alerts.PumpAlertLifetime
import app.aaps.core.interfaces.pump.alerts.PumpAlertSeverity
import app.aaps.core.interfaces.pump.alerts.PumpAlerts
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.aaruy.AaruyPump
import app.aaps.pump.aaruy.common.enums.AlarmState
import app.aaps.pump.aaruy.keys.AaruyStringKey
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.RETURNS_SMART_NULLS
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

/** The alarms of the pump reach the user as pump alerts (acceptance A1-A6). */
class AaruyAlertsTest {

    /** Keeps what was raised and resolved, as the core would. */
    private class RecordingPumpAlerts : PumpAlerts {

        private val _active = MutableStateFlow<List<PumpAlert>>(emptyList())
        override val active: StateFlow<List<PumpAlert>> = _active
        override val sounding: PumpAlert? = null
        val raised = mutableListOf<PumpAlert>()
        val resolved = mutableListOf<String>()

        override fun raise(alert: PumpAlert) {
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

        override fun silence(source: String, key: String) = Unit
        override fun snooze(source: String, key: String, minutes: Int) = Unit
        override fun act(source: String, key: String) = Unit
    }

    private var now = 1_000L
    private val dateUtil: DateUtil = mock(DateUtil::class.java).also { whenever(it.now()).thenAnswer { now } }
    private val preferences: Preferences = mock(Preferences::class.java, RETURNS_SMART_NULLS)
    private val commandQueue: CommandQueue = mock(CommandQueue::class.java)
    private val pump = AaruyPump(
        mock(AAPSLogger::class.java), mock(ResourceHelper::class.java), preferences, dateUtil,
        mock(PumpSync::class.java), mock(TemporaryBasalStorage::class.java), mock(BolusProgressData::class.java)
    )
    private val pumpAlerts = RecordingPumpAlerts()
    private val alerts = AaruyAlerts(pumpAlerts, commandQueue, pump, dateUtil, mock(AAPSLogger::class.java))
    private val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())

    @AfterEach
    fun tearDown() = scope.cancel()

    private fun alert(alarm: AlarmState): PumpAlert = pumpAlerts.active.value.single { it.key == alarm.name }

    /** A blocked motor repeats until it is confirmed. Seen again, it keeps its first time, so a silence holds. */
    @Test
    fun anAlarmThePumpRepeatsKeepsTheTimeItWasFirstSeen() {
        alerts.start(scope)
        pump.addAlarm(AlarmState.PUMP_MOTOR_BLOCK)
        now = 5_000L
        pump.addAlarm(AlarmState.PUMP_MOTOR_BLOCK)

        val alert = alert(AlarmState.PUMP_MOTOR_BLOCK)
        assertThat(alert.source).isEqualTo(AaruyAlerts.SOURCE)
        assertThat(alert.raisedAt).isEqualTo(1_000L)
        assertThat(alert.lifetime).isEqualTo(PumpAlertLifetime.CONDITION)
    }

    /** A3: delivery stopped or not safe rings, a problem for soon chimes, a refused command is information. */
    @Test
    fun theSeverityFollowsWhatThePumpDoes() {
        assertThat(AaruyAlerts.severityOf(AlarmState.PUMP_MOTOR_BLOCK)).isEqualTo(PumpAlertSeverity.CRITICAL)
        assertThat(AaruyAlerts.severityOf(AlarmState.PUMP_NO_RESERVOIR)).isEqualTo(PumpAlertSeverity.CRITICAL)
        assertThat(AaruyAlerts.severityOf(AlarmState.PUMP_ZERO_RESERVOIR)).isEqualTo(PumpAlertSeverity.CRITICAL)
        assertThat(AaruyAlerts.severityOf(AlarmState.PUMP_STOP_TOO_LONG)).isEqualTo(PumpAlertSeverity.CRITICAL)
        assertThat(AaruyAlerts.severityOf(AlarmState.PUMP_MOTOR_FAILED_ERROR)).isEqualTo(PumpAlertSeverity.CRITICAL)
        assertThat(AaruyAlerts.severityOf(AlarmState.PUMP_LOW_RESERVOIR)).isEqualTo(PumpAlertSeverity.WARNING)
        assertThat(AaruyAlerts.severityOf(AlarmState.PUMP_LOW_POWER)).isEqualTo(PumpAlertSeverity.WARNING)
        assertThat(AaruyAlerts.severityOf(AlarmState.PUMP_STOP_15M_LATER)).isEqualTo(PumpAlertSeverity.WARNING)
        assertThat(AaruyAlerts.severityOf(AlarmState.PUMP_RUN_BOLUS)).isEqualTo(PumpAlertSeverity.INFO)
    }

    /** A5: the confirm on the pump is the action of a pump alarm, through the queue. A stop has no step on the pump. */
    @Test
    fun theConfirmOnThePumpIsTheActionThroughTheQueue(): Unit = runBlocking {
        alerts.start(scope)
        pump.addAlarm(AlarmState.PUMP_MOTOR_BLOCK)
        pump.addAlarm(AlarmState.PUMP_STOP_TOO_LONG)
        val confirmed = mock(PumpEnactResult::class.java).also { whenever(it.success).thenReturn(true) }
        whenever(commandQueue.customCommand(any<AaruyConfirmAlarmsCommand>())).thenReturn(confirmed)

        assertThat(alert(AlarmState.PUMP_MOTOR_BLOCK).action!!.run()).isTrue()
        assertThat(alert(AlarmState.PUMP_STOP_TOO_LONG).action).isNull()
    }

    /** A6: the pump dropped it, or another pump came: its alert ends. */
    @Test
    fun anAlarmThePumpNoLongerHoldsIsResolved() {
        alerts.start(scope)
        pump.addAlarm(AlarmState.PUMP_LOW_RESERVOIR)
        pump.addAlarm(AlarmState.PUMP_STOP_TOO_LONG)

        pump.removeAlarm(AlarmState.PUMP_STOP_TOO_LONG)
        assertThat(pumpAlerts.resolved).containsExactly(AlarmState.PUMP_STOP_TOO_LONG.name)

        pump.clearAllAlarms()
        assertThat(pumpAlerts.active.value).isEmpty()
    }

    /** A4: what the pump still holds comes back at an app start, with the time it was first seen. */
    @Test
    fun storedAlarmsAreRaisedAgainAtStartWithTheirTime() {
        whenever(preferences.get(AaruyStringKey.PumpActiveAlarm)).thenReturn("PUMP_MOTOR_BLOCK@1234")
        now = 99_000L
        pump.loadVarsFromSP()
        alerts.start(scope)

        assertThat(alert(AlarmState.PUMP_MOTOR_BLOCK).raisedAt).isEqualTo(1234L)
    }

    /** A refused command is over when the user saw it. Each one is new. */
    @Test
    fun aRefusedCommandIsAnEventAtTheTimeItCame() {
        alerts.start(scope)
        now = 7_000L
        pump.warn(AlarmState.PUMP_RUN_TMP_BASAL)

        val alert = alert(AlarmState.PUMP_RUN_TMP_BASAL)
        assertThat(alert.lifetime).isEqualTo(PumpAlertLifetime.EVENT)
        assertThat(alert.severity).isEqualTo(PumpAlertSeverity.INFO)
        assertThat(alert.raisedAt).isEqualTo(7_000L)
    }

    /** A6: the driver stops, as when another pump is chosen. */
    @Test
    fun stopResolvesEveryAlert() {
        alerts.start(scope)
        pump.addAlarm(AlarmState.PUMP_MOTOR_BLOCK)
        pump.warn(AlarmState.PUMP_RUN_BOLUS)

        alerts.stop()
        assertThat(pumpAlerts.active.value).isEmpty()
    }
}
