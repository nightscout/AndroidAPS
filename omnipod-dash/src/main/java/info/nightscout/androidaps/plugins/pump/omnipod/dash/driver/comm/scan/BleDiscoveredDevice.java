package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.scan;

import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.os.ParcelUuid;

import java.util.List;

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions.DiscoveredInvalidPodException;

public class BleDiscoveredDevice {
    private final ScanResult scanResult;
    private final long podID;
    private final int sequenceNo;
    private final long lotNo;

    public BleDiscoveredDevice(ScanResult scanResult, long searchPodID)
            throws DiscoveredInvalidPodException {

        this.scanResult = scanResult;
        this.podID = searchPodID;

        this.validateServiceUUIDs();
        this.validatePodID();
        this.lotNo = this.parseLotNo();
        this.sequenceNo = this.parseSeqNo();
    }

    private static String extractUUID16(ParcelUuid uuid) {
        return uuid.toString().substring(4, 8);
    }

    private void validateServiceUUIDs()
            throws DiscoveredInvalidPodException {
        ScanRecord scanRecord = scanResult.getScanRecord();
        List<ParcelUuid> serviceUUIDs = scanRecord.getServiceUuids();

        if (serviceUUIDs.size() != 9) {
            throw new DiscoveredInvalidPodException("Expected 9 service UUIDs, got" + serviceUUIDs.size(), serviceUUIDs);
        }
        if (!extractUUID16(serviceUUIDs.get(0)).equals("4024")) {
            // this is the service that we filtered for
            throw new DiscoveredInvalidPodException("The first exposed service UUID should be 4024, got " + extractUUID16(serviceUUIDs.get(0)), serviceUUIDs);
        }
        // TODO understand what is serviceUUIDs[1]. 0x2470. Alarms?
        if (!extractUUID16(serviceUUIDs.get(2)).equals("000a")) {
            // constant?
            throw new DiscoveredInvalidPodException("The third exposed service UUID should be 000a, got " + serviceUUIDs.get(2), serviceUUIDs);
        }
    }

    private void validatePodID()
            throws DiscoveredInvalidPodException {
        ScanRecord scanRecord = scanResult.getScanRecord();
        List<ParcelUuid> serviceUUIDs = scanRecord.getServiceUuids();
        String hexPodID = extractUUID16(serviceUUIDs.get(3)) + extractUUID16(serviceUUIDs.get(4));
        Long podID = Long.parseLong(hexPodID, 16);
        if (this.podID != podID) {
            throw new DiscoveredInvalidPodException("This is not the POD we are looking for. " + this.podID + " found: " + podID, serviceUUIDs);
        }
    }

    private long parseLotNo() {
        ScanRecord scanRecord = scanResult.getScanRecord();
        List<ParcelUuid> serviceUUIDs = scanRecord.getServiceUuids();
        String lotSeq = extractUUID16(serviceUUIDs.get(5)) +
                extractUUID16(serviceUUIDs.get(6)) +
                extractUUID16(serviceUUIDs.get(7));

        return Long.parseLong(lotSeq.substring(0, 10), 16);
    }

    private int parseSeqNo() {
        ScanRecord scanRecord = scanResult.getScanRecord();
        List<ParcelUuid> serviceUUIDs = scanRecord.getServiceUuids();
        String lotSeq = extractUUID16(serviceUUIDs.get(7)) +
                extractUUID16(serviceUUIDs.get(8));

        return Integer.parseInt(lotSeq.substring(2), 16);
    }

    public ScanResult getScanResult() {
        return this.scanResult;
    }

    @Override public String toString() {
        return "BleDiscoveredDevice{" +
                "scanResult=" + scanResult +
                ", podID=" + podID +
                ", sequenceNo=" + sequenceNo +
                ", lotNo=" + lotNo +
                '}';
    }
}
