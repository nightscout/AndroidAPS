package app.aaps.pump.omnipod.dash.driver.comm.exceptions

open class ScanException : Exception {
    constructor(message: String) : super(message)
    constructor(errorCode: Int) : super("errorCode$errorCode")
}
