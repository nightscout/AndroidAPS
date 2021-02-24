package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothProfile;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;

public class BleCommCallbacks extends BluetoothGattCallback {
    private final CountDownLatch serviceDiscoveryComplete;
    private final CountDownLatch connected;
    private final AAPSLogger aapsLogger;
    private final Map<CharacteristicType, BlockingQueue<byte[]>> incomingPackets;

    public BleCommCallbacks(AAPSLogger aapsLogger, Map<CharacteristicType, BlockingQueue<byte[]>> incomingPackets) {
        this.serviceDiscoveryComplete = new CountDownLatch(1);
        this.connected = new CountDownLatch(1);
        this.aapsLogger = aapsLogger;
        this.incomingPackets = incomingPackets;
    }


    @Override public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
        super.onConnectionStateChange(gatt, status, newState);
        this.aapsLogger.debug(LTag.PUMPBTCOMM,"OnConnectionStateChange discovered with status/state"+status+"/"+newState);
        if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
            this.connected.countDown();
        }
    }

    @Override public void onServicesDiscovered(BluetoothGatt gatt, int status) {
        super.onServicesDiscovered(gatt, status);
        this.aapsLogger.debug(LTag.PUMPBTCOMM,"OnServicesDiscovered with status"+status);
        if (status == gatt.GATT_SUCCESS) {
            this.serviceDiscoveryComplete.countDown();
        }
    }

    public void waitForConnection(int timeout_ms)
        throws InterruptedException {
        this.connected.await(timeout_ms, TimeUnit.MILLISECONDS);
    }

    public void waitForServiceDiscovery(int timeout_ms)
        throws InterruptedException {
        this.serviceDiscoveryComplete.await(timeout_ms, TimeUnit.MILLISECONDS);
    }

}
