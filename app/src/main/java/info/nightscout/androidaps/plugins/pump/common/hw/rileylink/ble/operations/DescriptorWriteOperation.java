package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.operations;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattDescriptor;
import android.os.SystemClock;

import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.RileyLinkBLE;

/**
 * Created by geoff on 5/26/16.
 */
public class DescriptorWriteOperation extends BLECommOperation {

    private static final Logger LOG = LoggerFactory.getLogger(DescriptorWriteOperation.class);

    private BluetoothGattDescriptor descr;


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
                SystemClock.sleep(1); // This is to allow the IBinder thread to exit before we continue, allowing easier
                                      // understanding of the sequence of events.
                // success
            } else {
                LOG.error("Timeout waiting for descriptor write operation to complete");
                timedOut = true;
            }
        } catch (InterruptedException e) {
            LOG.error("Interrupted while waiting for descriptor write operation to complete");
            interrupted = true;
        }
    }
}
