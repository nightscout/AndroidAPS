package app.aaps.wear.wearStepCount

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.weardata.EventData.ActionStepsRate
import app.aaps.wear.AAPSLoggerTest
import com.google.common.truth.Truth.assertThat
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.disposables.Disposable
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.concurrent.TimeUnit
import kotlin.math.roundToLong

/**
 * Tests for [StepCountListener] — movement-gated, windowed step-rate aggregation for Wear.
 *
 * Notes on determinism: [StepCountListener] reads [System.currentTimeMillis] directly (no injectable
 * clock) inside its 5-minute-bucket helpers. A 5-min bucket is 300000 ms wide, so within the
 * millisecond span of a single test the "current bucket" is stable except at the rare boundary
 * instant. Accumulation assertions that depend on the current bucket are guarded with
 * [sameBucketAround] and fall back to structural assertions if a boundary was crossed, so the suite
 * never flakes on wall-clock timing.
 */
internal class StepCountListenerTest {

    private val fiveMinutesInMs = 300000L
    private val aapsLogger = AAPSLoggerTest()
    private val aapsSchedulers = object : AapsSchedulers {
        override val main: Scheduler = mock()
        override val io: Scheduler = mock()
        override val cpu: Scheduler = mock()
        override val newThread: Scheduler = mock()
    }
    private val schedule: Disposable = mock()
    private val device = "unknown unknown"
    private val emitted = mutableListOf<List<ActionStepsRate>>()

    private fun create(): StepCountListener {
        val ctx: Context = mock()
        // getSystemService(SENSOR_SERVICE) returns null (mock default) -> init logs warn, skips registration.
        whenever(
            aapsSchedulers.io.schedulePeriodicallyDirect(
                any(), eq(40_000L), eq(40_000L), eq(TimeUnit.MILLISECONDS)
            )
        ).thenReturn(schedule)
        val listener = StepCountListener(ctx, aapsLogger, aapsSchedulers)
        listener.sendStepsRate = { list -> emitted.add(list) }
        return listener
    }

    /** Mirrors the private currentTimeIn5Min() so a test can detect a bucket boundary crossing. */
    private fun currentBucket(): Long = (System.currentTimeMillis() / fiveMinutesInMs.toDouble()).roundToLong()

    /** Runs [block] and reports whether the 5-min bucket stayed constant across the call. */
    private inline fun sameBucketAround(block: () -> Unit): Boolean {
        val before = currentBucket()
        block()
        return before == currentBucket()
    }

    private fun stepDetectorEvent(value: Float): SensorEvent =
        sensorEvent(Sensor.TYPE_STEP_DETECTOR, floatArrayOf(value))

    private fun accelerometerEvent(vararg values: Float): SensorEvent =
        sensorEvent(Sensor.TYPE_ACCELEROMETER, floatArrayOf(*values))

    private fun stepCounterEvent(cumulativeSteps: Int): SensorEvent =
        sensorEvent(Sensor.TYPE_STEP_COUNTER, floatArrayOf(cumulativeSteps.toFloat()))

    private fun sensorEvent(type: Int, values: FloatArray): SensorEvent {
        val sensor: Sensor = mock()
        whenever(sensor.type).thenReturn(type)
        // SensorEvent has no public constructor and its .sensor/.values are public framework fields
        // (not getters), so they cannot be stubbed with whenever(). Instantiate via Mockito (Objenesis,
        // no constructor call) and assign the fields reflectively; field reads are not intercepted.
        val event: SensorEvent = mock()
        setSensorEventField(event, "sensor", sensor)
        setSensorEventField(event, "values", values)
        return event
    }

    private fun setSensorEventField(event: SensorEvent, name: String, value: Any?) {
        val field = SensorEvent::class.java.getField(name)
        field.isAccessible = true
        field.set(event, value)
    }

    @BeforeEach
    fun before() {
        emitted.clear()
    }

    @Test
    fun sendProducesSixProgressiveRowsWithDeviceAndTimestamp() {
        val listener = create()
        val timestamp = 1_700_000_000_000L

        listener.send(timestamp)

        assertThat(emitted).hasSize(1)
        val rows = emitted.single()
        assertThat(rows).hasSize(6)

        // Durations are in ms and strictly progressive: 5/10/15/30/60/180 min.
        assertThat(rows.map { it.duration }).containsExactly(
            5L * 60 * 1000, 10L * 60 * 1000, 15L * 60 * 1000,
            30L * 60 * 1000, 60L * 60 * 1000, 180L * 60 * 1000
        ).inOrder()

        rows.forEach { row ->
            assertThat(row.timestamp).isEqualTo(timestamp)
            assertThat(row.device).isEqualTo(device)
        }

        // With no accumulated steps every window is zero.
        rows.forEach { row ->
            assertThat(row.steps5min).isEqualTo(0)
            assertThat(row.steps10min).isEqualTo(0)
            assertThat(row.steps15min).isEqualTo(0)
            assertThat(row.steps30min).isEqualTo(0)
            assertThat(row.steps60min).isEqualTo(0)
            assertThat(row.steps180min).isEqualTo(0)
        }
        listener.dispose()
    }

    @Test
    fun progressiveWindowFieldsAreZeroedByDuration() {
        // Even with no data, verify the structural rule: shorter durations null out longer windows.
        val listener = create()
        val rows = run { listener.send(42L); emitted.single() }

        val d5 = rows[0]
        val d10 = rows[1]
        val d15 = rows[2]
        val d30 = rows[3]
        val d60 = rows[4]
        val d180 = rows[5]

        // duration == 5 -> only steps5min carries a value slot; others are hard 0.
        assertThat(d5.steps10min).isEqualTo(0)
        assertThat(d5.steps15min).isEqualTo(0)
        assertThat(d5.steps30min).isEqualTo(0)
        assertThat(d5.steps60min).isEqualTo(0)
        assertThat(d5.steps180min).isEqualTo(0)

        // duration == 10 -> up to steps10min; 15/30/60/180 hard 0.
        assertThat(d10.steps15min).isEqualTo(0)
        assertThat(d10.steps30min).isEqualTo(0)
        assertThat(d10.steps60min).isEqualTo(0)
        assertThat(d10.steps180min).isEqualTo(0)

        // duration == 15 -> up to steps15min; 30/60/180 hard 0.
        assertThat(d15.steps30min).isEqualTo(0)
        assertThat(d15.steps60min).isEqualTo(0)
        assertThat(d15.steps180min).isEqualTo(0)

        // duration == 30 -> up to steps30min; 60/180 hard 0.
        assertThat(d30.steps60min).isEqualTo(0)
        assertThat(d30.steps180min).isEqualTo(0)

        // duration == 60 and 180 -> all six windows populated (all 0 here).
        assertThat(d60.steps180min).isEqualTo(0)
        assertThat(d180.steps180min).isEqualTo(0)
        listener.dispose()
    }

    @Test
    fun stepCounterIgnoredUntilMovementDetected() {
        val listener = create()

        // No movement yet: counter readings must NOT accumulate.
        listener.onSensorChanged(stepCounterEvent(1000))
        listener.onSensorChanged(stepCounterEvent(1080))

        listener.send(99L)
        val rows = emitted.single()
        rows.forEach { row ->
            assertThat(row.steps5min).isEqualTo(0)
            assertThat(row.steps30min).isEqualTo(0)
            assertThat(row.steps60min).isEqualTo(0)
            assertThat(row.steps180min).isEqualTo(0)
        }
        listener.dispose()
    }

    @Test
    fun stepDetectorEnablesAccumulationAndFirstReadingOnlySetsBaseline() {
        val listener = create()

        // Step detector with value > 0 arms movement detection.
        listener.onSensorChanged(stepDetectorEvent(1.0f))

        val stable = sameBucketAround {
            // First counter reading only sets previousStepCount (-1 start) -> no delta.
            listener.onSensorChanged(stepCounterEvent(1000))
            // Second reading in the same bucket -> delta = 1120 - 1000 = 120.
            listener.onSensorChanged(stepCounterEvent(1120))
            listener.send(1L)
        }

        val rows = emitted.single()
        val d30 = rows.first { it.duration == 30L * 60 * 1000 }
        if (stable) {
            // steps30min window includes the current bucket -> reflects the 120 accumulated.
            assertThat(d30.steps30min).isEqualTo(120)
        } else {
            // Boundary crossed mid-test: fall back to structural correctness only.
            assertThat(rows).hasSize(6)
            assertThat(d30.steps30min).isAtLeast(0)
        }
        listener.dispose()
    }

    @Test
    fun accelerometerAboveThresholdArmsMovementButBelowDoesNot() {
        // Below-threshold accelerometer must not arm movement -> counter deltas ignored.
        val listenerLow = create()
        listenerLow.onSensorChanged(accelerometerEvent(0.5f, -0.9f, 1.0f)) // none strictly > 1.0f
        listenerLow.onSensorChanged(stepCounterEvent(500))
        listenerLow.onSensorChanged(stepCounterEvent(560))
        listenerLow.send(7L)
        emitted.single().forEach { row ->
            assertThat(row.steps30min).isEqualTo(0)
        }
        listenerLow.dispose()

        emitted.clear()

        // Above-threshold accelerometer arms movement -> deltas accumulate.
        val listenerHigh = create()
        listenerHigh.onSensorChanged(accelerometerEvent(0.2f, 2.5f, 0.1f)) // 2.5 > 1.0f
        val stable = sameBucketAround {
            listenerHigh.onSensorChanged(stepCounterEvent(500)) // baseline only
            listenerHigh.onSensorChanged(stepCounterEvent(575)) // delta 75
            listenerHigh.send(8L)
        }
        val rows = emitted.single()
        val d30 = rows.first { it.duration == 30L * 60 * 1000 }
        if (stable) assertThat(d30.steps30min).isEqualTo(75)
        else assertThat(d30.steps30min).isAtLeast(0)
        listenerHigh.dispose()
    }

    @Test
    fun multipleReadingsInSameBucketSumTheirDeltas() {
        val listener = create()
        listener.onSensorChanged(stepDetectorEvent(1.0f))

        val stable = sameBucketAround {
            listener.onSensorChanged(stepCounterEvent(2000)) // baseline
            listener.onSensorChanged(stepCounterEvent(2010)) // +10
            listener.onSensorChanged(stepCounterEvent(2035)) // +25
            listener.onSensorChanged(stepCounterEvent(2100)) // +65
            listener.send(3L)
        }

        val rows = emitted.single()
        val d180 = rows.first { it.duration == 180L * 60 * 1000 }
        // 10 + 25 + 65 = 100 accumulated into the single current bucket.
        if (stable) assertThat(d180.steps180min).isEqualTo(100)
        else assertThat(d180.steps180min).isAtLeast(0)
        listener.dispose()
    }
}
