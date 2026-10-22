package app.aaps.pump.omnipod.omnipod5.bledriver.pod.util
import app.aaps.pump.omnipod.common.bledriver.pod.util.BluetoothServiceUuids

import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.ParcelUuid
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.omnipod.common.bledriver.comm.exceptions.ScanException
import app.aaps.pump.omnipod.common.bledriver.comm.exceptions.ScanFailFoundTooManyException
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.scan.BleDiscoveredDevice
import app.aaps.pump.omnipod.common.bledriver.pod.definition.PodType
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * A pairable pod found during a [PodTypeAwarePodScanner] scan, holding both the raw
 * Android scan result and the parsed [PodAdvertisement] for whichever pod type matched.
 */
class PodTypeAwareDiscoveredDevice(
    val scanResult: ScanResult,
    val advertisement: PodAdvertisement
) : BleDiscoveredDevice {

    override val address: String get() = scanResult.device.address

    override fun toString(): String =
        "PodTypeAwareDiscoveredDevice{address=$address, podType=${advertisement.podType}, " +
            "pairable=${advertisement.pairable}}"
}

/**
 * BLE scanner supporting pod discovery across pod types (currently Dash and Omnipod 5),
 * additive alongside the existing Dash-only
 * [app.aaps.pump.omnipod.common.bledriver.comm.legacy.scan.PodScanner] rather than
 * replacing it - that class's existing single-pod-type contract and callers are
 * untouched by this file.
 *
 * Scanning behavior intentionally differs by pod type, matching how each pod's
 * advertisement actually works (see [PodAdvertisement]):
 * - **Dash**: the target pod's id is already known (e.g. from a prior scan or user
 *   input), so [scanForPod] filters for a specific [expectedPodId].
 * - **Omnipod 5**: a fresh pod's real identity isn't known before pairing - its
 *   advertisement only indicates "not yet paired to anyone" - so [expectedPodId] is
 *   ignored for O5 and [scanForPod] simply returns the first pairable O5 advertisement
 *   found.
 */
class PodTypeAwarePodScanner(
    private val aapsLogger: AAPSLogger,
    private val bluetoothAdapter: BluetoothAdapter
) {

    @Throws(InterruptedException::class, ScanException::class)
    fun scanForPod(podType: PodType, expectedPodId: Long? = null): PodTypeAwareDiscoveredDevice {
        val scanner = bluetoothAdapter.bluetoothLeScanner
            ?: throw ScanException("BluetoothLeScanner not available (Bluetooth may be off or unsupported)")
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid.fromString(BluetoothServiceUuids.unpairedAdvertisementUuid(podType)))
            .build()
        val scanSettings = ScanSettings.Builder()
            .setLegacy(false)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val collector = PodTypeAwareScanCollector(aapsLogger, podType, expectedPodId)
        aapsLogger.debug(LTag.PUMPBTCOMM, "Scanning for $podType with filter: $filter settings: $scanSettings")
        scanner.startScan(listOf(filter), scanSettings, collector)
        Thread.sleep(SCAN_DURATION_MS)
        scanner.flushPendingScanResults(collector)
        scanner.stopScan(collector)

        val found = collector.collect()
        aapsLogger.debug(LTag.PUMPBTCOMM, "Scan for $podType complete: ${found.size} pairable pod(s) found")
        return when {
            found.isEmpty()    -> throw ScanException("No pairable $podType pod found")
            found.size > 1     -> throw ScanFailFoundTooManyException(found)
            else               -> found[0]
        }
    }

    companion object {

        private const val SCAN_DURATION_MS = 5000L
    }
}

private class PodTypeAwareScanCollector(
    private val aapsLogger: AAPSLogger,
    private val podType: PodType,
    private val expectedPodId: Long?
) : ScanCallback() {

    private val found: ConcurrentHashMap<String, PodTypeAwareDiscoveredDevice> = ConcurrentHashMap()
    private var scanFailed = 0

    override fun onScanResult(callbackType: Int, result: ScanResult) {
        aapsLogger.debug(LTag.PUMPBTCOMM, "Scan found: $result")

        val serviceUuids: List<ParcelUuid>? = result.scanRecord?.serviceUuids
        val advertisement = PodAdvertisement.parse(serviceUuids, podType) ?: return

        if (!advertisement.pairable) return

        if (podType == PodType.DASH && expectedPodId != null && advertisement.podId != expectedPodId) {
            aapsLogger.debug(
                LTag.PUMPBTCOMM,
                "Ignoring pairable pod with non-matching podId: found=${advertisement.podId}, expected=$expectedPodId"
            )
            return
        }

        found[result.device.address] = PodTypeAwareDiscoveredDevice(result, advertisement)
        aapsLogger.debug(
            LTag.PUMPBTCOMM,
            "Accepted pairable $podType pod: address=${result.device.address}, podId=${advertisement.podId}"
        )
    }

    override fun onScanFailed(errorCode: Int) {
        aapsLogger.warn(LTag.PUMPBTCOMM, "Scan failed with errorCode: $errorCode")
        scanFailed = errorCode
    }

    @Throws(ScanException::class)
    fun collect(): List<PodTypeAwareDiscoveredDevice> {
        if (scanFailed != 0) {
            throw ScanException(scanFailed)
        }
        return Collections.unmodifiableList(found.values.toList())
    }
}
