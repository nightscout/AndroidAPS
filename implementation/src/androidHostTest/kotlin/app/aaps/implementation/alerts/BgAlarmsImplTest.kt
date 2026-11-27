package app.aaps.implementation.alerts

import app.aaps.core.data.model.GV
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.interfaces.alerts.Alert
import app.aaps.core.interfaces.alerts.AlertChoice
import app.aaps.core.interfaces.alerts.SharedSilence
import app.aaps.core.interfaces.alerts.AlertKind
import app.aaps.core.interfaces.alerts.AlertSetting
import app.aaps.core.interfaces.alerts.AlertSound
import app.aaps.core.interfaces.alerts.AlertSeverity
import app.aaps.core.interfaces.alerts.Alerts
import app.aaps.core.interfaces.alerts.BgAlarmRule
import app.aaps.core.interfaces.alerts.BgAlarmRules
import app.aaps.core.interfaces.alerts.BgAlert
import app.aaps.core.interfaces.alerts.BgDirection
import app.aaps.core.interfaces.alerts.BgRuleType
import app.aaps.core.interfaces.aps.APSResult
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.nsclient.ProcessedDeviceStatusData
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.keys.IntKey
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

        override fun setChoice(setting: AlertSetting, choice: AlertChoice) {}
        override fun clearChoice(kind: AlertKind) {}
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

        /** Until when each key is silenced, as the test sets it. */
        val silenced = mutableMapOf<String, Long>()
        val snoozed = mutableListOf<Pair<String, Int>>()
        override fun snooze(source: String, key: String, minutes: Int) {
            snoozed += key to minutes
        }

        override val pauses: StateFlow<Map<String, Long>> = MutableStateFlow(emptyMap())
        override fun pause(kinds: Collection<AlertKind>, until: Long) {}
        override fun resume(kinds: Collection<AlertKind>) {}
        override val sounds: StateFlow<Map<String, AlertSound>> = MutableStateFlow(emptyMap())
        override fun setSound(setting: AlertSetting, sound: AlertSound?) {}
        override fun test(kind: AlertKind, severity: AlertSeverity, vibrate: Boolean): Boolean = true
        override fun silenceEverywhere(source: String, key: String, minutes: Int) {}
        override fun canSilenceEverywhere(alert: Alert): Boolean = false
        override fun applySharedSilence(silence: SharedSilence) {}
        override fun silencedUntil(source: String, key: String): Long = silenced[key] ?: 0L
        override fun act(source: String, key: String) {}
        val booked = mutableListOf<Pair<Alert, Long>>()
        override fun schedule(alert: Alert, at: Long): Boolean {
            booked += alert to at
            return true
        }

        override fun unschedule(source: String, key: String) {}
    }

    private val persistenceLayer: PersistenceLayer = mock()
    private val preferences: Preferences = mock()
    private val profileUtil: ProfileUtil = mock()
    private val notificationManager: NotificationManager = mock()
    private val rh: TextResolver = mock()
    private val dateUtil: DateUtil = mock()
    private val config: Config = mock()
    private val loop: Loop = mock()
    private val processedDeviceStatusData: ProcessedDeviceStatusData = mock()
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
        sut = BgAlarmsImpl(persistenceLayer, preferences, rules, alerts, profileUtil, mock<DecimalFormatter>(), notificationManager, rh, dateUtil, config, loop, processedDeviceStatusData, mock<RxBus>(), aapsLogger, mock<CoroutineScope>())
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
        assertThat(alert.silenceMinutes).isEqualTo(15)
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

    private fun low(threshold: Double, id: String = "low$threshold", severity: AlertSeverity = AlertSeverity.WARNING) =
        BgAlarmRule(id = id, direction = BgDirection.LOW, thresholdMgdl = threshold, severity = severity)

    private fun high(threshold: Double, duration: Int = 0) =
        BgAlarmRule(id = "high$threshold", direction = BgDirection.HIGH, thresholdMgdl = threshold, durationMinutes = duration)

    /** The silence where the user does not pick one is the kind's (decided 2026-11-26), not a setting of the rule. */
    @Test
    fun `a low rule rings as the low kind with its own severity and the kind's silence`() {
        ruleList.value = listOf(low(70.0))
        readings(80.0, 75.0, 68.0)

        evaluate()

        val alert = alerts.raised.single()
        assertThat(alert.kind).isEqualTo(BgAlert.LOW)
        assertThat(alert.key).isEqualTo(BgAlarmsImpl.KEY_LOW)
        assertThat(alert.severity).isEqualTo(AlertSeverity.WARNING)
        assertThat(alert.silenceMinutes).isEqualTo(30)
        assertThat(alert.breakThroughDnd).isFalse()
    }

    @Test
    fun `a high rule rings under its own key`() {
        ruleList.value = listOf(high(250.0))
        readings(240.0, 255.0)

        evaluate()

        assertThat(alerts.raised.single().kind).isEqualTo(BgAlert.HIGH)
        assertThat(alerts.raised.single().key).isEqualTo(BgAlarmsImpl.KEY_HIGH)
        // A high can wait longer than a low
        assertThat(alerts.raised.single().silenceMinutes).isEqualTo(60)
    }

    /**
     * A level rule rings at the first reading past its threshold, also one that came with a delay from somewhere
     * (decided 2026-11-27): a false low ends by itself, "only if it stays high" is persistent high.
     */
    @Test
    fun `a level rule rings at the first reading past its threshold whatever delay it has`() {
        ruleList.value = listOf(high(250.0, duration = 15), low(80.0).copy(durationMinutes = 15))
        readings(200.0, 255.0)
        evaluate()
        assertThat(alerts.raised.single().raisedAt).isEqualTo(now)

        alerts.raised.clear()
        readings(100.0, 75.0)
        evaluate()
        assertThat(alerts.raised.single().kind).isEqualTo(BgAlert.LOW)
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

    /** An edited threshold applies at once: the alert of the rule as it was must not stay (found on the Pixel). */
    @Test
    fun `an edited rule ends its alert when the readings are clear of the new threshold`() {
        ruleList.value = listOf(low(90.0, id = "r"))
        readings(79.0)
        evaluate()
        assertThat(alerts.raised.single().kind).isEqualTo(BgAlert.LOW)

        ruleList.value = listOf(low(72.0, id = "r"))
        evaluate()

        assertThat(alerts.resolved).containsExactly(BgAlarmsImpl.KEY_LOW)
    }

    // ---- stale data (step 2b) -----------------------------------------------------------------------

    private fun staleOn(thresholdMinutes: Int = 30) {
        whenever(preferences.get(IntKey.AlertsStaleDataThreshold)).thenReturn(thresholdMinutes)
        whenever(dateUtil.timeString(any())).thenReturn("14:35")
    }

    /** Booked whatever this phone chose: its kind is Off by default, and then the core shows nothing and books nothing on the phone. */
    @Test
    fun `the stale data alarm is booked at the last reading plus the threshold`() {
        staleOn()
        readings(100.0)

        evaluate()

        val (alert, at) = alerts.booked.single()
        assertThat(alert.kind).isEqualTo(BgAlert.STALE)
        assertThat(at).isEqualTo(now + 30 * minute)
        // The same gap is the same alert, so a silence of it holds
        assertThat(alert.raisedAt).isEqualTo(at)
    }

    @Test
    fun `the same booking is not made again`() {
        staleOn()
        readings(100.0)

        evaluate()
        evaluate()

        assertThat(alerts.booked).hasSize(1)
    }

    /**
     * #5495: after a cold start the newest stored reading is old only because the app was not running. That must not
     * ring at once: the alarm waits a grace time for the first reading.
     */
    @Test
    fun `after a start the stale data alarm waits for the first reading`() {
        staleOn()
        sut.startedAt = now
        readings(100.0, newestAt = now - 45 * minute)

        evaluate()

        assertThat(alerts.booked.single().second).isEqualTo(now + BgAlarmsImpl.STALE_GRACE_MS)
    }

    @Test
    fun `a reading in time ends the stale data alarm and books the next one`() {
        staleOn()
        alerts.raise(Alert(kind = BgAlert.STALE, key = BgAlarmsImpl.KEY_STALE, severity = AlertSeverity.CRITICAL, title = BgAlert.STALE.title, raisedAt = now - minute))
        readings(100.0)

        evaluate()

        assertThat(alerts.resolved).contains(BgAlarmsImpl.KEY_STALE)
        assertThat(alerts.booked.single().second).isEqualTo(now + 30 * minute)
    }

    // ---- persistent high, rate and predicted low (step 2c) -------------------------------------------

    private fun persistentHigh(threshold: Double, duration: Int) =
        BgAlarmRule(id = "ph$threshold", type = BgRuleType.PERSISTENT_HIGH, direction = BgDirection.HIGH, thresholdMgdl = threshold, durationMinutes = duration)

    private fun rate(direction: BgDirection, perMinute: Double) =
        BgAlarmRule(id = "rate$direction$perMinute", type = BgRuleType.RATE, direction = direction, thresholdMgdl = perMinute)

    private fun predictedLow(threshold: Double, aheadMinutes: Int = 30) =
        BgAlarmRule(id = "pl$threshold", type = BgRuleType.PREDICTED_LOW, direction = BgDirection.LOW, thresholdMgdl = threshold, durationMinutes = aheadMinutes)

    /** Readings every [stepMinutes], the newest now: [first], then [perStep] more each step, [count] readings. */
    private fun line(first: Double, perStep: Double, count: Int, stepMinutes: Int = 5) {
        readings = (0 until count).map { i -> gv(now - (count - 1 - i) * stepMinutes * minute, first + i * perStep) }.reversed()
    }

    @Test
    fun `persistent high rings after its time with regular readings`() {
        ruleList.value = listOf(persistentHigh(180.0, duration = 30))
        readings(170.0, 200.0, 200.0, 200.0, 200.0, 200.0, 200.0, 200.0)

        evaluate()

        val alert = alerts.raised.single()
        assertThat(alert.kind).isEqualTo(BgAlert.PERSISTENT_HIGH)
        assertThat(alert.key).isEqualTo(BgAlarmsImpl.KEY_PERSISTENT_HIGH)
        assertThat(alert.raisedAt).isEqualTo(now - 30 * minute)
    }

    @Test
    fun `persistent high does not ring while BG comes down`() {
        ruleList.value = listOf(persistentHigh(180.0, duration = 30))
        line(first = 260.0, perStep = -5.0, count = 8)

        evaluate()

        assertThat(alerts.raised).isEmpty()
    }

    @Test
    fun `persistent high does not ring with a gap in the readings`() {
        ruleList.value = listOf(persistentHigh(180.0, duration = 30))
        readings = listOf(0, 5, 10, 22, 27, 30).map { gv(now - it * minute, 200.0) }

        evaluate()

        assertThat(alerts.raised).isEmpty()
    }

    @Test
    fun `falling fast rings when the average and the last step are both past the rate`() {
        ruleList.value = listOf(rate(BgDirection.LOW, 1.5))
        line(first = 150.0, perStep = -10.0, count = 7)

        evaluate()

        val alert = alerts.raised.single()
        assertThat(alert.kind).isEqualTo(BgAlert.FALLING_FAST)
        assertThat(alert.key).isEqualTo(BgAlarmsImpl.KEY_FALLING)
    }

    @Test
    fun `falling fast works the same with 1-minute readings`() {
        ruleList.value = listOf(rate(BgDirection.LOW, 1.5))
        line(first = 150.0, perStep = -2.0, count = 31, stepMinutes = 1)

        evaluate()

        assertThat(alerts.raised.single().kind).isEqualTo(BgAlert.FALLING_FAST)
    }

    /** A fall that has stopped does not ring, though the average over 15 minutes is still steep. */
    @Test
    fun `falling fast does not ring when the last step is flat`() {
        ruleList.value = listOf(rate(BgDirection.LOW, 1.5))
        readings(150.0, 140.0, 130.0, 120.0, 110.0, 100.0, 100.0)

        evaluate()

        assertThat(alerts.raised).isEmpty()
    }

    /** One noisy jump is not a rise: the average over 15 minutes stays small. */
    @Test
    fun `one jump does not ring rising fast`() {
        ruleList.value = listOf(rate(BgDirection.HIGH, 2.0))
        readings(120.0, 120.0, 121.0, 120.0, 121.0, 120.0, 135.0)

        evaluate()

        assertThat(alerts.raised).isEmpty()
    }

    @Test
    fun `the rate is per minute, the line through the readings`() {
        line(first = 100.0, perStep = 10.0, count = 4)

        assertThat(BgAlarmsImpl.averageRate(readings, BgAlarmsImpl.RATE_WINDOW_MS, 10 * minute)).isWithin(0.001).of(2.0)
        // Too short a span makes no rate
        assertThat(BgAlarmsImpl.averageRate(readings.take(2), BgAlarmsImpl.RATE_WINDOW_MS, 10 * minute)).isNull()
    }

    private fun prediction(vararg values: Double, at: Long = now, sensor: SourceSensor = SourceSensor.IOB_PREDICTION): APSResult {
        val result: APSResult = mock()
        whenever(result.date).thenReturn(at)
        whenever(result.predictionsAsGv).thenReturn(
            values.mapIndexed { i, v -> GV(timestamp = at + i * 5 * minute, value = v, raw = null, noise = null, trendArrow = TrendArrow.NONE, sourceSensor = sensor) }.toMutableList()
        )
        return result
    }

    private fun masterPrediction(result: APSResult) {
        whenever(config.APS).thenReturn(true)
        whenever(loop.lastRun).thenReturn(Loop.LastRun().apply { constraintsProcessed = result })
    }

    @Test
    fun `predicted low rings when the loop predicts a low within the time ahead`() {
        whenever(dateUtil.timeString(any())).thenReturn("14:35")
        ruleList.value = listOf(predictedLow(70.0, aheadMinutes = 30))
        readings(100.0)
        masterPrediction(prediction(100.0, 93.0, 86.0, 79.0, 72.0, 65.0))

        evaluate()

        val alert = alerts.raised.single()
        assertThat(alert.kind).isEqualTo(BgAlert.PREDICTED_LOW)
        assertThat(alert.key).isEqualTo(BgAlarmsImpl.KEY_PREDICTED_LOW)
    }

    @Test
    fun `a low predicted later than the time ahead does not ring`() {
        ruleList.value = listOf(predictedLow(70.0, aheadMinutes = 20))
        readings(100.0)
        masterPrediction(prediction(100.0, 93.0, 86.0, 79.0, 72.0, 65.0))

        evaluate()

        assertThat(alerts.raised).isEmpty()
    }

    /** Already low is the level rules' job; a predicted low then would ring twice for one low. */
    @Test
    fun `predicted low does not ring when BG is already below the threshold`() {
        ruleList.value = listOf(predictedLow(70.0))
        readings(68.0)
        masterPrediction(prediction(68.0, 64.0, 60.0))

        evaluate()

        assertThat(alerts.raised.map { it.kind }).doesNotContain(BgAlert.PREDICTED_LOW)
    }

    @Test
    fun `an old prediction is not used`() {
        ruleList.value = listOf(predictedLow(70.0))
        readings(100.0)
        masterPrediction(prediction(100.0, 80.0, 60.0, at = now - 20 * minute))

        evaluate()

        assertThat(alerts.raised).isEmpty()
    }

    /** With carbs on board the carbs curve leads: the insulin curve alone would ring after every meal. */
    @Test
    fun `the carbs curve is used when there is one`() {
        ruleList.value = listOf(predictedLow(70.0))
        readings(100.0)
        val result = prediction(100.0, 80.0, 60.0)
        val iob = result.predictionsAsGv
        val cob = prediction(100.0, 110.0, 120.0, sensor = SourceSensor.COB_PREDICTION).predictionsAsGv
        val both = (iob + cob).toMutableList()
        whenever(result.predictionsAsGv).thenReturn(both)
        masterPrediction(result)

        evaluate()

        assertThat(alerts.raised).isEmpty()
    }

    @Test
    fun `a client uses the master's prediction`() {
        whenever(dateUtil.timeString(any())).thenReturn("14:35")
        whenever(config.APS).thenReturn(false)
        ruleList.value = listOf(predictedLow(70.0))
        readings(100.0)
        val result = prediction(100.0, 80.0, 60.0)
        whenever(processedDeviceStatusData.getAPSResult()).thenReturn(result)

        evaluate()

        assertThat(alerts.raised.single().kind).isEqualTo(BgAlert.PREDICTED_LOW)
    }

    @Test
    fun `old rules without a type are level rules and every type is stored`() {
        val impl = BgAlarmRulesImpl(preferences, aapsLogger, mock<CoroutineScope>())
        val old = impl.parse("""[{"id":"a","direction":"LOW","thresholdMgdl":70.0,"severity":"WARNING"}]""")
        assertThat(old.single().type).isEqualTo(BgRuleType.LEVEL)

        val all = listOf(persistentHigh(180.0, 60), rate(BgDirection.LOW, 3.0), rate(BgDirection.HIGH, 2.5), predictedLow(70.0))
        assertThat(impl.parse(impl.toJson(all))).isEqualTo(all)
    }

    @Test
    fun `a rule that does not fit its type is dropped when read`() {
        val impl = BgAlarmRulesImpl(preferences, aapsLogger, mock<CoroutineScope>())

        val rules = impl.parse("""[{"id":"a","type":"PREDICTED_LOW","direction":"HIGH","thresholdMgdl":70.0,"severity":"WARNING"}]""")

        assertThat(rules).isEmpty()
    }

    // ---- smart silence (step 2d) --------------------------------------------------------------------

    @Test
    fun `a silenced low does not ring again while BG comes back up`() {
        ruleList.value = listOf(low(70.0))
        readings(60.0, 62.0, 64.0, 66.0, 68.0)
        alerts.silenced[BgAlarmsImpl.KEY_LOW] = now + 3 * minute

        evaluate()

        assertThat(alerts.snoozed).containsExactly(BgAlarmsImpl.KEY_LOW to 3 + BgAlarmsImpl.SMART_SILENCE_EXTEND_MINUTES)
    }

    @Test
    fun `a silenced high does not ring again while BG comes down`() {
        ruleList.value = listOf(high(250.0))
        readings(290.0, 285.0, 280.0, 275.0, 270.0)
        alerts.silenced[BgAlarmsImpl.KEY_HIGH] = now + 2 * minute

        evaluate()

        assertThat(alerts.snoozed.single().first).isEqualTo(BgAlarmsImpl.KEY_HIGH)
    }

    @Test
    fun `a silenced low rings again when BG does not come back`() {
        ruleList.value = listOf(low(70.0))
        readings(65.0, 65.0, 66.0, 65.0, 65.0)
        alerts.silenced[BgAlarmsImpl.KEY_LOW] = now + 3 * minute

        evaluate()

        assertThat(alerts.snoozed).isEmpty()
    }

    /** Only at the end of the silence: before that the next readings decide. */
    @Test
    fun `smart silence waits for the end of the silence`() {
        ruleList.value = listOf(low(70.0))
        readings(60.0, 62.0, 64.0, 66.0, 68.0)
        alerts.silenced[BgAlarmsImpl.KEY_LOW] = now + 20 * minute

        evaluate()

        assertThat(alerts.snoozed).isEmpty()
    }

    @Test
    fun `urgent low is never silenced longer`() {
        readings(40.0, 42.0, 44.0, 46.0, 48.0)
        alerts.silenced[BgAlarmsImpl.KEY_LOW] = now + 3 * minute

        evaluate()

        assertThat(alerts.raised.last().kind).isEqualTo(BgAlert.URGENT_LOW)
        assertThat(alerts.snoozed).isEmpty()
    }
}
