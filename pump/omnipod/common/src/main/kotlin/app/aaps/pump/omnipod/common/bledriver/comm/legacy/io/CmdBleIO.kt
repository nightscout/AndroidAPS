package app.aaps.pump.omnipod.common.bledriver.comm.legacy.io

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.os.SystemClock
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.omnipod.common.bledriver.comm.OmnipodDashBleManagerImpl
import app.aaps.pump.omnipod.common.bledriver.comm.command.BleCommand
import app.aaps.pump.omnipod.common.bledriver.comm.command.BleCommandHello
import app.aaps.pump.omnipod.common.bledriver.comm.command.BleCommandType
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io.BleConfirmError
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io.BleConfirmIncorrectData
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io.BleConfirmResult
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io.BleConfirmSuccess
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io.CharacteristicType
import app.aaps.pump.omnipod.common.bledriver.comm.legacy.callbacks.BleCommCallbacks
import java.util.concurrent.BlockingQueue
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io.CmdBleIO as CmdBleIOInterface

class CmdBleIO(
    private val aapsLogger: AAPSLogger,
    characteristic: BluetoothGattCharacteristic,
    private val incomingPackets: BlockingQueue<ByteArray>,
    gatt: BluetoothGatt,
    bleCommCallbacks: BleCommCallbacks
) : BleIO(
    aapsLogger,
    characteristic,
    incomingPackets,
    gatt,
    bleCommCallbacks,
    CharacteristicType.CMD
), CmdBleIOInterface {

    override fun peekCommand(): ByteArray? {
        return incomingPackets.peek()
    }

    override fun hello() = hello(OmnipodDashBleManagerImpl.CONTROLLER_ID)

    /**
     * O5 sends its own certificate-derived controller id here (not the Dash
     * [OmnipodDashBleManagerImpl.CONTROLLER_ID]). The id announced in this handshake must
     * match the source id used later in the pairing messages (SP1/SP2), or the pod aborts
     * the pairing.
     */
    fun hello(controllerId: Int) = sendAndConfirmPacket(BleCommandHello(controllerId).data)

    override fun expectCommandType(expected: BleCommand, timeoutMs: Long): BleConfirmResult {
        val deadlineMs = SystemClock.elapsedRealtime() + timeoutMs
        while (true) {
            val remainingMs = deadlineMs - SystemClock.elapsedRealtime()
            if (remainingMs <= 0) {
                return BleConfirmError("Error reading packet")
            }
            val received = receivePacket(remainingMs) ?: return BleConfirmError("Error reading packet")
            when {
                received.isEmpty()                                       ->
                    return BleConfirmIncorrectData(received)

                received[0] == expected.data[0]                          ->
                    return BleConfirmSuccess

                received[0] == BleCommandType.PAIR_STATUS.value          -> {
                    aapsLogger.debug(
                        LTag.PUMPBTCOMM,
                        "expectCommandType: skipping intermediate PAIR_STATUS while waiting for $expected"
                    )
                }

                else                                                     ->
                    return BleConfirmIncorrectData(received)
            }
        }
    }
}
