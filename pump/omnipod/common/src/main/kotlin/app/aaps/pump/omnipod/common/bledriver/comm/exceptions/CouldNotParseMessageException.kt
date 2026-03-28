package app.aaps.pump.omnipod.common.bledriver.comm.exceptions

import app.aaps.core.utils.toHex

class CouldNotParseMessageException(val payload: ByteArray) :
    Exception("Could not parse message payload: ${payload.toHex()}")
