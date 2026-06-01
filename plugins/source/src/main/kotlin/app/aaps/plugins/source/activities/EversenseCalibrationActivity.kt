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
import com.nightscout.eversense.enums.EversenseType
import com.nightscout.eversense.models.ActiveAlarm
import com.nightscout.eversense.models.EversenseCGMResult
import com.nightscout.eversense.models.EversenseState
import com.nightscout.eversense.util.EversenseLogger
import javax.inject.Inject

@AndroidEntryPoint
class EversenseCalibrationActivity : AppCompatActivity() {

    @Inject lateinit var profileUtil: ProfileUtil
    @Inject lateinit var eversense: EversenseCGMPlugin

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

        EversenseLogger.info(TAG, "Activity opened — connected: ${eversense.isConnected()}")

        val unitLabel = findViewById<TextView>(R.id.calibration_unit_label)
        unitLabel.text = profileUtil.units.asText

        val bgInput = findViewById<EditText>(R.id.calibration_bg_input)
        bgInput.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        bgInput.isEnabled = true

        val submitButton = findViewById<Button>(R.id.calibration_submit_button)
        submitButton.isEnabled = true

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

            if (eversense.isConnected()) {
                sendCalibration(bgMgDl, submitButton)
            } else {
                EversenseLogger.info(TAG, "Not connected — triggering reconnect before calibration")
                reconnectThenCalibrate(bgMgDl, submitButton)
            }
        }
    }

    private fun reconnectThenCalibrate(bgMgDl: Int, submitButton: Button) {
        var reconnected = false

        val watcher = object : EversenseWatcher {
            override fun onTransmitterReady() {
                if (!reconnected) {
                    reconnected = true
                    EversenseLogger.info(TAG, "Transmitter ready — proceeding with calibration")
                    sendCalibration(bgMgDl, submitButton)
                }
            }
            override fun onConnectionChanged(connected: Boolean) { /* No-op: only onTransmitterReady is needed for calibration */ }
            override fun onStateChanged(state: EversenseState) { /* No-op */ }
            override fun onCGMRead(type: EversenseType, readings: List<EversenseCGMResult>) { /* No-op */ }
            override fun onAlarmReceived(alarm: ActiveAlarm) { /* No-op */ }
            override fun onTransmitterNotPlaced() { /* No-op */ }
        }

        connectionWatcher = watcher
        eversense.addWatcher(watcher)
        eversense.connect(null)

        mainHandler.postDelayed({
            if (!reconnected) {
                EversenseLogger.warning(TAG, "Reconnect timed out — calibration aborted")
                eversense.removeWatcher(watcher)
                connectionWatcher = null
                mainHandler.post {
                    submitButton.isEnabled = true
                    Toast.makeText(this, getString(R.string.eversense_calibration_failed), Toast.LENGTH_LONG).show()
                }
            }
        }, RECONNECT_TIMEOUT_MS)
    }

    private fun sendCalibration(bgMgDl: Int, submitButton: Button) {
        Thread {
            connectionWatcher?.let {
                eversense.removeWatcher(it)
                connectionWatcher = null
            }
            EversenseLogger.info(TAG, "Calibration thread started — sending $bgMgDl mg/dL to transmitter")
            val success = eversense.sendCalibration(bgMgDl)
            EversenseLogger.info(TAG, "Calibration result — success: $success")
            if (success) {
                EversenseLogger.info(TAG, "Triggering fullSync after successful calibration")
                eversense.triggerFullSync(force = true)
            }
            runOnUiThread {
                if (success) {
                    Toast.makeText(this, getString(R.string.eversense_calibration_success), Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    submitButton.isEnabled = true
                    Toast.makeText(this, getString(R.string.eversense_calibration_failed), Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        connectionWatcher?.let {
            eversense.removeWatcher(it)
            connectionWatcher = null
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }
}