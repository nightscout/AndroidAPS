package app.aaps.pump.eopatch.core.response

import app.aaps.pump.eopatch.core.code.PatchBleResultCode
import kotlin.time.Duration.Companion.seconds

abstract class BaseResponse(
    var resultCode: PatchBleResultCode = PatchBleResultCode.SUCCESS
) {

    val timestamp: Long = System.currentTimeMillis()

    val isSuccess: Boolean get() = resultCode.isSuccess

    fun convertSecondToMilli(timeSec: Int): Long = timeSec.toLong().seconds.inWholeMilliseconds

    override fun toString(): String = "{resultCode:$resultCode}"
}
