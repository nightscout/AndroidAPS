package app.aaps.plugins.source.activities

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import app.aaps.ui.compose.eversenseCalibrationDialog.EversenseCalibrationDynamics
import app.aaps.ui.compose.eversenseCalibrationDialog.EversenseCalibrationDynamicsWarning
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.keys.LongNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.MetroAppCompatActivity
import app.aaps.plugins.source.R
import app.aaps.plugins.eversense.EversenseCGMPlugin
import app.aaps.plugins.eversense.callbacks.EversenseWatcher
import app.aaps.plugins.eversense.enums.EversenseType
import app.aaps.plugins.eversense.models.ActiveAlarm
import app.aaps.plugins.eversense.models.EversenseCGMResult
import app.aaps.plugins.eversense.models.EversenseState
import app.aaps.plugins.eversense.util.EversenseLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import dev.zacsweers.metro.Inject

class EversenseCalibrationActivity : MetroAppCompatActivity() {

    @Inject lateinit var profileUtil: ProfileUtil
    @Inject lateinit var eversenseCGM: EversenseCGMPlugin
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var dynamics: EversenseCalibrationDynamics

    private var submitting by mutableStateOf(false)

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val TAG = "EversenseCalibration"
        private const val RECONNECT_TIMEOUT_MS = 30000L
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var connectionWatcher: EversenseWatcher? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setEversenseContent(R.string.eversense_calibration_action, preferences) {
            var input by rememberSaveable { mutableStateOf("") }
            EversenseCalibrationDynamicsWarning(dynamics)
            EversenseCard {
                OutlinedTextField(
                    value = input, onValueChange = { input = it },
                    label = { Text(stringResource(R.string.eversense_calibration_bg_value)) },
                    suffix = { Text(profileUtil.units.displayLabel) },
                    enabled = !submitting, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submitCalibration(input) })
                )
                Button(onClick = { submitCalibration(input) }, enabled = !submitting, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.eversense_calibration_submit))
                }
            }
        }
    }

    private fun submitCalibration(rawInput: String) {
        if (submitting) return
        val bgValue = rawInput.toDoubleOrNull()
        if (bgValue == null || !bgValue.isFinite() || bgValue <= 0) {
            Toast.makeText(this, getString(R.string.eversense_calibration_invalid_value), Toast.LENGTH_SHORT).show()
            return
        }
        val bgMgDl = profileUtil.convertToMgdl(bgValue, profileUtil.units).toInt()
        submitting = true
        if (eversenseCGM.isConnected()) sendCalibration(bgMgDl)
        else reconnectThenCalibrate(bgMgDl)
    }

    private fun reconnectThenCalibrate(bgMgDl: Int) {
        var reconnected = false

        val watcher = object : EversenseWatcher {
            override fun onTransmitterReady() {
                if (!reconnected) {
                    reconnected = true
                    EversenseLogger.info(TAG, "Transmitter ready — proceeding with calibration")
                    sendCalibration(bgMgDl)
                }
            }
            override fun onConnectionChanged(connected: Boolean) {}
            override fun onStateChanged(state: EversenseState) {}
            override fun onCGMRead(type: EversenseType, readings: List<EversenseCGMResult>) {}
            override fun onAlarmReceived(alarm: ActiveAlarm) {}
            override fun onTransmitterNotPlaced() {}
        }

        connectionWatcher = watcher
        eversenseCGM.addWatcher(watcher)
        eversenseCGM.connect(null)

        mainHandler.postDelayed({
            if (!reconnected) {
                EversenseLogger.warning(TAG, "Reconnect timed out — calibration aborted")
                eversenseCGM.removeWatcher(watcher)
                connectionWatcher = null
                mainHandler.post {
                    submitting = false
                    Toast.makeText(this, getString(R.string.eversense_calibration_failed), Toast.LENGTH_LONG).show()
                }
            }
        }, RECONNECT_TIMEOUT_MS)
    }

    private fun sendCalibration(bgMgDl: Int) {
        ioScope.launch {
            connectionWatcher?.let {
                eversenseCGM.removeWatcher(it)
                connectionWatcher = null
            }
            EversenseLogger.info(TAG, "Calibration coroutine started — sending $bgMgDl mg/dL to transmitter")
            val success = eversenseCGM.sendCalibration(bgMgDl)
            EversenseLogger.info(TAG, "Calibration result — success: $success")
            if (success) {
                EversenseLogger.info(TAG, "Triggering fullSync after successful calibration")
                eversenseCGM.triggerFullSync(force = true)
                // Drives the home-screen "Calibration in progress" countdown banner.
                preferences.put(LongNonKey.EversenseLastCalibrationSubmittedAt, System.currentTimeMillis())
            }
            runOnUiThread {
                if (success) {
                    Toast.makeText(this@EversenseCalibrationActivity, getString(R.string.eversense_calibration_success), Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    submitting = false
                    Toast.makeText(this@EversenseCalibrationActivity, getString(R.string.eversense_calibration_failed), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ioScope.cancel()
        mainHandler.removeCallbacksAndMessages(null)
        connectionWatcher?.let {
            eversenseCGM.removeWatcher(it)
            connectionWatcher = null
        }
    }

}
