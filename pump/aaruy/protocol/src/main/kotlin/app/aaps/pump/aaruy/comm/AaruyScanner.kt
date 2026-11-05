package app.aaps.pump.aaruy.comm

import android.annotation.SuppressLint
import no.nordicsemi.android.support.v18.scanner.BluetoothLeScannerCompat
import no.nordicsemi.android.support.v18.scanner.ScanCallback
import no.nordicsemi.android.support.v18.scanner.ScanResult
import no.nordicsemi.android.support.v18.scanner.ScanSettings

/** Finds pumps by their name. */
internal class AaruyScanner {

    private var scanning = false
    private val results = ArrayList<ScanResult>()

    var listener: ScanResultListener? = null

    private val callback = object : ScanCallback() {
        override fun onBatchScanResults(batch: List<ScanResult>) = onBatch(batch)
    }

    /** Keeps each pump once. Other devices nearby are left out. */
    @SuppressLint("MissingPermission")
    internal fun onBatch(batch: List<ScanResult>) {
        batch.forEach { result ->
            val name = result.device.name
            if (!name.isNullOrEmpty() && name.contains(DEVICE_NAME_PREFIX) && results.none { it.device == result.device }) results.add(result)
        }
        listener?.onScanResult(results)
    }

    fun start() {
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setLegacy(false)
            .setReportDelay(500L)
            .setUseHardwareBatchingIfSupported(false)
            .build()
        results.clear()
        if (!scanning) {
            scanning = true
            BluetoothLeScannerCompat.getScanner().startScan(null, settings, callback)
        }
    }

    fun stop() {
        if (scanning) {
            scanning = false
            BluetoothLeScannerCompat.getScanner().stopScan(callback)
        }
    }

    private companion object {

        const val DEVICE_NAME_PREFIX = "aaruy_"
    }
}
