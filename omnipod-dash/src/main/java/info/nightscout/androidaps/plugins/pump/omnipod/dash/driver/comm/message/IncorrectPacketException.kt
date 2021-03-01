package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.message

import info.nightscout.androidaps.utils.extensions.toHex

class IncorrectPacketException(val expectedIndex: Byte, val payload: ByteArray) : Exception("Invalid payload: ${payload.toHex()}. Expected index: $expectedIndex")
