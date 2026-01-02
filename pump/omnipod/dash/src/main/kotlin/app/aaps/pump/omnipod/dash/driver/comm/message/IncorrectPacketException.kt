package app.aaps.pump.omnipod.dash.driver.comm.message

import app.aaps.core.utils.toHex

class IncorrectPacketException(
    val payload: ByteArray,
    expectedIndex: Byte? = null
) : Exception("Invalid payload: ${payload.toHex()}. Expected index: $expectedIndex")
