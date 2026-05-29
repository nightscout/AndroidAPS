package app.aaps.plugins.source.activities

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.MenuItem
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import app.aaps.core.interfaces.profile.ProfileUtil
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint
import app.aaps.plugins.source.R
import com.nightscout.eversense.EversenseCGMPlugin
import com.nightscout.eversense.callbacks.EversenseWatcher
import com.nightscout.eversense.enums.CalibrationReadiness
import com.nightscout.eversense.enums.EversenseType
import com.nightscout.eversense.models.ActiveAlarm
import com.nightscout.eversense.models.EversenseCGMResult
import com.nightscout.eversense.models.EversenseState
import com.nightscout.eversense.util.EversenseLogger
import javax.inject.Inject

@AndroidEntryPoint
class EversenseCalibrationActivity : AppCompatActivity() {

    @Inject lateinit var profileUtil: ProfileUtil

    companion object {
        private const val TAG = "EversenseCalibration"
        private const val RECONNECT_TIMEOUT_MS = 30000L
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var connectionWatcher: EversenseWatcher? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_eversense_calibration)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.eversense_calibration_action)

        val state = EversenseCGMPlugin.instance.getCurrentState()
        EversenseLogger.info(TAG, "Activity opened — readiness: ${state?.calibrationReadiness}, phase: ${state?.calibrationPhase}, connected: ${EversenseCGMPlugin.instance.isConnected()}")

        val statusText = findViewById<TextView>(R.id.calibration_status)
        statusText.text = when {
            state == null -> getString(R.string.eversense_not_connected)
            state.calibrationReadiness == CalibrationReadiness.READY -> getString(R.string.eversense_calibration_ready)
            else -> readinessMessage(state.calibrationReadiness)
        }

        val unitLabel = findViewById<TextView>(R.id.calibration_unit_label)
        unitLabel.text = profileUtil.units.asText

        val bgInput = findViewById<EditText>(R.id.calibration_bg_input)
        bgInput.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        bgInput.isEnabled = true // Allow submission regardless of readiness — matches official app

        val submitButton = findViewById<Button>(R.id.calibration_submit_button)
        submitButton.isEnabled = true // Allow submission regardless of readiness — matches official app

        // Live-refresh button and status whenever transmitter state changes while screen is open
        val stateWatcher = object : EversenseWatcher {
            override fun onStateChanged(state: EversenseState) {
                mainHandler.post {
                    val ready = true // Readiness is informational only — matches official app
                    submitButton.isEnabled = ready
                    bgInput.isEnabled = ready
                    statusText.text = readinessMessage(state.calibrationReadiness)
                    EversenseLogger.info(TAG, "State updated — readiness: ")
                }
            }
            override fun onTransmitterReady() {}
            override fun onConnectionChanged(connected: Boolean) {}
            override fun onCGMRead(type: EversenseType, readings: List<EversenseCGMResult>) {}
            override fun onAlarmReceived(alarm: ActiveAlarm) {}
            override fun onTransmitterNotPlaced() {}
        }
        EversenseCGMPlugin.instance.addWatcher(stateWatcher)
        connectionWatcher = stateWatcher

        submitButton.setOnClickListener {
            val rawInput = bgInput.text.toString()
            EversenseLogger.info(TAG, "Submit pressed — raw input: '$rawInput', units: ${profileUtil.units.asText}")
            val bgValue = rawInput.toDoubleOrNull()
            if (bgValue == null || bgValue <= 0) {
                EversenseLogger.warning(TAG, "Invalid calibration value — bgValue: $bgValue")
                Toast.makeText(this, getString(R.string.eversense_calibration_invalid_value), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val bgMgDl = if (profileUtil.units.asText == "mmol") {
                (bgValue * 18.0182).toInt()
            } else {
                bgValue.toInt()
            }
            EversenseLogger.info(TAG, "Calibration submitting — bgValue: $bgValue, bgMgDl: $bgMgDl")

            submitButton.isEnabled = false
            statusText.text = getString(R.string.eversense_calibration_sending)

            if (EversenseCGMPlugin.instance.isConnected()) {
                sendCalibration(bgMgDl, submitButton, statusText)
            } else {
                EversenseLogger.info(TAG, "Not connected — triggering reconnect before calibration")
                statusText.text = getString(R.string.eversense_reconnecting)
                reconnectThenCalibrate(bgMgDl, submitButton, statusText)
            }
        }
    }

    private fun reconnectThenCalibrate(bgMgDl: Int, submitButton: Button, statusText: TextView) {
        var reconnected = false

        val watcher = object : EversenseWatcher {
            override fun onTransmitterReady() {
                if (!reconnected) {
                    reconnected = true
                    EversenseLogger.info(TAG, "Transmitter ready after full sync — proceeding with calibration")
                    mainHandler.post {
                        statusText.text = getString(R.string.eversense_calibration_sending)
                    }
                    sendCalibration(bgMgDl, submitButton, statusText)
                }
            }
            override fun onConnectionChanged(connected: Boolean) {}
            override fun onStateChanged(state: EversenseState) {}
            override fun onCGMRead(type: EversenseType, readings: List<EversenseCGMResult>) {}
            override fun onAlarmReceived(alarm: ActiveAlarm) {}
            override fun onTransmitterNotPlaced() {}
        }

        connectionWatcher = watcher
        EversenseCGMPlugin.instance.addWatcher(watcher)
        EversenseCGMPlugin.instance.connect(null)

        // Timeout — if not reconnected within 15 seconds, give up
        mainHandler.postDelayed({
            if (!reconnected) {
                EversenseLogger.warning(TAG, "Transmitter ready timed out after ${RECONNECT_TIMEOUT_MS/1000}s — calibration aborted")
                EversenseCGMPlugin.instance.removeWatcher(watcher)
                connectionWatcher = null
                mainHandler.post {
                    submitButton.isEnabled = true
                    statusText.text = getString(R.string.eversense_calibration_ready)
                    Toast.makeText(this, getString(R.string.eversense_calibration_failed), Toast.LENGTH_LONG).show()
                }
            }
        }, RECONNECT_TIMEOUT_MS)
    }

    private fun sendCalibration(bgMgDl: Int, submitButton: Button, statusText: TextView) {
        Thread {
            connectionWatcher?.let {
                EversenseCGMPlugin.instance.removeWatcher(it)
                connectionWatcher = null
            }
            EversenseLogger.info(TAG, "Calibration thread started — sending $bgMgDl mg/dL to transmitter")
            val success = EversenseCGMPlugin.instance.sendCalibration(bgMgDl)
            EversenseLogger.info(TAG, "Calibration result — success: $success")
            if (success) {
                EversenseLogger.info(TAG, "Triggering fullSync after successful calibration")
                EversenseCGMPlugin.instance.triggerFullSync(force = true)
            }
            runOnUiThread {
                if (success) {
                    Toast.makeText(this, getString(R.string.eversense_calibration_success), Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    submitButton.isEnabled = true
                    statusText.text = getString(R.string.eversense_calibration_ready)
                    Toast.makeText(this, getString(R.string.eversense_calibration_failed), Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun readinessMessage(readiness: CalibrationReadiness): String = when (readiness) {
        CalibrationReadiness.READY                    -> getString(R.string.eversense_calibration_ready)
        CalibrationReadiness.NOT_ENOUGH_DATA          -> "Please ensure your Transmitter is placed over the sensor, wait a few minutes, and try again."
        CalibrationReadiness.GLUCOSE_RATE_TOO_HIGH    -> "Your Sensor glucose is changing too quickly. Please wait 10 minutes and try again."
        CalibrationReadiness.TOO_SOON                 -> "It is not time for your scheduled calibration. Please try again when prompted."
        CalibrationReadiness.DROPOUT_PHASE            -> "Glucose data is currently unavailable. Please measure your glucose manually using your blood glucose meter."
        CalibrationReadiness.SENSOR_EOL               -> "Sensor is retired. Please contact your health care provider."
        CalibrationReadiness.NO_SENSOR_LINKED         -> "Your Transmitter is not linked to a Sensor. Please link a Sensor and try again."
        CalibrationReadiness.UNSUPPORTED_MODE         -> "Your Transmitter is in a mode which does not support Calibration. Please contact your health care provider for further questions."
        CalibrationReadiness.WAITING_POST_CALIBRATION -> "Waiting for post calibration sensor measurements. Please wait a few minutes and try again."
        CalibrationReadiness.LED_DISCONNECT_DETECTED  -> "Sensor disconnect detected. Please ensure your Transmitter is placed correctly over the sensor."
        CalibrationReadiness.TRANSMITTER_EOL          -> "Your transmitter needs to be replaced. Contact your distributor to order a new transmitter."
        CalibrationReadiness.REASON_UNKNOWN           -> "Calibration is not available. Please try again later."
    }

    override fun onDestroy() {
        super.onDestroy()
        connectionWatcher?.let {
            EversenseCGMPlugin.instance.removeWatcher(it)
            connectionWatcher = null
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }
}