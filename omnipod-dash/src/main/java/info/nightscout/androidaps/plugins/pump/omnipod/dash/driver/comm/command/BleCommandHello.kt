package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.command

import java.nio.ByteBuffer

class BleCommandHello(controllerId: Int) : BleCommand(
    BleCommandType.HELLO,
    ByteBuffer.allocate(6)
        .put(1.toByte()) // TODO find the meaning of this constant
        .put(4.toByte()) // TODO find the meaning of this constant
        .putInt(controllerId).array()
)
