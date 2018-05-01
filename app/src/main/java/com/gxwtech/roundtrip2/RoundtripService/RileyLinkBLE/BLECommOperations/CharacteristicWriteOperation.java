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
public class CharacteristicWriteOperation extends BLECommOperation {
    private static final String TAG = "CharacteristicWriteOp";
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic characteristic;
    private byte[] value;
    private Semaphore operationComplete = new Semaphore(0,true);

    public CharacteristicWriteOperation(BluetoothGatt gatt, BluetoothGattCharacteristic chara, byte[] value) {
        this.gatt = gatt;
        this.characteristic = chara;
        this.value = value;
    }
    @Override
    public void execute(RileyLinkBLE comm) {

        characteristic.setValue(value);
        gatt.writeCharacteristic(characteristic);
        // wait here for callback to notify us that value was written.
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

    }

    // This will be run on the IBinder thread
    @Override
    public void gattOperationCompletionCallback(UUID uuid, byte[] value) {
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
