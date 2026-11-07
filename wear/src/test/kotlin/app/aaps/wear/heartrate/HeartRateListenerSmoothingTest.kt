package app.aaps.wear.heartrate

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.weardata.EventData.ActionHeartRate
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.wear.AAPSLoggerTest
import app.aaps.wear.R
import com.google.common.truth.Truth.assertThat
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.disposables.Disposable
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import java.util.concurrent.TimeUnit

/**
 * Tests for the smoothing/averaging path of [HeartRateListener.Sampler] that is exercised only when
 * `sp.getInt(R.string.key_heart_rate_smoothing, 1)` returns a value greater than 1.
 *
 * The existing [HeartRateListenerTest] always stubs smoothing == 1, so the multi-interval averaging
 * branch in `Sampler.averageHeartRate` (its 62000ms window filter and the
 * `avgNb > averageHistory/4 || allDuration.toMinute() > averageHistory/2` send-gate) and the
 * accumulation of [ActionHeartRate] history across several sampling intervals stay uncovered.
 *
 * Each sampling interval here is exactly 62_000ms long so the timestamps line up with the window
 * constant used by the production code, making the boundary of the averaging window deterministic.
 */
internal class HeartRateListenerSmoothingTest {

    private val aapsLogger = AAPSLoggerTest()
    private val aapsSchedulers = object : AapsSchedulers {
        override val main: Scheduler = mock()
        override val io: Scheduler = mock()
        override val cpu: Scheduler = mock()
        override val newThread: Scheduler = mock()
    }
    private val schedule: Disposable = mock()
    private val sp: SP = mock()
    private val heartRates = mutableListOf<ActionHeartRate>()
    private val device = "unknown unknown"

    /** Length of one sampling interval; matches the production 62000ms window granularity. */
    private val interval = 62_000L

    private fun create(timestampMillis: Long): HeartRateListener {
        val ctx: Context = mock()
        whenever(
            aapsSchedulers.io.schedulePeriodicallyDirect(
                any(), eq(60_000L), eq(60_000L), eq(TimeUnit.MILLISECONDS)
            )
        ).thenReturn(schedule)
        val listener = HeartRateListener(ctx, aapsLogger, sp, aapsSchedulers, timestampMillis)
        verify(aapsSchedulers.io).schedulePeriodicallyDirect(
            any(), eq(60_000L), eq(60_000L), eq(TimeUnit.MILLISECONDS)
        )
        listener.sendHeartRate = { hr -> heartRates.add(hr) }
        return listener
    }

    private fun sendSensorEvent(
        listener: HeartRateListener,
        timestamp: Long,
        heartRate: Int,
        sensorType: Int? = Sensor.TYPE_HEART_RATE,
        accuracy: Int = SensorManager.SENSOR_STATUS_ACCURACY_HIGH
    ) {
        listener.onSensorChanged(sensorType, accuracy, timestamp, floatArrayOf(heartRate.toFloat()))
    }

    @BeforeEach
    fun before() {
        heartRates.clear()
    }

    @AfterEach
    fun cleanup() {
        verifyNoInteractions(aapsSchedulers.main)
        verifyNoMoreInteractions(aapsSchedulers.io)
        verifyNoInteractions(aapsSchedulers.cpu)
        verifyNoInteractions(aapsSchedulers.newThread)
        verify(schedule).dispose()
    }

    /**
     * With smoothing == 2 the count-gate `avgNb > averageHistory/4` reduces to `avgNb > 0`, so every
     * interval that has a valid sample emits. The averaging window is `(2-1)*62000 = 62000`, i.e. the
     * current interval plus the immediately preceding one. Feeding a constant heart rate over two
     * intervals yields that same value averaged with itself (still the constant).
     */
    @Test
    fun smoothingTwoConstantRateEmitsSameValueEachInterval() {
        whenever(sp.getInt(R.string.key_heart_rate_smoothing, 1)).thenReturn(2)
        val start = System.currentTimeMillis()
        val listener = create(start)

        val t1 = start + interval
        sendSensorEvent(listener, start, 90)
        listener.send(t1)

        val t2 = t1 + interval
        sendSensorEvent(listener, t1, 90)
        listener.send(t2)

        assertThat(heartRates).containsExactly(
            ActionHeartRate(interval, t1, 90.0, device),
            ActionHeartRate(interval, t2, 90.0, device),
        ).inOrder()
        listener.dispose()
    }

    /**
     * The core averaging assertion. With smoothing == 3 the window is `(3-1)*62000 = 124000` (the two
     * most recent intervals plus the current one) and the count-gate `avgNb > 3/4 = 0` always passes.
     *
     * Feeding a rising heart rate (80, 100, 120, 140) across four 62000ms intervals yields the
     * running mean over the sliding window:
     *  - send1: {80}                 -> 80
     *  - send2: {80,100}             -> 90
     *  - send3: {80,100,120}         -> 100  (oldest entry is exactly on the window boundary, included)
     *  - send4: {100,120,140}        -> 120  (first entry now falls just outside the window)
     */
    @Test
    fun smoothingThreeAveragesSlidingWindowAcrossIntervals() {
        whenever(sp.getInt(R.string.key_heart_rate_smoothing, 1)).thenReturn(3)
        val start = System.currentTimeMillis()
        val listener = create(start)

        val rates = intArrayOf(80, 100, 120, 140)
        var intervalStart = start
        for (rate in rates) {
            sendSensorEvent(listener, intervalStart, rate)
            intervalStart += interval
            listener.send(intervalStart)
        }

        val t1 = start + interval
        val t2 = t1 + interval
        val t3 = t2 + interval
        val t4 = t3 + interval
        assertThat(heartRates).containsExactly(
            ActionHeartRate(interval, t1, 80.0, device),
            ActionHeartRate(interval, t2, 90.0, device),
            ActionHeartRate(interval, t3, 100.0, device),
            ActionHeartRate(interval, t4, 120.0, device),
        ).inOrder()
        listener.dispose()
    }

    /**
     * With smoothing == 15 the count-gate is `avgNb > 15/4 = 3` and the duration-gate is
     * `allDuration.toMinute() > 15/2 = 7.5`. The first three intervals accumulate only 1, 2 and 3
     * samples (durations 1.03, 2.07 and 3.10 minutes) so neither branch of the OR is satisfied and
     * `averageHeartRate` returns null -> nothing is sent. On the fourth interval avgNb becomes 4 > 3,
     * the gate opens and the mean of the four collected values is emitted.
     */
    @Test
    fun smoothingFifteenSuppressesEmitUntilEnoughSamplesThenAverages() {
        whenever(sp.getInt(R.string.key_heart_rate_smoothing, 1)).thenReturn(15)
        val start = System.currentTimeMillis()
        val listener = create(start)

        val rates = intArrayOf(60, 80, 100, 120)
        var intervalStart = start
        val sizesAfterEachSend = mutableListOf<Int>()
        for (rate in rates) {
            sendSensorEvent(listener, intervalStart, rate)
            intervalStart += interval
            listener.send(intervalStart)
            sizesAfterEachSend.add(heartRates.size)
        }

        // Sends 1..3 are gated off (too few samples), only send 4 emits.
        assertThat(sizesAfterEachSend).containsExactly(0, 0, 0, 1).inOrder()

        val t4 = start + 4 * interval
        val expectedAvg = (60.0 + 80.0 + 100.0 + 120.0) / 4.0
        assertThat(heartRates).containsExactly(
            ActionHeartRate(interval, t4, expectedAvg, device)
        )
        listener.dispose()
    }

    /**
     * A single interval with smoothing == 15 must not emit anything: avgNb == 1 is not greater than
     * `15/4 == 3` and the accumulated duration (~1.03 min) is not greater than `7.5` min, so the
     * send-gate keeps the value back. This isolates the "too-few-samples yields null" branch.
     */
    @Test
    fun smoothingFifteenSingleIntervalYieldsNull() {
        whenever(sp.getInt(R.string.key_heart_rate_smoothing, 1)).thenReturn(15)
        val start = System.currentTimeMillis()
        val listener = create(start)

        sendSensorEvent(listener, start, 75)
        listener.send(start + interval)

        assertThat(heartRates).isEmpty()
        listener.dispose()
    }
}
