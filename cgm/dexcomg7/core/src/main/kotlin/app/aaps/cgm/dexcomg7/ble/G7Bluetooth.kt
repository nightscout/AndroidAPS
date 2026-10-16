package app.aaps.cgm.dexcomg7.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import app.aaps.cgm.dexcomg7.protocol.G7Advertisement
import app.aaps.cgm.dexcomg7.protocol.G7Gatt
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import java.util.UUID

/** One advertisement seen during a scan. */
class G7ScanResult(val address: String, val advertisement: G7Advertisement, val rssi: Int)

/**
 * The Android Bluetooth adapter, as far as this driver needs it: permission checks, a filtered scan,
 * and looking up a device by address.
 */
@SuppressLint("MissingPermission")
@SingleIn(AppScope::class)
@Inject
class G7Bluetooth(private val context: Context) {

    private val adapter: BluetoothAdapter?
        get() = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    val isEnabled: Boolean get() = runCatching { adapter?.isEnabled == true }.getOrDefault(false)

    /**
     * True when this app may scan and connect. False is normal on a fresh install: plugins start before
     * the permission has been asked for. Checked before every scan, because a scan without the
     * permission throws on a Bluetooth thread where nothing catches it, and the app is killed.
     */
    val hasPermissions: Boolean
        get() = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    /** Why nothing could work right now, or null when Bluetooth is usable. */
    fun blockedReason(): String? = when {
        !hasPermissions -> "Bluetooth permission not granted"
        adapter == null -> "No Bluetooth adapter"
        !isEnabled      -> "Bluetooth is off"
        else            -> null
    }

    fun device(address: String): BluetoothDevice? = runCatching { adapter?.getRemoteDevice(address) }.getOrNull()

    private var scanCallback: ScanCallback? = null

    /**
     * Starts a scan filtered on the service UUID a sensor advertises (FEBC). An unfiltered scan with the
     * screen off returns nothing on current Android. Returns false when the scan could not start.
     */
    fun startScan(onResult: (G7ScanResult) -> Unit, onFailed: (String) -> Unit): Boolean {
        stopScan()
        blockedReason()?.let {
            onFailed(it)
            return false
        }
        val scanner = adapter?.bluetoothLeScanner ?: run {
            onFailed("No Bluetooth scanner")
            return false
        }
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(UUID.fromString(G7Gatt.ADVERTISEMENT_SERVICE)), ParcelUuid(UUID.fromString(G7Gatt.ADVERTISEMENT_SERVICE_MASK)))
            .build()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val name = result.scanRecord?.deviceName ?: runCatching { result.device.name }.getOrNull() ?: return
                val manufacturerData = result.scanRecord?.getManufacturerSpecificData(G7Gatt.MANUFACTURER_ID)
                onResult(G7ScanResult(result.device.address, G7Advertisement(name, manufacturerData), result.rssi))
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, it) }
            }

            override fun onScanFailed(errorCode: Int) {
                onFailed("Scan failed: $errorCode")
            }
        }
        scanCallback = callback
        return runCatching { scanner.startScan(listOf(filter), settings, callback) }
            .onFailure { onFailed("Bluetooth refused the scan: ${it.message}") }
            .isSuccess
    }

    fun stopScan() {
        val callback = scanCallback ?: return
        scanCallback = null
        runCatching { adapter?.bluetoothLeScanner?.stopScan(callback) }
    }
}
