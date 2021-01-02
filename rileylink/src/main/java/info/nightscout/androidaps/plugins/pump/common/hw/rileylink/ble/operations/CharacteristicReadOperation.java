package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.operations;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.os.SystemClock;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.RileyLinkBLE;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.data.GattAttributes;

/**
 * Created by geoff on 5/26/16.
 */
public class CharacteristicReadOperation extends BLECommOperation {

    private final AAPSLogger aapsLogger;

    private final BluetoothGattCharacteristic characteristic;


    public CharacteristicReadOperation(AAPSLogger aapsLogger, BluetoothGatt gatt, BluetoothGattCharacteristic chara) {
        this.aapsLogger = aapsLogger;
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
                SystemClock.sleep(1); // This is to allow the IBinder thread to exit before we continue, allowing easier
                // understanding of the sequence of events.
                // success
            } else {
                aapsLogger.error(LTag.PUMPBTCOMM, "Timeout waiting for gatt write operation to complete");
                timedOut = true;
            }
        } catch (InterruptedException e) {
            aapsLogger.error(LTag.PUMPBTCOMM, "Interrupted while waiting for gatt write operation to complete");
            interrupted = true;
        }
        value = characteristic.getValue();
    }


    @Override
    public void gattOperationCompletionCallback(UUID uuid, byte[] value) {
        super.gattOperationCompletionCallback(uuid, value);
        if (!characteristic.getUuid().equals(uuid)) {
            aapsLogger.error(LTag.PUMPBTCOMM, String.format(
                    "Completion callback: UUID does not match! out of sequence? Found: %s, should be %s",
                    GattAttributes.lookup(characteristic.getUuid()), GattAttributes.lookup(uuid)));
        }
        operationComplete.release();
    }
}
