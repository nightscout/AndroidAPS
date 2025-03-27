package app.aaps.pump.common.hw.rileylink.ble.operations

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.os.SystemClock
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.common.hw.rileylink.ble.RileyLinkBLE
import app.aaps.pump.common.hw.rileylink.ble.data.GattAttributes.lookup
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Created by geoff on 5/26/16.
 */
class CharacteristicReadOperation(private val aapsLogger: AAPSLogger, val gatt: BluetoothGatt, val characteristic: BluetoothGattCharacteristic) : BLECommOperation() {

    @Suppress("deprecation") override fun execute(comm: RileyLinkBLE) {
        gatt.readCharacteristic(characteristic)
        // wait here for callback to notify us that value was read.
        try {
            val didAcquire = operationComplete.tryAcquire(getGattOperationTimeout_ms().toLong(), TimeUnit.MILLISECONDS)
            if (didAcquire) {
                SystemClock.sleep(1) // This is to allow the IBinder thread to exit before we continue, allowing easier
                // understanding of the sequence of events.
                // success
            } else {
                aapsLogger.error(LTag.PUMPBTCOMM, "Timeout waiting for gatt write operation to complete")
                timedOut = true
            }
        } catch (e: InterruptedException) {
            aapsLogger.error(LTag.PUMPBTCOMM, "Interrupted while waiting for gatt write operation to complete")
            interrupted = true
        }
        value = characteristic.value
    }

    override fun gattOperationCompletionCallback(uuid: UUID, value: ByteArray) {
        super.gattOperationCompletionCallback(uuid, value)
        if (characteristic.uuid != uuid) {
            aapsLogger.error(
                LTag.PUMPBTCOMM, String.format(
                    "Completion callback: UUID does not match! out of sequence? Found: %s, should be %s",
                    lookup(characteristic.uuid), lookup(uuid)
                )
            )
        }
        operationComplete.release()
    }
}
