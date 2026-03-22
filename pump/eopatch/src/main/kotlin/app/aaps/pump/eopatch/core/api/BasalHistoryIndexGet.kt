package app.aaps.pump.eopatch.core.api

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.pump.eopatch.core.ble.BaseAPI
import app.aaps.pump.eopatch.core.scan.IBleDevice
import javax.inject.Inject
import javax.inject.Singleton
import app.aaps.pump.eopatch.core.ble.BytesConverter
import app.aaps.pump.eopatch.core.ble.PatchFunc
import app.aaps.pump.eopatch.core.response.BasalHistoryIndexResponse
import io.reactivex.rxjava3.core.Single

@Singleton
class BasalHistoryIndexGet @Inject constructor(patch: IBleDevice, aapsLogger: AAPSLogger) : BaseAPI<BasalHistoryIndexResponse>(PatchFunc.GET_BASAL_HISTORY_INDEX, patch, aapsLogger) {
    override fun parse(bytes: ByteArray): BasalHistoryIndexResponse {
        val lastIndex = BytesConverter.toUInt(bytes[DATA1], bytes[DATA2])
        val curIndex = BytesConverter.toUInt(bytes[DATA3], bytes[DATA4])
        return BasalHistoryIndexResponse(lastIndex, curIndex)
    }

    fun get(): Single<BasalHistoryIndexResponse> = writeAndRead(generate())
}
