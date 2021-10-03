package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.session

interface DisconnectHandler {

    fun onConnectionLost(status: Int)
}
