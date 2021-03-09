package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions

open class FailedToConnectException : Exception {
    constructor(message: String?) : super("Failed to connect: ${message ?: ""}")
}
