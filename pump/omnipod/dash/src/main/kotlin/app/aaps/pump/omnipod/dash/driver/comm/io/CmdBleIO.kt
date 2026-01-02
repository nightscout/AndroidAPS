package app.aaps.pump.omnipod.dash.driver.comm.io

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.pump.omnipod.dash.driver.comm.OmnipodDashBleManagerImpl
import app.aaps.pump.omnipod.dash.driver.comm.callbacks.BleCommCallbacks
import app.aaps.pump.omnipod.dash.driver.comm.command.BleCommand
import app.aaps.pump.omnipod.dash.driver.comm.command.BleCommandHello
import java.util.concurrent.BlockingQueue

sealed class BleConfirmResult

object BleConfirmSuccess : BleConfirmResult()
data class BleConfirmIncorrectData(val payload: ByteArray) : BleConfirmResult()
data class BleConfirmError(val msg: String) : BleConfirmResult()

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

    fun peekCommand(): ByteArray? {
        return incomingPackets.peek()
    }

    fun hello() = sendAndConfirmPacket(BleCommandHello(OmnipodDashBleManagerImpl.CONTROLLER_ID).data)

    fun expectCommandType(expected: BleCommand, timeoutMs: Long = DEFAULT_IO_TIMEOUT_MS): BleConfirmResult {
        return receivePacket(timeoutMs)?.let {
            if (it.isNotEmpty() && it[0] == expected.data[0])
                BleConfirmSuccess
            else
                BleConfirmIncorrectData(it)
        }
            ?: BleConfirmError("Error reading packet")
    }
}
