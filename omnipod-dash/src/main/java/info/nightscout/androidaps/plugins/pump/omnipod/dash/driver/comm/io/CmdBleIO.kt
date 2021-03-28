package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.io

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.OmnipodDashBleManagerImpl
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.callbacks.BleCommCallbacks
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.command.BleCommand
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.command.BleCommandHello
import java.util.concurrent.BlockingQueue

sealed class BleConfirmResult

object BleConfirmSuccess : BleConfirmResult()
data class BleConfirmIncorrectData(val payload: ByteArray) : BleConfirmResult()
data class BleConfirmError(val msg: String, val cause: Throwable? = null) : BleConfirmResult()

class CmdBleIO(
    logger: AAPSLogger,
    characteristic: BluetoothGattCharacteristic,
    private val incomingPackets: BlockingQueue<ByteArray>,
    gatt: BluetoothGatt,
    bleCommCallbacks: BleCommCallbacks
) : BleIO(
    logger,
    characteristic,
    incomingPackets,
    gatt,
    bleCommCallbacks,
    CharacteristicType.CMD
) {
    init {
    }

    fun peekCommand(): ByteArray? {
        return incomingPackets.peek()
    }

    fun hello() = sendAndConfirmPacket(BleCommandHello(OmnipodDashBleManagerImpl.CONTROLLER_ID).data)


    fun expectCommandType(expected: BleCommand, timeoutMs: Long = DEFAULT_IO_TIMEOUT_MS): BleConfirmResult {
        return when (val actual = receivePacket(timeoutMs)) {
            is BleReceiveError -> BleConfirmError(actual.toString())
            is BleReceivePayload ->
                if (actual.payload.isEmpty() || actual.payload[0] != expected.data[0]) {
                    BleConfirmIncorrectData(actual.payload)
                } else {
                    BleConfirmSuccess
                }
        }
    }
}

