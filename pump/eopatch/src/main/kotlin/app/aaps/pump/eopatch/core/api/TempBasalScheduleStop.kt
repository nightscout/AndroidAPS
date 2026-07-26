package app.aaps.pump.eopatch.core.api

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.pump.eopatch.core.ble.BaseBooleanAPI
import app.aaps.pump.eopatch.core.scan.IBleDevice
import javax.inject.Inject
import javax.inject.Singleton
import app.aaps.pump.eopatch.core.ble.PatchFunc
import app.aaps.pump.eopatch.core.response.PatchBooleanResponse
import io.reactivex.rxjava3.core.Single

@Singleton
class TempBasalScheduleStop @Inject constructor(patch: IBleDevice, aapsLogger: AAPSLogger) : BaseBooleanAPI(PatchFunc.STOP_TEMP_BASAL, patch, aapsLogger) {
    fun stop(): Single<PatchBooleanResponse> = writeAndRead(generate())
}
