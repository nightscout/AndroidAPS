package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions

import info.nightscout.androidaps.utils.extensions.toHex

class CouldNotParseMessageException(val payload: ByteArray) : Exception("Could not parse message payload: ${payload.toHex()}")
