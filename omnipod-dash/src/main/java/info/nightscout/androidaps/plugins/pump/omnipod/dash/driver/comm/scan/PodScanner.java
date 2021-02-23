package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.scan;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanSettings;
import android.os.ParcelUuid;

import java.util.Arrays;
import java.util.List;

import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions.ScanFailException;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions.ScanFailFoundTooManyException;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions.ScanFailNotFoundException;

public class PodScanner {
    public static final String SCAN_FOR_SERVICE_UUID = "00004024-0000-1000-8000-00805F9B34FB";
    public static final long POD_ID_NOT_ACTIVATED = 4294967294L;
    private static final int SCAN_DURATION_MS = 5000;

    private final BluetoothAdapter bluetoothAdapter;
    private final AAPSLogger logger;

    public PodScanner(AAPSLogger logger, BluetoothAdapter bluetoothAdapter) {
        this.bluetoothAdapter = bluetoothAdapter;
        this.logger = logger;
    }

    public BleDiscoveredDevice scanForPod(String serviceUUID, long podID)
            throws InterruptedException, ScanFailException {
        BluetoothLeScanner scanner = this.bluetoothAdapter.getBluetoothLeScanner();

        ScanFilter filter = new ScanFilter.Builder()
                .setServiceUuid(ParcelUuid.fromString(serviceUUID))
                .build();

        ScanSettings scanSettings = new ScanSettings.Builder()
                .setLegacy(false)
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();

        ScanCollector scanCollector = new ScanCollector(this.logger, podID);
        this.logger.debug(LTag.PUMPBTCOMM, "Scanning with filters: "+ filter.toString() + " settings" + scanSettings.toString());
        scanner.startScan(Arrays.asList(filter), scanSettings, scanCollector);

        Thread.sleep(SCAN_DURATION_MS);

        scanner.flushPendingScanResults(scanCollector);
        scanner.stopScan(scanCollector);

        List<BleDiscoveredDevice> collected = scanCollector.collect();
        if (collected.size() == 0) {
            throw new ScanFailNotFoundException();
        } else if (collected.size() > 1) {
            throw new ScanFailFoundTooManyException(collected);
        }
        return collected.get(0);
    }
}
