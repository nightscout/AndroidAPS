package org.monkey.d.ruffy.ruffy.driver;

import android.bluetooth.BluetoothDevice;

/**
 * Created by fishermen21 on 15.05.17.
 */

public interface BTHandler {
    void deviceConnected();

    void log(String s);

    void fail(String s);

    void deviceFound(BluetoothDevice bd);

    void handleRawData(byte[] buffer, int bytes);

    void requestBlueTooth();
}
