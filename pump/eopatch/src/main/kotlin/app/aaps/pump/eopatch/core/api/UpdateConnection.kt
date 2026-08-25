package app.aaps.pump.eopatch.core.api

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.pump.eopatch.core.ble.BaseAPI
import app.aaps.pump.eopatch.core.scan.IBleDevice
import javax.inject.Inject
import javax.inject.Singleton
import app.aaps.pump.eopatch.core.ble.PatchFunc
import app.aaps.pump.eopatch.core.response.UpdateConnectionResponse
import io.reactivex.rxjava3.core.Single

@Singleton
class UpdateConnection @Inject constructor(patch: IBleDevice, aapsLogger: AAPSLogger) : BaseAPI<UpdateConnectionResponse>(PatchFunc.UPDATE_CONNECTION, patch, aapsLogger) {
    override fun parse(bytes: ByteArray): UpdateConnectionResponse {
        val newState = ByteArray(SIZE)
        System.arraycopy(bytes, NOOP1, newState, 0, SIZE)
        return UpdateConnectionResponse(newState)
    }

    fun get(): Single<UpdateConnectionResponse> = writeAndRead(generate())
    override fun generate(): ByteArray = allocate().putByte(1).putByte(0).build()

    companion object {
        private const val SIZE = 20
    }
}
