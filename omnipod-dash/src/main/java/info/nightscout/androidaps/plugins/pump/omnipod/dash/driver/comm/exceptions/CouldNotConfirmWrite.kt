package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions

class CouldNotConfirmWrite(private val sent: ByteArray, private val confirmed: ByteArray?) : Exception()