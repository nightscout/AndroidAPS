package info.nightscout.androidaps.plugins.PumpDanaRS.events;

import android.bluetooth.BluetoothDevice;

/**
 * Created by mike on 01.09.2017.
 */

public class EventDanaRSConnection {
    public EventDanaRSConnection(boolean isConnect, boolean isBusy, boolean isError, BluetoothDevice device) {
        this.isConnect = isConnect;
        this.isBusy = isBusy;
        this.isError = isError;
        this.device = device;
    }

    public boolean isConnect;
    public boolean isBusy;
    public boolean isError;
    public BluetoothDevice device;
}
