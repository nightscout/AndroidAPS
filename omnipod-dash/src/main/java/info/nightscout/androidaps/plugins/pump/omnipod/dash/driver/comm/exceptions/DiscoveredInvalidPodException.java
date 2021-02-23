package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions;

import android.os.ParcelUuid;

import java.util.List;

public class DiscoveredInvalidPodException extends Exception {
    public DiscoveredInvalidPodException(String message, List<ParcelUuid> serviceUUIds) {
        super(message + " service UUIDs: " + serviceUUIds);
    }
}
