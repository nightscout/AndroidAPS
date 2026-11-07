package app.aaps.pump.eopatch.core.api

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.pump.eopatch.core.ble.BaseBooleanAPI
import app.aaps.pump.eopatch.core.ble.PatchFunc
import app.aaps.pump.eopatch.core.response.PatchBooleanResponse
import app.aaps.pump.eopatch.core.scan.IBleDevice
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.reactivex.rxjava3.core.Single

@SingleIn(AppScope::class)
@Inject
class BuzzerStop(patch: IBleDevice, aapsLogger: AAPSLogger) : BaseBooleanAPI(PatchFunc.STOP_BUZZER, patch, aapsLogger) {
    fun stop(): Single<PatchBooleanResponse> = writeAndRead(generate())
}
