package app.aaps.pump.omnipod.common.bledriver.comm.session

interface ConnectionStateChangeHandler {

    fun onConnectionStateChange(status: Int, newState: Int)
}
