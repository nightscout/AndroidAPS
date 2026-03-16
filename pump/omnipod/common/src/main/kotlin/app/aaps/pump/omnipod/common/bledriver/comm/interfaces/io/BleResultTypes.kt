package app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io

sealed class BleSendResult

object BleSendSuccess : BleSendResult()
data class BleSendErrorSending(val msg: String, val cause: Throwable? = null) : BleSendResult()
data class BleSendErrorConfirming(val msg: String, val cause: Throwable? = null) : BleSendResult()

sealed class BleConfirmResult

object BleConfirmSuccess : BleConfirmResult()
data class BleConfirmIncorrectData(val payload: ByteArray) : BleConfirmResult()
data class BleConfirmError(val msg: String) : BleConfirmResult()
