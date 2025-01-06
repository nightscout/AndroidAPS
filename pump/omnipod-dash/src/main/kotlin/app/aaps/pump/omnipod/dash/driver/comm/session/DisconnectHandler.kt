package app.aaps.pump.omnipod.dash.driver.comm.session

interface DisconnectHandler {

    fun onConnectionLost(status: Int)
}
