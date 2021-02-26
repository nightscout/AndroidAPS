package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions

open class ScanFailException : Exception {
    constructor()
    constructor(errorCode: Int) : super("errorCode$errorCode")
}