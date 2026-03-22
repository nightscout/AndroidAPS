package app.aaps.pump.eopatch.core.response

import app.aaps.pump.eopatch.core.code.PatchBleResultCode
import java.util.concurrent.TimeUnit

class WakeUpTimeResponse(success: Boolean, private val wakeUpSecond: Int) : BaseResponse() {

    init {
        resultCode = if (success) PatchBleResultCode.SUCCESS else PatchBleResultCode.UNKNOWN_ERROR
    }

    val timeInMillis: Long get() = TimeUnit.SECONDS.toMillis(wakeUpSecond.toLong())
}
