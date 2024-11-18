package app.aaps.wear.wearStepCount

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.util.Log
import androidx.annotation.VisibleForTesting
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.wear.comm.IntentWearToMobile
import io.reactivex.rxjava3.disposables.Disposable
import java.util.concurrent.TimeUnit
import kotlin.math.roundToLong

class StepCountListener(
    private val ctx: Context,
    private val aapsLogger: AAPSLogger,
    aapsSchedulers: AapsSchedulers
) : SensorEventListener, Disposable {

    private val samplingIntervalMillis = 40_000L
    private val stepsMap = LinkedHashMap<Long, Int>()
    private val fiveMinutesInMs = 300000
    private val numOf5MinBlocksToKeep = 40
    private var previousStepCount = -1
    private var schedule: Disposable? = null
    private var movementDetected = false

    init {
        aapsLogger.info(LTag.WEAR, "Create ${javaClass.simpleName}")
        val sensorManager = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager?
        if (sensorManager == null) {
            aapsLogger.warn(LTag.WEAR, "Cannot get sensor manager to get steps rate readings")
        } else {
            sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            }
            sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            }
            sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            }
        }
        schedule = aapsSchedulers.io.schedulePeriodicallyDirect(
            ::send, samplingIntervalMillis, samplingIntervalMillis, TimeUnit.MILLISECONDS
        )
    }

    var sendStepsRate: (List<EventData.ActionStepsRate>) -> Unit = { stepsList ->
        aapsLogger.info(LTag.WEAR, "sendStepsRate called")
        stepsList.forEach { steps ->
            ctx.startForegroundService(IntentWearToMobile(ctx, steps))
        }
    }

    override fun isDisposed() = schedule == null

    override fun dispose() {
        aapsLogger.info(LTag.WEAR, "Dispose ${javaClass.simpleName}")
        schedule?.dispose()
        (ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager?)?.unregisterListener(this)
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        Log.i(LTag.WEAR.name, "onAccuracyChanged: Sensor: $sensor; accuracy: $accuracy")
    }

    private fun currentTimeIn5Min(): Long {
        return (System.currentTimeMillis() / fiveMinutesInMs.toDouble()).roundToLong()
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor?.type) {
            Sensor.TYPE_STEP_DETECTOR -> {
                if (event.values[0] > 0) {
                    movementDetected = true
                }
            }

            Sensor.TYPE_ACCELEROMETER -> {
                val limit = 1.0f
                if (event.values.any { kotlin.math.abs(it) > limit }) {
                    movementDetected = true
                }
            }

            Sensor.TYPE_STEP_COUNTER  -> {
                if (movementDetected && event.values.isNotEmpty()) {
                    val now = currentTimeIn5Min()
                    val stepCount = event.values[0].toInt()
                    if (previousStepCount >= 0) {
                        var recentStepCount = stepCount - previousStepCount
                        if (stepsMap.contains(now)) {
                            recentStepCount += stepsMap.getValue(now)
                        }
                        stepsMap[now] = recentStepCount
                    }
                    previousStepCount = stepCount

                    if (stepsMap.size > numOf5MinBlocksToKeep) {
                        val removeBefore = now - numOf5MinBlocksToKeep
                        stepsMap.entries.removeIf { it.key < removeBefore }
                    }
                }
            }
        }
    }

    private fun send() {
        send(System.currentTimeMillis())
    }

    private fun getStepsInLast5Min(): Int {
        val now = currentTimeIn5Min() - 1
        return if (stepsMap.contains(now)) stepsMap.getValue(now) else 0
    }

    private fun getStepsInLast10Min(): Int {
        val tenMinAgo = currentTimeIn5Min() - 2
        return if (stepsMap.contains(tenMinAgo)) stepsMap.getValue(tenMinAgo) else 0
    }

    private fun getStepsInLast15Min(): Int {
        val fifteenMinAgo = currentTimeIn5Min() - 3
        return if (stepsMap.contains(fifteenMinAgo)) stepsMap.getValue(fifteenMinAgo) else 0
    }

    private fun getStepsInLast30Min(): Int {
        return getStepsInLastXMin(6)
    }

    private fun getStepsInLast60Min(): Int {
        return getStepsInLastXMin(12)
    }

    private fun getStepsInLast180Min(): Int {
        return getStepsInLastXMin(36)
    }

    @VisibleForTesting
    fun send(timestampMillis: Long) {
        val stepsInLast5Minutes = getStepsInLast5Min()
        val stepsInLast10Minutes = getStepsInLast10Min()
        val stepsInLast15Minutes = getStepsInLast15Min()
        val stepsInLast30Minutes = getStepsInLast30Min()
        val stepsInLast60Minutes = getStepsInLast60Min()
        val stepsInLast180Minutes = getStepsInLast180Min()

        aapsLogger.debug(LTag.WEAR, "Steps in last 5 minutes: $stepsInLast5Minutes")
        aapsLogger.debug(LTag.WEAR, "Steps in last 10 minutes: $stepsInLast10Minutes")
        aapsLogger.debug(LTag.WEAR, "Steps in last 15 minutes: $stepsInLast15Minutes")
        aapsLogger.debug(LTag.WEAR, "Steps in last 30 minutes: $stepsInLast30Minutes")
        aapsLogger.debug(LTag.WEAR, "Steps in last 60 minutes: $stepsInLast60Minutes")
        aapsLogger.debug(LTag.WEAR, "Steps in last 180 minutes: $stepsInLast180Minutes")

        val device = (Build.MANUFACTURER ?: "unknown") + " " + (Build.MODEL ?: "unknown")

        val stepsList = listOf(
            EventData.ActionStepsRate(
                duration = 5 * 60 * 1000,
                timestamp = timestampMillis,
                steps5min = stepsInLast5Minutes,
                steps10min = 0,
                steps15min = 0,
                steps30min = 0,
                steps60min = 0,
                steps180min = 0,
                device = device
            ),
            EventData.ActionStepsRate(
                duration = 10 * 60 * 1000,
                timestamp = timestampMillis,
                steps5min = stepsInLast5Minutes,
                steps10min = stepsInLast10Minutes,
                steps15min = 0,
                steps30min = 0,
                steps60min = 0,
                steps180min = 0,
                device = device
            ),
            EventData.ActionStepsRate(
                duration = 15 * 60 * 1000,
                timestamp = timestampMillis,
                steps5min = stepsInLast5Minutes,
                steps10min = stepsInLast10Minutes,
                steps15min = stepsInLast15Minutes,
                steps30min = 0,
                steps60min = 0,
                steps180min = 0,
                device = device
            ),
            EventData.ActionStepsRate(
                duration = 30 * 60 * 1000,
                timestamp = timestampMillis,
                steps5min = stepsInLast5Minutes,
                steps10min = stepsInLast10Minutes,
                steps15min = stepsInLast15Minutes,
                steps30min = stepsInLast30Minutes,
                steps60min = 0,
                steps180min = 0,
                device = device
            ),
            EventData.ActionStepsRate(
                duration = 60 * 60 * 1000,
                timestamp = timestampMillis,
                steps5min = stepsInLast5Minutes,
                steps10min = stepsInLast10Minutes,
                steps15min = stepsInLast15Minutes,
                steps30min = stepsInLast30Minutes,
                steps60min = stepsInLast60Minutes,
                steps180min = stepsInLast180Minutes,
                device = device
            ),
            EventData.ActionStepsRate(
                duration = 180 * 60 * 1000,
                timestamp = timestampMillis,
                steps5min = stepsInLast5Minutes,
                steps10min = stepsInLast10Minutes,
                steps15min = stepsInLast15Minutes,
                steps30min = stepsInLast30Minutes,
                steps60min = stepsInLast60Minutes,
                steps180min = stepsInLast180Minutes,
                device = device
            )
        )
        sendStepsRate(stepsList)
    }

    private fun getStepsInLastXMin(numberOf5MinIncrements: Int): Int {
        var stepCount = 0
        val thirtyMinAgo = currentTimeIn5Min() - numberOf5MinIncrements
        val now = currentTimeIn5Min()
        for (entry in stepsMap.entries) {
            if (entry.key in (thirtyMinAgo + 1)..now) {
                stepCount += entry.value
            }
        }
        return stepCount
    }

}
