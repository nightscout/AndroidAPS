package info.nightscout.androidaps.heartrate

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import info.nightscout.rx.logging.AAPSLoggerTest
import info.nightscout.rx.weardata.EventData.ActionHeartRate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock

internal class HeartRateListenerTest {
    private val aapsLogger = AAPSLoggerTest()
    private val heartRates = mutableListOf<ActionHeartRate>()
    private val device = "unknown unknown"

    private fun create(): HeartRateListener {
        val ctx = mock(Context::class.java)
        val listener = HeartRateListener(ctx, aapsLogger)
        listener.sendHeartRate = { hr -> heartRates.add(hr) }
        return listener
    }

    private fun sendSensorEvent(
        listener: HeartRateListener,
        timestamp: Long,
        heartRate: Int,
        sensorType: Int? = Sensor.TYPE_HEART_RATE,
        accuracy: Int = SensorManager.SENSOR_STATUS_ACCURACY_HIGH) {
        listener.onSensorChanged(sensorType, accuracy, timestamp, floatArrayOf(heartRate.toFloat()))
    }
    
    @BeforeEach
    fun init() {
        heartRates.clear()
    }
    
    @Test
    fun onSensorChanged() {
        val listener = create()
        val start = System.currentTimeMillis()
        sendSensorEvent(listener, start, 80)
        assertEquals(0, heartRates.size)
        sendSensorEvent(listener, start + 60_001L,180)
        assertEquals(1, heartRates.size)
        assertEquals(ActionHeartRate(start, start, 80, device), heartRates.first())
    }

    @Test
    fun onSensorChanged2() {
        val listener = create()
        val start = System.currentTimeMillis()
        sendSensorEvent(listener, start, 80)
        assertEquals(0, heartRates.size)
        sendSensorEvent(listener, start + 1000L,100)
        assertEquals(0, heartRates.size)
        sendSensorEvent(listener, start + 60_001L,180)
        assertEquals(1, heartRates.size)
        assertEquals(ActionHeartRate(start, start + 1000L, 90, device), heartRates.first())
    }

    @Test
    fun onSensorChangedOldValue() {
        val listener = create()
        val start = System.currentTimeMillis()
        sendSensorEvent(listener, start, 80)
        assertEquals(0, heartRates.size)
        val start2 = start + 120_001L
        sendSensorEvent(listener, start2, 100)
        assertEquals(0, heartRates.size)
        sendSensorEvent(listener, start + 180_001L, 180)
        assertEquals(1, heartRates.size)
        assertEquals(ActionHeartRate(start2, start2, 100, device), heartRates.first())
    }

    @Test
    fun onSensorChangedMultiple() {
        val listener = create()
        val start = System.currentTimeMillis()
        sendSensorEvent(listener, start, 80)
        assertEquals(0, heartRates.size)
        val start2 = start + 60_000L
        sendSensorEvent(listener, start2,100)
        assertEquals(1, heartRates.size)
        sendSensorEvent(listener, start2 + 60_000L,180)
        assertEquals(2, heartRates.size)
        assertEquals(ActionHeartRate(start, start, 80, device), heartRates[0])
        assertEquals(ActionHeartRate(start2, start2, 100, device), heartRates[1])
    }

    @Test
    fun onSensorChangedNoContact() {
        val listener = create()
        val start = System.currentTimeMillis()
        sendSensorEvent(listener, start, 80)
        assertEquals(0, heartRates.size)
        sendSensorEvent(listener, start + 1000L, 100, accuracy = SensorManager.SENSOR_STATUS_NO_CONTACT)
        assertEquals(0, heartRates.size)
        sendSensorEvent(listener, start + 60_001L, 180)
        assertEquals(1, heartRates.size)
        assertEquals(ActionHeartRate(start, start, 80, device), heartRates.first())
    }
}
