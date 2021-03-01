package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.callbacks

sealed class CharacteristicWriteConfirmation

data class CharacteristicWriteConfirmationPayload(val payload: ByteArray) : CharacteristicWriteConfirmation()

data class CharacteristicWriteConfirmationError(val status: Int) : CharacteristicWriteConfirmation()
