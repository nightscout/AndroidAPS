package app.aaps.pump.omnipod.common.bledriver.comm.session

interface DisconnectHandler {

    fun onConnectionLost(status: Int)
}
