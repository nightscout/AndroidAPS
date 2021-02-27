package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.message

import info.nightscout.androidaps.utils.extensions.toHex

class CrcMismatchException(val expected: Long, val got: Long, val payload: ByteArray) :
    Exception("CRC missmatch. Got: ${got}. Expected: ${expected}. Payload: ${payload.toHex()}")