package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.operations;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattDescriptor;
import android.os.SystemClock;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import app.aaps.core.interfaces.logging.AAPSLogger;
import app.aaps.core.interfaces.logging.LTag;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.RileyLinkBLE;

/**
 * Created by geoff on 5/26/16.
 */
public class DescriptorWriteOperation extends BLECommOperation {

    private final AAPSLogger aapsLogger;

    private final BluetoothGattDescriptor descr;


    public DescriptorWriteOperation(AAPSLogger aapsLogger, BluetoothGatt gatt, BluetoothGattDescriptor descr, byte[] value) {
        this.aapsLogger = aapsLogger;
        this.gatt = gatt;
        this.descr = descr;
        this.value = value;
    }


    @Override
    public void gattOperationCompletionCallback(UUID uuid, byte[] value) {
        super.gattOperationCompletionCallback(uuid, value);
        operationComplete.release();
    }


    @SuppressWarnings({"deprecation"})
    @Override
    public void execute(RileyLinkBLE comm) {
        descr.setValue(value);
        gatt.writeDescriptor(descr);
        // wait here for callback to notify us that value was read.
        try {
            boolean didAcquire = operationComplete.tryAcquire(getGattOperationTimeout_ms(), TimeUnit.MILLISECONDS);
            if (didAcquire) {
                SystemClock.sleep(1); // This is to allow the IBinder thread to exit before we continue, allowing easier
                // understanding of the sequence of events.
                // success
            } else {
                aapsLogger.error(LTag.PUMPBTCOMM, "Timeout waiting for descriptor write operation to complete");
                timedOut = true;
            }
        } catch (InterruptedException e) {
            aapsLogger.error(LTag.PUMPBTCOMM, "Interrupted while waiting for descriptor write operation to complete");
            interrupted = true;
        }
    }
}
