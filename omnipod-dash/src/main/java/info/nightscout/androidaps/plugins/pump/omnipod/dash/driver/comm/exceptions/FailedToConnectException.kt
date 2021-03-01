package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions

open class FailedToConnectException : Exception {
    constructor() : super()
    constructor(message: String?) : super(message)
}
