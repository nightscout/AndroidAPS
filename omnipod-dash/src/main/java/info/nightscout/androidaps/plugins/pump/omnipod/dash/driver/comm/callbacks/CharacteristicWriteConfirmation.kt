package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.callbacks

sealed class CharacteristicWriteConfirmation

class CharacteristicWriteConfirmationPayload(val payload: ByteArray, val status: Int) : CharacteristicWriteConfirmation()

class CharacteristicWriteConfirmationError(val status: Int) : CharacteristicWriteConfirmation()