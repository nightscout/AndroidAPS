package app.aaps.pump.omnipod.common.bledriver.comm.io

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.pump.omnipod.common.bledriver.comm.OmnipodDashBleManagerImpl
import app.aaps.pump.omnipod.common.bledriver.comm.callbacks.BleCommCallbacks
import app.aaps.pump.omnipod.common.bledriver.comm.command.BleCommand
import app.aaps.pump.omnipod.common.bledriver.comm.command.BleCommandHello
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.CmdBleIO as CmdBleIOInterface
import java.util.concurrent.BlockingQueue

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
), CmdBleIOInterface {

    override fun peekCommand(): ByteArray? {
        return incomingPackets.peek()
    }

    override fun hello() {
        sendAndConfirmPacket(BleCommandHello(OmnipodDashBleManagerImpl.CONTROLLER_ID).data)
    }

    override fun expectCommandType(expected: BleCommand, timeoutMs: Long): BleConfirmResult {
        return receivePacket(timeoutMs)?.let {
            if (it.isNotEmpty() && it[0] == expected.data[0])
                BleConfirmSuccess
            else
                BleConfirmIncorrectData(it)
        }
            ?: BleConfirmError("Error reading packet")
    }
}
