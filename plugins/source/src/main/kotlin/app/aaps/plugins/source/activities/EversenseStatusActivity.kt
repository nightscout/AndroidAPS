package app.aaps.plugins.source.activities

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint
import app.aaps.plugins.source.R
import app.aaps.plugins.eversense.EversenseCGMPlugin
import app.aaps.plugins.eversense.callbacks.EversenseScanCallback
import app.aaps.plugins.eversense.callbacks.EversenseWatcher
import app.aaps.plugins.eversense.models.ActiveAlarm
import app.aaps.plugins.eversense.models.EversenseState
import app.aaps.plugins.eversense.models.EversenseScanResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class EversenseStatusActivity : AppCompatActivity(), EversenseWatcher {

    @Inject lateinit var eversense: EversenseCGMPlugin

    private val mainHandler = Handler(Looper.getMainLooper())
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    override fun onResume() {
        super.onResume()
        eversense.addWatcher(this)
        updateStatus()
    }

    override fun onPause() {
        super.onPause()
        eversense.removeWatcher(this)
    }

    // EversenseWatcher: update button instantly on connection/state change
    override fun onConnectionChanged(connected: Boolean) { mainHandler.post { updateStatus() } }
    override fun onStateChanged(state: EversenseState) { mainHandler.post { updateStatus() } }
    override fun onTransmitterReady() {}
    override fun onTransmitterNotPlaced() {}
    override fun onAlarmReceived(alarm: ActiveAlarm) {}
    override fun onCGMRead(type: app.aaps.plugins.eversense.enums.EversenseType, readings: List<app.aaps.plugins.eversense.models.EversenseCGMResult>) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_eversense_status)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.eversense_status_title)

        updateStatus()

        findViewById<Button>(R.id.eversense_btn_connect).setOnClickListener {
            handleConnectTap()
        }

        findViewById<Button>(R.id.eversense_btn_sync).setOnClickListener {
            if (eversense.isConnected()) {
                ioScope.launch { eversense.triggerFullSync(force = true) }
                mainHandler.postDelayed({ updateStatus() }, 5000)
            }
        }
    }

    private fun updateStatus() {
        val state = eversense.getCurrentState()
        val notConnected = getString(R.string.eversense_not_connected)

        findViewById<TextView>(R.id.eversense_status_connected).text =
            "Connected: " + if (eversense.isConnected()) "✅" else "❌"
        findViewById<TextView>(R.id.eversense_status_battery).text =
            "Battery: " + (state?.let { "${it.batteryPercentage}%" } ?: notConnected)
        findViewById<TextView>(R.id.eversense_status_insertion).text =
            "Insertion date: " + (state?.let { dateFormatter.format(Date(it.insertionDate)) } ?: notConnected)
        findViewById<TextView>(R.id.eversense_status_last_sync).text =
            "Last sync: " + (state?.let { if (it.lastSync > 0) dateFormatter.format(Date(it.lastSync)) else "Never" } ?: notConnected)
        findViewById<TextView>(R.id.eversense_status_signal).text =
            "Placement signal: " + (state?.let { signalToLabel(it.sensorSignalStrength) } ?: notConnected)

        findViewById<TextView>(R.id.eversense_status_phase).text =
            "Calibration phase: " + (state?.calibrationPhase?.name ?: notConnected)

        findViewById<TextView>(R.id.eversense_status_last_cal).text =
            "Last calibration: " + (state?.let { if (it.lastCalibrationDate > 0) dateFormatter.format(Date(it.lastCalibrationDate)) else notConnected } ?: notConnected)
        findViewById<TextView>(R.id.eversense_status_next_cal).text =
            "Next calibration: " + (state?.let { if (it.nextCalibrationDate > 0) dateFormatter.format(Date(it.nextCalibrationDate)) else notConnected } ?: notConnected)
        findViewById<Button>(R.id.eversense_btn_connect).text =
            if (eversense.isConnected()) "Disconnect" else "Connect"
        findViewById<Button>(R.id.eversense_btn_sync).isEnabled = eversense.isConnected()
    }

    private fun handleConnectTap() {
        if (eversense.isConnected()) {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.eversense_scan_title))
                .setMessage("Disconnect from transmitter?")
                .setPositiveButton("Disconnect") { _, _ ->
                    eversense.disconnect()
                    mainHandler.postDelayed({ updateStatus() }, 500)
                }
                .setNegativeButton("Cancel", null)
                .show()
        } else {
            val prefs = getSharedPreferences("EversenseCGMManager", Context.MODE_PRIVATE)
            val hasStoredDevice = prefs.getString("eversense_remote_device", null) != null
            if (hasStoredDevice) {
                ioScope.launch { eversense.connect(null) }
                mainHandler.postDelayed({ updateStatus() }, 3000)
            } else {
                showDeviceSelectionDialog()
            }
        }
    }

    private fun showDeviceSelectionDialog() {
        val foundDevices = mutableListOf<EversenseScanResult>()
        var isCancelled = false
        var dialog: AlertDialog? = null

        val scanCallback = object : EversenseScanCallback {
            override fun onResult(item: EversenseScanResult) {
                if (!isCancelled && item.name.matches(Regex("T\\d+.*")) && foundDevices.none { it.name == item.name })
                    foundDevices.add(item)
            }
        }

        eversense.startScan(scanCallback)

        mainHandler.postDelayed({
            if (isCancelled) return@postDelayed
            eversense.stopScan()
            dialog?.dismiss()
            if (foundDevices.isEmpty()) {
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.eversense_scan_title))
                    .setMessage(getString(R.string.eversense_no_transmitters_found))
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            } else {
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.eversense_scan_title))
                    .setItems(foundDevices.map { it.name }.toTypedArray()) { _, position ->
                        ioScope.launch { eversense.connect(foundDevices[position].device) }
                        mainHandler.postDelayed({ updateStatus() }, 3000)
                    }
                    .setNegativeButton(getString(R.string.eversense_scan_cancel), null)
                    .show()
            }
        }, 10000)

        dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.eversense_scan_title))
            .setMessage(getString(R.string.eversense_scan_in_progress))
            .setNegativeButton(getString(R.string.eversense_scan_cancel)) { _, _ ->
                isCancelled = true
                eversense.stopScan()
            }
            .setCancelable(false)
            .show()
    }

    private fun signalToLabel(strength: Int): String = when {
        strength >= 75 -> getString(R.string.eversense_signal_excellent)
        strength >= 48 -> getString(R.string.eversense_signal_good)
        strength >= 30 -> getString(R.string.eversense_signal_fair)
        strength >= 25 -> getString(R.string.eversense_signal_weak)
        strength > 0   -> getString(R.string.eversense_signal_poor)
        else           -> getString(R.string.eversense_not_connected)
    }

    override fun onDestroy() {
        super.onDestroy()
        ioScope.cancel()
        eversense.removeWatcher(this)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }
}