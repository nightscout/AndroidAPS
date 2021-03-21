package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.message

import info.nightscout.androidaps.utils.extensions.toHex

class IncorrectPacketException(
    val payload: ByteArray,
    val expectedIndex: Byte? = null
) : Exception("Invalid payload: ${payload.toHex()}. Expected index: $expectedIndex")
