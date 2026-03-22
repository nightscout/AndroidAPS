package app.aaps.pump.eopatch.core.response

import app.aaps.pump.eopatch.core.code.PatchBleResultCode
import java.util.concurrent.TimeUnit

abstract class BaseResponse(
    var resultCode: PatchBleResultCode = PatchBleResultCode.SUCCESS
) {

    val timestamp: Long = System.currentTimeMillis()

    val isSuccess: Boolean get() = resultCode.isSuccess

    fun convertSecondToMilli(timeSec: Int): Long = TimeUnit.SECONDS.toMillis(timeSec.toLong())

    override fun toString(): String = "{resultCode:$resultCode}"
}
