package app.aaps.pump.ap550s.compose.pairing

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelUuid
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale

/** One pump found while scanning. The serial number comes from the advertisement, not from the name. */
data class Ap550ScannedDevice(
    val name: String,
    val address: String,
    val serialNumber: String?
)

/**
 * Bluetooth LE scan for pumps that advertise the AP550 service.
 *
 * The service UUID is used as a scan filter, so the callback only ever sees pump-like devices. The
 * name is taken from the advertisement; the serial number has to be read out of the raw
 * advertisement bytes, because the pump does not put it in any standard field.
 *
 * The scanner is owned by the pairing wizard: [start] when the list is on screen, [stop] when the
 * user picked a device or left.
 */
class Ap550BleScanner @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val context: Context
) {

    private val _devices = MutableStateFlow<List<Ap550ScannedDevice>>(emptyList())
    val devices: StateFlow<List<Ap550ScannedDevice>> = _devices

    /** Last scan error code, or null. Set by the Android stack when scanning cannot start. */
    private val _scanError = MutableStateFlow<Int?>(null)
    val scanError: StateFlow<Int?> = _scanError

    @Volatile private var scanning = false

    private val adapter get() =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    fun start() {
        if (scanning) return
        _scanError.value = null
        if (!hasScanPermission()) {
            aapsLogger.error(LTag.PUMPBTCOMM, "BLE scan needs the scan permission")
            return
        }
        val scanner = adapter?.takeIf { it.isEnabled }?.bluetoothLeScanner
        if (scanner == null) {
            aapsLogger.error(LTag.PUMPBTCOMM, "BLE scan needs an enabled Bluetooth adapter")
            return
        }
        _devices.value = emptyList()
        scanning = true
        scanner.startScan(
            listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid.fromString(SERVICE_UUID)).build()),
            ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
            scanCallback
        )
        aapsLogger.debug(LTag.PUMPBTCOMM, "BLE scan started")
    }

    fun stop() {
        if (!scanning) return
        scanning = false
        if (hasScanPermission() && adapter?.isEnabled == true) {
            adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        }
        aapsLogger.debug(LTag.PUMPBTCOMM, "BLE scan stopped")
    }

    private fun hasScanPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED

    private val scanCallback = object : ScanCallback() {

        override fun onScanResult(callbackType: Int, result: ScanResult) {
            addDevice(result)
        }

        override fun onBatchScanResults(results: List<ScanResult>) {
            results.forEach { addDevice(it) }
        }

        override fun onScanFailed(errorCode: Int) {
            aapsLogger.error(LTag.PUMPBTCOMM, "BLE scan failed with error code $errorCode")
            scanning = false
            _scanError.value = errorCode
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun addDevice(result: ScanResult) {
        val device = result.device
        val record = result.scanRecord ?: return
        val name = record.deviceName.orEmpty()
        // The service filter already dropped everything that is not a pump; these two checks keep a
        // device out of the list when the advertisement is close but not one we can pair with.
        val hasPumpService = record.serviceUuids?.any { it.uuid.toString().equals(SERVICE_UUID, ignoreCase = true) } == true
        if (!hasPumpService || !name.lowercase(Locale.US).contains(NAME_FILTER)) {
            aapsLogger.debug(LTag.PUMPBTCOMM, "Skipping ${device.address}: $name does not look like a pump")
            return
        }
        val serialNumber = parseSerialNumber(record.bytes)
        val scanned = Ap550ScannedDevice(name = name, address = device.address, serialNumber = serialNumber)
        _devices.update { current ->
            // A device is re-reported on every advertisement. Replace the entry instead of adding it
            // again, so the list stays one row per pump.
            if (current.any { it.address == scanned.address }) {
                current.map { if (it.address == scanned.address) scanned else it }
            } else {
                current + scanned
            }
        }
        aapsLogger.debug(LTag.PUMPBTCOMM, "Pump ${scanned.address} name=$name serial=${serialNumber ?: "-"}")
    }

    companion object {

        /** Service the pump advertises. */
        private const val SERVICE_UUID = "0000d355-0000-1000-8000-00805f9b34fb"

        /** Pumps carry this part in their advertised name. */
        private const val NAME_FILTER = "550"

        /** Advertisement type of the serial number, as used by the pump. */
        private const val DATA_TYPE_URI = 0x24

        /** The serial number sits in the last part of the advertisement. */
        private const val SERIAL_LENGTH = 13

        /**
         * Reads the serial number out of the raw advertisement.
         *
         * Newer firmware puts it in a URI field as text. Older firmware (before DCN18) sends three
         * numbers instead, which are printed as two digits each. An advertisement that is too short
         * is not a pump we can pair with, so null is returned.
         */
        private fun parseSerialNumber(record: ByteArray?): String? {
            if (record == null || record.size < MIN_RECORD_SIZE) {
                return null
            }
            // The URI field moves by two bytes on firmware whose byte 13 is 'E'.
            val startIndex = if (record[13] == 'E'.code.toByte()) 46 else 44
            if (record[startIndex - 2].toInt() == DATA_TYPE_URI) {
                return buildString {
                    for (i in startIndex until startIndex + SERIAL_LENGTH) {
                        val value = record[i].toInt() and 0xFF
                        append(if (value in PRINTABLE_RANGE) value.toChar() else '.')
                    }
                }
            }
            // Older firmware: three bytes, back to front, two digits each.
            val offset = if (record[13] == 'E'.code.toByte()) 22 else 21
            val parts = (offset + 2 downTo offset).map { record[it].toInt() and 0xFF }
            return parts.joinToString(separator = "") { String.format(Locale.US, "%02d", it) }
        }

        /** Below this the advertisement does not hold a serial number. */
        private const val MIN_RECORD_SIZE = 62

        private val PRINTABLE_RANGE = 32..126
    }
}