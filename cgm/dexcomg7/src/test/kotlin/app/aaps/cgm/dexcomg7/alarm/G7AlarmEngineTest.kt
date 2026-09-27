package app.aaps.cgm.dexcomg7.alarm

import app.aaps.cgm.dexcomg7.data.G7State
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

class G7AlarmEngineTest {

    private val minute = 60_000L
    private val start = 1_700_000_000_000L
    private val now = start + 3 * 24 * 60 * minute

    private fun config(change: (G7AlarmType, G7AlarmTypeConfig) -> G7AlarmTypeConfig = { _, c -> c }) = G7AlarmConfig(
        enabled = true,
        types = G7AlarmType.entries.associateWith { type ->
            change(
                type,
                G7AlarmTypeConfig(
                    enabled = type.defaultEnabled,
                    levelMgdl = when (type) {
                        G7AlarmType.LOW  -> 70.0
                        G7AlarmType.HIGH -> 250.0
                        else             -> null
                    },
                    rate = 2,
                    delayMinutes = type.delay?.defaultMinutes ?: 0,
                    repeatMinutes = type.defaultRepeatMinutes,
                    sound = type.defaultSound
                )
            )
        }
    )

    private fun state(glucose: Int?, at: Long = now, algorithm: Int = 6, rate: Double? = 0.0) = G7State(
        address = "AA",
        pairingCode = "1234",
        sensorName = "DXCM12",
        activatedAt = start,
        sessionLengthSeconds = 907_200,
        pairedAt = start,
        latestReadingAt = at,
        latestGlucose = glucose,
        latestAlgorithmState = algorithm,
        latestTrendRate = rate
    )

    private fun raises(step: G7AlarmStep) = step.actions.filterIsInstance<G7AlarmAction.Raise>()
    private fun clears(step: G7AlarmStep) = step.actions.filterIsInstance<G7AlarmAction.Clear>().map { it.type }

    @Test
    fun nothingInRange() {
        val step = G7AlarmEngine.step(state(120), config(), G7AlarmRuntime(), now)
        assertThat(step.actions).isEmpty()
        assertThat(step.runtime.raised).isEmpty()
    }

    @Test
    fun lowGoesOffWithSound() {
        val step = G7AlarmEngine.step(state(65), config(), G7AlarmRuntime(), now)
        assertThat(raises(step)).containsExactly(G7AlarmAction.Raise(G7AlarmType.LOW, withSound = true, reminder = false))
        assertThat(step.nextCheckAt).isEqualTo(now + G7AlarmEngine.REMINDER_MS)
    }

    @Test
    fun urgentLowQuietsLowAndUrgentLowSoon() {
        val step = G7AlarmEngine.step(state(50, rate = -2.0), config(), G7AlarmRuntime(), now)
        assertThat(raises(step).map { it.type }).containsExactly(G7AlarmType.URGENT_LOW)
    }

    @Test
    fun urgentLowSoonLooksTwentyMinutesAhead() {
        // 90 - 2 * 20 = 50, below 55. Low (70) is not reached yet.
        val step = G7AlarmEngine.step(state(90, rate = -2.0), config(), G7AlarmRuntime(), now)
        assertThat(raises(step).map { it.type }).containsExactly(G7AlarmType.URGENT_LOW_SOON)
        assertThat(raises(G7AlarmEngine.step(state(90, rate = -1.0), config(), G7AlarmRuntime(), now))).isEmpty()
    }

    @Test
    fun vibrateFirstThenSoundAfterFiveMinutes() {
        val cfg = config { t, c -> if (t == G7AlarmType.LOW) c.copy(vibrateFirst = true) else c }
        val first = G7AlarmEngine.step(state(65), cfg, G7AlarmRuntime(), now)
        assertThat(raises(first)).containsExactly(G7AlarmAction.Raise(G7AlarmType.LOW, withSound = false, reminder = false))

        val tooSoon = G7AlarmEngine.step(state(64, at = now + 4 * minute), cfg, first.runtime, now + 4 * minute)
        assertThat(tooSoon.actions).isEmpty()

        val reminder = G7AlarmEngine.step(state(63, at = now + 5 * minute), cfg, first.runtime, now + 5 * minute)
        assertThat(raises(reminder)).containsExactly(G7AlarmAction.Raise(G7AlarmType.LOW, withSound = true, reminder = true))
    }

    @Test
    fun acknowledgeSnoozesForTheRepeatTime() {
        val raised = G7AlarmEngine.step(state(65), config(), G7AlarmRuntime(), now)
        val ack = G7AlarmEngine.acknowledge(raised.runtime, config(), G7AlarmType.LOW, now + minute)
        assertThat(clears(ack)).containsExactly(G7AlarmType.LOW)
        assertThat(ack.runtime.raised).isEmpty()

        // Low repeats after 15 minutes by default.
        val during = G7AlarmEngine.step(state(64, at = now + 10 * minute), config(), ack.runtime, now + 10 * minute)
        assertThat(during.actions).isEmpty()
        assertThat(during.nextCheckAt).isEqualTo(now + 16 * minute)

        val after = G7AlarmEngine.step(state(64, at = now + 16 * minute), config(), during.runtime, now + 16 * minute)
        assertThat(raises(after)).containsExactly(G7AlarmAction.Raise(G7AlarmType.LOW, withSound = true, reminder = false))
    }

    @Test
    fun repeatNeverStaysQuietUntilTheConditionHasGone() {
        val cfg = config { t, c -> if (t == G7AlarmType.LOW) c.copy(repeatMinutes = 0) else c }
        val raised = G7AlarmEngine.step(state(65), cfg, G7AlarmRuntime(), now)
        val ack = G7AlarmEngine.acknowledge(raised.runtime, cfg, null, now)
        val stillLow = G7AlarmEngine.step(state(60, at = now + 60 * minute), cfg, ack.runtime, now + 60 * minute)
        assertThat(stillLow.actions).isEmpty()

        // Back in range for a moment is not enough...
        val brief = G7AlarmEngine.step(state(80, at = now + 65 * minute), cfg, stillLow.runtime, now + 65 * minute)
        val lowAgain = G7AlarmEngine.step(state(65, at = now + 70 * minute), cfg, brief.runtime, now + 70 * minute)
        assertThat(lowAgain.actions).isEmpty()

        // ...but after 15 minutes in range it may go off again.
        val inRange = G7AlarmEngine.step(state(90, at = now + 75 * minute), cfg, lowAgain.runtime, now + 75 * minute)
        val cleared = G7AlarmEngine.step(state(90, at = now + 90 * minute), cfg, inRange.runtime, now + 90 * minute)
        val newLow = G7AlarmEngine.step(state(65, at = now + 95 * minute), cfg, cleared.runtime, now + 95 * minute)
        assertThat(raises(newLow).map { it.type }).containsExactly(G7AlarmType.LOW)
    }

    @Test
    fun clearsWhenTheConditionGoes() {
        val raised = G7AlarmEngine.step(state(65), config(), G7AlarmRuntime(), now)
        val back = G7AlarmEngine.step(state(80, at = now + 5 * minute), config(), raised.runtime, now + 5 * minute)
        assertThat(clears(back)).containsExactly(G7AlarmType.LOW)
        assertThat(back.runtime.raised).isEmpty()
    }

    @Test
    fun highWaitsForItsDelay() {
        val cfg = config { t, c -> if (t == G7AlarmType.HIGH) c.copy(delayMinutes = 30) else c }
        val first = G7AlarmEngine.step(state(260), cfg, G7AlarmRuntime(), now)
        assertThat(first.actions).isEmpty()
        assertThat(first.nextCheckAt).isAtMost(now + 30 * minute)
        val later = G7AlarmEngine.step(state(270, at = now + 30 * minute), cfg, first.runtime, now + 30 * minute)
        assertThat(raises(later).map { it.type }).containsExactly(G7AlarmType.HIGH)
    }

    @Test
    fun rateAlarmsOnlyWhenTurnedOn() {
        assertThat(raises(G7AlarmEngine.step(state(150, rate = 3.0), config(), G7AlarmRuntime(), now))).isEmpty()
        val cfg = config { t, c -> if (t == G7AlarmType.RISE_RATE) c.copy(enabled = true) else c }
        assertThat(raises(G7AlarmEngine.step(state(150, rate = 3.0), cfg, G7AlarmRuntime(), now)).map { it.type }).containsExactly(G7AlarmType.RISE_RATE)
    }

    @Test
    fun signalLossAfterItsDelay() {
        val last = state(120, at = now)
        val before = G7AlarmEngine.step(last, config(), G7AlarmRuntime(), now + 19 * minute)
        assertThat(before.actions).isEmpty()
        assertThat(before.nextCheckAt).isEqualTo(now + 20 * minute)
        val after = G7AlarmEngine.step(last, config(), before.runtime, now + 20 * minute)
        assertThat(raises(after).map { it.type }).containsExactly(G7AlarmType.SIGNAL_LOSS)
    }

    @Test
    fun staleReadingDoesNotRaiseGlucoseAlarms() {
        val step = G7AlarmEngine.step(state(50, at = now - 12 * minute), config(), G7AlarmRuntime(), now)
        assertThat(raises(step)).isEmpty()
    }

    @Test
    fun failedSensorRaisesOnlySensorFailed() {
        val step = G7AlarmEngine.step(state(null, algorithm = 25), config(), G7AlarmRuntime(), now)
        assertThat(raises(step).map { it.type }).containsExactly(G7AlarmType.SENSOR_FAILED)
    }

    @Test
    fun briefSensorIssueAfterItsDelay() {
        val issue = state(null, algorithm = 18)
        val first = G7AlarmEngine.step(issue, config(), G7AlarmRuntime(), now)
        assertThat(first.actions).isEmpty()
        val later = G7AlarmEngine.step(issue.copy(latestReadingAt = now + 20 * minute), config(), first.runtime, now + 20 * minute)
        assertThat(raises(later).map { it.type }).containsExactly(G7AlarmType.SENSOR_ISSUE)
    }

    @Test
    fun turningAlarmsOffClearsEverything() {
        val raised = G7AlarmEngine.step(state(50), config(), G7AlarmRuntime(), now)
        val off = G7AlarmEngine.step(state(50), config().copy(enabled = false), raised.runtime, now + minute)
        assertThat(clears(off)).containsExactly(G7AlarmType.URGENT_LOW)
        assertThat(off.runtime).isEqualTo(G7AlarmRuntime())
        assertThat(off.nextCheckAt).isNull()
    }

    @Test
    fun runtimeSurvivesSavingAndLoading() {
        val runtime = G7AlarmEngine.step(state(65), config(), G7AlarmRuntime(), now).runtime
        val json = Json { ignoreUnknownKeys = true }
        assertThat(json.decodeFromString(G7AlarmRuntime.serializer(), json.encodeToString(G7AlarmRuntime.serializer(), runtime))).isEqualTo(runtime)
    }

    @Test
    fun everyAlarmHasSoundsAndItsDefaultIsOneOfThem() {
        G7AlarmType.entries.forEach { type ->
            assertThat(G7AlarmSound.choicesFor(type.family)).contains(type.defaultSound)
            assertThat(type.defaultSound.family).isEqualTo(type.family)
        }
    }

    @Test
    fun settingKeysAreUnique() {
        val keys = G7AlarmKeys.all.map { it.key }
        assertThat(keys).containsNoDuplicates()
        assertThat(G7AlarmSound.entries.map { it.id }).containsNoDuplicates()
    }

    @Test
    fun nightRunsPastMidnight() {
        assertThat(G7AlarmNight.isNight(23 * 60, 22, 7)).isTrue()
        assertThat(G7AlarmNight.isNight(3 * 60, 22, 7)).isTrue()
        assertThat(G7AlarmNight.isNight(7 * 60, 22, 7)).isFalse()
        assertThat(G7AlarmNight.isNight(12 * 60, 22, 7)).isFalse()
        assertThat(G7AlarmNight.isNight(22 * 60, 22, 7)).isTrue()
    }

    @Test
    fun nightWithinOneDayAndNoNight() {
        assertThat(G7AlarmNight.isNight(2 * 60, 1, 6)).isTrue()
        assertThat(G7AlarmNight.isNight(6 * 60, 1, 6)).isFalse()
        assertThat(G7AlarmNight.isNight(3 * 60, 5, 5)).isFalse()
    }

    @Test
    fun lowAlarmsVibrateLongerOnTheWatchAtNight() {
        val low = G7AlarmKeys.of(G7AlarmType.URGENT_LOW)
        assertThat(low.watchVibrationNight.defaultValue).isGreaterThan(low.watchVibration.defaultValue)
        G7AlarmKeys.types.values.forEach {
            assertThat(it.watchVibration.entries.keys).containsExactly(0, 3, 5, 7, 10)
            assertThat(it.watchVibration.entries).containsKey(it.watchVibration.defaultValue)
            assertThat(it.watchVibrationNight.entries).containsKey(it.watchVibrationNight.defaultValue)
        }
    }
}
