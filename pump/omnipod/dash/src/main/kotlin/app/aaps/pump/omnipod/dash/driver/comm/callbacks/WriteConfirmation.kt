package app.aaps.pump.omnipod.dash.driver.comm.callbacks

sealed class WriteConfirmation

data class WriteConfirmationSuccess(val uuid: String, val payload: ByteArray) : WriteConfirmation()

data class WriteConfirmationError(
    val msg: String,
    val status: Int = 0
) : WriteConfirmation()
