package app.aaps.pump.omnipod.common.bledriver.comm.legacy.io

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.pump.omnipod.common.bledriver.comm.OmnipodDashBleManagerImpl
import app.aaps.pump.omnipod.common.bledriver.comm.command.BleCommand
import app.aaps.pump.omnipod.common.bledriver.comm.command.BleCommandHello
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io.BleConfirmError
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io.BleConfirmIncorrectData
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io.BleConfirmResult
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io.BleConfirmSuccess
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io.CmdBleIO as CmdBleIOInterface
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io.CharacteristicType
import app.aaps.pump.omnipod.common.bledriver.comm.legacy.callbacks.BleCommCallbacks
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

    override fun hello() = sendAndConfirmPacket(BleCommandHello(OmnipodDashBleManagerImpl.CONTROLLER_ID).data)

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
