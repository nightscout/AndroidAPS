package app.aaps.pump.omnipod.dash.driver.comm.scan

import android.bluetooth.le.ScanRecord
import android.bluetooth.le.ScanResult
import android.os.ParcelUuid

class BleDiscoveredDevice(val scanResult: ScanResult, private val scanRecord: ScanRecord, private val podId: Long) {

    private val sequenceNo: Int
    private val lotNo: Long

    @Throws(DiscoveredInvalidPodException::class)
    private fun validateServiceUUIDs() {
        val serviceUuids = scanRecord.serviceUuids
        if (serviceUuids.size != 9) {
            throw DiscoveredInvalidPodException("Expected 9 service UUIDs, got" + serviceUuids.size, serviceUuids)
        }
        if (extractUUID16(serviceUuids[0]) != MAIN_SERVICE_UUID) {
            // this is the service that we filtered for
            throw DiscoveredInvalidPodException(
                "The first exposed service UUID should be 4024, got " + extractUUID16(
                    serviceUuids[0]
                ),
                serviceUuids
            )
        }
        // TODO understand what is serviceUUIDs[1]. 0x2470. Alarms?
        if (extractUUID16(serviceUuids[2]) != UNKNOWN_THIRD_SERVICE_UUID) {
            // constant?
            throw DiscoveredInvalidPodException(
                "The third exposed service UUID should be 000a, got " + serviceUuids[2],
                serviceUuids
            )
        }
    }

    @Throws(DiscoveredInvalidPodException::class)
    private fun validatePodId() {
        val serviceUUIDs = scanRecord.serviceUuids
        val hexPodId = extractUUID16(serviceUUIDs[3]) + extractUUID16(serviceUUIDs[4])
        val podId = hexPodId.toLong(16)
        if (this.podId != podId) {
            throw DiscoveredInvalidPodException(
                "This is not the POD we are looking for: ${this.podId} . Found: $podId/$hexPodId",
                serviceUUIDs
            )
        }
    }

    private fun parseLotNo(): Long {
        val serviceUUIDs = scanRecord.serviceUuids
        val lotSeq = extractUUID16(serviceUUIDs[5]) +
            extractUUID16(serviceUUIDs[6]) +
            extractUUID16(serviceUUIDs[7])
        return lotSeq.substring(0, 10).toLong(16)
    }

    private fun parseSeqNo(): Int {
        val serviceUUIDs = scanRecord.serviceUuids
        val lotSeq = extractUUID16(serviceUUIDs[7]) +
            extractUUID16(serviceUUIDs[8])
        return lotSeq.substring(2).toInt(16)
    }

    override fun toString(): String {
        return "BleDiscoveredDevice{" +
            "scanRecord=" + scanRecord +
            ", podID=" + podId +
            "scanResult=" + scanResult +
            ", sequenceNo=" + sequenceNo +
            ", lotNo=" + lotNo +
            '}'
    }

    companion object {

        const val MAIN_SERVICE_UUID = "4024"
        const val UNKNOWN_THIRD_SERVICE_UUID = "000a" // FIXME: why is this 000a?
        private fun extractUUID16(uuid: ParcelUuid): String {
            return uuid.toString().substring(4, 8)
        }
    }

    init {
        validateServiceUUIDs()
        validatePodId()
        lotNo = parseLotNo()
        sequenceNo = parseSeqNo()
    }
}
