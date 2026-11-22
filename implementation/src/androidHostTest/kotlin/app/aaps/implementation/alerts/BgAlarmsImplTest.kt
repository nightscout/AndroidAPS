package app.aaps.implementation.alerts

import app.aaps.core.data.model.GV
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.interfaces.alerts.Alert
import app.aaps.core.interfaces.alerts.AlertChoice
import app.aaps.core.interfaces.alerts.AlertKind
import app.aaps.core.interfaces.alerts.AlertSeverity
import app.aaps.core.interfaces.alerts.Alerts
import app.aaps.core.interfaces.alerts.BgAlarmRule
import app.aaps.core.interfaces.alerts.BgAlarmRules
import app.aaps.core.interfaces.alerts.BgAlert
import app.aaps.core.interfaces.alerts.BgDirection
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/** The urgent low BG alarm (`_docs/ALARMS.md`, step 2a): when it rings, when it ends, and what it carries. */
class BgAlarmsImplTest : TestBase() {

    /** What the evaluator asks of the alert core. */
    private class RecordingAlerts : Alerts {

        private val _active = MutableStateFlow<List<Alert>>(emptyList())
        override val active: StateFlow<List<Alert>> = _active
        override val sounding: Alert? = null
        override val choices: StateFlow<Map<String, AlertChoice>> = MutableStateFlow(emptyMap())
        val raised = mutableListOf<Alert>()
        val resolved = mutableListOf<String>()

        override fun setChoice(kind: AlertKind, choice: AlertChoice) {}
        override fun raise(alert: Alert) {
            raised += alert
            _active.value = _active.value.filterNot { it.source == alert.source && it.key == alert.key } + alert
        }

        override fun resolve(source: String, key: String) {
            resolved += key
            _active.value = _active.value.filterNot { it.source == source && it.key == key }
        }

        override fun resolveAll(source: String) {}
        override fun silence(source: String, key: String, minutes: Int?) {}
        override fun snooze(source: String, key: String, minutes: Int) {}
        override fun act(source: String, key: String) {}
        override fun schedule(alert: Alert, at: Long): Boolean = true
        override fun unschedule(source: String, key: String) {}
    }

    private val persistenceLayer: PersistenceLayer = mock()
    private val preferences: Preferences = mock()
    private val profileUtil: ProfileUtil = mock()
    private val notificationManager: NotificationManager = mock()
    private val rh: TextResolver = mock()
    private val dateUtil: DateUtil = mock()
    private val config: Config = mock()
    private val alerts = RecordingAlerts()
    private val ruleList = MutableStateFlow<List<BgAlarmRule>>(emptyList())
    private val rules = object : BgAlarmRules {
        override val rules: StateFlow<List<BgAlarmRule>> = ruleList
        override fun save(rules: List<BgAlarmRule>) {}
    }

    private val now = 1_000_000_000L
    private val minute = 60_000L
    private var readings = listOf<GV>()

    private lateinit var sut: BgAlarmsImpl

    @BeforeEach
    fun setUp() {
        whenever(dateUtil.now()).thenReturn(now)
        whenever(preferences.get(UnitDoubleKey.AlertUrgentLow)).thenReturn(54.0)
        doAnswer { it.arguments[0] as Double }.whenever(profileUtil).convertToMgdlDetect(any())
        whenever(profileUtil.fromMgdlToStringWithUnits(anyOrNull())).thenReturn("50 mg/dl")
        runBlocking { whenever(persistenceLayer.getBgReadingsDataFromTime(any(), eq(false))).thenAnswer { readings } }
        sut = BgAlarmsImpl(persistenceLayer, preferences, rules, alerts, profileUtil, notificationManager, rh, dateUtil, config, aapsLogger, mock<CoroutineScope>())
    }

    /** Readings every 5 minutes, the newest [values] last, the newest one now. */
    private fun readings(vararg values: Double, newestAt: Long = now) {
        readings = values.reversed().mapIndexed { i, v -> gv(newestAt - i * 5 * minute, v) }
    }

    private fun gv(at: Long, value: Double) = GV(
        timestamp = at, value = value, raw = null, noise = null, trendArrow = TrendArrow.FLAT, sourceSensor = SourceSensor.UNKNOWN
    )

    private fun evaluate() = runBlocking { sut.evaluate() }

    @Test
    fun `a reading below the threshold rings urgent low`() {
        readings(70.0, 60.0, 50.0)

        evaluate()

        val alert = alerts.raised.single()
        assertThat(alert.kind).isEqualTo(BgAlert.URGENT_LOW)
        assertThat(alert.severity).isEqualTo(AlertSeverity.CRITICAL)
        // Rings also in Do Not Disturb, and is silenced only briefly
        assertThat(alert.breakThroughDnd).isTrue()
        assertThat(alert.silenceMinutes).isEqualTo(BgAlarmsImpl.URGENT_LOW_SILENCE_MINUTES)
    }

    @Test
    fun `nothing rings above the threshold`() {
        readings(70.0, 60.0, 55.0)

        evaluate()

        assertThat(alerts.raised).isEmpty()
    }

    /** The occurrence is the episode: the same episode is the same alert after a restart, so its silence holds. */
    @Test
    fun `the alert's time is the first reading of the low episode`() {
        readings(80.0, 57.0, 52.0, 50.0)

        evaluate()

        // 57 is within the end margin, so the episode started there, 10 minutes ago
        assertThat(alerts.raised.single().raisedAt).isEqualTo(now - 10 * minute)
    }

    @Test
    fun `it ends only a little above the threshold`() {
        readings(50.0)
        evaluate()

        readings(50.0, 57.0)
        evaluate()
        assertThat(alerts.resolved).isEmpty()

        readings(50.0, 57.0, 60.0)
        evaluate()
        assertThat(alerts.resolved).containsExactly(BgAlarmsImpl.KEY_LOW)
    }

    /** Old data is the stale data alarm's job; a level alarm neither starts nor ends on it. */
    @Test
    fun `old readings change nothing`() {
        readings(50.0, newestAt = now - 20 * minute)

        evaluate()

        assertThat(alerts.raised).isEmpty()
    }

    /** `_docs/CGM_DESIGN.md`, section 3: a reading from the future neither raises nor ends an alarm. */
    @Test
    fun `a reading from the future is not used`() {
        readings = listOf(gv(now + 10 * minute, 40.0), gv(now, 80.0))

        evaluate()

        assertThat(alerts.raised).isEmpty()
    }

    // ---- the user's own rules (step 2a-2) ----------------------------------------------------------

    private fun low(threshold: Double, id: String = "low$threshold", duration: Int = 0, severity: AlertSeverity = AlertSeverity.WARNING) =
        BgAlarmRule(id = id, direction = BgDirection.LOW, thresholdMgdl = threshold, severity = severity, durationMinutes = duration, silenceMinutes = 45)

    private fun high(threshold: Double, duration: Int = 0) =
        BgAlarmRule(id = "high$threshold", direction = BgDirection.HIGH, thresholdMgdl = threshold, durationMinutes = duration, silenceMinutes = 120)

    @Test
    fun `a low rule rings as the low kind with its own severity and silence`() {
        ruleList.value = listOf(low(70.0))
        readings(80.0, 75.0, 68.0)

        evaluate()

        val alert = alerts.raised.single()
        assertThat(alert.kind).isEqualTo(BgAlert.LOW)
        assertThat(alert.key).isEqualTo(BgAlarmsImpl.KEY_LOW)
        assertThat(alert.severity).isEqualTo(AlertSeverity.WARNING)
        assertThat(alert.silenceMinutes).isEqualTo(45)
        assertThat(alert.breakThroughDnd).isFalse()
    }

    @Test
    fun `a high rule rings under its own key`() {
        ruleList.value = listOf(high(250.0))
        readings(240.0, 255.0)

        evaluate()

        assertThat(alerts.raised.single().kind).isEqualTo(BgAlert.HIGH)
        assertThat(alerts.raised.single().key).isEqualTo(BgAlarmsImpl.KEY_HIGH)
    }

    @Test
    fun `a rule rings only when it lasted long enough`() {
        ruleList.value = listOf(high(250.0, duration = 15))
        readings(200.0, 255.0, 260.0)
        evaluate()
        assertThat(alerts.raised).isEmpty()

        readings(200.0, 255.0, 260.0, 262.0, 265.0)
        evaluate()
        assertThat(alerts.raised.single().raisedAt).isEqualTo(now - 15 * minute)
    }

    @Test
    fun `a disabled rule or one outside its window does not ring`() {
        val from = 600
        ruleList.value = listOf(low(70.0).copy(enabled = false), low(75.0, id = "empty").copy(fromMinute = from, toMinute = from))
        readings(60.0)

        evaluate()

        assertThat(alerts.raised).isEmpty()
    }

    @Test
    fun `a window may cross midnight`() {
        val night = low(70.0).copy(fromMinute = 22 * 60, toMinute = 7 * 60)

        assertThat(night.appliesAt(23 * 60)).isTrue()
        assertThat(night.appliesAt(3 * 60)).isTrue()
        assertThat(night.appliesAt(12 * 60)).isFalse()
    }

    /** Urgent low is the strongest low: it replaces the low rule at once, and rings. */
    @Test
    fun `urgent low takes over from a low rule`() {
        ruleList.value = listOf(low(70.0))
        readings(68.0, newestAt = now - 10 * minute)
        evaluate()

        readings(68.0, 60.0, 50.0)
        evaluate()

        assertThat(alerts.raised.last().kind).isEqualTo(BgAlert.URGENT_LOW)
        assertThat(alerts.raised.last().raisedAt).isNotEqualTo(alerts.raised.first().raisedAt)
    }

    /** Coming back from urgent low into the low range is the same occurrence: it does not ring again. */
    @Test
    fun `a weaker rule taking over keeps the occurrence`() {
        ruleList.value = listOf(low(70.0))
        readings(50.0)
        evaluate()
        val urgentAt = alerts.raised.single().raisedAt

        readings(50.0, 62.0, 65.0)
        evaluate()

        assertThat(alerts.raised.last().kind).isEqualTo(BgAlert.LOW)
        assertThat(alerts.raised.last().raisedAt).isEqualTo(urgentAt)
    }
}