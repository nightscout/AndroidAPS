package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions

open class FailedToConnectException(message: String? = null) : Exception("Failed to connect: ${message ?: ""}")
