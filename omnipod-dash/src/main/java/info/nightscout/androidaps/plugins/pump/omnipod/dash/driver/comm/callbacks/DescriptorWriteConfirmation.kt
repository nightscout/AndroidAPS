package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.callbacks

sealed class DescriptorWriteConfirmation

data class DescriptorWriteConfirmationUUID(val uuid: String) : DescriptorWriteConfirmation()

data class DescriptorWriteConfirmationError(val status: Int) : DescriptorWriteConfirmation()