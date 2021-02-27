package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions

class CouldNotConfirmDescriptorWriteException(override val message: String?) : Exception(message) {
    constructor(sent: String, confirmed: String) : this("Could not confirm write. Sent: {$sent} .Received: ${confirmed}")
    constructor(status: Int) : this("Could not confirm write. Write status: ${status}")
}