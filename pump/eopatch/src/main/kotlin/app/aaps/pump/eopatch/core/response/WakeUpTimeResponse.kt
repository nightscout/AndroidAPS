package app.aaps.pump.eopatch.core.response

import app.aaps.pump.eopatch.core.code.PatchBleResultCode
import kotlin.time.Duration.Companion.seconds

class WakeUpTimeResponse(success: Boolean, private val wakeUpSecond: Int) : BaseResponse() {

    init {
        resultCode = if (success) PatchBleResultCode.SUCCESS else PatchBleResultCode.UNKNOWN_ERROR
    }

    val timeInMillis: Long get() = wakeUpSecond.toLong().seconds.inWholeMilliseconds
}
