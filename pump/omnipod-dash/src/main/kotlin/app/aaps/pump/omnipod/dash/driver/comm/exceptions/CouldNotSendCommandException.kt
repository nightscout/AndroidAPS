package app.aaps.pump.omnipod.dash.driver.comm.exceptions

class CouldNotSendCommandException(val msg: String = "Could not send command") : Exception(msg)
