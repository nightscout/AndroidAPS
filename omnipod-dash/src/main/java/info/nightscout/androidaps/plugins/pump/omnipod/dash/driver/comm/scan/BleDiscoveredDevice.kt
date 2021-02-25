package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.scan

import android.bluetooth.le.ScanResult
import android.os.ParcelUuid
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions.DiscoveredInvalidPodException

class BleDiscoveredDevice(val scanResult: ScanResult, private val podID: Long) {

    private val sequenceNo: Int
    private val lotNo: Long
    @Throws(DiscoveredInvalidPodException::class)
    private fun validateServiceUUIDs() {
        val scanRecord = scanResult.scanRecord
            ?: throw DiscoveredInvalidPodException("Scan record is null");
        val serviceUUIDs = scanRecord.serviceUuids
        if (serviceUUIDs.size != 9) {
            throw DiscoveredInvalidPodException("Expected 9 service UUIDs, got" + serviceUUIDs.size, serviceUUIDs)
        }
        if (extractUUID16(serviceUUIDs[0]) != "4024") {
            // this is the service that we filtered for
            throw DiscoveredInvalidPodException("The first exposed service UUID should be 4024, got " + extractUUID16(serviceUUIDs[0]), serviceUUIDs)
        }
        // TODO understand what is serviceUUIDs[1]. 0x2470. Alarms?
        if (extractUUID16(serviceUUIDs[2]) != "000a") {
            // constant?
            throw DiscoveredInvalidPodException("The third exposed service UUID should be 000a, got " + serviceUUIDs[2], serviceUUIDs)
        }
    }

    @Throws(DiscoveredInvalidPodException::class)
    private fun validatePodID() {
        val scanRecord = scanResult.scanRecord
        val serviceUUIDs = scanRecord.serviceUuids
        val hexPodID = extractUUID16(serviceUUIDs[3]) + extractUUID16(serviceUUIDs[4])
        val podID = hexPodID.toLong(16)
        if (this.podID != podID) {
            throw DiscoveredInvalidPodException("This is not the POD we are looking for. " + this.podID + " found: " + podID, serviceUUIDs)
        }
    }

    private fun parseLotNo(): Long {
        val scanRecord = scanResult.scanRecord
        val serviceUUIDs = scanRecord.serviceUuids
        val lotSeq = extractUUID16(serviceUUIDs[5]) +
            extractUUID16(serviceUUIDs[6]) +
            extractUUID16(serviceUUIDs[7])
        return lotSeq.substring(0, 10).toLong(16)
    }

    private fun parseSeqNo(): Int {
        val scanRecord = scanResult.scanRecord
        val serviceUUIDs = scanRecord.serviceUuids
        val lotSeq = extractUUID16(serviceUUIDs[7]) +
            extractUUID16(serviceUUIDs[8])
        return lotSeq.substring(2).toInt(16)
    }

    override fun toString(): String {
        return "BleDiscoveredDevice{" +
            "scanResult=" + scanResult +
            ", podID=" + podID +
            ", sequenceNo=" + sequenceNo +
            ", lotNo=" + lotNo +
            '}'
    }

    companion object {

        private fun extractUUID16(uuid: ParcelUuid): String {
            return uuid.toString().substring(4, 8)
        }
    }

    init {
        validateServiceUUIDs()
        validatePodID()
        lotNo = parseLotNo()
        sequenceNo = parseSeqNo()
    }
}