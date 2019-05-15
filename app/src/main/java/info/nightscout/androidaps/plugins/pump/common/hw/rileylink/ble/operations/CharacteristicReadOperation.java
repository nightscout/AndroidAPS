package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.operations;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.os.SystemClock;

import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.RileyLinkBLE;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.data.GattAttributes;

/**
 * Created by geoff on 5/26/16.
 */
public class CharacteristicReadOperation extends BLECommOperation {

    private static final Logger LOG = LoggerFactory.getLogger(L.PUMPBTCOMM);

    private BluetoothGattCharacteristic characteristic;


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
                SystemClock.sleep(1); // This is to allow the IBinder thread to exit before we continue, allowing easier
                                      // understanding of the sequence of events.
                // success
            } else {
                LOG.error("Timeout waiting for gatt write operation to complete");
                timedOut = true;
            }
        } catch (InterruptedException e) {
            if (isLogEnabled())
                LOG.error("Interrupted while waiting for gatt write operation to complete");
            interrupted = true;
        }
        value = characteristic.getValue();
    }


    @Override
    public void gattOperationCompletionCallback(UUID uuid, byte[] value) {
        super.gattOperationCompletionCallback(uuid, value);
        if (!characteristic.getUuid().equals(uuid)) {
            LOG.error(String.format(
                "Completion callback: UUID does not match! out of sequence? Found: %s, should be %s",
                GattAttributes.lookup(characteristic.getUuid()), GattAttributes.lookup(uuid)));
        }
        operationComplete.release();
    }

    private boolean isLogEnabled() {
        return L.isEnabled(L.PUMPBTCOMM);
    }

}
