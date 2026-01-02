package app.aaps.pump.omnipod.dash.driver.comm.session

interface ConnectionStateChangeHandler {

    fun onConnectionStateChange(status: Int, newState: Int)
}
