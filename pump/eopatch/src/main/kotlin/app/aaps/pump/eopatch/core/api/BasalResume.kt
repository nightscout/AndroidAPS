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
class BasalResume @Inject constructor(patch: IBleDevice, aapsLogger: AAPSLogger) : BaseBooleanAPI(PatchFunc.RESUME_NORMAL_BASAL, patch, aapsLogger) {
    override fun generate(): ByteArray = allocate().putByte(DEFAULT).build()
    fun resume(): Single<PatchBooleanResponse> = writeAndRead(generate())

    companion object {
        const val DEFAULT = 0
    }
}
