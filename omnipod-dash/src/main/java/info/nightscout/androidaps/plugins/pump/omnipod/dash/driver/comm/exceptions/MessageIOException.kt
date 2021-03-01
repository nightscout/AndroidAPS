package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions

class MessageIOException : Exception {
    constructor(msg: String) : super(msg)
    constructor(cause: Throwable) : super("Caught Exception during Message I/O", cause)
}
