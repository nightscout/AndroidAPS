package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.scan;

import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions.DiscoveredInvalidPodException;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions.ScanFailException;

public class ScanCollector extends ScanCallback {
    private final AAPSLogger logger;
    private final long podID;
    // there could be different threads calling the onScanResult callback
    private final ConcurrentHashMap<String, ScanResult> found;
    private int scanFailed;

    public ScanCollector(AAPSLogger logger, long podID) {
        this.podID = podID;
        this.logger = logger;
        this.found = new ConcurrentHashMap<String, ScanResult>();
    }

    @Override
    public void onScanResult(int callbackType, ScanResult result) {
        // callbackType will be ALL
        this.logger.debug(LTag.PUMPBTCOMM, "Scan found: "+result.toString());
        this.found.put(result.getDevice().getAddress(), result);
    }

    @Override
    public void onScanFailed(int errorCode) {
        this.scanFailed = errorCode;
        this.logger.warn(LTag.PUMPBTCOMM, "Scan failed with errorCode: "+errorCode);
        super.onScanFailed(errorCode);
    }

    public List<BleDiscoveredDevice> collect()
            throws ScanFailException {
        List<BleDiscoveredDevice> ret = new ArrayList<>();

        if (this.scanFailed != 0) {
            throw new ScanFailException(this.scanFailed);
        }

        logger.debug(LTag.PUMPBTCOMM, "ScanCollector looking for podID: " + this.podID);

        for (ScanResult result : this.found.values()) {
            try {
                BleDiscoveredDevice device = new BleDiscoveredDevice(result, this.podID);
                ret.add(device);
                logger.debug(LTag.PUMPBTCOMM, "ScanCollector found: " + result.toString() + "Pod ID: " + this.podID);
            } catch (DiscoveredInvalidPodException e) {
                logger.debug(LTag.PUMPBTCOMM, "ScanCollector: pod not matching" + e.toString());
                // this is not the POD we are looking for
            }
        }
        return Collections.unmodifiableList(ret);
    }
}

