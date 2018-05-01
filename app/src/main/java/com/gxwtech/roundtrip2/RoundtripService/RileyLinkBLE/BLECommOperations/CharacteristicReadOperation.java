package com.gxwtech.roundtrip2.RoundtripService.RileyLinkBLE.BLECommOperations;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.os.SystemClock;
import android.util.Log;

import com.gxwtech.roundtrip2.RoundtripService.RileyLinkBLE.GattAttributes;
import com.gxwtech.roundtrip2.RoundtripService.RileyLinkBLE.RileyLinkBLE;

import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Created by geoff on 5/26/16.
 */
public class CharacteristicReadOperation extends BLECommOperation {
    private static final String TAG = "CharacteristicReadOp";
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic characteristic;
    private byte[] value;
    private Semaphore operationComplete = new Semaphore(0,true);
    public CharacteristicReadOperation(BluetoothGatt gatt, BluetoothGattCharacteristic chara) {
        this.gatt = gatt;
        this.characteristic = chara;
    }
    @Override
    public void execute(RileyLinkBLE comm) {
        gatt.readCharacteristic(characteristic);
        // wait here for callback to notify us that value was read.
        try {
            boolean didAcquire = operationComplete.tryAcquire(getGattOperationTimeout_ms(), TimeUnit.MILLISECONDS);
            if (didAcquire) {
                SystemClock.sleep(1); // This is to allow the IBinder thread to exit before we continue, allowing easier understanding of the sequence of events.
                // success
            } else {
                Log.e(TAG,"Timeout waiting for gatt write operation to complete");
                timedOut = true;
            }
        } catch (InterruptedException e) {
            Log.e(TAG,"Interrupted while waiting for gatt write operation to complete");
            interrupted = true;
        }
        value = characteristic.getValue();
    }

    @Override
    public void gattOperationCompletionCallback(UUID uuid, byte[] value) {
        super.gattOperationCompletionCallback(uuid, value);
        if (!characteristic.getUuid().equals(uuid)) {
            Log.e(TAG,String.format("Completion callback: UUID does not match! out of sequence? Found: %s, should be %s",
                    GattAttributes.lookup(characteristic.getUuid()),GattAttributes.lookup(uuid)));
        }
        operationComplete.release();
    }

    @Override
    public byte[] getValue() {
        return value;
    }
}
