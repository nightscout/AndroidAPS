package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.command

class BleCommandNack(idx: Byte) : BleCommand(BleCommandType.NACK, byteArrayOf(idx))
