package app.aaps.pump.omnipod.common.bledriver.comm.exceptions

open class ScanException : Exception {
    constructor(message: String) : super(message)
    constructor(errorCode: Int) : super("errorCode$errorCode")
}
