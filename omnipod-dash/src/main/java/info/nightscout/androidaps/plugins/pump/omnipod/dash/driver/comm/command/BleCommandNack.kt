package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.command

import java.nio.ByteBuffer

class BleCommandNack(idx: Byte): BleCommand(BleCommandType.NACK, byteArrayOf(idx))
