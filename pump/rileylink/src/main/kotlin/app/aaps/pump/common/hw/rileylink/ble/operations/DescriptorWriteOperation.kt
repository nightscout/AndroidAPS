package app.aaps.pump.common.hw.rileylink.ble.operations

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattDescriptor
import android.os.SystemClock
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.common.hw.rileylink.ble.RileyLinkBLE
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Created by geoff on 5/26/16.
 */
class DescriptorWriteOperation(private val aapsLogger: AAPSLogger, private val gatt: BluetoothGatt, private val descr: BluetoothGattDescriptor, value: ByteArray) : BLECommOperation() {

    init {
        this.value = value
    }

    override fun gattOperationCompletionCallback(uuid: UUID, value: ByteArray) {
        super.gattOperationCompletionCallback(uuid, value)
        operationComplete.release()
    }

    @Suppress("deprecation") override fun execute(comm: RileyLinkBLE) {
        descr.setValue(value)
        gatt.writeDescriptor(descr)
        // wait here for callback to notify us that value was read.
        try {
            val didAcquire = operationComplete.tryAcquire(getGattOperationTimeout_ms().toLong(), TimeUnit.MILLISECONDS)
            if (didAcquire) {
                SystemClock.sleep(1) // This is to allow the IBinder thread to exit before we continue, allowing easier
                // understanding of the sequence of events.
                // success
            } else {
                aapsLogger.error(LTag.PUMPBTCOMM, "Timeout waiting for descriptor write operation to complete")
                timedOut = true
            }
        } catch (e: InterruptedException) {
            aapsLogger.error(LTag.PUMPBTCOMM, "Interrupted while waiting for descriptor write operation to complete")
            interrupted = true
        }
    }
}
