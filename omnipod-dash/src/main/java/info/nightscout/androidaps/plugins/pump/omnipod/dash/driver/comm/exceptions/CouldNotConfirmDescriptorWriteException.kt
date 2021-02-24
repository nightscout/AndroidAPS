package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions

class CouldNotConfirmDescriptorWriteException(private val received: String, private val expected: String) : Exception()