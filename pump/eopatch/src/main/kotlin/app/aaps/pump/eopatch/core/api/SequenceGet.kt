package app.aaps.pump.eopatch.core.api

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.pump.eopatch.core.ble.BaseAPI
import app.aaps.pump.eopatch.core.scan.IBleDevice
import javax.inject.Inject
import javax.inject.Singleton
import app.aaps.pump.eopatch.core.ble.PatchFunc
import app.aaps.pump.eopatch.core.response.KeyResponse
import io.reactivex.rxjava3.core.Single

@Singleton
class SequenceGet @Inject constructor(patch: IBleDevice, aapsLogger: AAPSLogger) : BaseAPI<KeyResponse>(PatchFunc.GET_SEQ_NUM, patch, aapsLogger) {
    override fun parse(bytes: ByteArray): KeyResponse = KeyResponse.create(bytes[SEQ_INDEX], bytes[SEQ_INDEX + 1])
    fun get(): Single<KeyResponse> = writeAndRead(generate())

    companion object {
        private const val SEQ_INDEX = 37
    }
}
