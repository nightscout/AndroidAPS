package com.microtechmd.equil.ble.operations;

import android.bluetooth.BluetoothGatt;

import com.microtechmd.equil.ble.EquilBLE;

import java.util.UUID;
import java.util.concurrent.Semaphore;

/**
 * Created by geoff on 5/26/16.
 */
public abstract class BLECommOperation {

    public boolean timedOut = false;
    public boolean interrupted = false;
    protected byte[] value;
    protected BluetoothGatt gatt;
    protected Semaphore operationComplete = new Semaphore(0, true);


    // This is to be run on the main thread
    public abstract void execute(EquilBLE comm);


    public void gattOperationCompletionCallback(UUID uuid, byte[] value) {
    }


    public int getGattOperationTimeout_ms() {
        return 22000;
    }


    public byte[] getValue() {
        return value;
    }
}
