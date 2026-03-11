package app.aaps.pump.omnipod.common.bledriver.comm.legacy.scan

import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import android.os.ParcelUuid
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.omnipod.common.bledriver.comm.exceptions.ScanException
import app.aaps.pump.omnipod.common.bledriver.comm.exceptions.ScanFailFoundTooManyException
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.scan.PodScanner as PodScannerInterface
import java.util.Arrays

class PodScanner(private val logger: AAPSLogger, private val bluetoothAdapter: BluetoothAdapter) : PodScannerInterface {

    @Throws(InterruptedException::class, ScanException::class)
    override fun scanForPod(serviceUUID: String?, podID: Long): app.aaps.pump.omnipod.common.bledriver.comm.interfaces.scan.BleDiscoveredDevice {
        val scanner = bluetoothAdapter.bluetoothLeScanner
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid.fromString(serviceUUID))
            .build()
        val scanSettings = ScanSettings.Builder()
            .setLegacy(false)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        val scanCollector = ScanCollector(logger, podID)
        logger.debug(LTag.PUMPBTCOMM, "Scanning with filters: $filter settings$scanSettings")
        scanner.startScan(Arrays.asList(filter), scanSettings, scanCollector)
        Thread.sleep(SCAN_DURATION_MS.toLong())
        scanner.flushPendingScanResults(scanCollector)
        scanner.stopScan(scanCollector)
        val collected = scanCollector.collect()
        if (collected.isEmpty()) {
            throw ScanException("Not found")
        } else if (collected.size > 1) {
            throw ScanFailFoundTooManyException(collected)
        }
        return collected[0]
    }

    companion object {
        const val SCAN_FOR_SERVICE_UUID = PodScannerInterface.SCAN_FOR_SERVICE_UUID
        const val POD_ID_NOT_ACTIVATED = PodScannerInterface.POD_ID_NOT_ACTIVATED
        private const val SCAN_DURATION_MS = 5000
    }
}
