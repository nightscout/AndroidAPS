package info.nightscout.androidaps.heartrate

import android.content.Context
import android.content.Context.SENSOR_SERVICE
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.annotation.VisibleForTesting
import info.nightscout.androidaps.comm.IntentWearToMobile
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.rx.weardata.EventData
import java.lang.Long.max
import java.lang.Long.min

/**
 * Gets heart rate readings from watch and sends them once per minute to the phone.
 */
class HeartRateListener(
    private val ctx: Context,
    private val aapsLogger: AAPSLogger
) :  SensorEventListener {

    private val samplingIntervalMillis = 60_000L
    private var sampler: Sampler? = null

    init {
        aapsLogger.info(LTag.WEAR, "Create ${javaClass.simpleName}")
        val sensorManager = ctx.getSystemService(SENSOR_SERVICE) as SensorManager?
        if (sensorManager == null) {
            aapsLogger.warn(LTag.WEAR, "Cannot get sensor manager to get heart rate readings")
        } else {
            val heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)
            if (heartRateSensor == null) {
                aapsLogger.warn(LTag.WEAR, "Cannot get heart rate sensor")
            } else {
                sensorManager.registerListener(this, heartRateSensor, SensorManager.SENSOR_DELAY_NORMAL)
            }
        }
    }

    @VisibleForTesting
    var sendHeartRate: (EventData.ActionHeartRate)->Unit = { hr -> ctx.startService(IntentWearToMobile(ctx, hr)) }

    fun onDestroy() {
        aapsLogger.info(LTag.WEAR, "Destroy ${javaClass.simpleName}")
        (ctx.getSystemService(SENSOR_SERVICE) as SensorManager?)?.unregisterListener(this)
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
    }

    private fun send(sampler: Sampler) {
        sampler.heartRate.let { hr ->
            aapsLogger.info(LTag.WEAR, "Send heart rate $hr")
            sendHeartRate(hr)
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        onSensorChanged(event.sensor?.type, event.accuracy, System.currentTimeMillis(), event.values)
    }

    @VisibleForTesting
    fun onSensorChanged(sensorType: Int?, accuracy: Int, timestamp: Long, values: FloatArray) {
        if (sensorType == null || sensorType != Sensor.TYPE_HEART_RATE || values.isEmpty()) {
            aapsLogger.error(LTag.WEAR, "Invalid SensorEvent $sensorType $accuracy $timestamp ${values.joinToString()}")
            return
        }
        when (accuracy) {
            SensorManager.SENSOR_STATUS_NO_CONTACT -> return
            SensorManager.SENSOR_STATUS_UNRELIABLE -> return
        }
        val heartRate = values[0].toInt()
        sampler = sampler.let { s ->
            if (s == null || s.age(timestamp) !in 0..120_000 ) {
                Sampler(timestamp, heartRate)
            } else if (s.age(timestamp) >= samplingIntervalMillis) {
                send(s)
                Sampler(timestamp, heartRate)
            } else {
                s.addHeartRate(timestamp, heartRate)
                s
            }
        }
    }

    private class Sampler(timestampMillis: Long, heartRate: Int) {
        private var startMillis: Long = timestampMillis
        private var endMillis: Long = timestampMillis
        private var valueCount: Int = 1
        private var valueSum: Int = heartRate
        private val device = (Build.MANUFACTURER ?: "unknown") + " " + (Build.MODEL ?: "unknown")

        val beatsPerMinute get() = valueSum / valueCount
        val heartRate get() = EventData.ActionHeartRate(startMillis, endMillis, beatsPerMinute, device)

        fun age(timestampMillis: Long) = timestampMillis - startMillis

        fun addHeartRate(timestampMillis: Long, heartRate: Int) {
            startMillis = min(startMillis, timestampMillis)
            endMillis = max(endMillis, timestampMillis)
            valueCount++
            valueSum += heartRate
        }
    }
}
