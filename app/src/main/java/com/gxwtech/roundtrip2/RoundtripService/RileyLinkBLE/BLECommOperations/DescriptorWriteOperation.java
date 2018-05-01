package com.gxwtech.roundtrip2.RoundtripService.RileyLinkBLE.BLECommOperations;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattDescriptor;
import android.os.SystemClock;
import android.util.Log;

import com.gxwtech.roundtrip2.RoundtripService.RileyLinkBLE.RileyLinkBLE;

import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Created by geoff on 5/26/16.
 */
public class DescriptorWriteOperation extends BLECommOperation {

    private static final String TAG = "DescrWriteOp";
    private BluetoothGatt gatt;
    private BluetoothGattDescriptor descr;
    private byte[] value;
    private Semaphore operationComplete = new Semaphore(0,true);

    public DescriptorWriteOperation(BluetoothGatt gatt, BluetoothGattDescriptor descr, byte[] value) {
        this.gatt = gatt;
        this.descr = descr;
        this.value = value;
    }

    @Override
    public void gattOperationCompletionCallback(UUID uuid, byte[] value) {
        super.gattOperationCompletionCallback(uuid, value);
        operationComplete.release();
    }

    @Override
    public void execute(RileyLinkBLE comm) {
        descr.setValue(value);
        gatt.writeDescriptor(descr);
        // wait here for callback to notify us that value was read.
        try {
            boolean didAcquire = operationComplete.tryAcquire(getGattOperationTimeout_ms(), TimeUnit.MILLISECONDS);
            if (didAcquire) {
                SystemClock.sleep(1); // This is to allow the IBinder thread to exit before we continue, allowing easier understanding of the sequence of events.
                // success
            } else {
                Log.e(TAG,"Timeout waiting for descriptor write operation to complete");
                timedOut = true;
            }
        } catch (InterruptedException e) {
            Log.e(TAG,"Interrupted while waiting for descriptor write operation to complete");
            interrupted = true;
        }
    }
}
