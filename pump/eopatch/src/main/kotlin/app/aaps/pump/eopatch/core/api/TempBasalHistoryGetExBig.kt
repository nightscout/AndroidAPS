package app.aaps.pump.eopatch.core.api

import app.aaps.pump.eopatch.core.ble.AppConstant
import app.aaps.pump.eopatch.core.ble.BaseAPI
import app.aaps.pump.eopatch.core.ble.BytesConverter
import app.aaps.pump.eopatch.core.ble.PatchFunc
import app.aaps.pump.eopatch.core.define.IPatchConstant.Companion.BASAL_HISTORY_SIZE_BIG
import app.aaps.pump.eopatch.core.define.IPatchConstant.Companion.BASAL_SEQ_MAX
import app.aaps.pump.eopatch.core.response.BasalHistoryResponse
import app.aaps.pump.eopatch.core.util.FloatAdjusters
import io.reactivex.rxjava3.core.Single
import kotlin.math.min

class TempBasalHistoryGetExBig : BaseAPI<BasalHistoryResponse>(PatchFunc.GET_TEMP_BASAL_HISTORY_EX) {
    private var mCount = 0

    override fun parse(bytes: ByteArray): BasalHistoryResponse {
        val seq = BytesConverter.toUInt(bytes[DATA1], bytes[DATA2])
        var count = min(BASAL_SEQ_MAX - seq, BASAL_HISTORY_SIZE_BIG)
        count = min(mCount, count)
        val injectedDoseValues = FloatArray(count) { i ->
            val pumpCount = BytesConverter.toUInt(bytes[DATA3 + i])
            FloatAdjusters.FLOOR2_INSULIN(pumpCount * AppConstant.INSULIN_UNIT_P)
        }
        return BasalHistoryResponse(seq, injectedDoseValues, true)
    }

    fun get(seqNum: Int, count: Int): Single<BasalHistoryResponse> {
        mCount = count
        return writeAndRead(allocate().putShort(seqNum).build())
    }
}
