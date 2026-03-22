package app.aaps.pump.eopatch.core.api

import app.aaps.pump.eopatch.core.ble.BaseBooleanAPI
import app.aaps.pump.eopatch.core.ble.PatchFunc
import app.aaps.pump.eopatch.core.response.PatchBooleanResponse
import io.reactivex.rxjava3.core.Single
import kotlin.math.roundToInt

class BasalPause : BaseBooleanAPI(PatchFunc.PAUSE_BASAL) {
    fun pause(hour: Float): Single<PatchBooleanResponse> {
        var h = (hour * 2).roundToInt()
        if (h > 4) h = 0xFF
        return writeAndRead(allocate().putByte(h).build())
    }
}
