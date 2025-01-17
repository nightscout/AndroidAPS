package app.aaps.pump.omnipod.dash.driver.comm.exceptions

open class FailedToConnectException(message: String? = null) : Exception("Failed to connect: ${message ?: ""}")
