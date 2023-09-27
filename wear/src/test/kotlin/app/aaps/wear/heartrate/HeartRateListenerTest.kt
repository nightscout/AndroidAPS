package app.aaps.wear.heartrate

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.weardata.EventData.ActionHeartRate
import app.aaps.shared.tests.AAPSLoggerTest
import com.google.common.truth.Truth.assertThat
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.disposables.Disposable
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.util.concurrent.TimeUnit

internal class HeartRateListenerTest {

    private val aapsLogger = AAPSLoggerTest()
    private val aapsSchedulers = object : AapsSchedulers {
        override val main: Scheduler = mock(Scheduler::class.java)
        override val io: Scheduler = mock(Scheduler::class.java)
        override val cpu: Scheduler = mock(Scheduler::class.java)
        override val newThread: Scheduler = mock(Scheduler::class.java)
    }
    private val schedule = mock(Disposable::class.java)
    private val heartRates = mutableListOf<ActionHeartRate>()
    private val device = "unknown unknown"

    private fun create(timestampMillis: Long): HeartRateListener {
        val ctx = mock(Context::class.java)
        `when`(
            aapsSchedulers.io.schedulePeriodicallyDirect(
                any(), eq(60_000L), eq(60_000L), eq(TimeUnit.MILLISECONDS)
            )
        ).thenReturn(schedule)
        val listener = HeartRateListener(ctx, aapsLogger, aapsSchedulers, timestampMillis)
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
        Mockito.verifyNoInteractions(aapsSchedulers.main)
        Mockito.verifyNoMoreInteractions(aapsSchedulers.io)
        Mockito.verifyNoInteractions(aapsSchedulers.cpu)
        Mockito.verifyNoInteractions(aapsSchedulers.newThread)
        verify(schedule).dispose()
    }

    @Test
    fun onSensorChanged() {
        val start = System.currentTimeMillis()
        val d1 = 10_000L
        val d2 = 20_000L
        val listener = create(start)

        assertThat(listener.currentHeartRateBpm).isNull()
        sendSensorEvent(listener, start + d1, 80)
        assertThat(heartRates).isEmpty()
        assertThat(listener.currentHeartRateBpm).isEqualTo(80)

        listener.send(start + d2)
        assertThat(heartRates).containsExactly(ActionHeartRate(d2, start + d2, 80.0, device))
        listener.dispose()
    }

    @Test
    fun onSensorChanged2() {
        val start = System.currentTimeMillis()
        val d1 = 10_000L
        val d2 = 40_000L
        val listener = create(start)

        sendSensorEvent(listener, start, 80)
        assertThat(heartRates).isEmpty()
        assertThat(listener.currentHeartRateBpm).isEqualTo(80)
        sendSensorEvent(listener, start + d1, 100)
        assertThat(heartRates).isEmpty()
        assertThat(listener.currentHeartRateBpm).isEqualTo(100)


        listener.send(start + d2)
        assertThat(heartRates).containsExactly(ActionHeartRate(d2, start + d2, 95.0, device))
        listener.dispose()
    }

    @Test
    fun onSensorChangedMultiple() {
        val start = System.currentTimeMillis()
        val d1 = 10_000L
        val d2 = 40_000L
        val listener = create(start)

        sendSensorEvent(listener, start, 80)
        listener.send(start + d1)
        assertThat(heartRates).hasSize(1)

        sendSensorEvent(listener, start + d1, 100)
        assertThat(heartRates).hasSize(1)
        listener.send(start + d2)
        assertThat(heartRates).containsExactly(
            ActionHeartRate(d1, start + d1, 80.0, device),
            ActionHeartRate(d2 - d1, start + d2, 100.0, device),
        ).inOrder()
        listener.dispose()
    }

    @Test
    fun onSensorChangedNoContact() {
        val start = System.currentTimeMillis()
        val d1 = 10_000L
        val d2 = 40_000L
        val listener = create(start)

        sendSensorEvent(listener, start, 80)
        sendSensorEvent(listener, start + d1, 100, accuracy = SensorManager.SENSOR_STATUS_NO_CONTACT)
        assertThat(listener.currentHeartRateBpm).isNull()
        listener.send(start + d2)

        assertThat(heartRates).containsExactly(ActionHeartRate(d2, start + d2, 80.0, device))
        listener.dispose()
    }

    @Test
    fun onAccuracyChanged() {
        val start = System.currentTimeMillis()
        val d1 = 10_000L
        val d2 = 40_000L
        val d3 = 70_000L
        val listener = create(start)

        sendSensorEvent(listener, start, 80)
        listener.onAccuracyChanged(Sensor.TYPE_HEART_RATE, SensorManager.SENSOR_STATUS_UNRELIABLE, start + d1)
        sendSensorEvent(listener, start + d2, 100)
        listener.send(start + d3)

        assertThat(heartRates).containsExactly(ActionHeartRate(d3, start + d3, 95.0, device))
        listener.dispose()
    }
}
