package app.aaps.pump.omnipod.common.bledriver.comm.exceptions

class CouldNotSendCommandException(val msg: String = "Could not send command") : Exception(msg)
