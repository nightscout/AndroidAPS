package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.message

import info.nightscout.androidaps.extensions.toHex

class CrcMismatchException(val expected: Long, val actual: Long, val payload: ByteArray) :
    Exception("CRC mismatch. Actual: $actual. Expected: $expected. Payload: ${payload.toHex()}")
