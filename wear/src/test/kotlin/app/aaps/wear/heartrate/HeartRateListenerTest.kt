package app.aaps.wear.heartrate

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.BatteryManager
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
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import java.util.concurrent.TimeUnit

internal class HeartRateListenerTest {

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
    private lateinit var ctx: Context

    private fun create(timestampMillis: Long): HeartRateListener {
        ctx = mock()
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

    @Test
    fun onSensorChanged() {
        whenever(sp.getInt(R.string.key_heart_rate_smoothing, 1)).thenReturn(1)
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
        whenever(sp.getInt(R.string.key_heart_rate_smoothing, 1)).thenReturn(1)
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
        whenever(sp.getInt(R.string.key_heart_rate_smoothing, 1)).thenReturn(1)
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
        whenever(sp.getInt(R.string.key_heart_rate_smoothing, 1)).thenReturn(1)
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
        whenever(sp.getInt(R.string.key_heart_rate_smoothing, 1)).thenReturn(1)
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

    @Test
    fun onOffBodyDetected_shouldNotRecordHeartRate() {
        whenever(sp.getInt(R.string.key_heart_rate_smoothing, 1)).thenReturn(1)
        val start = System.currentTimeMillis()
        val d1 = 10_000L
        val d2 = 20_000L
        val listener = create(start)

        // Send heart rate while on body
        sendSensorEvent(listener, start, 80)
        assertThat(listener.currentHeartRateBpm).isEqualTo(80)

        // Simulate off-body detection (value 0 = not on body)
        listener.onSensorChanged(
            Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT,
            SensorManager.SENSOR_STATUS_ACCURACY_HIGH,
            start + d1,
            floatArrayOf(0f)
        )

        // Heart rate should be cleared when off body
        assertThat(listener.currentHeartRateBpm).isNull()

        // Send heart rate while off body - should be ignored
        sendSensorEvent(listener, start + d1, 90)
        assertThat(listener.currentHeartRateBpm).isNull()

        listener.send(start + d2)
        // No heart rate should be sent since device is off body
        assertThat(heartRates).isEmpty()
        listener.dispose()
    }

    @Test
    fun onOnBodyDetected_shouldResumeRecordingHeartRate() {
        whenever(sp.getInt(R.string.key_heart_rate_smoothing, 1)).thenReturn(1)
        val start = System.currentTimeMillis()
        val d1 = 10_000L
        val d2 = 20_000L
        val d3 = 40_000L
        val listener = create(start)

        // Start on body
        sendSensorEvent(listener, start, 80)
        assertThat(listener.currentHeartRateBpm).isEqualTo(80)

        // Go off body
        listener.onSensorChanged(
            Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT,
            SensorManager.SENSOR_STATUS_ACCURACY_HIGH,
            start + d1,
            floatArrayOf(0f)
        )
        assertThat(listener.currentHeartRateBpm).isNull()

        // Come back on body (value 1 = on body)
        listener.onSensorChanged(
            Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT,
            SensorManager.SENSOR_STATUS_ACCURACY_HIGH,
            start + d2,
            floatArrayOf(1f)
        )

        // Now send heart rate - should be recorded
        sendSensorEvent(listener, start + d2, 85)
        assertThat(listener.currentHeartRateBpm).isEqualTo(85)

        listener.send(start + d3)
        assertThat(heartRates).hasSize(1)
        assertThat(heartRates[0].beatsPerMinute).isEqualTo(85.0)
        listener.dispose()
    }

    @Test
    fun onDeviceCharging_shouldNotRecordHeartRate() {
        whenever(sp.getInt(R.string.key_heart_rate_smoothing, 1)).thenReturn(1)
        val start = System.currentTimeMillis()
        val d1 = 10_000L
        val d2 = 20_000L
        val listener = create(start)

        // Simulate device is charging
        val chargingIntent = Intent().apply {
            putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_CHARGING)
        }
        whenever(ctx.registerReceiver(any(), any<IntentFilter>())).thenReturn(chargingIntent)

        // Send heart rate while charging
        sendSensorEvent(listener, start, 80)
        assertThat(listener.currentHeartRateBpm).isNull()

        listener.send(start + d2)
        // No heart rate should be sent since device is charging
        assertThat(heartRates).isEmpty()
        listener.dispose()
    }

    @Test
    fun onDeviceNotCharging_shouldRecordHeartRate() {
        whenever(sp.getInt(R.string.key_heart_rate_smoothing, 1)).thenReturn(1)
        val start = System.currentTimeMillis()
        val d1 = 10_000L
        val d2 = 20_000L
        val listener = create(start)

        // Simulate device is not charging
        val notChargingIntent = Intent().apply {
            putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_DISCHARGING)
        }
        whenever(ctx.registerReceiver(any(), any<IntentFilter>())).thenReturn(notChargingIntent)

        // Send heart rate while not charging - should be recorded
        sendSensorEvent(listener, start, 80)
        assertThat(listener.currentHeartRateBpm).isEqualTo(80)

        listener.send(start + d2)
        assertThat(heartRates).hasSize(1)
        assertThat(heartRates[0].beatsPerMinute).isEqualTo(80.0)
        listener.dispose()
    }

    @Test
    fun isDeviceCharging_acUsbWireless_shouldReturnTrue() {
        whenever(sp.getInt(R.string.key_heart_rate_smoothing, 1)).thenReturn(1)
        val listener = create(System.currentTimeMillis())

        // Test AC charging
        val acIntent = Intent().apply {
            putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_CHARGING)
            putExtra(BatteryManager.EXTRA_PLUGGED, BatteryManager.BATTERY_PLUGGED_AC)
        }
        whenever(ctx.registerReceiver(any(), any<IntentFilter>())).thenReturn(acIntent)
        assertThat(listener.isDeviceCharging(ctx)).isTrue()

        // Test USB charging
        val usbIntent = Intent().apply {
            putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_CHARGING)
            putExtra(BatteryManager.EXTRA_PLUGGED, BatteryManager.BATTERY_PLUGGED_USB)
        }
        whenever(ctx.registerReceiver(any(), any<IntentFilter>())).thenReturn(usbIntent)
        assertThat(listener.isDeviceCharging(ctx)).isTrue()

        // Test Wireless charging
        val wirelessIntent = Intent().apply {
            putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_CHARGING)
            putExtra(BatteryManager.EXTRA_PLUGGED, BatteryManager.BATTERY_PLUGGED_WIRELESS)
        }
        whenever(ctx.registerReceiver(any(), any<IntentFilter>())).thenReturn(wirelessIntent)
        assertThat(listener.isDeviceCharging(ctx)).isTrue()

        listener.dispose()
    }

    @Test
    fun isDeviceCharging_notCharging_shouldReturnFalse() {
        whenever(sp.getInt(R.string.key_heart_rate_smoothing, 1)).thenReturn(1)
        val listener = create(System.currentTimeMillis())

        val dischargingIntent = Intent().apply {
            putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_DISCHARGING)
        }
        whenever(ctx.registerReceiver(any(), any<IntentFilter>())).thenReturn(dischargingIntent)
        assertThat(listener.isDeviceCharging(ctx)).isFalse()

        listener.dispose()
    }

}
